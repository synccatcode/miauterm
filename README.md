<p align="center">
  <img src="docs/images/miauterm-terminal.png" alt="Miauterm" width="112" height="112">
</p>

<h1 align="center">Miauterm</h1>

<p align="center">An Android terminal running a bundled Debian ARM64 root filesystem via PRoot — forked from Cortex Terminal.</p>

<p align="center">
  <a href="https://github.com/synccatcode/miauterm/releases/latest"><img alt="Download APK" src="https://img.shields.io/badge/Download-APK-168f79?style=for-the-badge&logo=android&logoColor=white"></a>
  <a href="https://github.com/synccatcode/miauterm/releases/latest"><img alt="v0.1.0" src="https://img.shields.io/badge/release-v0.1.0-34495e?style=for-the-badge"></a>
</p>

---

Miauterm is a fork of [Cortex Terminal](https://github.com/ssfuisu/cortex). It packages and launches a bundled Debian ARM64 userland through PRoot inside Android app storage without requiring root privileges or manual setup scripts.

---

## Overview

Miauterm builds on the architecture established by Cortex Terminal, maintaining a ready-to-use Debian environment that initializes directly on device launch.

* **Fork of Cortex Terminal:** Retains upstream terminal and engine foundations under the Apache 2.0 license while maintaining localized improvements, configurations, and release management.
* **Bundled Debian userland:** An interactive Debian shell with `apt`, package management, and basic utilities available immediately after installation.
* **No device root required:** Uses PRoot to simulate root privileges and virtualize filesystem paths in user space, leaving Android system partitions untouched.

---

## Upstream Focus vs. Miauterm Approach

Both projects bundle their Linux root filesystem directly inside the application APK for an offline, ready-to-use first launch. However, their underlying execution architectures are fundamentally different:

* **Upstream Cortex Terminal:** Bundles **Ubuntu 24.04 LTS (Glibc 2.39)** and executes it natively using direct loader techniques with **zero PRoot, zero containers, and zero virtualization**, prioritizing raw native performance without syscall translation overhead.
* **Miauterm Fork:** Replaces the native Ubuntu runner with a bundled **Debian ARM64 userland running via PRoot**. Miauterm trades raw execution speed for the consistent path virtualization and environment stability that PRoot provides across diverse Android devices and OEM kernels.

### Why Miauterm Chose Debian + PRoot:

* **Pre-bundled Debian Rootfs:** Like upstream's bundled Ubuntu, Miauterm packages its base root filesystem (`debian-arm64.tar.zst`) directly inside the APK. Extraction and initialization happen completely locally on first launch without downloading external bootstrap packages.
* **Consistent Userland Virtualization:** Running glibc-based Linux directly on Android without PRoot requires patching ELF headers, loaders, and managing strict Android Bionic/SELinux boundaries. PRoot simplifies this by virtualizing file paths and simulating standard root ownership via `ptrace(2)` entirely in user space.
* **Debian Footprint & Stability:** Debian ARM64 offers a compact, predictable base package ecosystem well-suited for mobile storage constraints.
* **Sandboxed Storage:** All userland files remain strictly within Miauterm's app-private directory (`/data/data/com.miauterm.terminal/files`), keeping Android system partitions untouched.

### Technical Trade-offs
Using PRoot instead of Cortex's zero-virtualization approach involves clear trade-offs:
* **Syscall Overhead:** Every system call passes through `ptrace` interception, so heavy I/O operations and builds are noticeably slower than Cortex's native execution.
* **Kernel Ceiling:** Operations that require true kernel-level privileges (e.g. kernel modules, raw sockets, container namespaces) cannot function under PRoot.
* **Target SDK 28:** Stays at API 28 so userland binaries can execute from app-private storage under Android's W^X policy.

---

## Features & Upstream Capabilities

The terminal emulation core and application framework are provided by Cortex Terminal, including:

### Terminal & Engine
* xterm-256color terminal emulator supporting Unicode, wide characters, and sextant block elements.
* Live in-terminal search bar with next/previous navigation and match counter.
* Pinch-to-zoom font scaling with persistent size retention.
* Tap-to-copy URL detection directly from the output buffer.
* OSC 52 clipboard write support for terminal programs (tmux, Neovim).

### Sessions & Background
* Concurrent terminal sessions managed through a top tab strip or navigation drawer.
* Session restoration preserving directory context and recent buffer history.
* Background execution support with wake lock toggle and notifications for long-running commands.
* Local HTTP server detection notifying when ports open on localhost.
* Configurable login user selection from `/etc/passwd`.

### Extra Keys Toolbar
* Configurable keyboard toolbar with latching modifiers (CTRL, ALT) and function keys.
* Editable layout stored at `~/.config/miauterm/keys` (nested JSON format) with an in-app visual editor in Settings.
* Dynamic reload support via `miauterm-cmd run keysreload`.

### System & Storage Management
* Built-in Linux file manager with file editing and permission viewing.
* Optional shared folder access mounting Android `Download` and `Documents` into `~/android/`.
* Backup and restore utility exporting Debian files, permissions, and symlinks to a structured ZIP archive.
* Failsafe recovery shell and diagnostic logging tools for troubleshooting.

---

## Requirements

| Specification | Requirement |
|---|---|
| **Operating System** | Android 15 (API 35) or newer |
| **Architecture** | `arm64-v8a` |
| **Storage** | ~1–2 GB free space for Debian rootfs and user packages |

---

## Installation

Download the signed APK and SHA-256 verification checksum from [GitHub Releases](https://github.com/synccatcode/miauterm/releases/latest).

Verify package integrity:
```sh
sha256sum -c Miauterm-v0.1.0-arm64.apk.sha256
```

---

## Settings Reference

| Option | Description |
|---|---|
| **Color Scheme** | Choose interface and terminal color palette |
| **Terminal Font** | JetBrains Mono, Fira Code, System Monospace, or custom TTF |
| **Session Tabs** | Toggle visibility of session tabs above the terminal |
| **Keep Screen On** | Prevent screen sleep while terminal is running |
| **Secure Screen** | Enforce `FLAG_SECURE` to block screenshots and recents previews |
| **Shared Storage** | Mount Android Download and Documents into `~/android/` |
| **Command Alerts** | Notifications for long commands completed in background |
| **Clipboard Bridge** | Allow programs inside Debian to write to Android clipboard (OSC 52) |
| **Server Detection** | Display alerts with direct URLs when localhost HTTP ports open |
| **Backup / Restore** | Archive Debian filesystem state or restore from previously saved backups |
| **Failsafe Console** | Emergency Android shell bypassing the PRoot environment |

---

## Privacy & Security

* The Debian root filesystem resides in Miauterm's private application storage (`/data/data/com.miauterm.terminal/files`).
* Android system storage and private data of other applications remain inaccessible.
* Linux accounts inside PRoot share the same underlying unprivileged Android application UID.

---

## Project Structure

```text
miauterm/
├── .github/
│   ├── dependabot.yml
│   ├── release-notes/              # Per-version changelogs
│   └── workflows/
│       └── build-apk.yml           # CI workflow: build, test, sign & release APK
├── app/                            # Android application module
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── assets/
│       │   │   ├── debian-arm64.tar.zst    # Bundled base rootfs archive
│       │   │   ├── fonts/                  # JetBrains Mono & Fira Code TTF
│       │   │   ├── licenses/               # Third-party license notices
│       │   │   ├── miauterm-cmd            # In-guest CLI command utility
│       │   │   └── proot/arm64-v8a/        # Prebuilt PRoot binaries & loader
│       │   ├── java/org/cortex/terminal/
│       │   │   ├── api/                    # Local app IPC server & command handlers
│       │   │   ├── runtime/                # PRoot launcher, rootfs manager & ELF patcher
│       │   │   ├── service/                # Foreground service & local server detector
│       │   │   ├── session/                # Terminal sessions, adapters & persistence
│       │   │   ├── view/                   # Terminal UI, extra-keys toolbar & theme chrome
│       │   │   └── *.kt                    # Activity & application entry points
│       │   └── res/                        # Layouts, themes, drawables & localizations
│       └── test/                           # App unit tests (Robolectric / JUnit)
├── core/
│   └── terminal-engine/            # Low-level terminal & PTY library module
│       ├── build.gradle.kts
│       └── src/
│           ├── main/
│           │   ├── cpp/                    # Native PTY bridge (JNI) & bundled zstd decoder
│           │   │   ├── CMakeLists.txt
│           │   │   ├── cortex-pty.c
│           │   │   └── zstd/
│           │   └── java/org/cortex/terminal/
│           │       ├── emulator/           # VT/xterm emulator, buffers & color schemes
│           │       ├── pty/                # Native PTY process interface
│           │       └── view/               # Sextant glyph matrix data
│           └── test/                       # Terminal engine parser & buffer test suite
├── docs/
│   ├── SECURITY.md
│   └── images/
│       └── miauterm-terminal.png
├── gradle/
│   ├── libs.versions.toml          # Centralized dependency catalog
│   └── wrapper/                    # Gradle wrapper binaries & properties
├── scripts/
│   ├── package-debian-rootfs.sh    # Rootfs packaging script
│   ├── sanitize-rootfs-tar.py      # Archive metadata & sanitization tool
│   └── verify-rootfs.sh            # Rootfs integrity verification
├── build.gradle.kts                # Root project build script
├── gradle.properties
├── gradlew
├── gradlew.bat
├── settings.gradle.kts             # Module definitions
├── LICENSE                         # Apache 2.0 license
└── THIRD_PARTY_NOTICES.md          # Comprehensive third-party attributions
```

---

## Credits

* **[Cortex Terminal](https://github.com/ssfuisu/cortex):** Upstream base project (Apache-2.0, see [`LICENSE`](LICENSE))
* **[PRoot](https://github.com/proot-me/proot):** User-space chroot/mount emulation (GPL-2.0-or-later)
* **[talloc](https://talloc.samba.org/):** Hierarchical memory allocator (GPL-3.0-or-later)
* **[android-shmem](https://github.com/termux/android-shmem):** System V shared memory support on Android
* **[JetBrains Mono](https://github.com/JetBrains/JetBrainsMono) & [Fira Code](https://github.com/tonsky/FiraCode):** Bundled monospace typefaces (SIL Open Font License 1.1)
* **[Debian GNU/Linux](https://www.debian.org/):** Bundled root filesystem userland (individual package licenses apply)
