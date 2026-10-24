<script setup lang="ts">
/**
 * 单条通知卡片：倒计时自动消失、点击关闭、悬停暂停、键盘可达。
 * 退出动画由父组件的 out 标志驱动（.toast-out 播完后父组件才移除）。
 */
import { computed, onBeforeUnmount, onMounted } from 'vue';

const props = defineProps<{
  id: number;
  title: string;
  body: string;
  level: string;
  ts: string;
  durationMs: number;
  /** 叠加模式的深度（0 = 最新、最上层） */
  depth: number;
  out: boolean;
}>();

const emit = defineEmits<{
  (e: 'dismiss', id: number): void;
}>();

const LEVEL_COLOR: Record<string, string> = {
  info: 'var(--info)',
  ok: 'var(--ok)',
  warn: 'var(--warn)',
  err: 'var(--err)',
};

/** 级别色调叠加层（覆盖在卡片底色之上） */
const LEVEL_TINT: Record<string, string> = {
  info: 'rgba(96, 165, 250, 0.16)',
  ok: 'rgba(74, 222, 128, 0.18)',
  warn: 'rgba(251, 191, 36, 0.18)',
  err: 'rgba(248, 113, 113, 0.20)',
};

const stripeColor = computed(() => LEVEL_COLOR[props.level] ?? LEVEL_COLOR.info);

/**
 * 多层背景：级别色调叠加 + 不透明底。
 * 第二层必须是 --toast-base 而不是 --bg-card：窗口去掉 acrylic 后没有材质兜底，
 * 用近乎透明的 --bg-card 会让 toast 在浅色桌面上几乎看不见。
 * 给 var() 带上兜底值：万一变量没定义，整条 background 声明会被判为无效，
 * 卡片就只剩一圈描边、没有底色。
 */
const cardBackground = computed(() => {
  const tint = LEVEL_TINT[props.level] ?? LEVEL_TINT.info;
  const base = 'var(--toast-base, rgba(20, 23, 29, 0.88))';
  return `linear-gradient(${tint}, ${tint}), linear-gradient(${base}, ${base})`;
});

let timer = 0;
let remain = props.durationMs;
let lastTick = Date.now();

function startTimer(ms: number): void {
  window.clearTimeout(timer);
  lastTick = Date.now();
  timer = window.setTimeout(() => emit('dismiss', props.id), ms);
}

onMounted(() => startTimer(remain));
onBeforeUnmount(() => window.clearTimeout(timer));

/** 悬停暂停：扣掉已流逝的时间 */
function onEnter(): void {
  window.clearTimeout(timer);
  remain = Math.max(0, remain - (Date.now() - lastTick));
}

function onLeave(): void {
  startTimer(remain);
}

function dismiss(): void {
  emit('dismiss', props.id);
}

function onKeydown(e: KeyboardEvent): void {
  if (e.key === 'Enter' || e.key === ' ') {
    e.preventDefault();
    dismiss();
  }
}
</script>

<template>
  <div
    class="toast"
    :class="['lv-' + level, { 'toast-out': out }]"
    :style="{ background: cardBackground, '--depth': String(depth) }"
    role="button"
    tabindex="0"
    :aria-label="$t('notify.dismissAria', { title })"
    @click="dismiss"
    @keydown="onKeydown"
    @mouseenter="onEnter"
    @mouseleave="onLeave"
  >
    <div class="toast-stripe" :style="{ background: stripeColor }"></div>
    <div class="toast-main">
      <div class="toast-head">
        <span class="toast-title">{{ title }}</span>
        <span class="toast-time">{{ ts }}</span>
      </div>
      <div class="toast-body">{{ body }}</div>
    </div>
  </div>
</template>
