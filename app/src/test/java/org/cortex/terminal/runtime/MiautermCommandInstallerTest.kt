package org.cortex.terminal.runtime

import androidx.test.core.app.ApplicationProvider
import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], shadows = [ShadowJavaFilesOs::class])
class MiautermCommandInstallerTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun installsExecutableCommandAndNotifySendAlias() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val root = temporaryFolder.newFolder("debian")

        MiautermCommandInstaller.install(application, root)

        val command = root.resolve("usr/local/bin/miauterm-cmd")
        val alias = root.resolve("usr/local/bin/notify-send")
        val expectedScript = application.assets.open("miauterm-cmd").use { it.readBytes() }

        assertTrue(command.canExecute())
        assertTrue(command.readBytes().contentEquals(expectedScript))
        assertEquals("miauterm-cmd", ElfLinkerPatcher.readSymlink(alias))
        assertTrue(MiautermCommandInstaller.isInstalled(root))
    }
}
