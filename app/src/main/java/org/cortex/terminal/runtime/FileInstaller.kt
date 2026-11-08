package org.cortex.terminal.runtime

import android.os.Build
import android.system.Os
import android.util.Log
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException

internal object FileInstaller {
    private const val TAG = "FileInstaller"

    fun install(file: File, text: String, mode: Int): Boolean = install(file, text.toByteArray(Charsets.UTF_8), mode)

    @Synchronized
    fun install(file: File, bytes: ByteArray, mode: Int): Boolean {
        if (holds(file, bytes)) {
            chmod(file, mode)
            return false
        }
        val dir = file.absoluteFile.parentFile ?: throw IOException("${file.path} has no directory")
        dir.mkdirs()
        val tmp = File(dir, ".${file.name}.cortex-new")
        ElfLinkerPatcher.deleteIfExists(tmp)
        tmp.writeBytes(bytes)
        chmod(tmp, mode)
        val renamed = ElfLinkerPatcher.atomicReplace(tmp, file)
        if (!renamed || !holds(file, bytes)) {
            if (file.exists()) file.delete()
            file.writeBytes(bytes)
            if (!holds(file, bytes)) {
                tmp.delete()
                throw IOException("could not replace ${file.path} renamed=$renamed")
            }
        }
        ElfLinkerPatcher.deleteIfExists(tmp)
        chmod(file, mode)
        return true
    }

    fun holds(file: File, bytes: ByteArray): Boolean {
        if (!file.isFile || file.length() != bytes.size.toLong() || ElfLinkerPatcher.isSymlink(file)) return false
        return try {
            file.readBytes().contentEquals(bytes)
        } catch (e: IOException) {
            false
        }
    }

    @Synchronized
    fun symlink(target: String, link: File): Boolean {
        if (ElfLinkerPatcher.readSymlink(link) == target) return false
        val dir = link.absoluteFile.parentFile ?: throw IOException("${link.path} has no directory")
        val tmp = File(dir, ".${link.name}.cortex-link")
        ElfLinkerPatcher.deleteIfExists(tmp)
        if (!ElfLinkerPatcher.createSymlink(target, tmp)) throw IOException("could not create ${tmp.path}")
        if (!ElfLinkerPatcher.atomicReplace(tmp, link)) {
            ElfLinkerPatcher.deleteIfExists(tmp)
            throw IOException("could not replace ${link.path}")
        }
        return true
    }

    fun chmod(file: File, mode: Int) {
        if (Build.VERSION.SDK_INT > 0) {
            try {
                Os.chmod(file.absolutePath, mode)
            } catch (e: Exception) {
                Log.w(TAG, "chmod ${Integer.toOctalString(mode)} ${file.path} failed: ${e.message}")
            }
        } else {

            file.setReadable((mode and 256) != 0, (mode and 4) == 0)
            file.setWritable((mode and 128) != 0, true)
            file.setExecutable((mode and 64) != 0, (mode and 1) == 0)
        }
    }
}

internal class FileLocks private constructor(
    private val files: List<RandomAccessFile>,
    private val locks: List<FileLock>
) : AutoCloseable {
    override fun close() {
        locks.forEach { try { it.release() } catch (_: Exception) {} }
        files.forEach { try { it.close() } catch (_: Exception) {} }
    }

    companion object {

        fun tryLock(paths: List<File>): FileLocks? {
            val files = mutableListOf<RandomAccessFile>()
            val locks = mutableListOf<FileLock>()
            try {
                for (path in paths) {
                    path.parentFile?.mkdirs()
                    val raf = RandomAccessFile(path, "rw")
                    files.add(raf)
                    val lock = try { raf.channel.tryLock() } catch (_: OverlappingFileLockException) { null }
                    if (lock == null) {
                        FileLocks(files, locks).close()
                        return null
                    }
                    locks.add(lock)
                }
            } catch (e: IOException) {
                FileLocks(files, locks).close()
                return null
            }
            return FileLocks(files, locks)
        }
    }
}
