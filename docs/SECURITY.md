# Security policy

Miauterm runs as an unprivileged Android app. PRoot's simulated Linux `root` account does not grant Android root access and does not create an additional Android security boundary. Debian and failsafe processes run under the app's Android UID.

The Debian root filesystem is stored in app-private storage. Access to Download and Documents is optional and requires the corresponding Android permission. When enabled, those folders are mounted into Debian under `~/android/`. Miauterm does not mount Android system directories or other apps' private data.

## Reporting a vulnerability

Please report security issues through [GitHub private vulnerability reporting](https://github.com/synccatcode/miauterm/security/advisories/new) when it is enabled for this repository. Otherwise, contact the maintainer through the [synccatcode GitHub profile](https://github.com/synccatcode) before posting details publicly. Include steps to reproduce, the affected app version, Android version, device model, and CPU architecture. Do not include personal files or secrets in a report.
