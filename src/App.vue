<script setup lang="ts">
/**
 * 单窗口外壳：标题栏 + 左侧导航 + 右侧内容。
 * 更新 / 设置 / 关于 三个视图在同一个无边框窗口内切换（KeepAlive 保留列表状态）。
 */
import { onMounted, ref, watch } from 'vue';
import { useI18n } from 'vue-i18n';
import { listen } from '@tauri-apps/api/event';
import { getVersion } from '@tauri-apps/api/app';
import TitleBar from './components/TitleBar.vue';
import MainView from './views/MainView.vue';
import SettingsView from './views/SettingsView.vue';
import AboutView from './views/AboutView.vue';
import { ICON } from './app/icons';
import { useCrossWindowSync } from './app/shared';

type ViewKey = 'updates' | 'settings' | 'about';

const { t, locale } = useI18n();

useCrossWindowSync();

function readHash(): ViewKey {
  const h = location.hash.replace(/^#\/?/, '');
  if (h === 'settings') return 'settings';
  if (h === 'about') return 'about';
  return 'updates';
}

const view = ref<ViewKey>(readHash());
const version = ref('');

const NAV: Array<{ key: ViewKey; labelKey: string; icon: string }> = [
  { key: 'updates', labelKey: 'nav.updates', icon: ICON.refresh },
  { key: 'settings', labelKey: 'nav.settings', icon: ICON.settings },
  { key: 'about', labelKey: 'nav.about', icon: ICON.info },
];

function navigate(v: ViewKey): void {
  view.value = v;
  // 用 replaceState 而非直接赋值：不产生历史记录，也不触发额外的滚动行为
  if (location.hash !== `#${v}`) {
    history.replaceState(null, '', `#${v}`);
  }
}

onMounted(() => {
  window.addEventListener('hashchange', () => {
    view.value = readHash();
  });

  // Rust 侧 open_settings / open_about 命令改为向本窗口发导航事件
  void listen<string>('navigate', (e) => {
    const v = e.payload;
    if (v === 'updates' || v === 'settings' || v === 'about') navigate(v);
  }).catch(() => undefined);

  void getVersion()
    .then((v) => {
      version.value = v;
    })
    .catch(() => undefined);
});

/* 文档标题固定为应用名（视图切换不改标题） */
const applyTitle = (): void => {
  document.title = t('app.title');
};
watch(locale, applyTitle);
applyTitle();
</script>

<template>
  <div class="app-shell">
    <TitleBar title-key="app.title" :show-min-max="true" />

    <div class="app-body">
      <aside class="sidebar">
        <nav class="nav">
          <button
            v-for="item in NAV"
            :key="item.key"
            class="nav-item"
            :class="{ active: view === item.key }"
            type="button"
            :aria-current="view === item.key ? 'page' : undefined"
            @click="navigate(item.key)"
          >
            <span class="nav-icon" v-html="item.icon"></span>
            <span class="nav-label">{{ $t(item.labelKey) }}</span>
          </button>
        </nav>
        <div class="sidebar-foot">
          <span class="mono">v{{ version || '—' }}</span>
        </div>
      </aside>

      <div class="content-pane">
        <KeepAlive>
          <MainView v-if="view === 'updates'" />
          <SettingsView v-else-if="view === 'settings'" />
          <AboutView v-else />
        </KeepAlive>
      </div>
    </div>
  </div>
</template>
