package org.cortex.terminal.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.FileObserver
import android.os.Process
import android.os.SystemClock
import android.util.Log
import androidx.preference.PreferenceManager
import org.cortex.terminal.LocaleHelper
import org.cortex.terminal.R
import org.cortex.terminal.api.ApiServer
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class LocalServers(private val context: Context, private val root: File) {
    private val dir = File(root, "run/cortex/listening")
    @Volatile
    private var executor: ScheduledExecutorService? = null
    private var observer: FileObserver? = null

    private class Pending(val program: String?, val since: Long, var nextCheck: Long, var attempts: Int = 0)

    private class Shown(val program: String?)

    private val pending = java.util.concurrent.ConcurrentHashMap<Int, Pending>()
    private val shown = java.util.concurrent.ConcurrentHashMap<Int, Shown>()
    private val ignored: MutableSet<Int> = java.util.concurrent.ConcurrentHashMap.newKeySet()

    @Volatile
    private var known: Set<Int>? = null

    private val stateLock = Any()
    @Volatile
    private var stopped = true

    @Synchronized
    fun start() {
        if (executor != null) return
        stopped = false
        executor = Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "Cortex-LocalServers").apply { isDaemon = true }
        }
        scheduleNext(1)
    }

    private fun scheduleNext(delaySeconds: Long) {
        val running = executor ?: return
        try {

            nextLook?.cancel(false)
            nextLook = running.schedule({
                safely { poll() }
                scheduleNext(nextDelaySeconds())
            }, delaySeconds, TimeUnit.SECONDS)
        } catch (_: java.util.concurrent.RejectedExecutionException) {
        }
    }

    @Volatile
    private var nextLook: java.util.concurrent.ScheduledFuture<*>? = null

    private fun nextDelaySeconds(): Long =
        if (pending.isNotEmpty() || shown.isNotEmpty() || SystemClock.elapsedRealtime() >= procRetryAt) POLL_SECONDS
        else IDLE_POLL_SECONDS

    @Synchronized
    fun stop() {
        stopped = true
        synchronized(stateLock) {
            observer?.stopWatching()
            observer = null
        }
        val running = executor
        executor = null
        running?.shutdownNow()

        try {
            running?.awaitTermination(1, TimeUnit.SECONDS)
        } catch (_: InterruptedException) {
        }
        synchronized(stateLock) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            for (port in shown.keys) nm.cancel(TAG, port)
            shown.clear()
            pending.clear()
            ignored.clear()
            known = null
        }
    }

    private fun safely(block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            Log.w(TAG, "Looking for servers failed: ${e.message}")
        }
    }

    private fun enabled() =
        PreferenceManager.getDefaultSharedPreferences(context).getBoolean(PREF_NOTIFY_SERVERS, true)

    @Suppress("DEPRECATION")
    private fun watch() {
        synchronized(stateLock) {
            if (stopped || observer != null) return
            if (!dir.isDirectory) {

                if (!File(root, "usr").isDirectory || !dir.mkdirs()) return
            }
            observer = object : FileObserver(dir.path, FileObserver.CLOSE_WRITE or FileObserver.MOVED_TO) {
                override fun onEvent(event: Int, path: String?) {
                    val port = path?.toIntOrNull() ?: return
                    try {
                        executor?.execute { safely { reported(port) } }
                    } catch (_: java.util.concurrent.RejectedExecutionException) {
                    }
                }
            }.also { it.startWatching() }
        }
    }

    private fun reported(port: Int) {
        if (stopped || shown.containsKey(port) || port == ApiServer.port) return
        ignored.remove(port)
        val program = readReport(File(dir, port.toString()))?.second
        addPending(port, program)
        check(port)

        if (pending.isNotEmpty()) scheduleNext(POLL_SECONDS)
    }

    private fun addPending(port: Int, program: String?) {
        val now = SystemClock.elapsedRealtime()
        pending[port] = Pending(program, now, now)
    }

    private fun poll() {
        if (stopped) return
        watch()
        if (stopped) return
        if (!enabled()) {
            if (shown.isNotEmpty()) stopShowing(shown.keys.toList())
            pending.clear()
            return
        }

        val listening = procListening()
        stopShowing(shown.keys.filter { if (listening != null) it !in listening else !accepts(it) })

        dir.listFiles()?.forEach { file ->
            val port = file.name.toIntOrNull() ?: return@forEach
            val report = readReport(file)
            if (report == null || !File("/proc/${report.first}").exists()) {
                file.delete()
            } else if (!shown.containsKey(port) && !pending.containsKey(port) && port !in ignored) {
                addPending(port, report.second)
            }
        }

        if (listening != null) ignored.retainAll(listening)
        else ignored.retainAll { File(dir, it.toString()).exists() }

        listening?.let { ports ->
            val before = known
            known = if (before == null) ports else before.intersect(ports) + shown.keys
            if (before != null) {
                for (port in ports - before - shown.keys - pending.keys - ignored) {
                    if (port != ApiServer.port) addPending(port, null)
                }
            }
        }
        for (port in pending.keys.toList()) check(port)
    }

    private fun check(port: Int) {
        val p = pending[port] ?: return
        val now = SystemClock.elapsedRealtime()
        if (!enabled() || now < p.nextCheck) return
        if (answersHttp(port)) {
            pending.remove(port)
            show(port, p.program)
        } else if (now - p.since > GIVE_UP_MS) {
            pending.remove(port)
            ignored += port
        } else {
            p.attempts++
            p.nextCheck = now + (POLL_SECONDS * 1000L shl (p.attempts - 1).coerceAtMost(5))
        }
    }

    private fun stopShowing(ports: List<Int>) {
        if (ports.isEmpty()) return
        synchronized(stateLock) {
            if (stopped) return
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            for (port in ports) {
                shown.remove(port)
                nm.cancel(TAG, port)
                File(dir, port.toString()).delete()
            }
        }
    }

    private fun readReport(file: File): Pair<Int, String?>? = try {
        ServerPorts.parseReport(file.readText())
    } catch (e: Exception) {
        null
    }

    private var procRetryAt = 0L

    private fun procListening(): Set<Int>? {
        if (SystemClock.elapsedRealtime() < procRetryAt) return null
        val uids = setOf(Process.myUid())
        var any = false
        val ports = mutableSetOf<Int>()
        for (name in listOf("/proc/net/tcp", "/proc/net/tcp6")) {
            val text = try {
                File(name).readText()
            } catch (e: Exception) {
                continue
            }
            any = true
            ports += ServerPorts.parseProcNetListening(text, uids)
        }
        if (!any) {
            procRetryAt = SystemClock.elapsedRealtime() + PROC_RETRY_MS
            return null
        }

        val api = ApiServer.port
        return if (api == null || api in ports) ports else null
    }

    private fun accepts(port: Int): Boolean = LOOPBACKS.any { address ->
        try {
            Socket().use { it.connect(InetSocketAddress(address, port), CONNECT_TIMEOUT_MS) }
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun answersHttp(port: Int): Boolean = LOOPBACKS.any { address ->
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(address, port), CONNECT_TIMEOUT_MS)
                socket.soTimeout = READ_TIMEOUT_MS
                socket.getOutputStream().write("HEAD / HTTP/1.0\r\nHost: localhost:$port\r\n\r\n".toByteArray())
                val buf = ByteArray(8)
                var n = 0
                while (n < buf.size) {
                    val r = socket.getInputStream().read(buf, n, buf.size - n)
                    if (r < 0) break
                    n += r
                }
                ServerPorts.isHttpResponse(String(buf, 0, n, Charsets.ISO_8859_1))
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun show(port: Int, program: String?) {
        synchronized(stateLock) {
            if (stopped) return
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val lc = LocaleHelper.wrap(context)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, lc.getString(R.string.servers_channel_name), NotificationManager.IMPORTANCE_LOW).apply {
                        description = lc.getString(R.string.servers_channel_description)
                        setShowBadge(false)
                    }
                )
            }
            val url = "http://localhost:$port/"
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
            val open = PendingIntent.getActivity(
                context, port,
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                flags
            )
            val copy = PendingIntent.getService(
                context, port,
                Intent(context, CortexService::class.java).setAction(CortexService.ACTION_COPY_LINK).putExtra(CortexService.EXTRA_LINK, url),
                flags
            )
            val title = if (program != null) lc.getString(R.string.server_notification_title, program, port)
            else lc.getString(R.string.server_notification_title_unknown, port)
            @Suppress("DEPRECATION")
            val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(context, CHANNEL_ID)
            else Notification.Builder(context)
            val notification = builder
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(lc.getString(R.string.server_notification_text, url))
                .setContentIntent(open)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .addAction(Notification.Action.Builder(null as android.graphics.drawable.Icon?, lc.getString(R.string.server_open), open).build())
                .addAction(Notification.Action.Builder(null as android.graphics.drawable.Icon?, lc.getString(R.string.server_copy_link), copy).build())
                .build()
            nm.notify(TAG, port, notification)
            shown[port] = Shown(program)
        }
    }

    companion object {
        private const val TAG = "LocalServers"
        const val CHANNEL_ID = "cortex_servers"
        const val PREF_NOTIFY_SERVERS = "notify_servers"
        private const val POLL_SECONDS = 3L
        private const val IDLE_POLL_SECONDS = 15L
        private const val PROC_RETRY_MS = 10 * 60 * 1000L
        private const val GIVE_UP_MS = 60_000L
        private const val CONNECT_TIMEOUT_MS = 500
        private const val READ_TIMEOUT_MS = 1500
        private val LOOPBACKS = listOf(InetAddress.getByName("127.0.0.1"), InetAddress.getByName("::1"))
    }
}
