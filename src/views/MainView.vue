<script setup lang="ts">
/**
 * 主窗口（label=main）：四源更新检查 / 列表 / 更新执行 / 日志 / 状态栏。
 * 由旧版 src/main.ts + index.html 移植为 Vue 响应式实现。
 */
import { computed, nextTick, onMounted, ref, watch } from 'vue';
import { useI18n } from 'vue-i18n';
import { invoke } from '@tauri-apps/api/core';
import { listen } from '@tauri-apps/api/event';
import { getCurrentWindow } from '@tauri-apps/api/window';
import UpdateItem from '../components/UpdateItem.vue';
import { ICON, SOURCE_ICON } from '../app/icons';
import { popCheckbox, pulse } from '../app/dom';
import { isItemIgnored } from '../app/app-settings';
import { useAppSettingsStore } from '../app/stores';
import type {
  EnvStatus,
  ItemResult,
  LogLine,
  Progress,
  SourceKind,
  UpdateItem as UpdateItemModel,
} from '../app/types';

const { t } = useI18n();
const appSettingsStore = useAppSettingsStore();
const appSettings = computed(() => appSettingsStore.state);

const SOURCE_ORDER: SourceKind[] = ['npm', 'winget', 'pip', 'openclaw'];
const MAX_LOG_LINES = 800;
const LOG_BADGE: Record<string, string> = {
  ok: 'OK',
  err: 'ERR',
  warn: 'WARN',
  info: 'INFO',
  cmd: 'OUT',
};

/* ============ 状态 ============ */
const items = ref<UpdateItemModel[]>([]);
const checked = ref(new Set<string>());
const busy = ref(false);
const scanned = ref(false);
const failedIds = ref(new Set<string>());
const lastCheckError = ref<string | null>(null);
/** 仅"新一次扫描完成"为 true：播放入场动画；其余渲染一律 false */
const animateItems = ref(false);
const lastEnv = ref<EnvStatus | null>(null);
const isAutostart = ref(false);

const logLines = ref<LogLine[]>([]);
const logFlashing = ref(false);
const logBody = ref<HTMLElement>();
const listScroll = ref<HTMLElement>();
const btnSel = ref<HTMLElement>();
const statusRightEl = ref<HTMLElement>();

const progress = ref({ visible: false, pct: 0, text: '', done: false });
const progressFillKey = ref(0);

/* 状态栏内容以 key + 插值保存，语言切换时 computed 自动重建 */
const statusLeft = ref<{ key: string; vars?: Record<string, string | number> }>({ key: 'status.idle' });
const statusRight = ref<{ key: string | null; vars?: Record<string, string | number> }>({ key: null });
const statusLeftText = computed(() => t(statusLeft.value.key, statusLeft.value.vars ?? {}));
const statusRightText = computed(() =>
  statusRight.value.key ? t(statusRight.value.key, statusRight.value.vars ?? {}) : '',
);

/* ============ 计算属性 ============ */

/** 当前"未被忽略"的项 —— 列表里真正显示出来的那些（items 是全量，含黑名单项） */
const visibleItems = computed(() =>
  items.value.filter((i) => !isItemIgnored(i.id, i.latest, appSettings.value)),
);
const ignoredCount = computed(() => items.value.length - visibleItems.value.length);
const selectedCount = computed(
  () => visibleItems.value.filter((i) => checked.value.has(i.id)).length,
);

interface ItemGroup {
  source: SourceKind;
  items: UpdateItemModel[];
  startIndex: number;
  allChecked: boolean;
  someChecked: boolean;
}

const groups = computed<ItemGroup[]>(() => {
  const out: ItemGroup[] = [];
  let startIndex = 0;
  for (const source of SOURCE_ORDER) {
    const groupItems = visibleItems.value.filter((i) => i.source === source);
    if (groupItems.length === 0) continue;
    const sel = groupItems.filter((i) => checked.value.has(i.id)).length;
    out.push({
      source,
      items: groupItems,
      startIndex,
      allChecked: sel === groupItems.length,
      someChecked: sel > 0 && sel < groupItems.length,
    });
    startIndex += groupItems.length;
  }
  return out;
});

