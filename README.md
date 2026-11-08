<p align="center">
  <img src="docs/images/miauterm-terminal.png" alt="Miauterm terminal icon" width="96" height="96">
</p>

<h1 align="center">Miauterm</h1>

<p align="center">An experimental Android terminal that runs Debian ARM64 with PRoot.</p>

<p align="center">
  <a href="https://github.com/synccatcode/miauterm/releases"><img alt="Releases" src="https://img.shields.io/badge/Releases-Miauterm-168f79?style=for-the-badge&logo=android&logoColor=white"></a>
  <a href="https://github.com/synccatcode/miauterm/actions/workflows/build-apk.yml"><img alt="Build APK workflow" src="https://github.com/synccatcode/miauterm/actions/workflows/build-apk.yml/badge.svg?branch=main"></a>
  <img alt="Source version 0.1.0" src="https://img.shields.io/badge/source%20version-0.1.0-34495e?style=for-the-badge">
</p>

Miauterm is an unofficial fork of [Cortex Terminal](https://github.com/ssfuisu/cortex). It replaces Cortex's Ubuntu runtime with a bundled Debian 13 (Trixie) ARM64 root filesystem launched through PRoot. Miauterm is not affiliated with or endorsed by the Cortex Terminal maintainers.

![Experimental project](https://img.shields.io/badge/Project%20status-Experimental-808080?style=flat-square)

## What Miauterm changes

The project keeps parts of Cortex Terminal's Android app and terminal engine under the upstream Apache-2.0 license. Miauterm changes the Linux runtime and adds Debian-specific app behavior.

- Debian ARM64 root filesystem packaged in the app and extracted into Miauterm's private app storage on first setup.
- PRoot-based Linux sessions. The displayed Linux `root` identity is simulated and does not grant Android root access.
- Debian backup, restore, and reset controls.
- Failsafe shell for troubleshooting when Debian cannot start. It remains under the Android app's normal UID and sandbox.
- Optional access to Android's Download and Documents folders, enabled by the user and mounted under `~/android/`.
- Linux file manager, PRoot diagnostics, and an extra-keys editor.
- `miauterm-cmd` app integration commands, including `keysreload` and `reloadfont`.

The terminal emulator, PTY bridge, session UI, and other retained Cortex code remain credited to the upstream project. See [Cortex Terminal](https://github.com/ssfuisu/cortex) and [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md).

## Requirements

| Item | Requirement |
|---|---|
| Android | 7.0 (API 24) or newer |
| CPU architecture | `arm64-v8a` |
| App source version | `0.1.0` |
| Android target SDK | 28 |
| Storage | Allow roughly 1–2 GB for the root filesystem and installed packages |

The app bundles the Debian root filesystem, so first setup extracts it locally. `apt update` and package installation need a working network connection.

## Get Miauterm

Public APK releases will be listed on the [Releases page](https://github.com/synccatcode/miauterm/releases) when available. A manual [Build APK workflow](https://github.com/synccatcode/miauterm/actions/workflows/build-apk.yml) uploads debug APKs as run artifacts. Download the artifact ZIP from the workflow run and extract it before installing the APK.

## Settings and terminal commands

Terminal font, text size, themes, sessions, shared-folder access, backups, and PRoot diagnostics are available in Settings. Shared-folder access is optional; only Download and Documents are mounted when permission is enabled.

To use a custom terminal font, put a valid TTF file at `~/.config/miauterm/font.ttf` in the selected Linux user's home directory, choose **Custom Font** in Settings, then reload it in the active session:

```sh
miauterm-cmd run reloadfont
```

Run `miauterm-cmd --help` inside Debian to see the supported app commands.

## Storage and isolation

The Debian root filesystem is kept in Miauterm's app-private storage. Android's app sandbox still applies to both Debian and failsafe sessions. Linux users inside PRoot share the app's Android UID, so they are not separate Android security identities. If the user enables shared-folder access, Download and Documents are intentionally available inside Debian. Miauterm does not mount Android system directories.

## Project layout

```text
miauterm/
├── .github/
│   ├── release-notes/              # Release notes by version
│   └── workflows/build-apk.yml     # Test, build, and package APK artifacts
├── app/
│   └── src/main/
│       ├── assets/                  # Debian rootfs, fonts, PRoot, and notices
│       ├── java/org/cortex/terminal/
│       │   ├── api/                 # miauterm-cmd local app API
│       │   ├── runtime/             # Debian, PRoot, backup, and file operations
│       │   ├── service/             # Foreground terminal service
│       │   ├── session/             # Terminal sessions
│       │   └── view/                # Terminal and app views
│       └── res/                     # Android resources and translations
├── core/terminal-engine/            # Terminal emulator and native PTY bridge
├── docs/                            # Security policy and project images
├── scripts/                         # Debian rootfs packaging and verification
├── THIRD_PARTY_NOTICES.md
└── LICENSE
```

## Licenses and credits

Miauterm's Cortex-derived source is distributed under Apache License 2.0. The bundled Debian packages, PRoot, talloc, Android shared-memory support, and fonts retain their respective licenses and notices. See [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md), the license files under `app/src/main/assets/licenses/`, and package copyright files inside Debian at `/usr/share/doc/*/copyright`.

- [Cortex Terminal](https://github.com/ssfuisu/cortex), upstream app and terminal code
- [PRoot](https://github.com/proot-me/proot), user-space path and mount emulation
- [Debian](https://www.debian.org/), bundled ARM64 userland
- [JetBrains Mono](https://github.com/JetBrains/JetBrainsMono) and [Fira Code](https://github.com/tonsky/FiraCode), bundled fonts
