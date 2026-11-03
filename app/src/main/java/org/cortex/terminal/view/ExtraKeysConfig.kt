package org.cortex.terminal.view

import android.os.FileObserver
import android.util.Log
import org.cortex.terminal.runtime.FileInstaller
import org.cortex.terminal.runtime.SafeDelete
import java.io.File

/**
 * ~/.config/miauterm/keys, the keys above the keyboard ([ExtraKeysLayout]), with ~/.config/miauterm/keys.example
 * (the default layout and how to write one) next to it.
 */
class ExtraKeysConfig(home: File) {
    private var home = home
    var dir = File(home, ".config/miauterm")
        private set
    var file = File(dir, FILE_NAME)
        private set
    private var example = File(dir, "$FILE_NAME.example")

    init {
        migrateLegacy(home)
    }

    private fun migrateLegacy(home: File) {
        val legacy = File(home, ".cortex")
        if (legacy.isDirectory) {
            dir.mkdirs()
            listOf(FILE_NAME, "$FILE_NAME.example", "font.ttf").forEach { name ->
                val old = File(legacy, name)
                val target = File(dir, name)
                if (old.isFile && !target.exists()) runCatching { old.copyTo(target) }
            }
            SafeDelete.deleteRecursively(legacy)
        }
    }

    fun setHome(home: File) {
        if (this.home.absoluteFile == home.absoluteFile) return
        stopWatching()
        this.home = home
        dir = File(home, ".config/miauterm")
        file = File(dir, FILE_NAME)
        example = File(dir, "$FILE_NAME.example")
        appliedText = null
        migrateLegacy(home)
    }

    // The text the keys were last made from ("" without the file)
    private var appliedText: String? = null
    private var observer: FileObserver? = null

    /** The layout to show when the file changed since the last call, null when it did not. */
    fun loadIfChanged(): ExtraKeysLayout.Result? {
        val text = read()
        if (text == appliedText) return null
        return parseAndRemember(text)
    }

    fun reload(): ExtraKeysLayout.Result = parseAndRemember(read())

    private fun parseAndRemember(text: String): ExtraKeysLayout.Result {
        appliedText = text
        return when {
            text == TOO_BIG -> ExtraKeysLayout.Result(emptyList(), "larger than ${MAX_BYTES / 1024} KB")
            text.isBlank() -> ExtraKeysLayout.Result(ExtraKeysLayout.DEFAULT_ROWS)
            else -> ExtraKeysLayout.parse(text)
        }
    }

    private fun read(): String = try {
        when {
            !file.isFile -> ""
            file.length() > MAX_BYTES -> TOO_BIG
            else -> file.readText()
        }
    } catch (e: Exception) {
        Log.w(TAG, "Cannot read ${file.path}: ${e.message}")
        ""
    }

    fun writeExample() {
        try {
            FileInstaller.install(example, ExtraKeysLayout.DEFAULT_TEXT + "\n", 420) // 0644
        } catch (e: Exception) {
            Log.w(TAG, "Cannot write ${example.path}: ${e.message}")
        }
    }

    fun ensureConfigFile() {
        if (file.exists()) return
        check(dir.isDirectory || dir.mkdirs()) { "Could not create ${dir.path}" }
        FileInstaller.install(file, ExtraKeysLayout.DEFAULT_TEXT + "\n", 420)
    }

    /** Calls [onChange] (on the observer's thread) whenever ~/.config/miauterm/keys is written, moved or removed. */
    @Suppress("DEPRECATION") // FileObserver(File) needs API 29
    fun watch(onChange: () -> Unit) {
        stopWatching()
        dir.mkdirs()
        val mask = FileObserver.CLOSE_WRITE or FileObserver.MOVED_TO or FileObserver.MOVED_FROM or
            FileObserver.DELETE or FileObserver.CREATE
        observer = object : FileObserver(dir.path, mask) {
            override fun onEvent(event: Int, path: String?) {
                if (path == FILE_NAME) onChange()
            }
        }.also { it.startWatching() }
    }

    fun stopWatching() {
        observer?.stopWatching()
        observer = null
    }

    companion object {
        private const val TAG = "ExtraKeysConfig"
        const val FILE_NAME = "keys"
        private const val MAX_BYTES = 64 * 1024L
        private const val TOO_BIG = "\u0000too big"

        fun writeLayout(file: File, text: String) {
            require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "over 64 KB" }
            val parsed = ExtraKeysLayout.parse(text)
            require(parsed.error == null) { parsed.error ?: "invalid layout" }
            val parent = checkNotNull(file.parentFile)
            check(parent.isDirectory || parent.mkdirs()) { "Could not create ${parent.path}" }
            file.writeText(text)
        }
    }
}