interface EmptyState {
  key: string;
  icon: string;
  vars?: Record<string, string | number>;
  spin?: boolean;
}

/** 列表空状态：传 i18n key 而非成品文本，语言切换自动重建 */
const emptyState = computed<EmptyState | null>((): EmptyState | null => {
  if (!scanned.value) {
    if (busy.value) return { key: 'empty.scanning', icon: ICON.refresh, spin: true };
    return { key: 'empty.notScanned', icon: ICON.search };
  }
  if (lastCheckError.value) {
    return {
      key: 'status.checkFailedDetail',
      icon: ICON.alert,
      vars: { error: lastCheckError.value.slice(0, 90) },
    };
  }
  if (visibleItems.value.length === 0) {
    if (items.value.length === 0) return { key: 'empty.allLatest', icon: ICON.checkCircle };
    return { key: 'empty.allIgnored', icon: ICON.eyeOffBig, vars: { count: items.value.length } };
  }
  return null;
});

const envChips = computed(() => {
  const env = lastEnv.value;
  if (!env) return [] as Array<{ key: string; text: string; on: boolean; title?: string }>;
  const chips: Array<{ key: string; text: string; on: boolean; title?: string }> = [];
  const pairs: Array<[string, boolean]> = [
    ['npm', env.npm],
    ['winget', env.winget],
    ['pip', env.pip],
    ['openclaw', env.openclaw],
  ];
  for (const [name, ok] of pairs) {
    chips.push({ key: name, text: t(ok ? 'env.ready' : 'env.missing', { name }), on: ok });
  }
  // Node 单独一条：它是 OpenClaw 的硬前置条件（>=24.16 <25 || >=26.1）
  if (env.nodeVersion) {
    chips.push({
      key: 'node',
      text: t(env.nodeOk ? 'env.nodeReady' : 'env.nodeOld', { version: env.nodeVersion }),
      on: env.nodeOk,
      title: env.nodeOk ? undefined : t('env.nodeHint'),
    });
  }
  // 管理员标记（单窗口后从标题栏移到工具条）
  if (env.isAdmin) {
    chips.push({ key: 'admin', text: t('app.admin'), on: true });
  }
  return chips;
});

function sourceIcon(source: SourceKind): string {
  return SOURCE_ICON[source] ?? ICON.package;
}

/* ============ 勾选 ============ */

function onItemToggle(id: string, on: boolean): void {
  if (on) checked.value.add(id);
  else checked.value.delete(id);
}

function onGroupCheck(source: SourceKind, e: Event): void {
  const input = e.target as HTMLInputElement;
  // 只作用于可见项，不能把被忽略的项塞进 checked
  const group = visibleItems.value.filter((i) => i.source === source);
  for (const i of group) {
    if (input.checked) checked.value.add(i.id);
    else checked.value.delete(i.id);
  }
  popCheckbox(input);
}

function onIgnoreVersion(item: UpdateItemModel): void {
  appSettingsStore.ignoreVersion(item.id, item.latest);
  checked.value.delete(item.id);
}

function onIgnoreForever(item: UpdateItemModel): void {
  appSettingsStore.ignoreForever(item.id);
  checked.value.delete(item.id);
}

/** 选中集合收敛到可见项（黑名单在设置窗口改过之后） */
watch(visibleItems, () => {
  const visible = new Set(visibleItems.value.map((i) => i.id));
  for (const id of Array.from(checked.value)) {
    if (!visible.has(id)) checked.value.delete(id);
  }
});

/** 选中数变化时弹一下，给"选中了几个"一个即时反馈 */
watch(selectedCount, () => pulse(btnSel.value, 'pulse-pop'));

/** 更新结束：隐藏进度条（与旧版 setBusy(false) 一致） */
watch(busy, (v) => {
  if (!v) progress.value = { ...progress.value, visible: false };
});

/* ============ 状态栏 ============ */

function setStatusLeft(key: string, vars?: Record<string, string | number>): void {
  statusLeft.value = { key, vars };
}

