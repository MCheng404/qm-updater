package com.qm.updater.repository

import android.util.Log
import com.qm.updater.data.ui.AppUpdate
import com.qm.updater.data.ui.SearchOutcome
import com.qm.updater.domain.AppGrouping
import com.qm.updater.prefs.Prefs
import com.qm.updater.util.filterVersionTag
import io.github.g00fy2.versioncompare.Version
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.timeout
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds

/**
 * Cross-source search.
 *
 * Sources emit progressively. Each source failure is isolated: partial results
 * stay visible and the UI is told how many sources failed.
 */
class SearchRepository(
    private val apkMirrorRepository: ApkMirrorRepository,
    private val fdroidRepository: FdroidRepository,
    private val izzyRepository: FdroidRepository,
    private val aptoideRepository: AptoideRepository,
    private val gitHubRepository: GitHubRepository,
    private val apkPureRepository: ApkPureRepository,
    private val gitLabRepository: GitLabRepository,
    private val playRepository: PlayRepository,
    private val prefs: Prefs
) {

    companion object {
        private const val TAG = "SearchRepository"
        private const val SOURCE_DEADLINE_MS = 30_000L
    }

    fun search(text: String) = flow {
        val sources = mutableListOf<Flow<Result<List<AppUpdate>>>>()
        val sourceNames = mutableListOf<String>()

        fun add(name: String, source: Flow<Result<List<AppUpdate>>>) {
            sourceNames += name
            sources += source
        }

        if (prefs.useApkMirror.get()) add("ApkMirror", apkMirrorRepository.search(text))
        if (prefs.useFdroid.get()) add("F-Droid", fdroidRepository.search(text))
        if (prefs.useIzzy.get()) add("IzzyOnDroid", izzyRepository.search(text))
        if (prefs.useAptoide.get()) add("Aptoide", aptoideRepository.search(text))
        if (prefs.useGitHub.get()) add("GitHub", gitHubRepository.search(text))
        if (prefs.useApkPure.get()) add("APKPure", apkPureRepository.search(text))
        if (prefs.useGitLab.get()) add("GitLab", gitLabRepository.search(text))
        if (prefs.usePlay.get()) add("Play", playRepository.search(text))

        if (sources.isEmpty()) {
            emit(Result.success(SearchOutcome(emptyList())))
            return@flow
        }

        val slots = arrayOfNulls<List<AppUpdate>>(sources.size)
        val failedSources = mutableSetOf<String>()

        sources
            .mapIndexed { index, source ->
                source.withDeadline(SOURCE_DEADLINE_MS).map { index to it }
            }
            .merge()
            .collect { (index, result) ->
                result
                    .onSuccess { slots[index] = it }
                    .onFailure {
                        slots[index] = emptyList()
                        failedSources += sourceNames[index]
                    }

                val merged = slots.filterNotNull().flatten()
                    // Remove only identical candidates. Keep variants from
                    // different sources so the UI can offer a source selector.
                    .distinctBy { candidateKey(it) }
                    .map { it to relevance(it, text) }
                    // Preserve the relevance order of groups; the UI groups by
                    // package without re-sorting alphabetically.
                    .groupBy { it.first.packageName }
                    .values
                    .sortedByDescending { group -> group.maxOf { it.second } }
                    .flatMap { group ->
                        group
                            .sortedWith(
                                compareByDescending<Pair<AppUpdate, Int>> { it.second }
                                    .thenComparator { a, b ->
                                        AppGrouping.CANDIDATE_ORDER.compare(a.first, b.first)
                                    }
                                    .thenBy { it.first.name.lowercase(Locale.ROOT) }
                            )
                            .map { it.first }
                    }

                val settled = slots.all { it != null }
                if (merged.isNotEmpty() || settled) {
                    if (merged.isEmpty() && failedSources.isNotEmpty()) {
                        emit(
                            Result.failure(
                                IllegalStateException(
                                    "Search failed in: ${failedSources.joinToString(", ")}"
                                )
                            )
                        )
                    } else {
                        emit(Result.success(SearchOutcome(merged, failedSources.size)))
                    }
                }
            }
    }.catch {
        emit(Result.failure(it))
        Log.e(TAG, "搜索失败。", it)
    }
}

private fun candidateKey(update: AppUpdate): String =
    listOf(
        update.source.name,
        update.packageName,
        update.versionCode.toString(),
        update.version,
        update.link.toString()
    ).joinToString("|")

private fun Flow<Result<List<AppUpdate>>>.withDeadline(millis: Long) = this
    .timeout(millis.milliseconds)
    .catch { t ->
        if (t !is TimeoutCancellationException) {
            Log.e("SearchRepository", "源异常，已跳过。", t)
        }
        emit(Result.failure(t))
    }

/**
 * Relevance score: exact field matches dominate; source trust and version
 * only break near-ties. Multi-word queries are scored token by token.
 */
private fun relevance(update: AppUpdate, query: String): Int {
    val q = query.trim().lowercase(Locale.ROOT)
    if (q.isEmpty()) return 0

    val fields = listOf(
        update.packageName.lowercase(Locale.ROOT),
        update.name.lowercase(Locale.ROOT)
    )
    val tokens = q.split(Regex("\\s+")).filter { it.isNotBlank() }
    val scores = tokens.mapNotNull { token ->
        fields.maxOf { fieldScore(it, token) }.takeIf { it > 0 }
    }
    if (scores.isEmpty()) return 0
    val base = scores.sum() / scores.size

    val priority = AppGrouping.priorityOf(update.source.name)
    val sourceBonus = if (priority == Int.MAX_VALUE) 0 else 5 - priority.coerceAtMost(5)
    val versionBonus = if (runCatching { Version(filterVersionTag(update.version)) }.getOrNull() != null) 2 else 0
    return base + sourceBonus + versionBonus
}

private fun fieldScore(field: String, token: String): Int = when {
    field == token -> 100
    field.startsWith("$token ") -> 85
    field.startsWith(token) -> 75
    field.contains(" $token") || field.contains("$token ") -> 65
    field.contains(token) -> 50
    else -> 0
}
