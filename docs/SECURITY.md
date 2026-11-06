# Security Policy

## Supported Versions

Only the latest release of Miauterm receives active security updates and vulnerability fixes.

| Version | Supported          |
| ------- | ------------------ |
| 0.1.x   | :white_check_mark: |
| < 0.1.0 | :x:                |

---

## Security Model & Sandboxing

Miauterm operates entirely within the standard Android application sandbox:

* **Unprivileged Execution:** The app runs as an unprivileged Android application UID. It does not request, require, or leverage device root access.
* **PRoot Userland Virtualization:** The simulated `root` and Linux user accounts inside the Debian environment exist purely for userland path resolution and package compatibility. They do not represent real Android root privileges or provide hardware-level isolation.
* **Isolated Storage:** The Debian root filesystem is stored exclusively in Miauterm's app-private data directory (`/data/data/com.miauterm.terminal/files`). Android's Linux DAC prevents other non-root applications from accessing this space.
* **Shared Storage Boundary:** Access to external folders (`Download` and `Documents`) is strictly optional, gated behind Android runtime storage permissions, and mounted exclusively at user discretion.

---

## Reporting a Vulnerability

We take the security of Miauterm seriously. If you discover a security vulnerability, please report it responsibly rather than opening a public issue.

### How to Report

1. **Email or Private Advisory:** 
   * Submit a report via [GitHub Private Vulnerability Reporting](https://github.com/synccatcode/miauterm/security/advisories/new) on the repository.
   * If private reporting is unavailable, reach out via the maintainer's contact listed on GitHub profile.
2. **Include in Your Report:**
   * A clear description of the vulnerability.
   * Steps to reproduce the issue (proof-of-concept script, commands, or APK behavior).
   * Device details (Android version, device model, architecture).
   * Any potential impact or attack vectors.

### What to Expect

* **Acknowledgment:** We will acknowledge receipt of your report within 48–72 hours.
* **Assessment & Fix:** We will validate the issue, determine its severity, and work on a fix in a private branch.
* **Disclosure:** Once a fix is verified and published in a release, we will publicly disclose the advisory with appropriate credit to the reporter.