function setStatusRight(key: string | null, vars?: Record<string, string | number>): void {
  statusRight.value = { key, vars };
}

/* ============ 日志 ============ */

function appendLog(line: LogLine): void {
  logLines.value.push(line);
  if (logLines.value.length > MAX_LOG_LINES) {
    logLines.value.splice(0, logLines.value.length - MAX_LOG_LINES);
  }
  nextTick(() => {
    const el = logBody.value;
    if (el) el.scrollTop = el.scrollHeight;
  });
}

function clearLog(): void {
  logLines.value = [];
}

/** 跳转到日志：滚到最新一行 + 闪一下高亮 */
function jumpToLog(): void {
  nextTick(() => {
    const el = logBody.value;
    if (!el) return;
    el.scrollTop = el.scrollHeight;
    logFlashing.value = true;
    window.setTimeout(() => {
      logFlashing.value = false;
    }, 1400);
  });
}

/* ============ 进度 ============ */

function onProgress(p: Progress): void {
  if (p.status === 'done') {
    progress.value = { visible: true, pct: 100, text: `${p.total} / ${p.total}`, done: true };
    // key 变化 → 元素重建 → done 收尾动画重新播放（等价于旧版的强制重排）
    progressFillKey.value++;
    return;
  }
  const pct = p.total > 0 ? Math.round((p.done / p.total) * 100) : 0;
  progress.value = {
    visible: true,
    pct,
    text: `${p.done} / ${p.total} · ${p.currentName}`,
    done: false,
  };
}

/* ============ 主流程 ============ */

async function doCheck(): Promise<void> {
  if (busy.value) return;
  busy.value = true;
  setStatusLeft('status.checking');
  setStatusRight(null);

  items.value = [];
  checked.value = new Set();
  failedIds.value = new Set();
  lastCheckError.value = null;
  scanned.value = false; // 进入"扫描中"空状态
  animateItems.value = false;

  try {
    const found = await invoke<UpdateItemModel[]>('check_updates');
    items.value = found;
    // 只默认勾选「未被忽略」的项
    checked.value = new Set(
      found
        .filter((i) => !isItemIgnored(i.id, i.latest, appSettings.value))
        .map((i) => i.id),
    );
    scanned.value = true;
    animateItems.value = true; // 新一次扫描完成 —— 播放入场动画
    setStatusLeft('status.checkDone', { count: items.value.length });
    setStatusRight(
      ignoredCount.value > 0 ? 'status.ignoredCount' : null,
      ignoredCount.value > 0 ? { count: ignoredCount.value } : undefined,
    );

    // 异步拉取应用图标（首次会触发 PowerShell 扫描注册表，5~10s）
    try {
      const icons = await invoke<Record<string, string>>('fetch_icons', { items: found });
      if (icons && Object.keys(icons).length > 0) {
        for (const item of items.value) {
          if (icons[item.id]) item.icon = icons[item.id];
        }
      }
    } catch (e) {
      setStatusRight('status.iconFailed', { error: String(e).slice(0, 40) });
    }
  } catch (e) {
    // 检查失败时给明确的错误态，而不是让空列表谎报"全部最新"
    scanned.value = true;
    items.value = [];
    checked.value = new Set();
    lastCheckError.value = String(e);
    setStatusLeft('status.checkFailed');
  } finally {
    busy.value = false;
  }
}

