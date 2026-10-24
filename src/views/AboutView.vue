<script setup lang="ts">
/**
 * 关于窗口（label=about）。
 * 由旧版 src/about.ts + about.html 移植为 Vue 响应式实现。
 */
import { computed, onMounted, ref, watch } from 'vue';
import { useI18n } from 'vue-i18n';
import { getVersion } from '@tauri-apps/api/app';
import { invoke } from '@tauri-apps/api/core';
import { open } from '@tauri-apps/plugin-shell';
import TitleBar from '../components/TitleBar.vue';
import { useCrossWindowSync } from '../app/shared';

useCrossWindowSync();
const { t, locale } = useI18n();

const GITHUB_PROFILE = 'https://github.com/MCheng404';
const GITHUB_REPO = 'https://github.com/MCheng404/qm-updater';
/** GitHub Releases 列表（需要多条才能筛出桌面版，故用列表接口而非 /latest） */
const REPO_API = 'https://api.github.com/repos/MCheng404/qm-updater/releases?per_page=20';

const version = ref('2.1.0');
const arch = ref('x64');
const platform = ref('Windows');
const checking = ref(false);

type StatusKind = 'idle' | 'checking' | 'new' | 'ok' | 'error';
const status = ref<{ kind: StatusKind; version?: string; url?: string }>({ kind: 'idle' });

const statusText = computed(() => {
  switch (status.value.kind) {
    case 'checking':
      return t('about.checking');
    case 'ok':
      return t('about.upToDate');
    case 'error':
      return t('common.error');
    case 'new':
      return `${t('about.newVersion')}: v${status.value.version ?? ''}`;
    default:
      return '';
  }
});

/**
 * 从 releases 列表里挑出**版本号最大的桌面版**。
 *
 * 为什么必须筛：本仓库同时发布 Android 版，tag 形如 `app-v3.1.4`，
 * 且全部标记为 prerelease=false / draft=false —— 所以 /releases/latest 必定返回 Android 版。
 * 桌面版 tag 规则：`v<semver>`（v2.3.0 / v2.2.2 …），按该形状过滤再按 semver 取最大值。
 */
function pickLatestDesktopRelease(releases: unknown): {
  tag: string;
  version: string;
  url?: string;
} | null {
  if (!Array.isArray(releases)) return null;

  let best: { tag: string; version: string; url?: string } | null = null;
  let bestKey: number[] = [0, 0, 0];

  for (const r of releases) {
    if (!r || typeof r !== 'object') continue;
    const rel = r as {
      tag_name?: unknown;
      html_url?: unknown;
      draft?: unknown;
      prerelease?: unknown;
    };
    if (rel.draft === true || rel.prerelease === true) continue;
    const tag = typeof rel.tag_name === 'string' ? rel.tag_name.trim() : '';
    const m = /^v(\d+)\.(\d+)\.(\d+)(?:[-+][0-9A-Za-z.-]+)?$/.exec(tag);
    if (!m) continue; // app-v3.1.4 等非桌面版在此被排除

    const key = [Number(m[1]), Number(m[2]), Number(m[3])];
    const isNewer =
      key[0] > bestKey[0] ||
      (key[0] === bestKey[0] && key[1] > bestKey[1]) ||
      (key[0] === bestKey[0] && key[1] === bestKey[1] && key[2] > bestKey[2]);

    if (!best || isNewer) {
      best = {
        tag,
        version: `${m[1]}.${m[2]}.${m[3]}`,
        url: typeof rel.html_url === 'string' ? rel.html_url : undefined,
      };
      bestKey = key;
    }
  }
  return best;
}

