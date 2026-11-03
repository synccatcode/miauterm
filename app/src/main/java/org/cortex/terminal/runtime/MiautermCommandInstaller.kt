package org.cortex.terminal.runtime

import android.content.Context
import java.io.File

internal object MiautermCommandInstaller {
    private const val MODE_EXECUTABLE = 0x1ed
    private const val COMMAND_PATH = "usr/local/bin/miauterm-cmd"
    private const val NOTIFY_SEND_PATH = "usr/local/bin/notify-send"

    fun install(context: Context, root: File) {
        val command = File(root, COMMAND_PATH)
        val script = context.assets.open("miauterm-cmd").use { it.readBytes() }
        FileInstaller.install(command, script, MODE_EXECUTABLE)
        check(command.canExecute()) { "Could not make miauterm-cmd executable" }
        FileInstaller.symlink("miauterm-cmd", File(root, NOTIFY_SEND_PATH))
    }

    fun isInstalled(root: File): Boolean =
        File(root, COMMAND_PATH).let { it.isFile && it.canExecute() } &&
            ElfLinkerPatcher.readSymlink(File(root, NOTIFY_SEND_PATH)) == "miauterm-cmd"
}
