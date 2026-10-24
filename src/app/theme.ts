/**
 * 主题状态与 CSS 变量应用，所有窗口共用。
 *
 * 设计约定（纯色背景版本）：
 * - 窗口不再使用任何透明材质（Acrylic / Mica / Blur），背景是**纯色**；
 * - 层次由 CSS 在 `html[data-theme]` 上给出的一组表面色（--surface-1/2/3）+ 阴影表达；
 * - JS 只负责「用户可调」的部分：强调色、文字色、圆角、文字渲染、深浅模式。
 */

export type ThemeMode = 'light' | 'dark' | 'system';

export interface ThemeState {
  presetId: string;
  accent: string;
  text: string;
  dim: string;
  /** 卡片/控件圆角基准（px） */
  radius: number;
  reduceMotion: boolean;
  mode: ThemeMode;
}

export const THEME_KEY = 'qm-updater.theme';

/* ============ 深色 / 浅色默认文字 ============ */
const DARK_TEXT = '#f2f5f9';
const DARK_DIM = '#a9b4c2';
const LIGHT_TEXT = '#151a21';
const LIGHT_DIM = '#5b6572';

function isDefaultText(t: string): boolean {
  return t === DARK_TEXT || t === LIGHT_TEXT;
}
function isDefaultDim(d: string): boolean {
  return d === DARK_DIM || d === LIGHT_DIM;
}

export const PRESETS: Array<{
  id: string;
  name: string;
  accent: string;
  text: string;
  dim: string;
}> = [
  { id: 'teal', name: '青碧', accent: '#2dd4bf', text: DARK_TEXT, dim: DARK_DIM },
  { id: 'azure', name: '天青', accent: '#60a5fa', text: DARK_TEXT, dim: DARK_DIM },
  { id: 'violet', name: '紫藤', accent: '#a78bfa', text: DARK_TEXT, dim: DARK_DIM },
  { id: 'amber', name: '琥珀', accent: '#fbbf24', text: DARK_TEXT, dim: DARK_DIM },
  { id: 'rose', name: '玫瑰', accent: '#fb7185', text: DARK_TEXT, dim: DARK_DIM },
  { id: 'jade', name: '竹青', accent: '#4ade80', text: DARK_TEXT, dim: DARK_DIM },
];

export const DEFAULT_THEME: ThemeState = {
  presetId: 'teal',
  accent: '#2dd4bf',
  text: DARK_TEXT,
  dim: DARK_DIM,
  radius: 18,
  reduceMotion: false,
  mode: 'system',
};

/* ============ 系统主题检测 ============ */

export function getSystemMode(): 'light' | 'dark' {
  if (typeof window !== 'undefined' && window.matchMedia) {
    return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
  }
  return 'dark';
}

export function resolveMode(mode: ThemeMode): 'light' | 'dark' {
  return mode === 'system' ? getSystemMode() : mode;
}

/**
 * 切换主题模式。如果文字颜色是默认值，自动切换到对应模式的默认文字色。
 */
export function applyMode(theme: ThemeState, mode: ThemeMode): ThemeState {
  const next: ThemeState = { ...theme, mode };
  const resolved = resolveMode(mode);
  if (isDefaultText(theme.text)) {
    next.text = resolved === 'dark' ? DARK_TEXT : LIGHT_TEXT;
  }
  if (isDefaultDim(theme.dim)) {
    next.dim = resolved === 'dark' ? DARK_DIM : LIGHT_DIM;
  }
  return next;
}

/* ============ 工具函数 ============ */

export function hexToRgba(hex: string, alpha: number): string {
  const h = hex.replace('#', '');
  const full =
    h.length === 3
      ? h
          .split('')
          .map((c) => c + c)
          .join('')
      : h;
  const r = parseInt(full.slice(0, 2), 16);
  const g = parseInt(full.slice(2, 4), 16);
  const b = parseInt(full.slice(4, 6), 16);
  return `rgba(${r}, ${g}, ${b}, ${alpha})`;
}

/* ============ 应用主题 ============ */

export function applyTheme(theme: ThemeState): void {
  const s = document.documentElement.style;
  const resolved = resolveMode(theme.mode);

  // 强调色（含派生：柔和底、光晕、实心按钮上的文字色）
  s.setProperty('--accent', theme.accent);
  s.setProperty('--accent-soft', hexToRgba(theme.accent, 0.16));
  s.setProperty('--accent-softer', hexToRgba(theme.accent, 0.08));
  s.setProperty('--accent-glow', hexToRgba(theme.accent, 0.35));
  s.setProperty('--accent-line', hexToRgba(theme.accent, 0.45));
  // 实心强调色按钮上的文字：所有预设都是中高亮度，统一用近黑保证对比度
  s.setProperty('--on-accent', '#08120f');

  // 文字
  s.setProperty('--text', theme.text);
  s.setProperty('--text-dim', hexToRgba(theme.dim, 0.78));
  s.setProperty('--text-faint', hexToRgba(theme.dim, 0.5));

  // 圆角阶梯（各组件按比例派生，改一个值整体变圆/变方）
  s.setProperty('--radius-base', `${theme.radius}px`);

  document.body.classList.toggle('reduce-motion', theme.reduceMotion);
  document.documentElement.setAttribute('data-theme', resolved);
}

/* ============ 持久化 ============ */

export function saveTheme(theme: ThemeState): void {
  try {
    localStorage.setItem(THEME_KEY, JSON.stringify(theme));
  } catch {
    /* 存储不可用时忽略 */
  }
}

export function loadTheme(): ThemeState {
  try {
    const raw = localStorage.getItem(THEME_KEY);
    if (!raw) return applyMode({ ...DEFAULT_THEME }, DEFAULT_THEME.mode);
    const parsed = JSON.parse(raw) as Partial<ThemeState>;
    const merged = { ...DEFAULT_THEME, ...parsed };
    // 按当前模式校正"默认文字色"：DEFAULT_THEME 的 text/dim 是深色模式的浅色值，
    // 若 mode=system 且系统是浅色，直接套用会出现深色文字压在深色底上。
    return applyMode(merged, merged.mode);
  } catch {
    return applyMode({ ...DEFAULT_THEME }, DEFAULT_THEME.mode);
  }
}

/**
 * 监听系统主题变化，当 mode === 'system' 时自动重新应用主题。
 */
export function watchSystemTheme(onChange: () => void): () => void {
  if (typeof window === 'undefined' || !window.matchMedia) return () => {};
  const mql = window.matchMedia('(prefers-color-scheme: dark)');
  const handler = () => onChange();
  mql.addEventListener('change', handler);
  return () => mql.removeEventListener('change', handler);
}
