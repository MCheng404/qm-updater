<script setup lang="ts">
/**
 * 自定义标题栏：主窗口 / 设置窗口 / 关于窗口共用。
 * 窗口按钮走 Tauri 窗口 API；设置齿轮由各窗口自行处理（emit open-settings）。
 */
import { getCurrentWindow } from '@tauri-apps/api/window';

const props = defineProps<{
  /** 标题 i18n key（app.title / settings.title / about.title） */
  titleKey: string;
  showSettings?: boolean;
  showMinMax?: boolean;
  admin?: boolean;
}>();

const emit = defineEmits<{
  (e: 'open-settings'): void;
}>();

void props;

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
      <span v-if="admin" class="tb-badge">{{ $t('app.admin') }}</span>
    </div>
    <div class="tb-right">
      <button
        v-if="showSettings"
        class="tb-btn"
        :title="$t('btn.settings')"
        type="button"
        @click="emit('open-settings')"
      >
        <svg viewBox="0 0 12 12">
          <circle cx="6" cy="6" r="1.9" />
          <path
            d="M6 .8v1.4M6 9.8v1.4M1.2 6H2.6M9.4 6h1.4M2.6 2.6l1 1M8.4 8.4l1 1M9.4 2.6l-1 1M3.6 8.4l-1 1"
          />
        </svg>
      </button>
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
