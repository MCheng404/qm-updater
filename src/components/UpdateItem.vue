<script setup lang="ts">
/** 单个更新项：复选框 / 应用图标 / 名称 / 版本变化 / 忽略操作 / 失败入口 */
import { computed } from 'vue';
import { useI18n } from 'vue-i18n';
import { ICON, SOURCE_ICON } from '../app/icons';
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

/** 无 PNG 图标时退化到来源矢量图标（尺寸由 CSS .item-icon-fallback .ico 控制） */
const fallbackIcon = computed(() => SOURCE_ICON[props.item.source] ?? ICON.package);

function onCheck(e: Event): void {
  const input = e.target as HTMLInputElement;
  emit('toggle', props.item.id, input.checked);
  popCheckbox(input);
}
</script>

<template>
  <div class="item" :class="{ checked: checked }">
    <label class="cb">
      <input
        type="checkbox"
        :checked="checked"
        :aria-label="item.name"
        @change="onCheck"
      />
      <span class="box"></span>
    </label>

    <div class="item-icon" :class="{ 'item-icon-fallback': !item.icon }">
      <img v-if="item.icon" :src="item.icon" :alt="item.name" :draggable="false" />
      <span v-else v-html="fallbackIcon"></span>
    </div>

    <div class="item-main">
      <div class="item-name">{{ item.name }}</div>
      <div class="item-meta">{{ item.pkgId ?? $t('group.' + item.source) }}</div>
    </div>

    <div class="item-ver">
      <span class="v-cur">{{ item.current }}</span>
      <span class="v-arrow">→</span>
      <span class="v-new">{{ item.latest }}</span>
      <span v-if="item.tag && item.tag !== 'latest'" class="tag">{{ item.tag }}</span>
    </div>

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
</template>
