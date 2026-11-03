package org.cortex.terminal.view

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class ExtraKeysConfigTest {
    @Test
    fun defaultKeysArePersistedUnderTheMiautermConfigDirectory() {
        val home = Files.createTempDirectory("miauterm-keys").toFile()
        try {
            val config = ExtraKeysConfig(home)
            assertTrue(!config.file.exists())
            config.ensureConfigFile()

            assertTrue(config.file.isFile)
            assertEquals(ExtraKeysLayout.DEFAULT_TEXT + "\n", config.file.readText())
            assertEquals(".config/miauterm/keys", config.file.relativeTo(home).invariantSeparatorsPath)
        } finally {
            home.deleteRecursively()
        }
    }

    @Test
    fun reloadReadsAChangedLayoutEvenAfterThePreviousTextWasApplied() {
        val home = Files.createTempDirectory("miauterm-keys-reload").toFile()
        try {
            val config = ExtraKeysConfig(home)
            config.ensureConfigFile()
            val original = config.reload()
            config.file.writeText(
                """
                    [
                      ["ESC", "/"],
                      ["TAB", "CTRL", "ALT", "LEFT", "DOWN", "RIGHT", "PGDN", "NEW"]
                    ]
                """.trimIndent()
            )

            val changed = config.reload()

            assertEquals(2, original.rows.size)
            assertEquals("NEW", changed.rows.last().last().label)
        } finally {
            home.deleteRecursively()
        }
    }

    @Test
    fun loadIfChangedAppliesKeysAddedToTheConfigFile() {
        val home = Files.createTempDirectory("miauterm-keys-watch").toFile()
        try {
            val config = ExtraKeysConfig(home)
            config.ensureConfigFile()
            assertEquals(2, config.loadIfChanged()?.rows?.size)

            config.file.writeText(ExtraKeysLayout.appendKey(config.file.readText(), "F12", textKey = false))

            val changed = config.loadIfChanged()

            assertEquals("F12", changed?.rows?.last()?.last()?.label)
            assertTrue(config.loadIfChanged() == null)
        } finally {
            home.deleteRecursively()
        }
    }

    @Test
    fun savingAnAddedFunctionKeyMakesItAvailableToTheBarReload() {
        val home = Files.createTempDirectory("miauterm-keys-add").toFile()
        try {
            val config = ExtraKeysConfig(home)
            config.ensureConfigFile()
            val updated = ExtraKeysLayout.appendKey(config.file.readText(), "F5", textKey = false)

            ExtraKeysConfig.writeLayout(config.file, updated)

            val result = config.reload()
            assertEquals(ExtraKeysLayout.SpecialKey(ExtraKeysLayout.Special.F5), result.rows.last().last())
        } finally {
            home.deleteRecursively()
        }
    }

    @Test
    fun switchingLinuxHomeReloadsThatUsersExtraKeys() {
        val firstHome = Files.createTempDirectory("miauterm-keys-first-user").toFile()
        val secondHome = Files.createTempDirectory("miauterm-keys-second-user").toFile()
        try {
            val config = ExtraKeysConfig(firstHome)
            config.ensureConfigFile()
            config.file.writeText("""[["F1"]]""")
            assertEquals("F1", config.reload().rows.single().single().label)

            config.setHome(secondHome)
            config.ensureConfigFile()
            config.file.writeText("""[["F2"]]""")
            assertEquals("F2", config.reload().rows.single().single().label)

            config.setHome(firstHome)
            assertEquals("F1", config.loadIfChanged()?.rows?.single()?.single()?.label)
        } finally {
            firstHome.deleteRecursively()
            secondHome.deleteRecursively()
        }
    }
}
