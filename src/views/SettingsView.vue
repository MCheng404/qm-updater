<script setup lang="ts">
/**
 * 设置窗口（label=settings）。
 * 由旧版 src/settings.ts + settings.html 移植为 Vue 响应式实现。
 */
import { computed, onMounted, ref } from 'vue';
import { useI18n } from 'vue-i18n';
import { invoke } from '@tauri-apps/api/core';
import { getVersion } from '@tauri-apps/api/app';
import { PRESETS } from '../app/theme';
import {
  DEFAULT_FONT,
  commitFont,
  previewFont,
  type FontSettings,
  type FontSource,
} from '../app/font';
import { useThemeStore, useAppSettingsStore } from '../app/stores';
import type { NotifyPrefs } from '../app/types';

const { t, locale } = useI18n();
const emit = defineEmits<{
  (e: 'navigate', view: 'about'): void;
}>();
const theme = useThemeStore();
const appSettings = useAppSettingsStore();

/* ============ 通知偏好 ============ */
const DEFAULT_NOTIFY: NotifyPrefs = {
  mode: 'native',
  position: 'bottom-right',
  durationMs: 4500,
  opacity: 90,
  maxStack: 4,
  stack: 'list',
};
const notify = ref<NotifyPrefs>({ ...DEFAULT_NOTIFY });

/** 通知位置 → i18n key 片段 */
const POS_KEY: Record<string, string> = {
  'top-left': 'topLeft',
  'top-center': 'topCenter',
  'top-right': 'topRight',
  'bottom-left': 'bottomLeft',
  'bottom-center': 'bottomCenter',
  'bottom-right': 'bottomRight',
};
const posName = computed(() => t(`settings.pos.${POS_KEY[notify.value.position] ?? 'bottomRight'}`));
const posHintText = computed(() => t(`settings.pos.${POS_KEY[notify.value.position] ?? 'bottomRight'}Hint`));

/* ============ 选项清单 ============ */
const FONT_SOURCES: Array<{ src: FontSource; labelKey: string }> = [
  { src: 'builtin', labelKey: 'settings.fontBuiltin' },
  { src: 'system', labelKey: 'settings.fontSystem' },
  { src: 'file', labelKey: 'settings.fontFile' },
];

/* ============ 开机自启动 ============ */
const autostartEnabled = ref(false);

/* ============ 字体 ============ */
const font = ref<FontSettings>({ ...DEFAULT_FONT });
const systemFonts = ref<string[]>([]);
const fontsLoading = ref(true);
const dirFonts = ref<string[]>([]);
/** 文件夹无字体时的提示文案覆盖（否则显示 customPath） */
const fontPathOverride = ref('');
const systemFontSelect = ref<HTMLSelectElement>();
const posMini = ref<HTMLElement>();

const fontFilePath = computed(
  () => fontPathOverride.value || font.value.customPath || t('settings.noFontFile'),
);
const fontScalePct = computed(() => Math.round(font.value.scale * 100));

/* ============ 其它 ============ */
const iconStat = ref('');
const logPath = ref('—');
const settingsVersion = ref('—');
const ignoredList = computed(() => appSettings.ignoredList());

/* ============ 主题 ============ */
function onColorInput(key: 'accent' | 'text' | 'dim', e: Event): void {
  theme.setColor(key, (e.target as HTMLInputElement).value);
}
function onNumberInput(setter: (v: number) => void, e: Event): void {
  setter(Number((e.target as HTMLInputElement).value));
}
function onChecked(setter: (v: boolean) => void, e: Event): void {
  setter((e.target as HTMLInputElement).checked);
}

/* ============ 开机自启动 ============ */
function onAutostartChange(e: Event): void {
  const enabled = (e.target as HTMLInputElement).checked;
  autostartEnabled.value = enabled;
  void invoke('set_autostart', { enable: enabled }).catch((err) => {
    console.warn('[settings] 开机自启动设置失败', err);
    autostartEnabled.value = !enabled; // 写入失败时回滚 UI
  });
}

/* ============ 语言 ============ */
function onLangChange(e: Event): void {
  const lang = (e.target as HTMLSelectElement).value as 'zh-CN' | 'en-US';
  locale.value = lang;
}