/** 检查 GitHub 最新版本 */
async function checkUpdate(): Promise<void> {
  if (checking.value) return;
  checking.value = true;
  status.value = { kind: 'checking' };

  // GitHub API 在国内网络下可能长时间无响应，必须加超时
  const controller = new AbortController();
  const timer = window.setTimeout(() => controller.abort(), 8000);

  try {
    const res = await fetch(REPO_API, {
      headers: { Accept: 'application/vnd.github.v3+json' },
      signal: controller.signal,
    });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const releases: unknown = await res.json();
    const latest = pickLatestDesktopRelease(releases);
    const current = await getVersion();

    if (!latest) {
      // 列表里一个桌面版都没有：不能报"已是最新"，那是错误结论
      status.value = { kind: 'error' };
    } else if (latest.version !== current) {
      status.value = { kind: 'new', version: latest.version, url: latest.url };
    } else {
      status.value = { kind: 'ok' };
    }
  } catch (e) {
    console.warn('[about] 检查更新失败', e);
    // 网络失败时明确提示错误，而不是谎报"已是最新版本"
    status.value = { kind: 'error' };
  } finally {
    window.clearTimeout(timer);
    checking.value = false;
  }
}

function openExternal(url: string): void {
  void open(url);
}

function openDownload(): void {
  void open(status.value.url ?? GITHUB_REPO);
}

onMounted(() => {
  // 版本号 + 架构 + 平台
  void getVersion()
    .then((v) => {
      version.value = v;
    })
    .catch(() => {
      version.value = '2.1.0';
    });

  void invoke<{ arch: string; platform: string }>('get_system_info')
    .then((info) => {
      arch.value = info.arch === 'x86_64' ? 'x64' : info.arch === 'aarch64' ? 'ARM64' : info.arch;
      platform.value = info.platform === 'windows' ? 'Windows' : info.platform;
    })
    .catch((e) => {
      console.warn('[about] 获取架构信息失败', e);
    });
});

/* 文档标题跟随语言（about.title） */
const applyDocTitle = (): void => {
  document.title = t('about.title');
};
watch(locale, applyDocTitle);
applyDocTitle();
</script>

