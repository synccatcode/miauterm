#!/usr/bin/env python3
"""Rewrite PRoot link2symlink host paths to guest-relative links in a rootfs tar."""

import copy
import os
import sys
import tarfile


def main() -> None:
    if len(sys.argv) != 4:
        raise SystemExit("usage: sanitize-rootfs-tar.py INPUT.tar OUTPUT.tar ROOTFS_DIR")

    source_path, output_path, rootfs_dir = sys.argv[1:]
    rootfs_dir = os.path.realpath(rootfs_dir).rstrip(os.sep)
    host_prefix = rootfs_dir + os.sep
    rewritten = 0

    with tarfile.open(source_path, mode="r:") as source, tarfile.open(
        output_path, mode="w", format=tarfile.GNU_FORMAT
    ) as output:
        for member in source:
            safe_member = copy.copy(member)
            target = member.linkname
            if member.issym() and target.startswith(host_prefix):
                guest_target = target[len(host_prefix):]
                safe_member.linkname = os.path.relpath(
                    guest_target, os.path.dirname(member.name) or "."
                )
                rewritten += 1

            stream = source.extractfile(member) if member.isfile() else None
            output.addfile(safe_member, stream)
            if stream is not None:
                stream.close()

    print(f"Rewrote {rewritten} PRoot host-path symlink(s)")


if __name__ == "__main__":
    main()