/* ============ 字体 ============ */
function setFontSource(src: FontSource): void {
  fontPathOverride.value = '';
  font.value = { ...font.value, source: src };
  // 首次切到系统字体时，先把下拉框当前值带上，避免出现"选了来源但没字族"
  if (src === 'system' && !font.value.family) {
    const v = systemFontSelect.value?.value;
    if (v) font.value = { ...font.value, family: v };
  }
  commitFont(font.value);
}

function onSystemFontChange(e: Event): void {
  fontPathOverride.value = '';
  font.value = {
    ...font.value,
    family: (e.target as HTMLSelectElement).value,
    source: 'system',
  };
  commitFont(font.value);
}

async function loadSystemFonts(): Promise<void> {
  let fonts: string[] = [];
  try {
    fonts = await invoke<string[]>('list_system_fonts');
  } catch (e) {
    console.warn('[settings] 读取系统字体失败', e);
  }
  systemFonts.value = fonts;
  fontsLoading.value = false;
}

/** 把某个字体文件设为当前字体 */
async function useFontFile(path: string): Promise<void> {
  try {
    await invoke('set_ui_font_file', { path });
  } catch (e) {
    console.warn('[settings] 设置字体文件失败', e);
    return;
  }
  fontPathOverride.value = '';
  font.value = {
    ...font.value,
    source: 'file',
    customPath: path,
    customName: path.split(/[\\/]/).pop() ?? path,
  };
  commitFont(font.value);
}

function pickFontFile(): void {
  void (async () => {
    const path = await invoke<string | null>('pick_font_file').catch(() => null);
    if (path) await useFontFile(path);
  })();
}

function pickFontDir(): void {
  void (async () => {
    const dir = await invoke<string | null>('pick_font_dir').catch(() => null);
    if (!dir) return;

    const files = await invoke<string[]>('list_font_files', { dir }).catch(() => []);
    dirFonts.value = files;
    if (files.length === 0) {
      fontPathOverride.value = t('settings.noFontInDir', { dir });
      return;
    }
    await useFontFile(files[0]);
  })();
}

function onDirFontChange(e: Event): void {
  const path = (e.target as HTMLSelectElement).value;
  if (path) void useFontFile(path);
}

function onFontScaleInput(e: Event): void {
  const pct = Number((e.target as HTMLInputElement).value);
  font.value = { ...font.value, scale: pct / 100 };
  // 拖动时只做实时预览；松手（change）才落盘 + 广播，避免刷爆 localStorage
  previewFont(font.value);
}

function onFontScaleChange(): void {
  commitFont(font.value);
}

function resetFont(): void {
  void (async () => {
    await invoke('set_ui_font_file', { path: null }).catch(() => undefined);
    fontPathOverride.value = '';
    font.value = { ...DEFAULT_FONT };
    commitFont(font.value);
  })();
}

/* ============ 通知偏好 ============ */
async function commitNotify(): Promise<void> {
  try {
    await invoke('save_notify_prefs', { prefs: notify.value });
  } catch {
    /* 静默 */
  }
}

function onNotifModeChange(e: Event): void {
  const input = e.target as HTMLInputElement;
  if (!input.checked) return;
  notify.value = { ...notify.value, mode: input.value };
  void commitNotify();
}

/** 迷你绘制窗口：点击区域 → 选位置 */
function onPosMiniClick(e: MouseEvent): void {
  if (notify.value.mode !== 'app') return;
  const el = posMini.value;
  if (!el) return;
  const rect = el.getBoundingClientRect();
  const x = (e.clientX - rect.left) / rect.width;
  const y = (e.clientY - rect.top) / rect.height;

  const v = y < 0.5 ? 'top' : 'bottom';
  const h = x < 0.34 ? 'left' : x > 0.66 ? 'right' : 'center';
  notify.value = { ...notify.value, position: `${v}-${h}` };
  void commitNotify();
}