async function doUpdate(target: UpdateItemModel[]): Promise<void> {
  if (busy.value || target.length === 0) return;
  busy.value = true;
  setStatusLeft('status.updatingCount', { count: target.length });
  setStatusRight(null);

  try {
    const results = await invoke<ItemResult[]>('start_update', { items: target });
    const ok = results.filter((r) => r.ok).length;
    const bad = results.length - ok;

    // 失败项留在列表里并带上「查看日志」入口
    failedIds.value = new Set(results.filter((r) => !r.ok).map((r) => r.id));

    // 已成功的项从列表中移除，让「成功 N · 失败 M」保持可见
    if (ok > 0) {
      const doneIds = new Set(results.filter((r) => r.ok).map((r) => r.id));
      items.value = items.value.filter((i) => !doneIds.has(i.id));
      doneIds.forEach((id) => checked.value.delete(id));
    }

    setStatusLeft('status.updateDone');
    setStatusRight('status.updateResult', { ok, failed: bad });
    pulse(statusRightEl.value, 'pulse-pop'); // 结果数字弹一下

    const level = bad === 0 ? 'ok' : ok === 0 ? 'err' : 'warn';
    void invoke('send_notification', {
      title: t('notify.updateComplete'),
      body: t('notify.updateResult', { ok, failed: bad }),
      level,
    }).catch(() => undefined);
  } catch (e) {
    setStatusLeft('status.updateFailed', { error: String(e) });
    void invoke('send_notification', {
      title: t('notify.updateFailed'),
      body: String(e).slice(0, 80),
      level: 'err',
    }).catch(() => undefined);
  } finally {
    busy.value = false;
  }
}

function doUpdateSelected(): void {
  void doUpdate(visibleItems.value.filter((i) => checked.value.has(i.id)));
}

function doUpdateAll(): void {
  void doUpdate(visibleItems.value);
}

/* ============ 版本号距离感应放大 ============ */
const VER_BASE_SIZE = 11.5;
const VER_MAX_SIZE = 15.5;
const VER_RADIUS = 150;
const STAGGER_STEP_MS = 30;
const STAGGER_CAP = 20;

type VerNode = { el: HTMLElement; cx: number; cy: number; t: number };
let verCache: VerNode[] = [];

function cacheVerNodes(): void {
  const root = listScroll.value;
  if (!root) {
    verCache = [];
    return;
  }
  verCache = Array.from(root.querySelectorAll<HTMLElement>('.item-ver')).map((el) => {
    const r = el.getBoundingClientRect();
    // t 置 -1：强制下一帧写一次初始值
    return { el, cx: r.left + r.width / 2, cy: r.top + r.height / 2, t: -1 };
  });
}

function setupProximityZoom(): void {
  const container = listScroll.value;
  if (!container) return;
  let frame = 0;
  let px = -9999;
  let py = -9999;

  const paint = (): void => {
    frame = 0;
    for (const node of verCache) {
      const dx = px - node.cx;
      const dy = py - node.cy;
      const dist = Math.sqrt(dx * dx + dy * dy);

      // smoothstep：靠近时变化柔和，远离时快速回落
      const raw = Math.max(0, Math.min(1, 1 - dist / VER_RADIUS));
      const t = raw * raw * (3 - 2 * raw);

      // 无实质变化就跳过，避免每帧全表重排
      if (Math.abs(t - node.t) < 0.008) continue;
      node.t = t;

      node.el.style.fontSize = `${(VER_BASE_SIZE + (VER_MAX_SIZE - VER_BASE_SIZE) * t).toFixed(2)}px`;
      node.el.style.opacity = (0.86 + 0.14 * t).toFixed(3);
    }
  };

  container.addEventListener('mousemove', (e) => {
    px = e.clientX;
    py = e.clientY;
    if (!frame) frame = requestAnimationFrame(paint);
  });
  container.addEventListener('mouseleave', () => {
    px = -9999;
    py = -9999;
    if (!frame) frame = requestAnimationFrame(paint);
  });

  // 滚动 / 缩放时重算坐标（rAF 合并，避免反复强制重排）
  let cacheQueued = false;
  const queueCache = (): void => {
    if (cacheQueued) return;
    cacheQueued = true;
    requestAnimationFrame(() => {
      cacheQueued = false;
      cacheVerNodes();
    });
  };
  container.addEventListener('scroll', queueCache, { passive: true });
  window.addEventListener('resize', queueCache);
}

function itemDelay(globalIndex: number): Record<string, string> | undefined {
  if (!animateItems.value) return undefined;
  const slot = Math.min(globalIndex, STAGGER_CAP);
  return { animationDelay: `${slot * STAGGER_STEP_MS}ms` };
}

/* ============ 窗口与初始化 ============ */

function getWindow(): ReturnType<typeof getCurrentWindow> | null {
  try {
    return getCurrentWindow();
  } catch {
    return null;
  }
}

