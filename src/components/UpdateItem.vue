<script setup lang="ts">
/** 单个更新项卡片：图标 / 名称 / 版本变化 / 忽略操作 / 失败入口 */
import { computed, ref } from 'vue';
import { useI18n } from 'vue-i18n';
import { ICON, SOURCE_ICON } from '../app/icons';
import { matchBuiltinIcon } from '../app/builtin-icons';
import { popCheckbox } from '../app/dom';
import type { UpdateItem as UpdateItemModel } from '../app/types';

const props = defineProps<{
  item: UpdateItemModel;
  checked: boolean;
  failed: boolean;
}>();

const emit = defineEmits<{
  (e: 'toggle', id: string, checked: boolean): void;
  (e: 'ignore-version', item: UpdateItemModel): void;
  (e: 'ignore-forever', item: UpdateItemModel): void;
  (e: 'view-log'): void;
}>();

const { t } = useI18n();

/** 图标优先级：内置图标（主动匹配）→ 后端获取的 PNG → 来源矢量图标 */
const builtinIcon = computed(() => matchBuiltinIcon(props.item));
const iconError = ref(false);
const iconSrc = computed(() => (iconError.value ? null : builtinIcon.value ?? props.item.icon ?? null));
/** 是否来自内置品牌图标（区分瓷片内边距与深色提亮，PNG 保持原样拉满） */
const isBuiltin = computed(() => !iconError.value && builtinIcon.value !== null);
/** 加载失败兜底：切回来源矢量图标 */
function onIconError() {
  iconError.value = true;
}
/** 无 PNG 图标时退化到来源矢量图标（尺寸由 CSS 控制） */
const fallbackIcon = computed(() => SOURCE_ICON[props.item.source] ?? ICON.package);

function onCheck(e: Event): void {
  const input = e.target as HTMLInputElement;
  emit('toggle', props.item.id, input.checked);
  popCheckbox(input);
}
</script>

<template>
  <div class="item" :class="{ checked, failed }">
    <div class="item-head">
      <div class="item-icon" :class="{ 'item-icon-fallback': !iconSrc, 'item-icon-builtin': isBuiltin }">
        <img
          v-if="iconSrc"
          :class="isBuiltin ? 'brand' : 'png'"
          :src="iconSrc"
          :alt="item.name"
          :draggable="false"
          @error="onIconError"
        />
        <span v-else v-html="fallbackIcon"></span>
      </div>
      <div class="item-main">
        <div class="item-name">{{ item.name }}</div>
        <div class="item-meta">{{ item.pkgId ?? $t('group.' + item.source) }}</div>
      </div>
      <label class="cb">
        <input type="checkbox" :checked="checked" :aria-label="item.name" @change="onCheck" />
        <span class="box"></span>
      </label>
    </div>

    <div class="item-foot">
      <div class="item-ver">
        <span class="v-cur">{{ item.current }}</span>
        <span class="v-arrow">→</span>
        <span class="v-new">{{ item.latest }}</span>
        <span v-if="item.tag && item.tag !== 'latest'" class="tag">{{ item.tag }}</span>
      </div>
      <div class="item-tail">
        <button
          v-if="failed"
          class="item-fail"
          type="button"
          :title="$t('action.failedViewLog')"
          @click="emit('view-log')"
        >
          <span v-html="ICON.alert"></span>
          <span>{{ $t('btn.viewLog') }}</span>
        </button>
        <div class="item-actions">
          <button
            class="item-action-btn"
            type="button"
            :title="t('action.ignoreVersion')"
            @click="emit('ignore-version', item)"
          >
            <span v-html="ICON.eyeOff"></span>
          </button>
          <button
            class="item-action-btn danger"
            type="button"
            :title="t('action.ignoreForever')"
            @click="emit('ignore-forever', item)"
          >
            <span v-html="ICON.ban"></span>
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
