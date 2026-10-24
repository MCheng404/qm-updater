# QM Updater

[![GitHub release](https://img.shields.io/github/v/release/MCheng404/qm-updater)](https://github.com/MCheng404/qm-updater/releases)
[![GitHub license](https://img.shields.io/github/license/MCheng404/qm-updater)](https://github.com/MCheng404/qm-updater/blob/main/LICENSE)
[![GitHub downloads](https://img.shields.io/github/downloads/MCheng404/qm-updater/total)](https://github.com/MCheng404/qm-updater/releases)

**简体中文 | [繁體中文](README.zh-TW.md) | [English](README.md)**

统一管理 npm / winget / pip / OpenClaw 四源软件更新的桌面工具，基于 Tauri 2 + Rust + TypeScript 构建。轻量、快速、无感。

## 功能特性

- 🔄 **四源统一更新检查** — 同时检查 npm 全局包、winget 软件、pip Python 包、OpenClaw
- 📢 **双模式通知** — 应用内浮层通知 + Windows 系统通知，可自选位置和时长
- 🎨 **主题定制** — 浅色 / 深色 / 跟随系统，6 种配色预设，自定义强调色/文字色/背景浓度/圆角
- 🌍 **多语言支持** — 简体中文 / English，设置中一键切换
- 🚫 **忽略管理** — 忽略此版本（版本更新后自动恢复）/ 永久忽略（黑名单可移除）
- ⚡ **开机自启动** — 可选开机自启动 + 启动时自动检查更新，支持自定义延迟（适应校园网认证等慢网络环境）
- 🖋️ **字体自定义** — 内置霞鹜新晰黑 / Inter / JetBrains Mono，可从系统字体列表、指定目录或单个字体文件更换界面字体，并按档缩放字号
- 📄 **运行日志** — 每日日志自动落盘，设置中一键打开；更新失败项点击即可跳转到对应日志
- 🔍 **高分屏适配** — 窗口自动适配系统 DPI 缩放，避免超出可用工作区
- 🪟 **关于窗口** — 软件介绍、GitHub 链接、检查更新
- 🖥️ **双架构支持** — x64 和 ARM64 独立构建版本

## 技术栈

- **前端**: Vue 3 + TypeScript + Vite + Pinia + vue-i18n（原生 CSS，无 UI 组件库）
- **后端**: Rust + Tauri 2
- **界面**: 纯色分层表面 · 大圆角 · 悬浮卡片 · 统一动效
- **打包**: NSIS 安装包

## 安装

### 下载

前往 [Releases](https://github.com/MCheng404/qm-updater/releases) 页面下载最新版本：

| 架构 | 安装包 | 绿色版 |
|------|--------|--------|
| x64（Intel/AMD） | `qm-updater-<版本号>-x64-setup.exe` | `qm-updater-x64.exe` |
| ARM64（高通骁龙） | `qm-updater-<版本号>-arm64-setup.exe` | `qm-updater-arm64.exe` |

> 不确定自己的架构？打开「设置 → 系统 → 关于」，查看「系统类型」。

### 从源码构建

```bash
# 安装依赖
npm install

# 开发模式
npm run tauri:dev

# 构建 x64 发布版
npm run tauri:build

# 构建 ARM64 发布版
npm run tauri:build -- --target aarch64-pc-windows-msvc
```

## 项目结构

```
├── src/                    # 前端源码（Vue 3）
│   ├── App.vue            # 单窗口外壳：标题栏 + 左侧导航 + 视图切换
│   ├── entries/           # 入口：main（主窗口）/ notify（通知浮层）
│   ├── views/             # 视图：MainView / SettingsView / AboutView / NotifyView
│   ├── components/        # 复用组件：TitleBar / UpdateItem / ToastCard
│   ├── app/               # 共享层
│   │   ├── i18n.ts        # vue-i18n 实例
│   │   ├── locales/       # 中英文语言包
│   │   ├── theme.ts       # 主题系统
│   │   ├── font.ts        # 字体设置
│   │   ├── app-settings.ts# 应用设置
│   │   ├── stores.ts      # Pinia stores（主题 / 应用设置）
│   │   ├── shared.ts      # 共享插件与跨窗口同步
│   │   ├── icons.ts       # 内嵌矢量图标
│   │   └── types.ts       # 前后端接口类型
│   └── styles.css         # 全局样式
├── src-tauri/             # Rust 后端
│   ├── src/
│   │   ├── main.rs        # 入口
│   │   ├── lib.rs         # 命令注册
│   │   ├── font.rs        # 系统字体枚举 / 自定义字体
│   │   ├── logfile.rs     # 每日日志落盘
│   │   ├── sources/       # 更新源
│   │   │   ├── npm.rs
│   │   │   ├── winget.rs
│   │   │   ├── pip.rs
│   │   │   └── openclaw.rs
│   │   └── ...
│   └── tauri.conf.json    # Tauri 配置
├── index.html             # 主窗口（内含 更新 / 设置 / 关于 三个视图）
└── notify.html            # 通知浮层窗口
```

## 许可证

MIT
