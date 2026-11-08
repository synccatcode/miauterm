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
            "# Managed by Miauterm. Android DNS with public fallback.\n" +
                "nameserver 192.0.2.53\n" +
                "nameserver 1.1.1.1\n" +
                "nameserver 8.8.8.8\n" +
                "options timeout:2 attempts:2\n",
            resolvConf.readText()
        )
    }

    @Test
    fun refreshesManagedDnsAndReplacesStaleResolverFiles() {
        val root = temporaryFolder.newFolder("debian")
        val resolvConf = File(root, "etc/resolv.conf").apply {
            parentFile.mkdirs()
            writeText("")
        }
        val firstServer = InetAddress.getByName("192.0.2.53")
        val secondServer = InetAddress.getByName("192.0.2.54")

        assertTrue(DebianRuntime.updateResolvConf(root, listOf(firstServer)))
        assertTrue(DebianRuntime.updateResolvConf(root, listOf(secondServer)))
        assertTrue(resolvConf.readText().contains("nameserver 192.0.2.54\n"))

        resolvConf.writeText("nameserver 9.9.9.9\n")
        assertTrue(DebianRuntime.updateResolvConf(root, listOf(firstServer)))
        assertTrue(resolvConf.readText().contains("nameserver 192.0.2.53\n"))
        assertFalse(resolvConf.readText().contains("nameserver 9.9.9.9"))
    }

    @Test
    fun writesFallbackDnsServersWhenAndroidHasNoDnsServers() {
        val root = temporaryFolder.newFolder("debian")
        val resolvConf = File(root, "etc/resolv.conf").apply {
            parentFile.mkdirs()
            writeText("")
        }

        assertTrue(DebianRuntime.updateResolvConf(root, emptyList()))
        assertTrue(resolvConf.readText().contains("nameserver 1.1.1.1\n"))
        assertTrue(resolvConf.readText().contains("nameserver 8.8.8.8\n"))
    }
    @Test
    fun parsesIpv4AnswersFromDnsOverHttpsJson() {
        val addresses = DebianRuntime.parseDnsJsonAddresses(
            """{"Status":0,"Answer":[{"type":5,"data":"debian.map.example."},{"type":28,"data":"2001:db8::1"},{"type":1,"data":"151.101.2.132"},{"type":1,"data":"not-an-ip"}]}"""
        )

        assertEquals(listOf("151.101.2.132"), addresses.map { it.hostAddress })
    }

    @Test
    fun upgradesDebianRepositorySourcesToHttps() {
        val root = temporaryFolder.newFolder("debian")
        val apt = File(root, "etc/apt")
        val sourceList = File(apt, "sources.list").apply {
            parentFile.mkdirs()
            writeText("deb http://deb.debian.org/debian trixie main\n")
        }
        val deb822 = File(apt, "sources.list.d/debian.sources").apply {
            parentFile.mkdirs()
            writeText("URIs: http://deb.debian.org/debian-security\n")
        }

        DebianRuntime.enforceRepositoryHttps(root)

        assertEquals("deb https://deb.debian.org/debian trixie main\n", sourceList.readText())
        assertEquals("URIs: https://deb.debian.org/debian-security\n", deb822.readText())
    }

}
