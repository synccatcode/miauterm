package org.cortex.terminal.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.net.InetAddress

class DebianRuntimeDnsTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun writesAndroidDnsServersIntoAnEmptyDebianResolverFile() {
        val root = temporaryFolder.newFolder("debian")
        val resolvConf = File(root, "etc/resolv.conf").apply {
            parentFile.mkdirs()
            writeText("")
        }

        assertTrue(DebianRuntime.updateResolvConf(root, listOf(InetAddress.getByName("192.0.2.53"))))
        assertEquals(
            "# Managed by Miauterm. Android network DNS servers.\n" +
                "nameserver 192.0.2.53\n",
            resolvConf.readText()
        )
    }

    @Test
    fun refreshesMiautermDnsAndPreservesCustomResolverFiles() {
        val root = temporaryFolder.newFolder("debian")
        val resolvConf = File(root, "etc/resolv.conf").apply {
            parentFile.mkdirs()
            writeText("")
        }
        val firstServer = InetAddress.getByName("192.0.2.53")
        val secondServer = InetAddress.getByName("192.0.2.54")

        assertTrue(DebianRuntime.updateResolvConf(root, listOf(firstServer)))
        assertTrue(DebianRuntime.updateResolvConf(root, listOf(secondServer)))
        assertTrue(resolvConf.readText().endsWith("nameserver 192.0.2.54\n"))

        resolvConf.writeText("nameserver 9.9.9.9\n")
        assertFalse(DebianRuntime.updateResolvConf(root, listOf(firstServer)))
        assertEquals("nameserver 9.9.9.9\n", resolvConf.readText())
    }

    @Test
    fun leavesTheResolverFileUntouchedWithoutNetworkDnsServers() {
        val root = temporaryFolder.newFolder("debian")
        val resolvConf = File(root, "etc/resolv.conf").apply {
            parentFile.mkdirs()
            writeText("")
        }

        assertFalse(DebianRuntime.updateResolvConf(root, emptyList()))
        assertEquals("", resolvConf.readText())
    }
}
