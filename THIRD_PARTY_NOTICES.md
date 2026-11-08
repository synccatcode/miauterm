# Third-party notices

Miauterm includes source code and runtime components with separate copyright and license terms. The Apache License 2.0 in [`LICENSE`](LICENSE) applies to the Miauterm and Cortex-derived application source unless a file or component has its own notice. It does not replace the notices below.

## Debian root filesystem

The app bundles a Debian ARM64 Trixie root filesystem. Debian is a community distribution and does not have one copyright holder or blanket license for every package. Copyright and license terms belong to the individual packages. Package notices remain in `/usr/share/doc/<package>/copyright` inside the root filesystem. Consult those files before redistributing or modifying Debian packages.

- [Debian legal information](https://www.debian.org/legal/licenses/)
- [Debian disclaimer and trademark information](https://www.debian.org/legal/disclaimer/)
- [Debian Policy on package copyright files](https://www.debian.org/doc/debian-policy/ch-archive)

## PRoot runtime

PRoot is distributed under GNU GPL version 2 or later. The bundled PRoot copyright and license text is in [`app/src/main/assets/licenses/PROOT-COPYING.txt`](app/src/main/assets/licenses/PROOT-COPYING.txt). The bundled talloc component has a separate GPLv3 notice in [`app/src/main/assets/licenses/TALLOC-COPYING.txt`](app/src/main/assets/licenses/TALLOC-COPYING.txt). Android shared-memory support has an attribution notice in [`app/src/main/assets/licenses/ANDROID-SHMEM-COPYRIGHT.txt`](app/src/main/assets/licenses/ANDROID-SHMEM-COPYRIGHT.txt).

- [PRoot source project](https://github.com/proot-me/proot)
- [PRoot static runtime source](https://github.com/proot-me/proot-static-build)

## Fonts

- **Fira Code**: Copyright 2014–2020 The Fira Code Project Authors. Licensed under SIL Open Font License 1.1. The license text is included in `app/src/main/assets/licenses/OFL-1.1.txt`. Source: [tonsky/FiraCode](https://github.com/tonsky/FiraCode).
- **JetBrains Mono**: Copyright 2020 The JetBrains Mono Project Authors. Licensed under SIL Open Font License 1.1. The license text is included in `app/src/main/assets/licenses/OFL-1.1.txt`. Source: [JetBrains Mono](https://github.com/JetBrains/JetBrainsMono).

## Project credits

Miauterm is an unofficial fork of [Cortex Terminal](https://github.com/ssfuisu/cortex). The retained upstream source is distributed under Apache License 2.0. Third-party names and marks remain the property of their owners. These notices do not grant trademark rights.