/** 键盘可达：方向键在 3×2 的落点里移动 */
function onPosMiniKeydown(e: KeyboardEvent): void {
  if (notify.value.mode !== 'app') return;
  const [v, h] = notify.value.position.split('-');
  const cols = ['left', 'center', 'right'];
  let ci = cols.indexOf(h);
  if (ci < 0) ci = 2;

  let next: string | null = null;
  switch (e.key) {
    case 'ArrowLeft':
      next = `${v}-${cols[Math.max(0, ci - 1)]}`;
      break;
    case 'ArrowRight':
      next = `${v}-${cols[Math.min(2, ci + 1)]}`;
      break;
    case 'ArrowUp':
      next = `top-${cols[ci]}`;
      break;
    case 'ArrowDown':
      next = `bottom-${cols[ci]}`;
      break;
    default:
      break;
  }
  if (!next) return;

  e.preventDefault();
  notify.value = { ...notify.value, position: next };
  void commitNotify();
}

function onNotifDurationInput(e: Event): void {
  notify.value = { ...notify.value, durationMs: Number((e.target as HTMLInputElement).value) };
  void commitNotify();
}

function onNotifOpacityInput(e: Event): void {
  notify.value = { ...notify.value, opacity: Number((e.target as HTMLInputElement).value) };
  void commitNotify();
}

function testNotif(): void {
  void invoke('send_notification', {
    title: notify.value.mode === 'native' ? t('settings.testNotifNative') : t('settings.testNotifApp'),
    body: t('settings.testNotifBody'),
    level: 'info',
  }).catch(() => undefined);
}

/* ============ 图标缓存刷新 ============ */
async function refreshIcons(): Promise<void> {
  iconStat.value = t('settings.refreshing');
  try {
    const entries = await invoke<Array<{ name: string; icon: string }>>('rebuild_icon_cache');
    iconStat.value = t('settings.iconCached', { count: entries.length });
  } catch (e) {
    iconStat.value = t('settings.iconFailed', { error: String(e).slice(0, 40) });
  }
}

/* ============ 日志 / 关于 ============ */
function openLogDir(): void {
  void invoke('open_log_dir').catch((err) => {
    console.warn('[settings] 打开日志文件夹失败', err);
  });
}

function openAbout(): void {
  emit('navigate', 'about');
}

/* ============ 初始化 ============ */
onMounted(() => {
  // 通知偏好
  void invoke<NotifyPrefs>('load_notify_prefs')
    .then((p) => {
      notify.value = p;
    })
    .catch(() => {
      notify.value = { ...DEFAULT_NOTIFY };
    });

  // 开机自启动当前状态
  void invoke<boolean>('get_autostart')
    .then((enabled) => {
      autostartEnabled.value = enabled;
    })
    .catch(() => undefined);

  // 系统字体（异步，不阻塞面板）
  void loadSystemFonts();

  // 日志目录展示
  void invoke<string>('get_log_dir')
    .then((dir) => {
      logPath.value = dir;
    })
    .catch(() => {
      logPath.value = '—';
    });

  // 版本号从运行时读取，避免与包版本不一致
  void getVersion()
    .then((v) => {
      settingsVersion.value = v;
    })
    .catch(() => {
      settingsVersion.value = '—';
    });
});

/* 文档标题由 App.vue 统一维护 */
</script>

