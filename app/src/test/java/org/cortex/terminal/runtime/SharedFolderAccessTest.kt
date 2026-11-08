package org.cortex.terminal.runtime

import android.Manifest
import android.app.Application
import android.content.ContextWrapper
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SharedFolderAccessTest {
    private lateinit var application: Application

    @Before
    fun resetPreferences() {
        application = ApplicationProvider.getApplicationContext()
        PreferenceManager.getDefaultSharedPreferences(application).edit().clear().commit()
    }

    @Test
    fun disablingSharingStopsMountAccessEvenWhenAndroidPermissionsRemainGranted() {
        shadowOf(application).grantPermissions(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
        PreferenceManager.getDefaultSharedPreferences(application).edit()
            .putBoolean(DebianRuntime.PREF_SHARED_FOLDER_ACCESS, false)
            .commit()

        assertTrue(DebianRuntime.hasSharedFolderPermission(application))
        assertFalse(DebianRuntime.isSharedFolderAccessEnabled(application))
    }

    @Test
    fun sharingCannotEnableWithoutAndroidPermissions() {
        PreferenceManager.getDefaultSharedPreferences(application).edit()
            .putBoolean(DebianRuntime.PREF_SHARED_FOLDER_ACCESS, true)
            .commit()

        assertFalse(DebianRuntime.isSharedFolderAccessEnabled(application))
    }

    @Test
    fun preparingDebianDoesNotCreateAndroidFoldersAndMovesManagedReadmeToHome() {
        val root = java.io.File(application.cacheDir, "rootfs-${System.nanoTime()}")
        val home = java.io.File(root, "root").apply { mkdirs() }
        java.io.File(home, "README.md").writeText("User notes")
        java.io.File(root, "README.md").writeText("# Miauterm Linux system\nLegacy managed file")

        try {
            DebianRuntime.prepareHome(root)

            assertFalse(java.io.File(home, "android").exists())
            assertTrue(java.io.File(root, "dev/pts").isDirectory)
            assertEquals("User notes", java.io.File(home, "README.md").readText())
            assertTrue(java.io.File(home, "Miauterm-README.md").isFile)
            assertTrue(java.io.File(root, "etc/motd").readText().contains("Welcome to Debian"))
            val prompt = java.io.File(root, "etc/profile.d/99-miauterm-prompt.sh").readText()
            assertTrue(prompt.contains("\\u@localhost"))
            assertTrue(prompt.contains("\\w"))
            assertTrue(prompt.contains("1;32m"))
            assertTrue(prompt.contains("34m"))
            assertFalse(prompt.contains("~"))
            assertTrue(java.io.File(home, "Miauterm-README.md").readText().contains("current directory"))
            assertFalse(java.io.File(root, "README.md").exists())
            val colors = java.io.File(root, "etc/profile.d/99-miauterm-colors.sh").readText()
            assertTrue(colors.contains("dircolors"))
            assertTrue(colors.contains("ls --color=auto"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun shellDoesNotBindAndroidSystemDirectoriesWhenSharedFolderAccessIsOff() {
        val files = java.io.File(application.cacheDir, "runtime-${System.nanoTime()}")
        val context = object : ContextWrapper(application) {
            override fun getFilesDir(): java.io.File = files
        }
        val root = java.io.File(files, "debian")

        try {
            val binds = DebianRuntime.shellArguments(context, null)
                .filter { it.startsWith("--bind=") }

            assertTrue(binds.isEmpty())
            assertFalse(java.io.File(root, "dev/pts").exists())
            assertFalse(java.io.File(root, "root/android").exists())
            assertFalse(binds.any { it.contains("/system") })
        } finally {
            files.deleteRecursively()
        }
    }

    @Test
    fun shellBindsTheAppCommandConnectionOnlyWhenItIsAvailable() {
        val files = java.io.File(application.cacheDir, "runtime-${System.nanoTime()}")
        val context = object : ContextWrapper(application) {
            override fun getApplicationContext(): android.content.Context = this
            override fun getFilesDir(): java.io.File = files
        }
        val connectionDirectory = java.io.File(files, "miauterm-cmd")
        val connectionFile = java.io.File(connectionDirectory, "connection")

        try {
            val withoutService = DebianRuntime.shellArguments(context, null)
                .filter { it.startsWith("--bind=") }
            assertTrue(withoutService.isEmpty())

            connectionDirectory.mkdirs()
            connectionFile.writeText("12345 ${"a".repeat(64)}\n")
            val withService = DebianRuntime.shellArguments(context, null)
                .filter { it.startsWith("--bind=") }
            assertTrue(withService.contains("--bind=${connectionDirectory.absolutePath}:/run/miauterm-cmd"))
            assertTrue(java.io.File(files, "debian/run/miauterm-cmd").isDirectory)
        } finally {
            files.deleteRecursively()
        }
    }

    @Test
    fun createsAndroidFolderOnlyAfterSharingPermissionIsGrantedAndEnabled() {
        shadowOf(application).grantPermissions(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
        PreferenceManager.getDefaultSharedPreferences(application).edit()
            .putBoolean(DebianRuntime.PREF_SHARED_FOLDER_ACCESS, true)
            .commit()
        val files = java.io.File(application.cacheDir, "runtime-${System.nanoTime()}")
        val context = object : ContextWrapper(application) {
            override fun getFilesDir(): java.io.File = files
        }
        val androidDirectory = java.io.File(files, "debian/root/android")

        try {
            assertFalse(androidDirectory.exists())

            DebianRuntime.shellArguments(context, null)

            assertTrue(androidDirectory.isDirectory)
        } finally {
            files.deleteRecursively()
        }
    }
}
