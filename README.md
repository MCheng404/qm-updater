# QM Updater

[![GitHub release](https://img.shields.io/github/v/release/MCheng404/qm-updater)](https://github.com/MCheng404/qm-updater/releases)
[![GitHub license](https://img.shields.io/github/license/MCheng404/qm-updater)](https://github.com/MCheng404/qm-updater/blob/main/LICENSE)
[![GitHub downloads](https://img.shields.io/github/downloads/MCheng404/qm-updater/total)](https://github.com/MCheng404/qm-updater/releases)

**[简体中文](README.zh-CN.md) | [繁體中文](README.zh-TW.md) | English**

One repository, two platforms: a unified software updater for **Windows desktop** and **Android**.

- **Desktop** (Tauri 2 + Rust + TypeScript): unified update checks across npm / winget / pip / OpenClaw.
- **Android** (Kotlin + Compose, `android/`): multi-source app updates with Root / Shizuku silent install.

Version numbers of both platforms stay in sync — one Git tag (`vX.Y.Z`) publishes both in a single GitHub Release.

## Features

### Desktop (Windows)

- 🔄 **Four-source unified update check** — npm global packages, winget software, pip Python packages, and OpenClaw
- 📢 **Dual-mode notifications** — In-app acrylic notifications + Windows system notifications, with customizable position and duration
- 🎨 **Theme customization** — Light / Dark / System follow, 6 color presets, custom accent color / text color / background opacity / corner radius
- 🌍 **Multi-language support** — Simplified Chinese / English, switch in settings
- 🚫 **Ignore management** — Ignore this version (auto-recovers after version change) / Ignore forever (blacklist, removable)
- ⚡ **Auto-start on boot** — Optional auto-start + auto-check on startup, with customizable delay for network authentication (e.g. campus Wi-Fi)
- 🖋️ **Font customization** — Built-in LXGW Neo XiHei Plus / Inter / JetBrains Mono; change the UI font from the system font list, a directory, or a single font file
- 📄 **Daily logs** — Logs written to disk per day, open with one click from Settings
- 🪟 **About window** — Software intro, GitHub links, check for updates
- 🖥️ **Dual architecture support** — x64 and ARM64 independent builds

### Android (`android/`)

- 🔍 **Multi-source updates** — APKPure / Aurora / GitHub release aggregation, batch update check
- 🔐 **Silent install** — Root / Shizuku direct install without confirmation; session-based split-APK (xapk) support
- 🛡️ **Fake Google Play install source** — records `com.android.vending` as installer on silent install to bypass "install from Google Play" source checks (configurable; does not bypass Play Integrity)
- 🔔 **Progress notifications** — download / install progress bar in the notification shade, failure details in the notification
- 🧹 **Auto cleanup** — cached APK files, installed package cleanup

## Installation

Go to the [Releases](https://github.com/MCheng404/qm-updater/releases) page. One release contains both platforms:

| Platform | Files |
|----------|-------|
| Windows x64 | `qm-updater-<version>-x64-setup.exe` (installer) / `qm-updater-x64.exe` (portable) |
| Windows ARM64 | `qm-updater-<version>-arm64-setup.exe` (installer) / `qm-updater-arm64.exe` (portable) |
| Android | `qm-updater-<version>.apk` (signed APK, minSdk 26) |

## Build from source

```bash
# ---- Desktop (Windows, requires Rust + Node + VS Build Tools) ----
npm install
npm run tauri:dev          # development mode
npm run tauri:build        # x64 release
npm run tauri:build -- --target aarch64-pc-windows-msvc   # ARM64 release

# ---- Android (requires JDK 17 + Android SDK, Gradle wrapper included) ----
cd android
./gradlew :app:assembleDebug     # debug APK
./gradlew :app:assembleRelease   # release (versionName/versionCode read from repo-root package.json; CI may override via BUILD_VERSION/BUILD_NUMBER)
```

## Project Structure

```
├── src/                    # Desktop frontend (Vue 3)
│   ├── views/             # MainView / SettingsView / AboutView / NotifyView
│   ├── components/        # TitleBar / UpdateItem / ToastCard
│   └── app/               # i18n / theme / font / settings / stores / icons
├── src-tauri/             # Desktop Rust backend (Tauri 2)
│   └── src/
│       ├── main.rs / lib.rs
│       ├── font.rs / logfile.rs
│       └── sources/       # npm.rs / winget.rs / pip.rs / openclaw.rs
├── android/               # Android app (Kotlin + Compose, independent Gradle project)
│   ├── app/src/main/kotlin/com/qm/updater/
│   │   ├── repository/    # updates / search / GitHub release checks
│   │   ├── viewmodel/     # install flow, notifications
│   │   ├── util/          # Downloader / RootInstaller / SessionInstaller
│   │   └── ui/            # Compose screens
│   └── gradle/            # Gradle wrapper 9.7.1 (mirrored)
├── .github/workflows/     # auto-tag.yml: push to release branch auto-tags; release.yml: tag → desktop + Android same-version Release
└── scripts/               # version sync helpers
```

## CI / Release

**Push-to-release**: `package.json` `version` is the single source of truth. Bump the version and push the `refactor/vue-frontend` branch — [auto-tag.yml](.github/workflows/auto-tag.yml) creates the `vX.Y.Z` tag automatically (skipped when the version is unchanged), which triggers [release.yml](.github/workflows/release.yml) to build the desktop installer/portable (x64 + ARM64, optional Authenticode signing) and the Android release APK in parallel, then publish **one Release with both platforms** under the same version number. A manual `workflow_dispatch` trigger is also available. `SHA256SUMS.txt` at the release root covers all 5 artifacts.

## License

MIT
