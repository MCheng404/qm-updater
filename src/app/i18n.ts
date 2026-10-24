/**
 * 多语言支持（vue-i18n）。
 *
 * 语言包在 src/app/locales/ 下，与 Rust 侧 i18n.rs 的后端消息表相互独立
 * （后端日志文案仍由 Rust 产出，通过 set_language 命令同步当前语言）。
 *
 * 约定：
 * - 模板里用 {{ $t('key') }} / :title="$t('key')" 等，locale 变化时自动重渲染；
 * - 动态文案（含插值）用 $t('key', { name: value })，占位符为 {name} 形式；
 * - 跨窗口同步：一个窗口切换语言后通过 Tauri 事件 lang-changed 广播。
 */

import { createI18n } from 'vue-i18n';
import zhCN from './locales/zh-CN';
import enUS from './locales/en-US';

export type Lang = 'zh-CN' | 'en-US';

export const LANG_KEY = 'qm-updater.lang';

export const LANGS: Array<{ code: Lang; label: string; flag: string }> = [
  { code: 'zh-CN', label: '简体中文', flag: '🇨🇳' },
  { code: 'en-US', label: 'English', flag: '🇺🇸' },
];

/** 与旧版 detectLang 一致：localStorage 优先，其次系统语言，兜底英文 */
function detectLang(): Lang {
  try {
    const saved = localStorage.getItem(LANG_KEY) as Lang | null;
    if (saved && (saved === 'zh-CN' || saved === 'en-US')) return saved;
  } catch {
    /* ignore */
  }
  if (typeof navigator !== 'undefined') {
    const sys = navigator.language;
    if (sys.startsWith('zh')) return 'zh-CN';
  }
  return 'en-US';
}

export const i18n = createI18n({
  legacy: false,
  locale: detectLang(),
  fallbackLocale: 'zh-CN',
  messages: {
    'zh-CN': zhCN,
    'en-US': enUS,
  },
});
