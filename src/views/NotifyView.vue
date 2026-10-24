<script setup lang="ts">
/**
 * 通知窗口（label=notify）。
 * 由旧版 src/notify.ts + notify.html 移植为 Vue 响应式实现。
 *
 * - 监听 Rust 端 notify-toast 事件，追加消息到 toast 栈
 * - 按 position 渲染堆叠方向（top-* 自上而下 / bottom-* 自下而上）
 * - 窗口尺寸贴合内容（透明留白会拦截鼠标点击），用 ResizeObserver 跟随
 */
import { computed, nextTick, onMounted, ref, watch } from 'vue';
import { invoke } from '@tauri-apps/api/core';
import { listen } from '@tauri-apps/api/event';
import { getCurrentWindow } from '@tauri-apps/api/window';
import ToastCard from '../components/ToastCard.vue';
import { useCrossWindowSync } from '../app/shared';
import type { NotifyPrefs, ToastPayload } from '../app/types';

useCrossWindowSync();

/** 与 Rust 侧 notify::default_prefs() 及设置面板默认值保持一致 */
const DEFAULT_PREFS: NotifyPrefs = {
  mode: 'native',
  position: 'bottom-right',
  durationMs: 4500,
  opacity: 90,
  maxStack: 4,
  stack: 'list',
};

const EXIT_ANIM_MS = 280;
const HIDE_DELAY_MS = 360;
/** 与 styles.css 中 `.toast-root` 的 gap / 上下 padding 保持一致 */
const TOAST_GAP = 8;
const ROOT_PAD = 12;
/**
 * 尺寸容差（逻辑 px）：窗口必须贴合内容，但贴合到像素级反而会被裁
 * （亚像素取整、描边/阴影让真实绘制范围大 1~2px）。给几像素余量。
 */
const SIZE_SLACK = 4;
/** 叠加模式下每张卡露出的高度（逻辑 px） */
const STACK_PEEK = 14;

interface LiveToast extends ToastPayload {
  id: number;
  out: boolean;
}

const prefs = ref<NotifyPrefs>({ ...DEFAULT_PREFS });
const toasts = ref<LiveToast[]>([]);
const root = ref<HTMLElement>();

let seq = 0;
let sizeObserver: ResizeObserver | null = null;

function getWindow(): ReturnType<typeof getCurrentWindow> | null {
  try {
    return getCurrentWindow();
  } catch {
    return null;
  }
}
const win = getWindow();

/* ============ 根容器 class / CSS 变量 ============ */

function directionFor(pos: string): 'top-down' | 'bottom-up' {
  return pos.startsWith('top') ? 'top-down' : 'bottom-up';
}

function alignFor(pos: string): 'align-left' | 'align-right' | 'align-center' {
  if (pos.endsWith('left')) return 'align-left';
  if (pos.endsWith('right')) return 'align-right';
  return 'align-center';
}

const rootClass = computed(() => [
  'toast-root',
  `dir-${directionFor(prefs.value.position)}`,
  alignFor(prefs.value.position),
  prefs.value.stack === 'stacked' ? 'stacked' : '',
]);

// --notif-opacity / --stack-peek 写在 documentElement 上（与旧版一致，CSS 在 body 层也可能引用）
watch(
  prefs,
  (p) => {
    const s = document.documentElement.style;
    s.setProperty('--notif-opacity', String(p.opacity / 100));
    // --stack-peek 是叠加模式卡片的层间步长：CSS 读它、TS 定值，
    // 这样「窗口高度测算」与「实际位移」共用一个真值来源
    s.setProperty('--stack-peek', `${STACK_PEEK}px`);
  },
  { immediate: true, deep: true },
);

/* ============ 窗口尺寸同步 ============ */

/**
 * 把通知窗口的尺寸/位置同步给 Rust 端。
 *
 * ⚠️ 叠加模式下整叠的高度**不是**各卡之和 —— 卡片互相重叠，多一张只多露出 PEEK。
 * 沿用"求和"会让窗口多出一大片透明区，把背后的点击全挡住。
 */
