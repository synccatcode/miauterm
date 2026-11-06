<p align="center">
  <img src="docs/images/miauterm-terminal.png" alt="Miauterm" width="112" height="112">
</p>

<h1 align="center">Miauterm</h1>

<p align="center">An unofficial fork of Cortex Terminal that runs a bundled Debian ARM64 userland via PRoot.</p>

<p align="center">
  <a href="https://github.com/synccatcode/miauterm/releases/latest"><img alt="Download APK" src="https://img.shields.io/badge/Download-APK-168f79?style=for-the-badge&logo=android&logoColor=white"></a>
  <a href="https://github.com/synccatcode/miauterm/releases/latest"><img alt="v0.1.0" src="https://img.shields.io/badge/release-v0.1.0-34495e?style=for-the-badge"></a>
</p>

---

Miauterm is an **unofficial fork** of [Cortex Terminal](https://github.com/ssfuisu/cortex). It is maintained independently and is not affiliated with, endorsed by, or an official release of the upstream Cortex project.

---

## Overview

Miauterm inherits the terminal emulation engine, UI chrome, and session management framework from Cortex Terminal, but completely replaces the execution backend and guest OS:

* **Upstream Cortex Terminal Foundation:** Retains the core VT/xterm emulator, PTY JNI bridge, session lifecycle, and Material UI under the Apache 2.0 license.
* **Debian Userland via PRoot:** Instead of upstream's native Ubuntu environment, Miauterm bundles a pre-packaged Debian 13 (Trixie) ARM64 root filesystem launched inside PRoot in private app storage.
* **No Root Required:** Operates entirely within the standard Android application sandbox without elevated device privileges.

---

## Upstream Focus vs. Miauterm Approach

Both projects bundle their base Linux root filesystem directly inside the application APK, allowing the initial environment to unpack locally on first launch without downloading external bootstrap images. (Subsequent package installations and `apt update`, of course, still require an active internet connection). However, their underlying execution architectures are fundamentally different:

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

## Feature Breakdown: Upstream Cortex vs. Miauterm Additions

To be completely transparent about what belongs to upstream Cortex Terminal and what was added or modified in this fork:

### Features Inherited from Upstream Cortex Terminal
The following core terminal features, architecture, and UI components are built directly upon upstream Cortex Terminal:
* **Terminal Emulator Engine:** Full xterm-256color terminal engine supporting Unicode, wide character rendering, and sextant block elements.
* **Session Management:** Concurrent terminal session tabs, navigation drawer, and session persistence across app lifecycle restarts.
* **Native PTY Bridge:** Custom JNI PTY connector (`cortex-pty.c`) and embedded zstd decompression decoder.
* **Interactive UI & Themes:** Theming engine (Catppuccin, Nord, Solarized, etc.), pinch-to-zoom text scaling, tap-to-copy URL detection, and OSC 52 clipboard support.
* **Foreground Service & Local Servers:** Background terminal service with wake lock toggle and automatic detection of localhost web servers started inside the guest.

### Features Added / Modified in Miauterm
The following features, modifications, and system utilities were specifically introduced in Miauterm:
* **PRoot Runtime Integration:** Complete replacement of upstream native Ubuntu execution with the bundled PRoot arm64-v8a binary and loader (`libtalloc`, `libandroid-shmem`).
* **Debian ARM64 Packaging & Management:** Bundled Debian rootfs packaging script (`package-debian-rootfs.sh`), sanitize pipeline (`sanitize-rootfs-tar.py`), and rootfs re-installation/reset system.
* **Debian Backup & Restore:** Complete backup export and restore mechanism (`DebianBackup.kt`) that archives Linux files, symlinks, and file modes into a structured ZIP archive.
* **Linux File Manager (`DebianFilesActivity.kt`):** A dedicated graphical file browser for navigating, previewing, and editing files inside the Debian root filesystem directly from the drawer.
* **Failsafe Console:** A dedicated failsafe mode (`pendingFailsafeSession`) that drops into an uncontained Android shell in app-private storage for diagnosing broken rootfs states.
* **PRoot Diagnostic Logging:** System in Settings to capture, view (`view_proot_log`), and clear detailed PRoot debug traces.
* **Shared Storage Permissions:** Controlled integration for mounting Android's `Download` and `Documents` folders into `~/android/Download` and `~/android/Documents`.
* **Dynamic Linker Syscall Patching (`ElfLinkerPatcher.kt`):** Runtime binary patching targeting incompatible syscalls (`set_robust_list`, `clone3`, `rseq`) on Android kernels.
* **In-app Extra Keys Visual Editor:** Settings UI to visually add, edit, and validate extra key combinations in `~/.config/miauterm/keys` with instant CLI reload support (`miauterm-cmd run keysreload`).
* **Local First-Boot Extraction:** Automated local extraction and verification flow on first launch with progress reporting, unpackaging the bundled base rootfs directly without external bootstrap downloads.

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
