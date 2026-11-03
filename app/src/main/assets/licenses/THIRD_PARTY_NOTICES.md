# Third-party notices

Miauterm combines independently licensed software and a Debian ARM64 root filesystem. The Apache License 2.0 in [`LICENSE`](LICENSE) applies to the Miauterm/Cortex-derived application source unless a file or component carries its own notice. It does not replace the licenses below.

## Debian root filesystem

The bundled Linux userland is Debian ARM64. Debian is a community distribution; there is no single copyright holder or blanket license for all packages. Copyright and license terms belong to each included package. Debian package notices are normally kept in `/usr/share/doc/<package>/copyright` inside the root filesystem. This archive does not remove those notices. Users should consult those files for the packages they install or redistribute.

- Debian legal information: <https://www.debian.org/legal/licenses/>
- Debian disclaimer and trademark information: <https://www.debian.org/legal/disclaimer/>
- Debian Policy on package copyright files: <https://www.debian.org/doc/debian-policy/ch-archive>

A user-imported rootfs has its own package set and corresponding notices. Miauterm does not relicense those packages.

## PRoot runtime

PRoot is distributed under GNU GPL version 2 or later. The bundled PRoot copyright/license text is shipped at [`app/src/main/assets/licenses/PROOT-COPYING.txt`](app/src/main/assets/licenses/PROOT-COPYING.txt). The bundled talloc component has its own GPLv3 notice at [`app/src/main/assets/licenses/TALLOC-COPYING.txt`](app/src/main/assets/licenses/TALLOC-COPYING.txt). Android shared-memory support includes its own attribution at [`app/src/main/assets/licenses/ANDROID-SHMEM-COPYRIGHT.txt`](app/src/main/assets/licenses/ANDROID-SHMEM-COPYRIGHT.txt).

- PRoot source project: <https://github.com/proot-me/proot>
- PRoot static runtime source: <https://github.com/proot-me/proot-static-build>

## Fonts

- **Fira Code** — Copyright 2014–2020 The Fira Code Project Authors; SIL Open Font License 1.1. The SIL Open Font License 1.1 text is included as `OFL-1.1.txt`. Source: <https://github.com/tonsky/FiraCode>.
- **JetBrains Mono** — Copyright 2020 The JetBrains Mono Project Authors; SIL Open Font License 1.1. The SIL Open Font License 1.1 text is included as `OFL-1.1.txt`. Source: <https://github.com/JetBrains/JetBrainsMono>.

## Project credits

- **Cortex Terminal**, the upstream project Miauterm is forked from: <https://github.com/ssfuisu/cortex>. Its retained application source is distributed under Apache License 2.0; see [`LICENSE`](LICENSE).

Third-party names and marks remain the property of their respective owners. These notices do not grant trademark rights.
