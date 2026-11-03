#!/usr/bin/env bash
set -euo pipefail

rootfs_dir="${1:-../debian}"
rootfs_dir="$(cd "$rootfs_dir" && pwd -P)"
output="app/src/main/assets/debian-arm64.tar.zst"
temp_dir="$(mktemp -d)"
trap 'rm -rf "$temp_dir"' EXIT

if [[ ! -f "$rootfs_dir/etc/debian_version" ]] || ! grep -q '^ID=debian$' "$rootfs_dir/etc/os-release"; then
    echo "Expected a Debian root filesystem at: $rootfs_dir" >&2
    exit 1
fi
if [[ ! -d "$rootfs_dir/tmp" || -L "$rootfs_dir/tmp" ]]; then
    echo "Expected /tmp to be a directory in: $rootfs_dir" >&2
    exit 1
fi
chmod 1777 "$rootfs_dir/tmp"

python3 - "$rootfs_dir" <<'PY'
import os
import sys

root = sys.argv[1]
commands = (
    "usr/bin/passwd",
    "usr/sbin/useradd",
    "usr/sbin/groupadd",
    "usr/sbin/userdel",
    "usr/sbin/usermod",
    "usr/bin/chown",
    "usr/bin/dpkg",
    "usr/bin/apt",
    "usr/bin/apt-get",
    "usr/bin/ping",
    "usr/sbin/ip",
    "usr/bin/ss",
    "usr/sbin/ifconfig",
    "usr/sbin/route",
    "usr/bin/netstat",
    "usr/sbin/arp",
    "usr/bin/curl",
    "usr/bin/wget",
)

for relative in commands:
    path = os.path.join(root, relative)
    if not os.path.isfile(path) or not os.access(path, os.X_OK) or os.path.getsize(path) < 4096:
        raise SystemExit(f"Required Debian command is missing, truncated, or not executable: {relative}")
    with open(path, "rb") as command:
        header = command.read(20)
    if len(header) < 20 or header[:6] != b"\x7fELF\x02\x01" or int.from_bytes(header[18:20], "little") != 183:
        raise SystemExit(f"Required Debian command is not an ARM64 ELF executable: {relative}")
PY

mkdir -p "$temp_dir/etc" "$(dirname "$output")"
tar -c -C "$rootfs_dir" \
    --exclude='./var/cache/apt/archives/*' \
    --exclude='./var/lib/apt/lists/*' \
    --exclude='./var/log/*' \
    --exclude='./root/.bash_history' \
    --exclude='./root/.ssh/*' \
    --exclude='./etc/apt/auth.conf.d/*' \
    --exclude='./etc/ssh/ssh_host_*' \
    --exclude='./etc/shadow' \
    --exclude='./etc/gshadow' \
    . -f "$temp_dir/debian.tar"

awk -F: 'BEGIN { OFS = ":" } { $2 = "*"; print }' "$rootfs_dir/etc/shadow" > "$temp_dir/etc/shadow"
chmod 0640 "$temp_dir/etc/shadow"
if [[ -f "$rootfs_dir/etc/gshadow" ]]; then
    awk -F: 'BEGIN { OFS = ":" } { $2 = "!"; print }' "$rootfs_dir/etc/gshadow" > "$temp_dir/etc/gshadow"
    chmod 0640 "$temp_dir/etc/gshadow"
fi
tar -r -f "$temp_dir/debian.tar" -C "$temp_dir" etc/shadow
if [[ -f "$temp_dir/etc/gshadow" ]]; then
    tar -r -f "$temp_dir/debian.tar" -C "$temp_dir" etc/gshadow
fi
python3 scripts/sanitize-rootfs-tar.py "$temp_dir/debian.tar" "$temp_dir/debian-clean.tar" "$rootfs_dir"
zstd -10 -T0 -q -f "$temp_dir/debian-clean.tar" -o "$output"
ls -lh "$output"
