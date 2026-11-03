package org.cortex.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BugReportTest {

    @Test
    fun format_writesSectionsWithKeysAndMarksMissingValues() {
        val text = BugReport.format(
            linkedMapOf(
                "Miauterm" to listOf("version" to "1.26.37 (137)", "package" to "com.miauterm.terminal", "Debian rootfs" to null, "runtime" to "PRoot"),
                "Android" to listOf("release" to null)
            )
        )
        assertEquals(
            "== Miauterm\nversion: 1.26.37 (137)\npackage: com.miauterm.terminal\nDebian rootfs: (unknown)\nruntime: PRoot\n\n== Android\nrelease: (unknown)\n\n",
            text
        )
    }

    @Test
    fun osRelease_isThePrettyName() {
        val text = "NAME=\"Debian GNU/Linux\"\nVERSION_ID=\"13\"\nPRETTY_NAME=\"Debian GNU/Linux 13 (trixie)\"\nID=debian\n"
        assertEquals("Debian GNU/Linux 13 (trixie)", BugReport.osRelease(text))
        assertEquals("Debian", BugReport.osRelease("PRETTY_NAME=Debian\n"))
        assertNull(BugReport.osRelease("NAME=Debian\n"))
        assertNull(BugReport.osRelease(null))
    }

    @Test
    fun packageCount_countsOnlyInstalledPackages() {
        val status = """
            Package: bash
            Status: install ok installed
            Version: 5.2.21-2

            Package: removed-one
            Status: deinstall ok config-files

            Package: half
            Status: install ok half-installed

            Package: coreutils
            Status: install ok installed
        """.trimIndent()
        assertEquals(2, BugReport.packageCount(status))
        assertEquals(0, BugReport.packageCount(""))
    }
}
