package org.cortex.terminal

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.StatFs
import androidx.core.content.FileProvider
import org.cortex.terminal.runtime.Environment
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object BugReport {
    private const val LOG_LINES = 4000
    private const val LOG_LIMIT = 2_000_000

    fun write(context: Context): File {
        val dir = File(context.cacheDir, "reports")
        dir.mkdirs()

        val dayAgo = System.currentTimeMillis() - 24 * 3600 * 1000L
        dir.listFiles()?.forEach { if (it.lastModified() < dayAgo) it.delete() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val file = File(dir, "miauterm-report-$stamp.txt")
        file.writeText(text(context))
        return file
    }

    fun share(activity: Activity, file: File) {
        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, "Miauterm bug report")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        send.clipData = ClipData.newRawUri(file.name, uri)
        activity.startActivity(Intent.createChooser(send, activity.getString(R.string.bug_report_chooser)))
    }

    internal fun text(context: Context): String {
        val root = Environment.getCortexRoot(context)
        val sections = linkedMapOf<String, List<Pair<String, String?>>>()
        sections["Miauterm"] = listOf(
            "version" to try {
                val info = context.packageManager.getPackageInfo(context.packageName, 0)
                @Suppress("DEPRECATION")
                "${info.versionName} (${info.versionCode})"
            } catch (e: Exception) {
                null
            },
            "package" to context.packageName,
            "Debian rootfs" to readOrNull(File(root, "etc/debian_version"))?.trim(),
            "runtime" to "PRoot"
        )
        sections["Android"] = listOf(
            "release" to "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            "device" to "${Build.MANUFACTURER} ${Build.MODEL}",
            "ABIs" to Build.SUPPORTED_ABIS.joinToString(),
            "free space" to try {
                "${StatFs(context.filesDir.path).availableBytes / (1024 * 1024)} MB"
            } catch (e: Exception) {
                null
            }
        )
        sections["Debian"] = listOf(
            "release" to osRelease(readOrNull(File(root, "etc/os-release"))),
            "packages" to readOrNull(File(root, "var/lib/dpkg/status"))?.let { packageCount(it).toString() },
        )
        return format(sections) + "\n== Log (newest $LOG_LINES lines)\n" + appLog()
    }

    internal fun format(sections: Map<String, List<Pair<String, String?>>>): String = buildString {
        for ((name, entries) in sections) {
            append("== ").append(name).append('\n')
            for ((key, value) in entries) append(key).append(": ").append(value ?: "(unknown)").append('\n')
            append('\n')
        }
    }

    internal fun osRelease(text: String?): String? =
        text?.lineSequence()?.firstOrNull { it.startsWith("PRETTY_NAME=") }
            ?.removePrefix("PRETTY_NAME=")?.trim('"')

    internal fun packageCount(status: String): Int =
        status.lineSequence().count { it.startsWith("Status: ") && it.endsWith(" installed") }

    private fun readOrNull(file: File): String? = try {
        if (file.isFile) file.readText() else null
    } catch (e: Exception) {
        null
    }

    private fun appLog(): String = try {
        val process = ProcessBuilder("logcat", "-d", "-v", "time", "-t", LOG_LINES.toString())
            .redirectErrorStream(true)
            .start()
        val bytes = process.inputStream.use { input ->
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            while (out.size() < LOG_LIMIT) {
                val n = input.read(buffer)
                if (n < 0) break
                out.write(buffer, 0, n)
            }
            out.toByteArray()
        }
        process.destroy()
        String(bytes, Charsets.UTF_8)
    } catch (e: Exception) {
        "(the log could not be read: ${e.message})\n"
    }
}
