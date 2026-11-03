<p align="center">
  <img src="docs/images/miauterm-terminal.png" alt="Miauterm app icon" width="112" height="112">
</p>

<h1 align="center">Miauterm</h1>

<p align="center">An experimental Android terminal for running Linux root filesystems with PRoot.</p>

<p align="center">
  <a href="https://github.com/synccatcode/miauterm/releases/latest"><img alt="Download latest APK" src="https://img.shields.io/badge/Download-Android%20APK-168f79?style=for-the-badge&logo=android&logoColor=white"></a>
  <a href="https://github.com/synccatcode/miauterm/releases/latest"><img alt="Latest release v0.1.0" src="https://img.shields.io/badge/Latest%20release-v0.1.0-34495e?style=for-the-badge"></a>
</p>

Miauterm is an unofficial fork of Cortex Terminal. It launches the bundled Debian ARM64 system with PRoot from Miauterm's private app storage. This repository maintains its own app changes, builds, and releases.

The app is in development and behavior may change between releases. Back up important files before changing or replacing a root filesystem.

## Download

Download `Miauterm-v0.1.0-arm64.apk` (signed) or `Miauterm-arm64-debug.apk` from [GitHub Releases](https://github.com/synccatcode/miauterm/releases/latest). Each APK is published with a SHA-256 checksum.

```sh
sha256sum -c Miauterm-v0.1.0-arm64.apk.sha256
```

Miauterm requires Android 15 (API 35) or newer on an `arm64-v8a` device. APKs are built and signed by GitHub Actions. The app targets API 28 to preserve PRoot execution from app-private storage; raising the target API requires repackaging the runtime. See the release notes for this compatibility constraint.

## What it does

- Starts an interactive Linux shell with PRoot; Debian login shells display the root filesystem's MOTD.
- Keeps the Linux root filesystem and PRoot runtime in Miauterm's app-private storage. It does not bind Android system directories.
- Offers optional access to Android `Download` and `Documents` at `/root/android/Download` and `/root/android/Documents`. Grant or revoke access in Settings; failsafe sessions omit these mounts.
- Resets Debian by reinstalling the bundled ARM64 root filesystem. Debian backups can be restored from Settings.
- Provides failsafe sessions, PRoot diagnostics, backup and restore, and rootfs reset and removal.
- Includes a file manager for the Linux filesystem and the app's Android shared-folder location.
- Lets users select an existing Linux account for new sessions. PRoot emulates Linux IDs; this does not create separate Android security identities or a strong privacy boundary between Linux users.
- Shows the active Linux account prompt, such as `root@localhost ~#`, in the terminal tabs and session drawer.
- Supports nested-array extra-key layouts stored at `~/.config/miauterm/keys`. Settings lists every built-in key, including navigation, modifiers, function keys, and app actions; saving there reloads the bar immediately. After editing the file in a shell, run `miauterm-cmd run keysreload` to apply it to the terminal. CTRL, ALT, and the function keys latch until tapped again, and a latched function key applies its sequence to the next key press. The Miauterm Linux README is placed in the root user's home without overwriting an existing README.
- Opens GitHub Releases from the terminal drawer.

Miauterm supports the bundled Debian system only. Debian and its packages retain their own copyrights and licenses; Miauterm does not relicense them.

## Privacy and storage

The Linux system is isolated inside Miauterm's private app directory. Android storage access is limited to the two user-selected folders above. Linux account switching is an environment convenience implemented through PRoot's simulated IDs; treat files in the rootfs as belonging to the app and do not rely on account switching to protect private files from another session.

Removing Debian permanently deletes its files. To install Debian again after removal, clear Miauterm's app data in Android settings. Export a backup before removing or resetting Debian if you need to keep its files.

## FAQ

**Which devices can run Miauterm?** Android 15 or newer on an ARM64 device.

**Why does Miauterm use PRoot?** Android apps cannot start a normal privileged Debian `chroot` without root access to the device. PRoot lets Miauterm run the bundled Debian userland from its app-private files without asking users to root Android. It simulates Linux paths and user IDs, so it is not the same as a real root environment; features that require kernel-level privileges may not work.

**Is Miauterm an official Cortex Terminal release?** No. Miauterm is an unofficial fork with its own code changes and releases. It is separate from the official Cortex Terminal project.

**Why is Android storage unavailable at first?** Miauterm does not request storage access on first launch. Enable access in Settings to expose only the selected Download and Documents folders to Debian.

**Does changing the Debian user protect files from other sessions?** No. PRoot emulates Linux user IDs inside the app. It does not create separate Android security identities.

**What does resetting Debian remove?** Reset installs a fresh bundled root filesystem and replaces the current Debian files. Back up anything you want to keep first.

**Can Debian programs use Android features?** Miauterm provides a small command client for supported app operations while its terminal service is running. It does not expose general access to Android system directories.

## Report a problem

Use **Settings → Support → Report a bug** or open the [Miauterm issue tracker](https://github.com/synccatcode/miauterm/issues/new/choose). Include the Android version, device model, Debian system status, and the relevant PRoot diagnostic log when possible. Review logs before sharing because they may include paths or command output.

## Build and project layout

GitHub Actions builds an ARM64 debug APK on branch pushes. A version tag (`v*`) builds, signs, verifies, and publishes the release APK and checksum. Signing credentials are stored as GitHub Actions secrets.

The repository is split between the Android app, the terminal engine, and packaging/release tools:

```text
miauterm/
├── .github/
│   ├── dependabot.yml
│   ├── release-notes/       # Per-version release notes
│   └── workflows/
│       └── build-apk.yml    # Tests, builds and publishes APKs
├── .gitignore
├── docs/
│   └── images/
│       └── miauterm-terminal.png
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── assets/
│       │   │   ├── debian-arm64.tar.zst
│       │   │   ├── fonts/              # Bundled terminal typefaces
│       │   │   ├── licenses/           # Third-party notices
│       │   │   ├── miauterm-cmd
│       │   │   └── proot/arm64-v8a/     # PRoot and native libraries
│       │   ├── java/org/cortex/terminal/
│       │   │   ├── api/                # Local app command API
│       │   │   ├── runtime/            # Debian, PRoot, files and setup
│       │   │   ├── service/            # Terminal service and local servers
│       │   │   ├── session/            # Shell sessions and persistence
│       │   │   ├── view/               # Terminal UI and extra keys
│       │   │   └── *.kt                # Activities, app setup and shared UI
│       │   └── res/
│       │       ├── drawable*/          # App and terminal graphics
│       │       ├── layout/             # Activity and row layouts
│       │       ├── mipmap*/            # Launcher icons
│       │       ├── values*/            # Themes, labels and translations
│       │       └── xml/                # Preferences and file-sharing paths
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
│           ├── main/
│           │   ├── cpp/                # PTY JNI and zstd decoder
│           │   └── java/org/cortex/terminal/
│           │       ├── emulator/       # Terminal state, rendering and keys
│           │       ├── pty/             # Native PTY bridge
│           │       └── view/            # Terminal glyph data
│           └── test/java/org/cortex/terminal/
│               └── emulator/
├── gradle/
│   ├── libs.versions.toml    # Dependency versions
│   └── wrapper/              # Gradle wrapper files
├── scripts/
│   ├── package-debian-rootfs.sh
│   └── sanitize-rootfs-tar.py
├── app/build.gradle.kts      # Android app build settings
├── build.gradle.kts          # Root build configuration
├── gradle.properties
├── settings.gradle.kts       # Gradle module registration
├── LICENSE
├── README.md
├── gradlew
├── gradlew.bat
└── THIRD_PARTY_NOTICES.md
```

The tree groups resource variants and individual unit tests by directory; translated strings live in `values-*/strings.xml`. `MainActivity.kt` owns terminal navigation and live extra-key reloads. `ExtraKeysLayout.kt` parses saved key layouts, `ExtraKeysConfig.kt` watches the config file, and `ExtraKeysView.kt` renders and dispatches the buttons. The package retains its upstream namespace as part of the Cortex Terminal fork.

To package the bundled Debian ARM64 root filesystem:

```sh
scripts/package-debian-rootfs.sh /path/to/debian-rootfs
```

The packaging script removes package caches, logs, shell history, SSH host keys, and authentication material before producing the app asset.

## Credits and licensing

Miauterm is forked from [Cortex Terminal](https://github.com/ssfuisu/cortex). The retained Cortex-derived application code is distributed under the Apache License 2.0 in [`LICENSE`](LICENSE). The bundled PRoot runtime and its supporting components carry separate licenses and notices, listed in [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md) and included in the app. Debian and its packages keep their own copyright and license terms. Consult `/usr/share/doc/<package>/copyright` in the installed root filesystem and [Debian's license information](https://www.debian.org/legal/licenses/).