<template>
  <div class="settings-scroll">
      <section class="settings-section">
        <h2>{{ $t('settings.appearancePresets') }}</h2>
        <div class="preset-grid">
          <button
            v-for="p in PRESETS"
            :key="p.id"
            class="preset"
            :class="{ active: theme.state.presetId === p.id }"
            type="button"
            @click="theme.setPreset(p.id)"
          >
            <div class="preset-swatch" :style="{ background: p.accent }"></div>
            <div class="preset-name">{{ $t('settings.preset.' + p.id) }}</div>
          </button>
        </div>
      </section>

      <section class="settings-section">
        <h2>{{ $t('settings.customColors') }}</h2>
        <div class="field-row">
          <label for="colorAccent">{{ $t('settings.accent') }}</label>
          <input
            id="colorAccent"
            type="color"
            :value="theme.state.accent"
            @input="onColorInput('accent', $event)"
          />
          <span class="field-value mono">{{ theme.state.accent.toUpperCase() }}</span>
          <button class="mini-btn" type="button" @click="theme.resetColor('accent')">
            {{ $t('common.reset') }}
          </button>
        </div>
        <div class="field-row">
          <label for="colorText">{{ $t('settings.textColor') }}</label>
          <input
            id="colorText"
            type="color"
            :value="theme.state.text"
            @input="onColorInput('text', $event)"
          />
          <span class="field-value mono">{{ theme.state.text.toUpperCase() }}</span>
          <button class="mini-btn" type="button" @click="theme.resetColor('text')">
            {{ $t('common.reset') }}
          </button>
        </div>
        <div class="field-row">
          <label for="colorDim">{{ $t('settings.dimColor') }}</label>
          <input
            id="colorDim"
            type="color"
            :value="theme.state.dim"
            @input="onColorInput('dim', $event)"
          />
          <span class="field-value mono">{{ theme.state.dim.toUpperCase() }}</span>
          <button class="mini-btn" type="button" @click="theme.resetColor('dim')">
            {{ $t('common.reset') }}
          </button>
        </div>
        <p class="field-hint">{{ $t('settings.dimHint') }}</p>
      </section>

      <section class="settings-section">
        <h2>{{ $t('settings.sectionUi') }}</h2>
        <div class="field-row">
          <label>{{ $t('settings.themeMode') }}</label>
          <div class="mode-selector">
            <button
              class="mode-btn"
              :class="{ active: theme.state.mode === 'light' }"
              type="button"
              @click="theme.setMode('light')"
            >
              {{ $t('settings.themeLight') }}
            </button>
            <button
              class="mode-btn"
              :class="{ active: theme.state.mode === 'dark' }"
              type="button"
              @click="theme.setMode('dark')"
            >
              {{ $t('settings.themeDark') }}
            </button>
            <button
              class="mode-btn"
              :class="{ active: theme.state.mode === 'system' }"
              type="button"
              @click="theme.setMode('system')"
            >
              {{ $t('settings.themeSystem') }}
            </button>
          </div>
        </div>
        <div class="field-row">
          <label for="selectLang">{{ $t('settings.language') }}</label>
          <select id="selectLang" class="select-input" :value="locale" @change="onLangChange">
            <option value="zh-CN">简体中文</option>
            <option value="en-US">English</option>
          </select>
        </div>
        <div class="field-row">
          <label for="rangeRadius">{{ $t('settings.radius') }}</label>
          <input
            id="rangeRadius"
            type="range"
            min="6"
            max="28"
            step="1"
            :value="theme.state.radius"
            @input="onNumberInput(theme.setRadius, $event)"
          />
          <span class="field-value mono">{{ theme.state.radius }}px</span>
        </div>
        <div class="field-row">
          <label for="toggleMotion">{{ $t('settings.reduceMotion') }}</label>
          <label class="switch">
            <input
              id="toggleMotion"
              type="checkbox"
              :checked="theme.state.reduceMotion"
              @change="onChecked(theme.setReduceMotion, $event)"
            />
            <span class="switch-track"><span class="switch-thumb"></span></span>
          </label>
        </div>
        <div class="field-row">
          <label for="toggleAutoCheck">{{ $t('settings.autoCheck') }}</label>
          <label class="switch">
            <input
              id="toggleAutoCheck"
              type="checkbox"
              :checked="appSettings.state.autoCheck"
              @change="onChecked(appSettings.setAutoCheck, $event)"
            />
            <span class="switch-track"><span class="switch-thumb"></span></span>
          </label>
        </div>
        <div class="field-row">
          <label for="toggleAutostart">{{ $t('settings.autostart') }}</label>
          <label class="switch">
            <input
              id="toggleAutostart"
              type="checkbox"
              :checked="autostartEnabled"
              @change="onAutostartChange"
            />
            <span class="switch-track"><span class="switch-thumb"></span></span>
          </label>
        </div>
        <div class="field-row" v-show="autostartEnabled">
          <label for="rangeAutostartDelay">{{ $t('settings.autostartDelay') }}</label>
          <input
            id="rangeAutostartDelay"
            type="range"
            min="0"
            max="120"
            step="1"
            :value="appSettings.state.autostartDelay"
            @input="onNumberInput(appSettings.setAutostartDelay, $event)"
          />
          <span class="field-value mono">{{ appSettings.state.autostartDelay }}s</span>
        </div>
        <p class="field-hint">{{ $t('settings.startupHint') }}</p>
      </section>

      <section class="settings-section">
        <h2>{{ $t('settings.sectionFont') }}</h2>
        <div class="field-row">
          <label>{{ $t('settings.fontSource') }}</label>
          <div class="mode-selector">
            <button
              v-for="f in FONT_SOURCES"
              :key="f.src"
              class="mode-btn"
              :class="{ active: font.source === f.src }"
              type="button"
              @click="setFontSource(f.src)"
            >
              {{ $t(f.labelKey) }}
            </button>
          </div>
        </div>

        <div class="font-pane" v-show="font.source === 'system'">
          <div class="field-row">
            <label for="selectSystemFont">{{ $t('settings.systemFontFamily') }}</label>
            <select
              id="selectSystemFont"
              ref="systemFontSelect"
              class="select-input font-select"
              :value="font.family"
              @change="onSystemFontChange"
            >
              <option v-if="fontsLoading" value="">{{ $t('common.loading') }}</option>
              <option v-else-if="systemFonts.length === 0" value="">
                {{ $t('settings.noSystemFont') }}
              </option>
              <option v-for="name in systemFonts" :key="name" :value="name">{{ name }}</option>
            </select>
          </div>
        </div>

        <div class="font-pane" v-show="font.source === 'file'">
          <div class="field-row">
            <label>{{ $t('settings.pickMethod') }}</label>
            <button class="mini-btn" type="button" @click="pickFontFile">
              {{ $t('settings.pickFontFile') }}
            </button>
            <button class="mini-btn" type="button" @click="pickFontDir">
              {{ $t('settings.pickFontDir') }}
            </button>
          </div>
          <div class="field-row" v-show="dirFonts.length > 0">
            <label for="selectDirFont">{{ $t('settings.dirFont') }}</label>
            <select id="selectDirFont" class="select-input font-select" @change="onDirFontChange">
              <option v-for="f in dirFonts" :key="f" :value="f">
                {{ f.split(/[\\/]/).pop() ?? f }}
              </option>
            </select>
          </div>
          <p class="field-hint mono">{{ fontFilePath }}</p>
        </div>

        <div class="field-row">
          <label for="rangeFontScale">{{ $t('settings.fontScale') }}</label>
          <input
            id="rangeFontScale"
            type="range"
            min="85"
            max="140"
            step="1"
            :value="fontScalePct"
            @input="onFontScaleInput"
            @change="onFontScaleChange"
          />
          <span class="field-value mono">{{ fontScalePct }}%</span>
        </div>

        <div class="font-preview">
          <div class="fp-title">{{ $t('settings.fontPreview') }}</div>
          <div class="fp-body">
            QM Updater 0123456789 ABCDEFG abcdefg
            <br />
            统一管理 npm / winget / pip / OpenClaw 四源软件更新
          </div>
        </div>

        <div class="settings-actions">
          <button class="mini-btn" type="button" @click="resetFont">
            {{ $t('settings.resetFont') }}
          </button>
        </div>
      </section>

      <section class="settings-section">
        <h2>{{ $t('settings.sectionNotification') }}</h2>
        <div class="not-mode-row">
          <label class="not-mode-card">
            <input
              type="radio"
              name="notifMode"
              value="native"
              :checked="notify.mode === 'native'"
              @change="onNotifModeChange"
            />
            <span class="not-mode-card-body">
              <strong>{{ $t('settings.notifNative') }}</strong>
              <small>{{ $t('settings.notifNativeHint') }}</small>
            </span>
          </label>
          <label class="not-mode-card">
            <input
              type="radio"
              name="notifMode"
              value="app"
              :checked="notify.mode === 'app'"
              @change="onNotifModeChange"
            />
            <span class="not-mode-card-body">
              <strong>{{ $t('settings.notifApp') }}</strong>
              <small>{{ $t('settings.notifAppHint') }}</small>
            </span>
          </label>
        </div>

        <div class="not-app-only" :class="{ active: notify.mode === 'app' }">
          <div class="field-row field-row-stack">
            <label>{{ $t('settings.notifPosition') }}</label>
            <div
              ref="posMini"
              class="pos-mini"
              :data-pos="notify.position"
              :title="posName"
              :aria-label="$t('settings.notifPosition') + ': ' + posName"
              tabindex="0"
              role="button"
              @click="onPosMiniClick"
              @keydown="onPosMiniKeydown"
            >
              <div class="pos-mini-frame"></div>
              <div class="pos-dot"></div>
            </div>
            <p class="field-hint pos-hint">{{ posHintText }}</p>
          </div>

          <div class="field-row">
            <label for="rangeNotifDuration">{{ $t('settings.notifDuration') }}</label>
            <input
              id="rangeNotifDuration"
              type="range"
              min="1500"
              max="12000"
              step="500"
              :value="notify.durationMs"
              @input="onNotifDurationInput"
            />
            <span class="field-value mono">{{ (notify.durationMs / 1000).toFixed(1) }}s</span>
          </div>

          <div class="field-row">
            <label for="rangeNotifOpacity">{{ $t('settings.notifOpacity') }}</label>
            <input
              id="rangeNotifOpacity"
              type="range"
              min="40"
              max="100"
              step="1"
              :value="notify.opacity"
              @input="onNotifOpacityInput"
            />
            <span class="field-value mono">{{ notify.opacity }}%</span>
          </div>

          <div class="settings-actions">
            <button class="mini-btn" type="button" @click="testNotif">
              {{ $t('settings.testNotif') }}
            </button>
          </div>
        </div>
      </section>

      <section class="settings-section">
        <h2>{{ $t('settings.sectionIcons') }}</h2>
        <p class="field-hint">{{ $t('settings.iconsHint') }}</p>
        <div class="settings-actions">
          <button class="mini-btn" type="button" @click="refreshIcons">
            {{ $t('settings.rebuildIcons') }}
          </button>
          <span class="field-value mono">{{ iconStat }}</span>
        </div>
      </section>

      <section class="settings-section">
        <h2>{{ $t('settings.sectionIgnore') }}</h2>
        <p class="field-hint">{{ $t('settings.ignoreHint') }}</p>
        <div class="ignore-list">
          <div v-if="ignoredList.length === 0" class="ignore-empty">
            {{ $t('settings.ignoreEmpty') }}
          </div>
          <div v-for="item in ignoredList" :key="item.id" class="ignore-row">
            <div class="ignore-info">
              <span class="ignore-name">{{ item.id }}</span>
              <span class="ignore-kind">{{
                item.kind === 'version'
                  ? $t('settings.ignoreVersionLabel', { version: item.version ?? '' })
                  : $t('settings.ignoreForeverLabel')
              }}</span>
            </div>
            <button class="mini-btn" type="button" @click="appSettings.removeIgnore(item.id)">
              {{ $t('common.remove') }}
            </button>
          </div>
        </div>
      </section>

      <section class="settings-section">
        <h2>{{ $t('settings.sectionLog') }}</h2>
        <p class="field-hint">{{ $t('settings.logHint') }}</p>
        <p class="field-hint mono">{{ logPath }}</p>
        <div class="settings-actions">
          <button class="mini-btn" type="button" @click="openLogDir">
            {{ $t('settings.openLogFolder') }}
          </button>
        </div>
      </section>

      <section class="settings-section">
        <h2>{{ $t('settings.sectionAbout') }}</h2>
        <div class="about-row">
          <div class="about-info">
            <div class="about-name">{{ $t('about.appName') }}</div>
            <div class="about-version">
              {{ $t('about.version') }} <span class="mono">{{ settingsVersion }}</span>
            </div>
            <div class="about-desc">{{ $t('settings.aboutDesc') }}</div>
          </div>
          <button class="btn btn-primary" type="button" @click="openAbout">
            <svg viewBox="0 0 16 16" width="14" height="14" fill="none" stroke="currentColor" stroke-width="1.8">
              <circle cx="8" cy="8" r="6.5" /><path d="M8 5v.01M8 8v3" />
            </svg>
            <span>{{ $t('settings.openAbout') }}</span>
          </button>
        </div>
      </section>

      <div class="settings-actions">
        <button class="btn" type="button" @click="theme.resetAll()">
          {{ $t('settings.resetAppearance') }}
        </button>
      </div>
  </div>
</template>

