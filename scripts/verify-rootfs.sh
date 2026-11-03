#!/usr/bin/env bash
# Proves the bundled Debian arm64 rootfs works as Miauterm sees it: the network
# commands are real binaries, and a chroot there manages accounts and packages.
# --net (used by the debug job) also probes outbound HTTPS from the chroot.
set -euo pipefail

net="${1:-}"
archive="app/src/main/assets/debian-arm64.tar.zst"
rootfs="$RUNNER_TEMP/miauterm-rootfs"

sudo rm -rf "$rootfs"
sudo mkdir -p "$rootfs"
zstd -dc "$archive" | sudo tar -xf - -C "$rootfs"

commands=(
    usr/bin/passwd usr/sbin/useradd usr/sbin/groupadd usr/sbin/userdel usr/sbin/usermod
    usr/bin/chown usr/bin/dpkg usr/bin/apt usr/bin/apt-get
    usr/bin/ping usr/sbin/ip usr/bin/ss usr/sbin/ifconfig usr/sbin/route
    usr/bin/netstat usr/sbin/arp usr/bin/curl usr/bin/wget
)
for command in "${commands[@]}"; do
    size="$(sudo stat -L -c '%s' "$rootfs/$command")"
    if [ "$size" -lt 4096 ]; then
        echo "Bundled Debian command is missing or truncated: /$command ($size bytes)" >&2
        exit 1
    fi
done

docker run --rm --platform linux/arm64 \
    --volume "$rootfs:/mnt/debian" \
    debian:trixie-slim \
    sh -ec '
      cp /etc/resolv.conf /mnt/debian/etc/resolv.conf
      chroot /mnt/debian /bin/sh -ec "
        apt update -qq
        curl -fsSI --max-time 15 https://deb.debian.org/debian/ >/dev/null
        ping -c 1 -W 1 127.0.0.1 >/dev/null
        ip -Version >/dev/null
        ss -V >/dev/null
        ifconfig --version >/dev/null
        route --version >/dev/null
        netstat --version >/dev/null
        arp --version >/dev/null
        useradd miauterm-home-check
        test -d /home/miauterm-home-check
      "
      : > /mnt/debian/etc/resolv.conf
    '