<template>
  <div class="glass-root about-root">
    <TitleBar title-key="about.title" />

    <div class="about-scroll">
      <div class="about-hero">
        <div class="about-logo">
          <svg viewBox="0 0 64 64" width="56" height="56" fill="none">
            <circle cx="32" cy="32" r="28" stroke="var(--accent)" stroke-width="2.5" opacity="0.6" />
            <path
              d="M20 34l8 8 16-18"
              stroke="var(--accent)"
              stroke-width="3.5"
              stroke-linecap="round"
              stroke-linejoin="round"
            />
            <circle cx="32" cy="32" r="4" fill="var(--accent)" opacity="0.8" />
          </svg>
        </div>
        <h1 class="about-app-name">{{ $t('about.appName') }}</h1>
        <div class="about-version-badge">
          <span>{{ $t('about.version') }}</span>
          <span class="mono">{{ version }}</span>
        </div>
        <div class="about-arch-badge">
          <svg
            viewBox="0 0 16 16"
            width="12"
            height="12"
            fill="none"
            stroke="currentColor"
            stroke-width="1.5"
          >
            <rect x="2" y="3" width="12" height="10" rx="1.5" /><path d="M6 7h4M6 10h4" />
          </svg>
          <span class="mono">{{ arch }}</span>
          <span class="arch-sep">·</span>
          <span>{{ platform }}</span>
        </div>
      </div>

      <p class="about-description">{{ $t('about.description') }}</p>

      <div class="about-section">
        <h3>{{ $t('about.features') }}</h3>
        <ul class="about-features">
          <li><span class="feature-dot"></span><span>{{ $t('about.feature1') }}</span></li>
          <li><span class="feature-dot"></span><span>{{ $t('about.feature2') }}</span></li>
          <li><span class="feature-dot"></span><span>{{ $t('about.feature3') }}</span></li>
          <li><span class="feature-dot"></span><span>{{ $t('about.feature4') }}</span></li>
          <li><span class="feature-dot"></span><span>{{ $t('about.feature5') }}</span></li>
        </ul>
      </div>

      <div class="about-section">
        <h3>{{ $t('about.github') }}</h3>
        <div class="about-links">
          <a
            class="about-link"
            href="#"
            @click.prevent="openExternal(GITHUB_PROFILE)"
          >
            <svg viewBox="0 0 24 24" width="20" height="20" fill="currentColor">
              <path
                d="M12 0C5.37 0 0 5.37 0 12c0 5.31 3.435 9.795 8.205 11.385.6.105.825-.255.825-.57 0-.285-.015-1.23-.015-2.235-3.015.555-3.795-.735-4.035-1.41-.135-.345-.72-1.41-1.23-1.695-.42-.225-1.02-.78-.015-.795.945-.015 1.62.87 1.845 1.23 1.08 1.815 2.805 1.305 3.495.99.105-.78.42-1.305.765-1.605-2.67-.3-5.46-1.335-5.46-5.925 0-1.305.465-2.385 1.23-3.225-.12-.3-.54-1.53.12-3.18 0 0 1.005-.315 3.3 1.23.96-.27 1.98-.405 3-.405s2.04.135 3 .405c2.295-1.56 3.3-1.23 3.3-1.23.66 1.65.24 2.88.12 3.18.765.84 1.23 1.905 1.23 3.225 0 4.605-2.805 5.625-5.475 5.925.435.375.81 1.095.81 2.22 0 1.605-.015 2.895-.015 3.3 0 .315.225.69.825.57A12.02 12.02 0 0024 12c0-6.63-5.37-12-12-12z"
              />
            </svg>
            <span>GitHub Profile</span>
            <svg viewBox="0 0 12 12" width="12" height="12" fill="none" stroke="currentColor" stroke-width="1.5">
              <path d="M3 9l6-6M5 3h4v4" />
            </svg>
          </a>
          <a class="about-link" href="#" @click.prevent="openExternal(GITHUB_REPO)">
            <svg
              viewBox="0 0 24 24"
              width="20"
              height="20"
              fill="none"
              stroke="currentColor"
              stroke-width="1.8"
            >
              <path d="M4 19.5A2.5 2.5 0 016.5 17H20" />
              <path d="M6.5 2H20v20H6.5A2.5 2.5 0 014 19.5v-15A2.5 2.5 0 016.5 2z" />
            </svg>
            <span>{{ $t('about.repository') }}</span>
            <svg viewBox="0 0 12 12" width="12" height="12" fill="none" stroke="currentColor" stroke-width="1.5">
              <path d="M3 9l6-6M5 3h4v4" />
            </svg>
          </a>
        </div>
      </div>

      <div class="about-section">
        <h3>{{ $t('about.checkUpdate') }}</h3>
        <div class="about-update">
          <button class="btn btn-primary" type="button" :disabled="checking" @click="checkUpdate">
            <svg viewBox="0 0 16 16" width="14" height="14" fill="none" stroke="currentColor" stroke-width="1.8">
              <path d="M2 8a6 6 0 0110.5-4M14 8a6 6 0 01-10.5 4M12 2v3h3M4 14v-3H1" />
            </svg>
            <span>{{ $t('about.checkUpdate') }}</span>
          </button>
          <div
            v-if="status.kind !== 'idle'"
            class="about-update-status"
            :class="status.kind === 'new' ? 'new' : status.kind === 'ok' ? 'ok' : status.kind === 'checking' ? 'checking' : ''"
          >
            <span>{{ statusText }}</span>
            <button
              v-if="status.kind === 'new'"
              class="btn btn-primary"
              style="margin-left: 8px"
              type="button"
              @click="openDownload"
            >
              {{ $t('about.download') }}
            </button>
          </div>
        </div>
      </div>

      <div class="about-footer">
        <span>{{ $t('about.madeWith') }}</span>
      </div>
    </div>
  </div>
</template>
