package org.cortex.terminal.runtime

import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

class DebianRuntimeRootfsTest {
    @Test
    fun rejectsTruncatedAccountAndPackageCommands() {
        val root = createValidRootfs()
        try {
            File(root, "usr/sbin/useradd").writeText("\n")

            try {
                DebianRuntime.verifyRootfs(root)
                fail("A truncated useradd binary must invalidate the rootfs")
            } catch (error: IllegalStateException) {
                assertTrue(error.message.orEmpty().contains("/usr/sbin/useradd"))
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectsMissingNetworkBinaries() {
        val root = createValidRootfs()
        try {
            File(root, "usr/bin/ping").delete()

            try {
                DebianRuntime.verifyNetworkCommands(root)
                fail("A missing ping binary must invalidate the rootfs")
            } catch (error: IllegalStateException) {
                assertTrue(error.message.orEmpty().contains("/usr/bin/ping"))
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun acceptsCompleteArm64AccountAndPackageCommands() {
        val root = createValidRootfs()
        try {
            DebianRuntime.verifyRootfs(root)
            DebianRuntime.verifyNetworkCommands(root)
        } finally {
            root.deleteRecursively()
        }
    }

    private fun createValidRootfs(): File {
        val root = kotlin.io.path.createTempDirectory("miauterm-rootfs-test").toFile()
        File(root, "etc").apply { mkdirs() }
        File(root, "etc/os-release").writeText("ID=debian\n")
        File(root, "etc/debian_version").writeText("13\n")
        File(root, "root").mkdirs()
        File(root, "bin").apply { mkdirs(); resolve("placeholder").writeText("") }
        File(root, "sbin").apply { mkdirs(); resolve("placeholder").writeText("") }

        listOf(
            "usr/bin/bash",
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
            "usr/bin/wget"
        ).forEach { path ->
            val command = File(root, path)
            command.parentFile.mkdirs()
            val elf = ByteArray(4096)
            elf[0] = 0x7f
            elf[1] = 'E'.code.toByte()
            elf[2] = 'L'.code.toByte()
            elf[3] = 'F'.code.toByte()
            elf[4] = 2
            elf[5] = 1
            elf[18] = 183.toByte()
            command.writeBytes(elf)
            command.setExecutable(true)
        }
        return root
    }
}
