package org.cortex.terminal.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SessionPromptTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun rootAndRegularUsersHaveTheirShellPromptSuffix() {
        assertEquals("root@localhost ~#", sessionPromptLabel("root"))
        assertEquals("mia@localhost ~$", sessionPromptLabel("mia"))
    }

    @Test
    fun failsafeShellUsesTheAppFilesDirectoryWithoutProot() {
        val home = temporaryFolder.newFolder("files")
        val cache = temporaryFolder.newFolder("cache")
        val environment = failsafeEnvironment(home, cache).toList()

        assertTrue(environment.contains("HOME=${home.absolutePath}"))
        assertTrue(environment.contains("PWD=${home.absolutePath}"))
        assertTrue(environment.contains("PS1=${home.absolutePath} \$ "))
        assertTrue(environment.contains("TMPDIR=${cache.absolutePath}"))
        assertFalse(environment.any { it.startsWith("PROOT_") })
    }
}
