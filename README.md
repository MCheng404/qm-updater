# QM Updater

[![GitHub release](https://img.shields.io/github/v/release/MCheng404/qm-updater)](https://github.com/MCheng404/qm-updater/releases)
[![GitHub license](https://img.shields.io/github/license/MCheng404/qm-updater)](https://github.com/MCheng404/qm-updater/blob/main/LICENSE)
[![GitHub downloads](https://img.shields.io/github/downloads/MCheng404/qm-updater/total)](https://github.com/MCheng404/qm-updater/releases)

**[简体中文](README.zh-CN.md) | [繁體中文](README.zh-TW.md) | English**

A desktop tool for unified software updates across npm, winget, pip, and OpenClaw. Built with Tauri 2 + Rust + TypeScript. Lightweight, fast, and seamless.

## Features

- 🔄 **Four-source unified update check** — Simultaneously check npm global packages, winget software, pip Python packages, and OpenClaw
- 📢 **Dual-mode notifications** — In-app acrylic notifications + Windows system notifications, with customizable position and duration
- 🎨 **Theme customization** — Light / Dark / System follow, 6 color presets, custom accent color / text color / background opacity / corner radius
- 🌍 **Multi-language support** — Simplified Chinese / English, switch in settings
- 🚫 **Ignore management** — Ignore this version (auto-recovers after version change) / Ignore forever (blacklist, removable)
- ⚡ **Auto-start on boot** — Optional auto-start + auto-check on startup, with customizable delay for network authentication (e.g. campus Wi-Fi)
- 🖋️ **Font customization** — Built-in LXGW Neo XiHei Plus / Inter / JetBrains Mono; change the UI font from the system font list, a directory, or a single font file, with tiered size scaling
- 📄 **Daily logs** — Logs written to disk per day, open with one click from Settings; click a failed item to jump straight to its log
- 🔍 **HiDPI aware** — Windows auto-fit to the monitor work area under system DPI scaling
- 🪟 **About window** — Software intro, GitHub links, check for updates
- 🖥️ **Dual architecture support** — x64 and ARM64 independent builds

## Tech Stack

- **Frontend**: Vue 3 + TypeScript + Vite + Pinia + vue-i18n (native CSS, no UI component library)
- **Backend**: Rust + Tauri 2
- **Window effects**: Acrylic / Mica blur
- **Packaging**: NSIS installer

## Installation

### Download

Go to the [Releases](https://github.com/MCheng404/qm-updater/releases) page and download the latest version:

| Architecture | Installer | Portable |
|--------------|-----------|----------|
| x64 (Intel/AMD) | `qm-updater-<version>-x64-setup.exe` | `qm-updater-x64.exe` |
| ARM64 (Snapdragon) | `qm-updater-<version>-arm64-setup.exe` | `qm-updater-arm64.exe` |

> Not sure which architecture? Open **Settings → System → About** and check **System type**.

### Build from source

```bash
# Install dependencies
npm install

# Development mode
npm run tauri:dev

# Build x64 release
npm run tauri:build

# Build ARM64 release
npm run tauri:build -- --target aarch64-pc-windows-msvc
```

## Project Structure

```
├── src/                    # Frontend source (Vue 3)
│   ├── entries/           # Entry points for the four windows: main / settings / notify / about
│   ├── views/             # Window views: MainView / SettingsView / NotifyView / AboutView
│   ├── components/        # Shared components: TitleBar / UpdateItem / ToastCard
│   ├── app/               # Shared layer
│   │   ├── i18n.ts        # vue-i18n instance
│   │   ├── locales/       # zh-CN / en-US message packs
│   │   ├── theme.ts       # Theme system
│   │   ├── font.ts        # Font settings
│   │   ├── app-settings.ts# App settings
│   │   ├── stores.ts      # Pinia stores (theme / app settings)
│   │   ├── shared.ts      # Shared plugin and cross-window sync
│   │   ├── icons.ts       # Inline vector icons
│   │   └── types.ts       # Shared frontend/backend types
│   └── styles.css         # Global styles
├── src-tauri/             # Rust backend
│   ├── src/
│   │   ├── main.rs        # Entry point
│   │   ├── lib.rs         # Command registration
│   │   ├── font.rs        # System font enumeration / custom fonts
│   │   ├── logfile.rs     # Daily log persistence
│   │   ├── sources/       # Update sources
│   │   │   ├── npm.rs
│   │   │   ├── winget.rs
│   │   │   ├── pip.rs
│   │   │   └── openclaw.rs
│   │   └── ...
│   └── tauri.conf.json    # Tauri configuration
├── index.html             # Main window
├── settings.html          # Settings window
├── notify.html            # Notification window
└── about.html             # About window
```

## License

MIT