function listenSafe<T>(event: string, handler: (payload: T) => void): void {
  void listen<T>(event, (e) => handler(e.payload)).catch((e) =>
    console.warn(`[main] ${event} 监听失败`, e),
  );
}

onMounted(async () => {
  setupProximityZoom();
  cacheVerNodes();
  watch(groups, () => nextTick(cacheVerNodes));

  listenSafe<LogLine>('update-log', appendLog);
  listenSafe<Progress>('update-progress', onProgress);

  // 逐源结果：检查过程中哪个源先完成就先显示，不必等最慢的（通常是 winget）
  listenSafe<{ source: SourceKind; items: UpdateItemModel[] }>('check-source-result', (payload) => {
    if (!busy.value) return; // 只在"检查中"合并；收尾时 doCheck 会用完整结果再渲一次
    const incoming = payload.items;
    if (incoming.length === 0) return;

    const ids = new Set(incoming.map((i) => i.id));
    items.value = [...items.value.filter((i) => !ids.has(i.id)), ...incoming];
    for (const i of incoming) {
      if (!isItemIgnored(i.id, i.latest, appSettings.value)) checked.value.add(i.id);
    }
    scanned.value = true;
    animateItems.value = false; // 每来一个源就闪一下不好看
    setStatusLeft('status.checkPartial', { count: visibleItems.value.length });
  });

  // 启动模式检测（开机自启动 vs 正常启动）—— 必须先于自动检查逻辑
  try {
    isAutostart.value = await invoke<boolean>('get_startup_mode');
  } catch {
    isAutostart.value = false;
  }

  // 环境探测（不阻塞主界面）
  void invoke<EnvStatus>('detect_env')
    .then((env) => {
      lastEnv.value = env;
    })
    .catch(() => {
      /* 环境探测失败不阻塞主界面 */
    });

  // 自动检查更新
  // - 开机自启动模式：无论 autoCheck 设置如何，都静默检查
  // - 正常启动模式：仅当用户开启 autoCheck 时自动检查
  if (isAutostart.value) {
    const delayMs = Math.max(0, appSettings.value.autostartDelay ?? 10) * 1000;
    window.setTimeout(() => void autostartCheck(), delayMs);
  } else if (appSettings.value.autoCheck) {
    window.setTimeout(() => void doCheck(), 500);
  }
});

/**
 * 开机自启动模式下的静默检查：
 * 有更新 → 显示主窗口并发送通知；无更新 → 保持后台静默运行
 */
async function autostartCheck(): Promise<void> {
  try {
    const result = await invoke<UpdateItemModel[]>('check_updates');
    // 与手动检查保持一致：items 保留全量，过滤交给 computed，
    // 这样状态栏才能算出"已忽略 N 项"
    items.value = result || [];
    checked.value = new Set(visibleItems.value.map((i) => i.id));
    scanned.value = true;

    // 全被忽略时不该弹窗打扰 —— 必须用可见项判断
    if (visibleItems.value.length > 0) {
      const win = getWindow();
      if (win) {
        await win.show();
        await win.setFocus();
      }
      void invoke('send_notification', {
        title: t('notify.foundTitle'),
        body: t('notify.foundBody', { count: visibleItems.value.length }),
        level: 'info',
      }).catch(() => undefined);
    } else {
      // 无更新：不再后台常驻（隐藏的 WebView2 仍占几十 MB 内存）。
      // 先发"已是最新"通知，留一点时间让 toast 播完，然后退出进程。
      void invoke('send_notification', {
        title: t('notify.noUpdateTitle'),
        body: t('notify.noUpdate'),
        level: 'ok',
      }).catch(() => undefined);
      window.setTimeout(() => {
        void getWindow()?.close(); // 关闭主窗口 = 退出进程（Rust 侧已绑定）
      }, 5000);
    }
  } catch (e) {
    console.warn('[autostart] 静默检查失败', e);
    // 检查失败时也显示窗口，让用户知道出了问题
    const win = getWindow();
    if (win) await win.show();
  }
}
</script>

