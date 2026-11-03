package org.cortex.terminal.runtime

import java.io.File
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

/** Removes a directory tree without following symbolic links; a symlink is deleted itself. */
internal object SafeDelete {
    fun deleteRecursively(file: File): Boolean {
        val path = file.toPath()
        return try {
            if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return true
            Files.walkFileTree(path, object : SimpleFileVisitor<Path>() {
                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    Files.deleteIfExists(file)
                    return FileVisitResult.CONTINUE
                }

                override fun postVisitDirectory(dir: Path, error: IOException?): FileVisitResult {
                    if (error != null) throw error
                    Files.deleteIfExists(dir)
                    return FileVisitResult.CONTINUE
                }
            })
            true
        } catch (_: IOException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }
}
