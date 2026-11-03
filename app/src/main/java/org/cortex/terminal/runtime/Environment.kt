package org.cortex.terminal.runtime

import android.content.Context
import java.io.File

object Environment {
    fun getCortexRoot(context: Context): File = File(context.filesDir, "debian")

    fun getHomeDir(context: Context): File {
        val root = getCortexRoot(context)
        val home = DebianRuntime.selectedAccount(context, root).home
        return File(root, home.removePrefix("/"))
    }

    fun getUsrDir(context: Context): File = File(getCortexRoot(context), "usr")

    fun getTmpDir(context: Context): File = File(getCortexRoot(context), "tmp")

    fun getNativeLibDir(context: Context): File = File(context.applicationInfo.nativeLibraryDir)

    fun buildEnvironment(context: Context): Array<String> = DebianRuntime.environment(context)
}