<template>
  <main class="view">
      <div class="toolbar">
        <div class="env-chips">
          <span
            v-for="chip in envChips"
            :key="chip.key"
            class="chip"
            :class="chip.on ? 'on' : 'off'"
            :title="chip.title"
            >{{ chip.text }}</span
          >
        </div>
        <div class="toolbar-actions">
          <button class="btn" type="button" :disabled="busy" @click="doCheck">
            <span class="btn-spinner" v-show="busy"></span>
            <span v-html="ICON.refresh"></span>
            <span>{{ busy ? $t('btn.checking') : $t('btn.check') }}</span>
          </button>
          <button
            ref="btnSel"
            class="btn btn-primary"
            type="button"
            :disabled="busy || selectedCount === 0"
            @click="doUpdateSelected"
          >
            {{ $t('btn.updateSelectedCount', { count: selectedCount }) }}
          </button>
          <button
            class="btn btn-primary"
            type="button"
            :disabled="busy || visibleItems.length === 0"
            @click="doUpdateAll"
          >
            <span v-html="ICON.download"></span>
            <span>{{ $t('btn.updateAll') }}</span>
          </button>
        </div>
      </div>

      <div class="progress-wrap" v-show="progress.visible">
        <div class="progress-bar">
          <div
            class="progress-fill"
            :key="progressFillKey"
            :class="{ done: progress.done }"
            :style="{ width: progress.pct + '%' }"
          ></div>
        </div>
        <span class="progress-text">{{ progress.text }}</span>
      </div>

      <div class="content">
        <section class="list-pane">
          <div ref="listScroll" class="list-scroll" :class="{ 'no-anim': !animateItems }">
            <template v-for="group in groups" :key="group.source">
              <div class="group" :class="{ 'checked-glow': group.allChecked }">
                <div class="group-head">
                  <label class="cb">
                    <input
                      type="checkbox"
                      :checked="group.allChecked"
                      :indeterminate.prop="group.someChecked"
                      :aria-label="$t('action.selectAllIn', { group: $t('group.' + group.source) })"
                      @change="onGroupCheck(group.source, $event)"
                    />
                    <span class="box"></span>
                  </label>
                  <span class="group-icon" v-html="sourceIcon(group.source)"></span>
                  <span class="group-title">{{ $t('group.' + group.source) }}</span>
                  <span class="group-count">{{ group.items.length }}</span>
                </div>
                <UpdateItem
                  v-for="(item, ii) in group.items"
                  :key="item.id"
                  :item="item"
                  :checked="checked.has(item.id)"
                  :failed="failedIds.has(item.id)"
                  :style="itemDelay(group.startIndex + ii)"
                  @toggle="onItemToggle"
                  @ignore-version="onIgnoreVersion"
                  @ignore-forever="onIgnoreForever"
                  @view-log="jumpToLog"
                />
              </div>
            </template>
          </div>
          <div v-if="emptyState" class="pane-empty">
            <div class="empty-inner">
              <span class="empty-icon" :class="{ spin: emptyState.spin }" v-html="emptyState.icon"></span>
              <span class="empty-text">{{ $t(emptyState.key, emptyState.vars ?? {}) }}</span>
            </div>
          </div>
        </section>

        <aside class="log-pane">
          <div class="pane-head">
            <span>{{ $t('log.title') }}</span>
            <button class="mini-btn" type="button" @click="clearLog">{{ $t('btn.clearLog') }}</button>
          </div>
          <div ref="logBody" class="log-body" :class="{ 'log-flash': logFlashing }">
            <div
              v-for="(line, i) in logLines"
              :key="i"
              class="log-card"
              :class="'lv-' + line.level"
            >
              <span class="log-time">{{ line.at }}</span>
              <span class="log-badge">{{ LOG_BADGE[line.level] ?? 'LOG' }}</span>
              <span class="log-text">{{ line.text }}</span>
            </div>
          </div>
        </aside>
      </div>

      <footer class="statusbar" role="status" aria-live="polite">
        <span>{{ statusLeftText }}</span>
        <span ref="statusRightEl" class="stat-right">{{ statusRightText }}</span>
      </footer>
  </main>
</template>

