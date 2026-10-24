<script setup lang="ts">
/**
 * 无边框窗口的自定义标题栏：左侧应用名，右侧窗口按钮，整条可拖拽。
 * 单窗口方案下不再有"设置"齿轮（左侧导航已承担入口）。
 */
import { getCurrentWindow } from '@tauri-apps/api/window';

defineProps<{
  /** 标题 i18n key */
  titleKey: string;
  showMinMax?: boolean;
}>();

let appWindow: ReturnType<typeof getCurrentWindow> | null = null;
try {
  appWindow = getCurrentWindow();
} catch {
  /* 非 Tauri 环境：窗口控制不可用 */
}

function onTitlebarMouseDown(e: MouseEvent): void {
  const target = e.target as HTMLElement;
  if (target.closest('.tb-btn')) return;
  if (e.buttons === 1) void appWindow?.startDragging();
}

function onTitlebarDblClick(e: MouseEvent): void {
  const target = e.target as HTMLElement;
  if (target.closest('.tb-btn')) return;
  void appWindow?.toggleMaximize();
}

function minimize(): void {
  void appWindow?.minimize();
}

function toggleMaximize(): void {
  void appWindow?.toggleMaximize();
}

function close(): void {
  void appWindow?.close();
}
</script>

<template>
  <header class="titlebar" @mousedown="onTitlebarMouseDown" @dblclick="onTitlebarDblClick">
    <div class="tb-left">
      <span class="tb-dot"></span>
      <span class="tb-title">{{ $t(titleKey) }}</span>
    </div>
    <div class="tb-right">
      <template v-if="showMinMax">
        <button class="tb-btn" :title="$t('app.minimize')" type="button" @click="minimize">
          <svg viewBox="0 0 10 10"><path d="M1 5h8" /></svg>
        </button>
        <button class="tb-btn" :title="$t('app.maximize')" type="button" @click="toggleMaximize">
          <svg viewBox="0 0 10 10"><rect x="1.2" y="1.2" width="7.6" height="7.6" /></svg>
        </button>
      </template>
      <button class="tb-btn tb-close" :title="$t('common.close')" type="button" @click="close">
        <svg viewBox="0 0 10 10"><path d="M1.5 1.5l7 7M8.5 1.5l-7 7" /></svg>
      </button>
    </div>
  </header>
</template>
