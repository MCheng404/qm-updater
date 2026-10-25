# QM Updater

[![GitHub release](https://img.shields.io/github/v/release/MCheng404/qm-updater)](https://github.com/MCheng404/qm-updater/releases)
[![GitHub license](https://img.shields.io/github/license/MCheng404/qm-updater)](https://github.com/MCheng404/qm-updater/blob/main/LICENSE)
[![GitHub downloads](https://img.shields.io/github/downloads/MCheng404/qm-updater/total)](https://github.com/MCheng404/qm-updater/releases)

**[简体中文](README.zh-CN.md) | 繁體中文 | [English](README.md)**

一個倉庫、雙端覆蓋：統一的軟體更新工具，同時服務 **Windows 桌面端** 與 **Android 行動端**。

- **桌面端**（Tauri 2 + Rust + TypeScript）：統一檢查 npm / winget / pip / OpenClaw 四源軟體更新。
- **安卓端**（Kotlin + Compose，位於 `android/` 子目錄）：多源應用更新，支援 Root / Shizuku 靜默安裝。

雙端版本號同源：打一個 Git tag（`vX.Y.Z`），一次發布同時產出桌面端與安卓端產物。

## 功能特色

### 桌面端（Windows）

- 🔄 **四源統一更新檢查** — 同時檢查 npm 全域套件、winget 軟體、pip Python 套件、OpenClaw
- 📢 **雙模式通知** — 應用內浮層通知 + Windows 系統通知，可自選位置和時長
- 🎨 **主題自訂** — 淺色 / 深色 / 跟隨系統，6 種配色預設，自訂強調色/文字色/背景濃度/圓角
- 🌍 **多語言支援** — 簡體中文 / English，設定中一鍵切換
- 🚫 **忽略管理** — 忽略此版本（版本更新後自動恢復）/ 永久忽略（黑名單可移除）
- ⚡ **開機自啟動** — 可選開機自啟動 + 啟動時自動檢查更新，支援自訂延遲（適應校園網路認證等慢網路環境）
- 🖋️ **字型自訂** — 內建霞鶩新晰黑 / Inter / JetBrains Mono，可從系統字型清單、指定目錄或單一字型檔更換介面字型
- 📄 **執行日誌** — 每日日誌自動寫入磁碟，設定中一鍵開啟
- 🪟 **關於視窗** — 軟體介紹、GitHub 連結、檢查更新
- 🖥️ **雙架構支援** — x64 和 ARM64 獨立建置版本

### 安卓端（`android/`）

- 🔍 **多源更新** — APKPure / Aurora / GitHub Release 聚合，批次檢查更新
- 🔐 **靜默安裝** — Root / Shizuku 免確認直接安裝；支援工作階段式分卷（xapk）安裝
- 🛡️ **偽裝 Google Play 安裝來源** — 靜默安裝時將安裝來源記錄為 `com.android.vending`，繞開「請在 Google Play 安裝/更新」的來源驗證（可關閉；不繞過 Play Integrity）
- 🔔 **進度通知** — 下載/安裝進度顯示於通知列，失敗原因也會出現在通知中
- 🧹 **自動清理** — 安裝後快取 APK 自動清理

## 安裝

前往 [Releases](https://github.com/MCheng404/qm-updater/releases) 頁面，每個 Release 同時包含雙端產物：

| 平台 | 檔案 |
|------|------|
| Windows x64 | `qm-updater-<版本號>-x64-setup.exe`（安裝包）/ `qm-updater-x64.exe`（綠色版） |
| Windows ARM64 | `qm-updater-<版本號>-arm64-setup.exe`（安裝包）/ `qm-updater-arm64.exe`（綠色版） |
| Android | `qm-updater-<版本號>.apk`（簽名 APK，minSdk 26） |

## 從原始碼建置

```bash
# ---- 桌面端（Windows，需 Rust + Node + VS Build Tools）----
npm install
npm run tauri:dev          # 開發模式
npm run tauri:build        # x64 發佈版
npm run tauri:build -- --target aarch64-pc-windows-msvc   # ARM64 發佈版

# ---- 安卓端（需 JDK 17 + Android SDK，Gradle wrapper 已內建）----
cd android
./gradlew :app:assembleDebug     # 除錯包
./gradlew :app:assembleRelease   # 發佈包（versionName/versionCode 自動從倉庫根 package.json 讀取；CI 可用 BUILD_VERSION/BUILD_NUMBER 覆蓋）
```

## 專案結構

```
├── src/                    # 桌面端前端（Vue 3）
│   ├── views/             # MainView / SettingsView / AboutView / NotifyView
│   ├── components/        # TitleBar / UpdateItem / ToastCard
│   └── app/               # i18n / theme / font / settings / stores / icons
├── src-tauri/             # 桌面端 Rust 後端（Tauri 2）
│   └── src/
│       ├── main.rs / lib.rs
│       ├── font.rs / logfile.rs
│       └── sources/       # npm.rs / winget.rs / pip.rs / openclaw.rs
├── android/               # 安卓端（Kotlin + Compose，獨立 Gradle 工程）
│   ├── app/src/main/kotlin/com/qm/updater/
│   │   ├── repository/    # 更新 / 搜尋 / GitHub Release 檢查
│   │   ├── viewmodel/     # 安裝流程、通知
│   │   ├── util/          # Downloader / RootInstaller / SessionInstaller
│   │   └── ui/            # Compose 介面
│   └── gradle/            # Gradle wrapper 9.7.1（已配大陸鏡像）
├── .github/workflows/     # auto-tag.yml：推送到發佈分支自動打 tag；release.yml：tag → 雙端同號一次發佈
└── scripts/               # 版本同步工具
```

## CI / 發佈

**推送即發版**：版本號唯一權威是 `package.json` 的 `version`。更動程式碼後 bump 版本號並推送 `refactor/vue-frontend` 分支，[auto-tag.yml](.github/workflows/auto-tag.yml) 自動打 `vX.Y.Z` tag（版本未變化時跳過）並觸發 [release.yml](.github/workflows/release.yml) 並行建置桌面端安裝包/綠色版（x64 + ARM64，可選 Authenticode 簽名）與安卓端 release APK，最終在**同一個 Release** 下發佈雙端產物（同一版本號）。也可在 Actions 頁面手動 `workflow_dispatch` 觸發。Release 根目錄的 `SHA256SUMS.txt` 涵蓋全部 5 個產物。

## 授權

MIT
