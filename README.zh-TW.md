# QM Updater

[![GitHub release](https://img.shields.io/github/v/release/MCheng404/qm-updater)](https://github.com/MCheng404/qm-updater/releases)
[![GitHub license](https://img.shields.io/github/license/MCheng404/qm-updater)](https://github.com/MCheng404/qm-updater/blob/main/LICENSE)
[![GitHub downloads](https://img.shields.io/github/downloads/MCheng404/qm-updater/total)](https://github.com/MCheng404/qm-updater/releases)

**[简体中文](README.zh-CN.md) | 繁體中文 | [English](README.md)**

統一管理 npm / winget / pip / OpenClaw 四種來源軟體更新的桌面工具，以 Tauri 2 + Rust + TypeScript 打造。輕量、快速、無感。

## 功能特色

- 🔄 **四源統一更新檢查** — 同時檢查 npm 全域套件、winget 軟體、pip Python 套件、OpenClaw
- 📢 **雙模式通知** — 應用程式內浮層通知 + Windows 系統通知，可自選位置與時長
- 🎨 **主題自訂** — 淺色 / 深色 / 跟隨系統，6 種配色預設，自訂強調色 / 文字色 / 背景濃度 / 圓角
- 🌍 **多語言支援** — 簡體中文 / English，於設定中一鍵切換
- 🚫 **忽略管理** — 忽略此版本（版本更新後自動恢復）/ 永久忽略（黑名單可移除）
- ⚡ **開機自動啟動** — 可選開機自動啟動 + 啟動時自動檢查更新，支援自訂延遲（適應校園網路認證等較慢的網路環境）
- 🖋️ **字型自訂** — 內建霞鶩新晰黑 / Inter / JetBrains Mono，可從系統字型清單、指定資料夾或單一字型檔更換介面字型，並分級縮放字級
- 📄 **執行日誌** — 每日日誌自動寫入磁碟，設定中一鍵開啟；更新失敗的項目點一下即可跳至對應日誌
- 🔍 **高解析度螢幕適配** — 視窗自動配合系統 DPI 縮放，避免超出可用工作區
- 🪟 **關於視窗** — 軟體介紹、GitHub 連結、檢查更新
- 🖥️ **雙架構支援** — x64 與 ARM64 獨立建置版本

## 技術棧

- **前端**: Vue 3 + TypeScript + Vite + Pinia + vue-i18n（原生 CSS，無 UI 元件庫）
- **後端**: Rust + Tauri 2
- **介面**: 純色分層表面 · 大圓角 · 懸浮卡片 · 統一動效
- **封裝**: NSIS 安裝程式

## 安裝

### 下載

前往 [Releases](https://github.com/MCheng404/qm-updater/releases) 頁面下載最新版本：

| 架構 | 安裝程式 | 免安裝版 |
|------|----------|----------|
| x64（Intel / AMD） | `qm-updater-<版本號>-x64-setup.exe` | `qm-updater-x64.exe` |
| ARM64（高通驍龍） | `qm-updater-<版本號>-arm64-setup.exe` | `qm-updater-arm64.exe` |

> 不確定自己的架構？開啟「設定 → 系統 → 關於」，查看「系統類型」。

### 從原始碼建置

```bash
# 安裝依賴
npm install

# 開發模式
npm run tauri:dev

# 建置 x64 發行版
npm run tauri:build

# 建置 ARM64 發行版
npm run tauri:build -- --target aarch64-pc-windows-msvc
```

## 專案結構

```
├── src/                    # 前端原始碼（Vue 3）
│   ├── App.vue            # 單視窗外殼：標題列 + 左側導覽 + 檢視切換
│   ├── entries/           # 進入點：main（主視窗）/ notify（通知浮層）
│   ├── views/             # 檢視：MainView / SettingsView / AboutView / NotifyView
│   ├── components/        # 共用元件：TitleBar / UpdateItem / ToastCard
│   ├── app/               # 共用層
│   │   ├── i18n.ts        # vue-i18n 實例
│   │   ├── locales/       # 中英文語言包
│   │   ├── theme.ts       # 主題系統
│   │   ├── font.ts        # 字型設定
│   │   ├── app-settings.ts# 應用程式設定
│   │   ├── stores.ts      # Pinia stores（主題 / 應用程式設定）
│   │   ├── shared.ts      # 共用外掛與跨視窗同步
│   │   ├── icons.ts       # 內嵌向量圖示
│   │   └── types.ts       # 前後端介面型別
│   └── styles.css         # 全域樣式
├── src-tauri/             # Rust 後端
│   ├── src/
│   │   ├── main.rs        # 進入點
│   │   ├── lib.rs         # 指令註冊
│   │   ├── font.rs        # 系統字型列舉 / 自訂字型
│   │   ├── logfile.rs     # 每日日誌寫入
│   │   ├── sources/       # 更新來源
│   │   │   ├── npm.rs
│   │   │   ├── winget.rs
│   │   │   ├── pip.rs
│   │   │   └── openclaw.rs
│   │   └── ...
│   └── tauri.conf.json    # Tauri 設定
├── index.html             # 主視窗（內含 更新 / 設定 / 關於 三個檢視）
└── notify.html            # 通知浮層視窗
```

## 授權條款

MIT
