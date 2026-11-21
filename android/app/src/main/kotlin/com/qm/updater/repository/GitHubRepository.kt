package com.qm.updater.repository

import android.net.Uri
import android.os.Build
import androidx.core.net.toUri
import android.util.Log
import com.qm.updater.BuildConfig
import com.qm.updater.data.github.GitHubApps
import com.qm.updater.data.github.GitHubRelease
import com.qm.updater.data.github.GitHubReleaseAsset
import com.qm.updater.data.ui.AppInstalled
import com.qm.updater.data.ui.AppUpdate
import com.qm.updater.data.ui.GitHubSource
import com.qm.updater.data.ui.Link
import com.qm.updater.data.ui.getApp
import com.qm.updater.prefs.Prefs
import com.qm.updater.service.GitHubService
import com.qm.updater.util.filterVersionTag
import io.github.g00fy2.versioncompare.Version
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.timeout
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.time.Duration.Companion.milliseconds
import java.util.concurrent.ConcurrentHashMap


class GitHubRepository(
    private val service: GitHubService,
    private val prefs: Prefs
) {

    companion object {
        private const val TAG = "GitHubRepository"

        /** 单个仓库的硬超时。 */
        private const val CHECK_DEADLINE_MS = 20_000L

        /** 自身更新指向本仓库（monorepo：安卓端在 android/ 子目录）。 */
        const val SELF_REPO_OWNER = "MCheng404"
        const val SELF_REPO_NAME = "qm-updater"

        /** 安卓发布使用的标签前缀，例如 `app-v3.2.0`。 */
        const val SELF_TAG_PREFIX = "app-v"

        /** Release lists are shared by update scan, search and MCP within this TTL. */
        private const val RELEASE_TTL_MS = 10 * 60 * 1000L

        /** Unauthenticated GitHub allows 60 requests/hour; keep bursts bounded. */
        private const val FETCH_SLOTS = 5
    }

    private data class ReleaseCacheEntry(
        val releases: List<GitHubRelease>,
        val expiresAt: Long
    )

    private val releaseCache = ConcurrentHashMap<String, ReleaseCacheEntry>()
    private val inFlight = ConcurrentHashMap<String, Deferred<List<GitHubRelease>>>()
    private val fetchGate = Semaphore(FETCH_SLOTS)
    private val cacheScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Fetch releases once per owner/repo. Variants of the same repository share
     * the response; a failed refresh falls back to the last successful result.
     */
    private suspend fun releases(user: String, repo: String): List<GitHubRelease> {
        val key = "$user/$repo"
        val cached = releaseCache[key]
        if (cached != null && cached.expiresAt > System.currentTimeMillis()) {
            return cached.releases
        }
        inFlight[key]?.takeIf { it.isActive }?.let { return it.await() }

        val deferred = cacheScope.async {
            fetchGate.withPermit {
                val fetched = runCatching {
                    service.getReleases(user, repo, 50)
                }
                val value = if (fetched.isSuccess) {
                    fetched.getOrDefault(emptyList()).also {
                        releaseCache[key] = ReleaseCacheEntry(
                            it,
                            System.currentTimeMillis() + RELEASE_TTL_MS
                        )
                    }
                } else {
                    releaseCache[key]?.releases?.takeIf { it.isNotEmpty() }
                        ?: fetched.getOrThrow()
                }
                value
            }
        }
        inFlight[key] = deferred
        return try {
            deferred.await()
        } finally {
            inFlight.remove(key, deferred)
        }
    }

    /**
     * 已安装应用的 GitHub 更新检查。
     *
     * === 相对上游的修复 ===
     * 上游这里同样是 `checks.combine { ... }`：必须等**每一个**仓库的请求都返回才 emit，
     * 也就是说任意一个仓库卡住（慢网络、限流重试、仓库已删除）都会让整个 GitHub 源
     * 一条结果都出不来。这与 [UpdatesRepository] 的问题是同一个反模式。
     *
     * 改为并发 + 槽位增量 emit + 单仓库硬超时：某个仓库出问题只影响它自己。
     */
    suspend fun updates(apps: List<AppInstalled>) = flow {
        val checks = mutableListOf(selfCheck())

        GitHubApps.forEach { app ->
            // Self-update is handled by selfCheck(); never skip an ordinary app
            // just because it used to sit at index 0 upstream.
            if (app.packageName == BuildConfig.APPLICATION_ID) return@forEach
            apps.find { it.packageName == app.packageName }?.let {
                checks.add(checkApp(apps, app.user, app.repo, app.packageName, it.version, app.extra))
            }
        }

        if (checks.isEmpty()) {
            emit(emptyList())
            return@flow
        }

        val slots = arrayOfNulls<List<AppUpdate>>(checks.size)
        checks
            .mapIndexed { index, check ->
                check.withDeadline(CHECK_DEADLINE_MS).map { index to it }
            }
            .merge()
            .collect { (index, updates) ->
                slots[index] = updates
                val merged = slots.filterNotNull().flatten()
                if (merged.isNotEmpty() || slots.all { it != null }) emit(merged)
            }
    }.catch {
        emit(emptyList())
        Log.e(TAG, "获取 release 失败。", it)
    }

    suspend fun search(text: String) = flow {
        val checks = mutableListOf<Flow<List<AppUpdate>>>()

        GitHubApps.forEach { app ->
            if (app.repo.contains(text, true) || app.user.contains(text, true) || app.packageName.contains(text, true)) {
                checks.add(checkApp(null, app.user, app.repo, app.packageName, "?", app.extra))
            }
        }

        if (checks.isEmpty()) {
            emit(Result.success(emptyList()))
            return@flow
        }

        // 与 updates() 同样的改造：不再等所有仓库，谁先回来谁先显示
        val slots = arrayOfNulls<List<AppUpdate>>(checks.size)
        checks
            .mapIndexed { index, check ->
                check.withDeadline(CHECK_DEADLINE_MS).map { index to it }
            }
            .merge()
            .collect { (index, updates) ->
                slots[index] = updates
                val merged = slots.filterNotNull().flatten()
                if (merged.isNotEmpty() || slots.all { it != null }) emit(Result.success(merged))
            }
    }.catch {
        emit(Result.failure(it))
        Log.e(TAG, "搜索失败。", it)
    }

    /**
     * 自身更新。
     *
     * 原来查的是**上游** rumboalla/apkupdater —— 那是电脑端那条线，与安卓端无关
     * （用户明确要求不要再查那边，那边已另行修复）。
     * 现在改为查**本项目自己的安卓发布**：`MCheng404/qm-updater`（android/ 子目录）里的 `app-v*` 标签。
     *
     * 标签形如 `app-v3.2.0`，**不含 versionCode**，所以不能沿用上游那套
     * `getVersions("1.2.3 (52)")` 的解析，也不能用 versionCode 判新旧 ——
     * 改用版本名做语义比较。版本护栏 `isRealUpgrade` 同样以版本名为主判定，
     * 因此 versionCode 填 0 不会被误杀。
     */
    private fun selfCheck() = flow {
        val repoReleases = releases(SELF_REPO_OWNER, SELF_REPO_NAME)
            .filter { it.tag_name.startsWith(SELF_TAG_PREFIX) }
            .filter { filterPreRelease(it) }
            .filter { findApkAsset(it.assets).isNotEmpty() }

        val latest = repoReleases
            .maxByOrNull { parseSelfVersion(it.tag_name) ?: Version("0.0.0") }
        val remoteVersion = latest?.let { parseSelfVersion(it.tag_name) }

        if (latest != null && remoteVersion != null) {
            val current = BuildConfig.VERSION_NAME
            val installedVersion = parseVersion(current)
            val newer = installedVersion == null || remoteVersion > installedVersion
            if (newer) {
                // findApkAsset 返回的已经是下载地址字符串，不是 asset 对象
                val apkUrl = findApkAsset(latest.assets)
                emit(listOf(AppUpdate(
                    name = "一键更新",
                    packageName = BuildConfig.APPLICATION_ID,
                    version = remoteVersion.toString(),
                    oldVersion = current,
                    // 本项目的发布标签里没有 versionCode，置 0；
                    // 版本护栏以版本名为准，不会因此漏报
                    versionCode = 0L,
                    oldVersionCode = BuildConfig.VERSION_CODE.toLong(),
                    source = GitHubSource,
                    link = Link.Url(apkUrl),
                    whatsNew = latest.body
                )))
            } else {
                // 没有更新时也要 emit 空列表，后续才能与其它来源合并
                emit(listOf())
            }
        } else {
            // We need to emit empty so it can be combined later
            emit(listOf())
        }
    }.catch {
        emit(emptyList())
        Log.e(TAG, "检查自身更新失败。", it)
    }

    private fun checkApp(
        apps: List<AppInstalled>?,
        user: String,
        repo: String,
        packageName: String,
        currentVersion: String,
        extra: Regex?
    ) = flow {
        val r = releases(user, repo)
        // Select the asset here so a release that cannot produce a usable APK
        // never becomes an update with an empty download URL.
        val candidates = r
            .filter { release ->
                if (packageName == "com.qm.updater.ci") {
                    Regex("CI-Release-3\\.\\d+").containsMatchIn(release.name)
                } else {
                    filterPreRelease(release)
                }
            }
            .mapNotNull { release ->
                val asset = findApkAssetArch(release.assets, extra)
                if (asset.browser_download_url.isBlank()) null else release to asset
            }

        // Release APIs sort by creation time, not semantic version. Pick the
        // highest version explicitly so a late-published older tag is not treated
        // as an upgrade.
        val latest = candidates.maxByOrNull { (release, _) ->
            parseVersion(release.tag_name) ?: Version("0")
        }
        val latestVersion = latest?.let { parseVersion(it.first.tag_name) }
        val installedVersion = parseVersion(currentVersion)
        val newer = latestVersion != null &&
            (installedVersion == null || latestVersion > installedVersion)

        Log.i(
            TAG,
            "检查 $packageName: 候选 release " + candidates.size +
                (if (latest == null) "" else "，最高 tag=${latest.first.tag_name}" +
                    "，本机=$currentVersion，判定=$newer")
        )

        if (latest != null && newer) {
            val (release, asset) = latest
            val app = apps?.getApp(packageName)
            emit(listOf(AppUpdate(
                name = repo,
                packageName = packageName,
                version = release.tag_name,
                oldVersion = app?.version ?: "?",
                versionCode = 0L,
                oldVersionCode = app?.versionCode ?: 0L,
                source = GitHubSource,
                link = Link.Url(asset.browser_download_url, asset.size),
                whatsNew = release.body,
                iconUri = if (apps == null) release.author.avatar_url.toUri() else Uri.EMPTY
            )))
        } else {
            emit(emptyList())
        }
    }.catch {
        emit(emptyList())
        Log.e(TAG, "检查 $packageName 失败。", it)
    }

    /** 从 `app-v3.2.0` 解析出版本对象；标签不匹配时返回 null。 */
    private fun parseSelfVersion(tag: String) = runCatching {
        Version(tag.removePrefix(SELF_TAG_PREFIX))
    }.getOrNull()

    /** Normalize both remote tags and installed version names before comparing. */
    private fun parseVersion(value: String) = runCatching {
        Version(filterVersionTag(value.trim()))
    }.getOrNull()

    private fun filterPreRelease(release: GitHubRelease) = when {
        prefs.ignorePreRelease.get() && release.prerelease -> false
        else -> true
    }

    private fun findApkAsset(assets: List<GitHubReleaseAsset>) = assets
        .filter { it.browser_download_url.endsWith(".apk", true) }
        .maxByOrNull { it.size }
        ?.browser_download_url
        .orEmpty()

    private fun findApkAssetArch(
        assets: List<GitHubReleaseAsset>,
        extra: Regex?
    ): GitHubReleaseAsset {
        val apks = assets
            .filter { it.browser_download_url.endsWith(".apk", true) }
            .filter { filterExtra(it, extra) }

        when {
            apks.isEmpty() -> return GitHubReleaseAsset(0L, "")
            apks.size == 1 -> return apks.first()
            else -> {
                // Try to match exact arch
                Build.SUPPORTED_ABIS.forEach { arch ->
                    apks.forEach { apk ->
                        if (apk.browser_download_url.contains(arch, true)) {
                            return apk
                        }
                    }
                }
                // Try to match arm64
                if (Build.SUPPORTED_ABIS.contains("arm64-v8a")) {
                    apks.forEach { apk ->
                        if (apk.browser_download_url.contains("arm64", true)) {
                            return apk
                        }
                    }
                }
                // Try to match x64
                if (Build.SUPPORTED_ABIS.contains("x86_64")) {
                    apks.forEach { apk ->
                        if (apk.browser_download_url.contains("x64", true)) {
                            return apk
                        }
                    }
                }
                // Try to match arm
                if (Build.SUPPORTED_ABIS.contains("armeabi-v7a")) {
                    apks.forEach { apk ->
                        if (apk.browser_download_url.contains("arm", true)) {
                            return apk
                        }
                    }
                }
                // If no match, return biggest apk in the hope it's universal
                return apks.maxByOrNull { it.size } ?: GitHubReleaseAsset(0L, "")
            }
        }
    }

    private fun filterExtra(asset: GitHubReleaseAsset, extra: Regex?): Boolean {
        if (extra == null) return true
        val caseInsensitive = Regex(extra.pattern, extra.options + RegexOption.IGNORE_CASE)
        return caseInsensitive.containsMatchIn(asset.browser_download_url)
    }

}

/** 给单个仓库检查加硬超时：超时只让该仓库空手而归，不拖累其它仓库。 */
private fun Flow<List<AppUpdate>>.withDeadline(millis: Long) = this
    .timeout(millis.milliseconds)
    .catch { t ->
        if (t !is TimeoutCancellationException) Log.e("GitHubRepository", "仓库检查异常，已跳过。", t)
        emit(emptyList())
    }
