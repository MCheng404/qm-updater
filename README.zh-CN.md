# QM Updater

[![GitHub release](https://img.shields.io/github/v/release/MCheng404/qm-updater)](https://github.com/MCheng404/qm-updater/releases)
[![GitHub license](https://img.shields.io/github/license/MCheng404/qm-updater)](https://github.com/MCheng404/qm-updater/blob/main/LICENSE)
[![GitHub downloads](https://img.shields.io/github/downloads/MCheng404/qm-updater/total)](https://github.com/MCheng404/qm-updater/releases)

**简体中文 | [繁體中文](README.zh-TW.md) | [English](README.md)**

一个仓库、双端覆盖：统一的软件更新工具，同时服务 **Windows 桌面端** 与 **Android 移动端**。

- **桌面端**（Tauri 2 + Rust + TypeScript）：统一检查 npm / winget / pip / OpenClaw 四源软件更新。
- **安卓端**（Kotlin + Compose，位于 `android/` 子目录）：多源应用更新，支持 Root / Shizuku 静默安装。

双端版本号同源：打一个 Git tag（`vX.Y.Z`），一次发布同时产出桌面端与安卓端产物。

## 功能特性

### 桌面端（Windows）

- 🔄 **四源统一更新检查** — 同时检查 npm 全局包、winget 软件、pip Python 包、OpenClaw
- 📢 **双模式通知** — 应用内浮层通知 + Windows 系统通知，可自选位置和时长
- 🎨 **主题定制** — 浅色 / 深色 / 跟随系统，6 种配色预设，自定义强调色/文字色/背景浓度/圆角
- 🌍 **多语言支持** — 简体中文 / English，设置中一键切换
- 🚫 **忽略管理** — 忽略此版本（版本更新后自动恢复）/ 永久忽略（黑名单可移除）
- ⚡ **开机自启动** — 可选开机自启动 + 启动时自动检查更新，支持自定义延迟（适应校园网认证等慢网络环境）
- 🖋️ **字体自定义** — 内置霞鹜新晰黑 / Inter / JetBrains Mono，可从系统字体列表、指定目录或单个字体文件更换界面字体
- 📄 **运行日志** — 每日日志自动落盘，设置中一键打开
- 🪟 **关于窗口** — 软件介绍、GitHub 链接、检查更新
- 🖥️ **双架构支持** — x64 和 ARM64 独立构建版本

### 安卓端（`android/`）

- 🔍 **多源更新** — APKPure / Aurora / GitHub Release 聚合，批量检查更新
- 🔐 **静默安装** — Root / Shizuku 免确认直接安装；支持会话式分卷（xapk）安装
- 🛡️ **伪装 Google Play 安装来源** — 静默安装时将安装来源记录为 `com.android.vending`，绕开「请在 Google Play 安装/更新」的来源校验（可关闭；不绕过 Play Integrity）
- 🔔 **进度通知** — 下载/安装进度显示在通知栏，失败原因也会出现在通知里
- 🧹 **自动清理** — 安装后缓存 APK 自动清理

## 安装

前往 [Releases](https://github.com/MCheng404/qm-updater/releases) 页面，每个 Release 同时包含双端产物：

| 平台 | 文件 |
|------|------|
| Windows x64 | `qm-updater-<版本号>-x64-setup.exe`（安装包）/ `qm-updater-x64.exe`（绿色版） |
| Windows ARM64 | `qm-updater-<版本号>-arm64-setup.exe`（安装包）/ `qm-updater-arm64.exe`（绿色版） |
| Android | `qm-updater-<版本号>.apk`（签名 APK，minSdk 26） |

## 从源码构建

```bash
# ---- 桌面端（Windows，需 Rust + Node + VS Build Tools）----
npm install
npm run tauri:dev          # 开发模式
npm run tauri:build        # x64 发布版
npm run tauri:build -- --target aarch64-pc-windows-msvc   # ARM64 发布版

# ---- 安卓端（需 JDK 17 + Android SDK，Gradle wrapper 已内置）----
cd android
./gradlew :app:assembleDebug     # 调试包
./gradlew :app:assembleRelease   # 发布包（versionName/versionCode 自动从仓库根 package.json 读取；CI 可用 BUILD_VERSION/BUILD_NUMBER 覆盖）
```

## 项目结构

```
├── src/                    # 桌面端前端（Vue 3）
│   ├── views/             # MainView / SettingsView / AboutView / NotifyView
│   ├── components/        # TitleBar / UpdateItem / ToastCard
│   └── app/               # i18n / theme / font / settings / stores / icons
├── src-tauri/             # 桌面端 Rust 后端（Tauri 2）
│   └── src/
│       ├── main.rs / lib.rs
│       ├── font.rs / logfile.rs
│       └── sources/       # npm.rs / winget.rs / pip.rs / openclaw.rs
├── android/               # 安卓端（Kotlin + Compose，独立 Gradle 工程）
│   ├── app/src/main/kotlin/com/qm/updater/
│   │   ├── repository/    # 更新 / 搜索 / GitHub Release 检查
│   │   ├── viewmodel/     # 安装流程、通知
│   │   ├── util/          # Downloader / RootInstaller / SessionInstaller
│   │   └── ui/            # Compose 界面
│   └── gradle/            # Gradle wrapper 9.7.1（已配国内镜像）
├── .github/workflows/     # auto-tag.yml：推送到发版分支自动打 tag；release.yml：tag → 双端同号一次发布
└── scripts/               # 版本同步工具
```

## CI / 发布

**推送即发版**：版本号唯一权威是 `package.json` 的 `version`。改动代码后 bump 版本号并推送 `refactor/vue-frontend` 分支，[auto-tag.yml](.github/workflows/auto-tag.yml) 自动打 `vX.Y.Z` tag（版本未变化时跳过）并触发 [release.yml](.github/workflows/release.yml) 并行构建桌面端安装包/绿色版（x64 + ARM64，可选 Authenticode 签名）与安卓端 release APK，最终在**同一个 Release** 下发布双端产物（同一版本号）。也可在 Actions 页面手动 `workflow_dispatch` 触发。Release 根目录的 `SHA256SUMS.txt` 覆盖全部 5 个产物。

## 许可证

MIT
