package org.cortex.terminal.runtime

import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import java.io.File

internal object SafeDelete {
    @Volatile
    var lastFailure: String? = null
        private set

    fun deleteRecursively(root: File): Boolean {
        lastFailure = null
        return try {
            val deleted = remove(root)
            if (!deleted && lastFailure == null) {
                lastFailure = "Could not remove ${root.absolutePath}"
            }
            deleted
        } catch (e: Throwable) {
            lastFailure = "${root.absolutePath}: ${e.message ?: e.javaClass.simpleName}"
            false
        }
    }

    private fun remove(file: File): Boolean {
        if (isLink(file)) return unlink(file)
        if (!file.exists()) return true
        if (file.isDirectory) {
            val readable = file.setReadable(true, true)
            val writable = file.setWritable(true, true)
            val executable = file.setExecutable(true, true)
            if (!readable || !writable || !executable) {
                lastFailure = "Could not access directory ${file.absolutePath}"
                return false
            }
            val children = file.listFiles()
            if (children == null) {
                lastFailure = "Could not list directory ${file.absolutePath}"
                return false
            }
            children.forEach { child ->
                if (!remove(child)) return false
            }
        }
        return unlink(file)
    }

    private fun isLink(file: File): Boolean = ElfLinkerPatcher.isSymlink(file)

    private fun unlink(entry: File): Boolean {
        if (entry.isDirectory && !isLink(entry)) {
            val deleted = entry.delete()
            if (!deleted) lastFailure = "Could not remove directory ${entry.absolutePath}"
            return deleted
        }
        return try {
            Os.remove(entry.absolutePath)
            true
        } catch (e: ErrnoException) {
            if (e.errno == OsConstants.ENOENT) {
                true
            } else {
                val deleted = entry.delete()
                if (!deleted) lastFailure = "Could not remove ${entry.absolutePath} (errno ${e.errno})"
                deleted
            }
        } catch (e: Throwable) {
            val deleted = entry.delete()
            if (!deleted) {
                lastFailure = "Could not remove ${entry.absolutePath}: ${e.message ?: e.javaClass.simpleName}"
            }
            deleted
        }
    }
}
