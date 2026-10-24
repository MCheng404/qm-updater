/** 前后端共享的接口定义（与 Rust 侧 serde 结构一一对应） */

export type SourceKind = 'npm' | 'winget' | 'pip' | 'openclaw';

export interface UpdateItem {
  id: string;
  source: SourceKind;
  name: string;
  current: string;
  latest: string;
  tag: string | null;
  pkgId: string | null;
  /** 应用图标 PNG data URL（由 fetch_icons 后置填充） */
  icon?: string | null;
}

export interface EnvStatus {
  npm: boolean;
  winget: boolean;
  pip: boolean;
  openclaw: boolean;
  isAdmin: boolean;
  /** 当前 PATH 上 node 的版本（形如 "26.4.0"），没装为 null */
  nodeVersion: string | null;
  /** 是否满足 OpenClaw 的要求（>=24.16 <25 || >=26.1）*/
  nodeOk: boolean;
}

export interface LogLine {
  level: string;
  text: string;
  at: string;
}

export interface Progress {
  done: number;
  total: number;
  currentId: string;
  currentName: string;
  status: string;
}

export interface ItemResult {
  id: string;
  name: string;
  ok: boolean;
}

/** 通知偏好（与 Rust 侧 notify::NotifyPrefs 对应） */
export interface NotifyPrefs {
  mode: string;
  position: string;
  durationMs: number;
  opacity: number;
  maxStack: number;
  /** 多条通知排布：list（列表）/ stacked（卡片叠加） */
  stack: string;
}

export interface ToastPayload {
  title: string;
  body: string;
  level: string;
  durationMs: number;
  ts: string;
}
