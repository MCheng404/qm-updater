/**
 * 共享插件与跨窗口同步 composable。
 *
 * sharedPlugin：安装 Pinia 与 vue-i18n（四个入口共用）。
 * useCrossWindowSync：每个窗口的 setup 里调用一次，负责：
 * - 初始应用主题 / 字体
 * - 监听 theme-changed / app-settings-changed / lang-changed / font-changed
 * - 语言切换时同步到 Rust 后端（set_language）并广播给其它窗口
 * - 系统主题变化时，跟随模式的窗口就地重新应用
 */

import { createPinia } from 'pinia';
import { watch } from 'vue';
import type { App } from 'vue';
import { emit, listen } from '@tauri-apps/api/event';
import { invoke } from '@tauri-apps/api/core';
import { i18n, LANG_KEY, type Lang } from './i18n';
import { applyTheme, watchSystemTheme } from './theme';
import { applyFont, loadFont, watchFont } from './font';
import { SETTINGS_CHANGED_EVENT, type AppSettings } from './app-settings';
import { useThemeStore, useAppSettingsStore } from './stores';
import type { ThemeState } from './theme';

export const sharedPlugin = {
  install(app: App): void {
    app.use(createPinia());
    app.use(i18n);
  },
};

/** 监听 Tauri 事件（非 Tauri 环境下静默失败） */
function safeListen<T>(event: string, handler: (payload: T) => void): void {
  void listen<T>(event, (e) => handler(e.payload)).catch(() => undefined);
}

export function useCrossWindowSync(): void {
  const theme = useThemeStore();
  const appSettings = useAppSettingsStore();

  // 1. 初始应用主题与字体（与旧版各入口 init 的第 3 步一致）
  applyTheme(theme.state);
  applyFont(loadFont());

  // 2. 主题广播：其它窗口（通常是设置窗口）改主题 → 应用
  safeListen<ThemeState>('theme-changed', (t) => theme.applyRemote(t));

  // 3. 系统主题变化：跟随模式的窗口就地重新应用（各窗口各自监听，无需广播）
  watchSystemTheme(() => theme.reapplyIfSystem());

  // 4. 应用设置广播：设置窗口改忽略列表 / 自动检查 → 主窗口应用
  safeListen<AppSettings>(SETTINGS_CHANGED_EVENT, (s) => appSettings.applyRemote(s));

  // 5. 字体广播：设置窗口换字体 → 其它窗口应用
  watchFont((f) => applyFont(f));

  // 6. 语言：本地切换 → 推送后端 + 广播；远端广播 → 应用（不再二次广播）
  const { locale } = i18n.global;
  let lastBroadcast: Lang = locale.value as Lang;

  watch(locale, (l) => {
    document.documentElement.lang = l;
    // 持久化（与旧版 setLang 一致，LANG_KEY 不变）
    try {
      localStorage.setItem(LANG_KEY, l);
    } catch {
      /* 存储不可用时忽略 */
    }
    if ((l as Lang) === lastBroadcast) return; // 来自远端广播，不再回声
    lastBroadcast = l as Lang;
    void invoke('set_language', { lang: l }).catch(() => undefined);
    void emit('lang-changed', l).catch(() => undefined);
  });

  safeListen<Lang>('lang-changed', (l) => {
    if (l === locale.value) return;
    lastBroadcast = l;
    locale.value = l;
    void invoke('set_language', { lang: l }).catch(() => undefined);
  });

  // 7. 启动即把当前语言推给后端（后端 setup 阶段就要产出日志文案）
  void invoke('set_language', { lang: locale.value }).catch(() => undefined);
}