function syncWindowSize(): void {
  const el = root.value;
  if (!el) return;
  const cards = Array.from(el.querySelectorAll<HTMLElement>('.toast'));
  if (cards.length === 0) return;

  const stacked = prefs.value.stack === 'stacked';
  const height = stacked
    ? cards[cards.length - 1].offsetHeight +
      STACK_PEEK * (cards.length - 1) +
      ROOT_PAD * 2 +
      SIZE_SLACK
    : cards.reduce((s, c) => s + c.offsetHeight, 0) +
      TOAST_GAP * (cards.length - 1) +
      ROOT_PAD * 2 +
      SIZE_SLACK;

  void invoke('layout_notify', { height }).catch(() => undefined);
}

/**
 * 让窗口尺寸**跟随内容自动变化**：字体延迟加载、正文换行数变化、
 * 系统缩放都会改变高度，窗口必须跟着变，否则裁内容或多出透明留白。
 */
function watchContentSize(): void {
  const el = root.value;
  if (!el) return;
  if (!sizeObserver) {
    sizeObserver = new ResizeObserver(() => syncWindowSize());
    sizeObserver.observe(el);
  }
  el.querySelectorAll<HTMLElement>('.toast').forEach((card) => {
    if (!card.dataset.sizeObserved) {
      card.dataset.sizeObserved = '1';
      sizeObserver?.observe(card);
    }
  });
}

/* ============ Toast 生命周期 ============ */

function spawnToast(p: ToastPayload): void {
  // 超出最大堆叠数时移除最旧的
  while (toasts.value.length >= prefs.value.maxStack && toasts.value.length > 0) {
    dismissToast(toasts.value[0].id, true);
  }

  const id = ++seq;
  toasts.value.push({ ...p, id, out: false });

  // 确保窗口可见
  void win?.show().catch(() => undefined);

  // 等新节点完成布局后再测量高度，避免读到 0
  nextTick(() => {
    syncWindowSize();
    watchContentSize();
  });
}

function dismissToast(id: number, immediate = false): void {
  const idx = toasts.value.findIndex((t) => t.id === id);
  if (idx < 0) return;

  if (immediate) {
    toasts.value.splice(idx, 1);
    nextTick(syncWindowSize);
  } else {
    toasts.value[idx].out = true;
    window.setTimeout(() => {
      const i = toasts.value.findIndex((t) => t.id === id);
      if (i >= 0) toasts.value.splice(i, 1);
      // 元素真正移除后再收缩窗口，避免动画期间窗口跳变
      syncWindowSize();
    }, EXIT_ANIM_MS);
  }

  // 栈空后延迟隐藏窗口（等待退出动画完成）
  if (toasts.value.length === 0) {
    window.setTimeout(() => {
      if (toasts.value.length === 0) {
        void win?.hide().catch(() => undefined);
      }
    }, HIDE_DELAY_MS);
  }
}

/* ============ 初始化 ============ */

function listenSafe<T>(event: string, handler: (payload: T) => void): void {
  void listen<T>(event, (e) => handler(e.payload)).catch((e) =>
    console.warn(`[notify] ${event} 监听失败`, e),
  );
}

onMounted(() => {
  void invoke<NotifyPrefs>('load_notify_prefs')
    .then((p) => {
      prefs.value = p;
    })
    .catch(() => {
      prefs.value = { ...DEFAULT_PREFS };
    });

  listenSafe<NotifyPrefs>('notify-prefs-changed', (p) => {
    prefs.value = p;
    // 位置/堆叠方向变化后重新校正窗口
    nextTick(() => {
      syncWindowSize();
      watchContentSize();
    });
  });

  listenSafe<ToastPayload>('notify-toast', spawnToast);
  // theme-changed / font-changed 由 useCrossWindowSync 处理
});
</script>

<template>
  <div ref="root" :class="rootClass">
    <ToastCard
      v-for="(toast, i) in toasts"
      :key="toast.id"
      :id="toast.id"
      :title="toast.title"
      :body="toast.body"
      :level="toast.level"
      :ts="toast.ts"
      :duration-ms="toast.durationMs"
      :depth="toasts.length - 1 - i"
      :out="toast.out"
      @dismiss="dismissToast"
    />
  </div>
</template>
