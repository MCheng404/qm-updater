/**
 * Pinia stores：主题与应用设置。
 *
 * 跨窗口同步模式（与旧版一致，但收拢到 store 里）：
 * - 本地修改 → commit()：应用 + 落盘 + emit Tauri 事件广播给其它窗口
 * - 收到广播 → applyRemote()：只应用 + 落盘，**不再广播**（避免多窗口回声环）
 */

import { defineStore } from 'pinia';
import { ref } from 'vue';
import { emit } from '@tauri-apps/api/event';
import {
  DEFAULT_THEME,
  PRESETS,
  applyMode,
  applyTheme,
  loadTheme,
  saveTheme,
  type ThemeMode,
  type ThemeState,
  type WindowMaterial,
  type TextRender,
} from './theme';
import {
  SETTINGS_CHANGED_EVENT,
  DEFAULT_SETTINGS,
  ignorePermanently,
  ignoreThisVersion,
  listIgnored,
  loadSettings,
  removeFromIgnore,
  saveSettings,
  type AppSettings,
} from './app-settings';

export const useThemeStore = defineStore('theme', () => {
  const state = ref<ThemeState>(loadTheme());

  /** 本地修改：应用 + 落盘 + 广播 */
  function commit(): void {
    applyTheme(state.value);
    saveTheme(state.value);
    void emit('theme-changed', JSON.parse(JSON.stringify(state.value))).catch(() => undefined);
  }

  /** 收到其它窗口的广播：只应用，不广播 */
  function applyRemote(t: ThemeState): void {
    state.value = { ...t };
    applyTheme(t);
    saveTheme(t);
  }

  /* ---- 各项修改 ---- */

  function setPreset(id: string): void {
    const p = PRESETS.find((x) => x.id === id);
    if (!p) return;
    state.value = { ...state.value, presetId: p.id, accent: p.accent, text: p.text, dim: p.dim };
    commit();
  }

  function setColor(key: 'accent' | 'text' | 'dim', value: string): void {
    state.value = { ...state.value, [key]: value, presetId: 'custom' };
    commit();
  }

  function resetColor(key: 'accent' | 'text' | 'dim'): void {
    const preset = PRESETS.find((p) => p.id === state.value.presetId) ?? PRESETS[0];
    state.value = { ...state.value, [key]: preset[key] };
    commit();
  }

  function setMode(mode: ThemeMode): void {
    state.value = applyMode(state.value, mode);
    commit();
  }

  function setOpacity(v: number): void {
    state.value = { ...state.value, opacity: v };
    commit();
  }

  function setRadius(v: number): void {
    state.value = { ...state.value, radius: v };
    commit();
  }

  function setReduceMotion(v: boolean): void {
    state.value = { ...state.value, reduceMotion: v };
    commit();
  }

  function setTextRender(v: TextRender): void {
    state.value = { ...state.value, textRender: v };
    commit();
  }

  function setTextStrength(v: number): void {
    state.value = { ...state.value, textStrength: v };
    commit();
  }

  function setMaterial(v: WindowMaterial): void {
    state.value = { ...state.value, material: v };
    commit();
  }

  function resetAll(): void {
    state.value = { ...DEFAULT_THEME };
    commit();
  }

  /** 系统主题变化且处于跟随模式时，就地重新应用（不广播，各窗口各自监听） */
  function reapplyIfSystem(): void {
    if (state.value.mode !== 'system') return;
    // 重新按当前系统模式校正默认文字色（深↔浅切换时白字/黑字要跟着换），
    // 自定义配色不受影响 —— applyMode 只改仍是默认值的那两个字段。
    state.value = applyMode(state.value, 'system');
    applyTheme(state.value);
    saveTheme(state.value);
  }

  return {
    state,
    commit,
    applyRemote,
    setPreset,
    setColor,
    resetColor,
    setMode,
    setOpacity,
    setRadius,
    setReduceMotion,
    setTextRender,
    setTextStrength,
    setMaterial,
    resetAll,
    reapplyIfSystem,
  };
});

export const useAppSettingsStore = defineStore('appSettings', () => {
  const state = ref<AppSettings>(loadSettings());

  function commit(): void {
    saveSettings(state.value);
    void emit(SETTINGS_CHANGED_EVENT, JSON.parse(JSON.stringify(state.value))).catch(
      () => undefined,
    );
  }

  function applyRemote(s: AppSettings): void {
    state.value = { ...s };
  }

  /* ---- 各项修改 ---- */

  function setAutoCheck(v: boolean): void {
    state.value = { ...state.value, autoCheck: v };
    commit();
  }

  function setAutostartDelay(v: number): void {
    state.value = { ...state.value, autostartDelay: v };
    commit();
  }

  function ignoreVersion(itemId: string, latestVersion: string): void {
    state.value = ignoreThisVersion(itemId, latestVersion, state.value);
    commit();
  }

  function ignoreForever(itemId: string): void {
    state.value = ignorePermanently(itemId, state.value);
    commit();
  }

  function removeIgnore(itemId: string): void {
    state.value = removeFromIgnore(itemId, state.value);
    commit();
  }

  const ignoredList = () => listIgnored(state.value);

  return {
    state,
    commit,
    applyRemote,
    setAutoCheck,
    setAutostartDelay,
    ignoreVersion,
    ignoreForever,
    removeIgnore,
    ignoredList,
  };
});

export { DEFAULT_SETTINGS };
