<p align="center">
  <img src="docs/images/miauterm-terminal.png" alt="Miauterm terminal icon" width="88" height="88">
</p>

<h1 align="center">Miauterm</h1>

<p align="center">An experimental Android terminal that runs Debian ARM64 in PRoot.</p>

<p align="center">
  <a href="https://github.com/synccatcode/miauterm/releases/latest"><img alt="Latest release version" src="https://img.shields.io/github/v/release/synccatcode/miauterm?style=for-the-badge&label=Latest%20release"></a>
  <img alt="Android ARM64" src="https://img.shields.io/badge/Android-arm64--v8a-3c7d5b?style=for-the-badge&logo=android&logoColor=white">
  <a href="https://github.com/synccatcode/miauterm/actions/workflows/build-apk.yml"><img alt="Build status" src="https://img.shields.io/github/actions/workflow/status/synccatcode/miauterm/build-apk.yml?event=workflow_dispatch&style=for-the-badge&label=Build%20status"></a>
</p>

> **Project status: Experimental**<br>
> The published APK is a test release for ARM64 Android devices. Runtime behavior and package compatibility can vary by device and Android version.

Miauterm is an unofficial, experimental fork of [Cortex Terminal](https://github.com/ssfuisu/cortex). It retains upstream terminal and Android application code while replacing Cortex's native Ubuntu runtime with a Debian 13 (Trixie) ARM64 root filesystem launched through PRoot. Miauterm is an independent project and is not affiliated with or endorsed by the Cortex maintainers.

## How it differs from Cortex

[Cortex Terminal](https://github.com/ssfuisu/cortex) runs Ubuntu natively on Android using its own userspace integration. Its upstream README describes that design as running without PRoot, a virtual machine, CPU emulation, or Android root access.

Miauterm takes a different route: it starts a Debian root filesystem under PRoot. PRoot presents Linux root paths and translates filesystem access while the Debian programs run as the app's Android process. This lets Miauterm use a conventional Debian userspace without requiring Android root. It does not emulate an ARM CPU; the included Debian root filesystem and native binaries target `arm64-v8a` devices. PRoot has different compatibility and performance tradeoffs from Cortex's native Ubuntu runtime.

| | Cortex Terminal | Miauterm |
|---|---|---|
| Linux userspace | Ubuntu 24.04, as described by upstream | Debian 13 (Trixie) ARM64 |
| Runtime | Native Android userspace integration | PRoot |
| CPU emulation | None | None for the ARM64 build |
| Android root required | No | No |
| Android security boundary | Android application sandbox | Android application sandbox; Linux users share the app's Android UID |

## Features

- Bundled Debian 13 ARM64 root filesystem with first-run extraction progress.
- PRoot terminal sessions, a failsafe shell, and Debian reset, backup, and restore tools.
- File browser for the app's Linux files, with file type and write-permission indicators.
- Optional access to Android's Download and Documents folders. Access is off until enabled by the user.
- Terminal themes, font selection, custom font reload, and configurable extra keys.
- User selection for Debian sessions. An empty selection starts a root PRoot session; configured users are selected by their exact Debian username.
- PRoot diagnostics and optional logging controls in Settings.
- `miauterm-cmd` for commands that connect a Debian shell to selected Miauterm app functions.

These features are implemented across Miauterm changes and retained Cortex components. See [Credits](#credits) and [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md) for attribution and component notices.

## Requirements and storage

| Requirement | Details |
|---|---|
| Android version | Android 7.0 (API 24) or newer |
| Target SDK | 28 |
| CPU architecture | `arm64-v8a` |
| Linux distribution | Bundled Debian 13 (Trixie) ARM64 root filesystem |
| App version | 0.1.0 |

Miauterm stores Debian inside its app-private files directory and extracts the bundled root filesystem on first start. Leave enough device storage for the extracted system and any packages you install. `apt update` and package installation require network access.

The Debian environment does not receive general access to Android storage. If enabled in Settings, only the selected Download and Documents locations are shared with new Linux sessions under `~/android/`. Both Debian and failsafe sessions remain inside the Android app sandbox. PRoot users are not separate Android users and do not gain Android root privileges.

## Download

Download the signed ARM64 APK from the [Miauterm v0.1.0 release](https://github.com/synccatcode/miauterm/releases/tag/v0.1.0):

- [Installable release APK](https://github.com/synccatcode/miauterm/releases/download/v0.1.0/Miauterm-v0.1.0-arm64.apk)
- [Release APK SHA-256](https://github.com/synccatcode/miauterm/releases/download/v0.1.0/Miauterm-v0.1.0-arm64.apk.sha256)
- [Debug APK](https://github.com/synccatcode/miauterm/releases/download/v0.1.0/Miauterm-arm64-debug.apk)

The [Releases page](https://github.com/synccatcode/miauterm/releases) contains the current APKs and their checksums. The [Build APK workflow](https://github.com/synccatcode/miauterm/actions/workflows/build-apk.yml) runs rootfs validation and selected tests, builds ARM64 APKs, and verifies the release package before publishing it.

## Terminal commands and fonts

Show the available app commands from a Debian shell:

```sh
miauterm-cmd --help
```

For a custom font, place a readable TTF file at `~/.config/miauterm/font.ttf` in the selected Debian user's home, select **Custom Font** in Settings, then run this in the active terminal:

```sh
miauterm-cmd run reloadfont
```

The app command service must be active for `miauterm-cmd` commands to reach Android. The command list and arguments are also documented in the bundled [`miauterm-cmd` script](app/src/main/assets/miauterm-cmd).

## Build from source

The GitHub Actions workflow is the project's release build path. It uses Java 17, Gradle 8.7, Android SDK platform 35, NDK 26.1.10909125, and CMake 3.22.1.

On a development computer with those tools installed, build a debug APK with:

```sh
./gradlew :app:assembleDebug
```

The APK is written under `app/build/outputs/apk/debug/`. The release variant requires the signing environment variables configured in `app/build.gradle.kts`; do not commit signing keys or passwords.

To inspect CI build results, open [Actions](https://github.com/synccatcode/miauterm/actions/workflows/build-apk.yml). The workflow also validates the bundled Debian root filesystem before building.

## Project structure

```text
miauterm/
├── .github/
│   ├── dependabot.yml
│   ├── release-notes/
│   │   └── v0.1.0.md
│   └── workflows/
│       └── build-apk.yml
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── assets/
│       │   │   ├── debian-arm64.tar.zst
│       │   │   ├── fonts/
│       │   │   ├── licenses/
│       │   │   ├── miauterm-cmd
│       │   │   └── proot/arm64-v8a/
│       │   ├── java/org/cortex/terminal/
│       │   │   ├── api/       # Android app command bridge
│       │   │   ├── runtime/   # Debian, PRoot, backup, and file operations
│       │   │   ├── service/   # Background terminal service
│       │   │   ├── session/   # Terminal session lifecycle
│       │   │   └── view/      # Terminal and extra-key views
│       │   └── res/
│       │       ├── drawable*/
│       │       ├── layout/
│       │       ├── mipmap*/
│       │       ├── values*/  # Strings and localized Android resources
│       │       └── xml/
│       └── test/java/org/cortex/terminal/
│           ├── api/
│           ├── runtime/
│           ├── service/
│           ├── session/
│           └── view/
├── core/
│   └── terminal-engine/
│       ├── build.gradle.kts
│       └── src/
│           ├── main/cpp/     # PTY bridge, CMake config, and Zstandard sources
│           ├── main/java/org/cortex/terminal/
│           │   ├── emulator/
│           │   ├── pty/
│           │   └── view/
│           └── test/java/org/cortex/terminal/emulator/
├── docs/
│   ├── images/
│   └── SECURITY.md
├── gradle/
│   ├── libs.versions.toml
│   └── wrapper/
├── scripts/
│   ├── package-debian-rootfs.sh
│   ├── sanitize-rootfs-tar.py
│   └── verify-rootfs.sh
├── build.gradle.kts
├── gradle.properties
├── gradlew
├── gradlew.bat
├── settings.gradle.kts
├── LICENSE
├── README.md
└── THIRD_PARTY_NOTICES.md
```

The `org.cortex.terminal` package and `core/terminal-engine` contain upstream-derived code alongside Miauterm changes. The package names do not imply that Miauterm authored the retained upstream components.

## Credits

- [Cortex Terminal](https://github.com/ssfuisu/cortex) for the upstream Android terminal application and terminal engine code used as the starting point for this fork.
- [PRoot](https://github.com/proot-me/proot) for the user-space runtime used to launch Debian.
- [Debian](https://www.debian.org/) for Debian 13 and its ARM64 userspace.
- [JetBrains Mono](https://github.com/JetBrains/JetBrainsMono) and [Fira Code](https://github.com/tonsky/FiraCode) for bundled fonts.

See [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md) and the files under `app/src/main/assets/licenses/` for the notices supplied with bundled components.
