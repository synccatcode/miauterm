package org.cortex.terminal.session

import android.content.Context
import android.util.Log
import org.cortex.terminal.R
import org.cortex.terminal.emulator.TerminalEmulator
import org.cortex.terminal.pty.PtyProcess
import org.cortex.terminal.runtime.DebianRuntime
import org.cortex.terminal.runtime.Environment
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors
import kotlin.concurrent.thread
import kotlin.concurrent.withLock

class TerminalSession(
    private val context: Context,
    var rows: Int = 24,
    var cols: Int = 80,
    var widthPx: Int = 0,
    var heightPx: Int = 0,
    var onRedraw: (() -> Unit)? = null,
    val failsafeMode: Boolean = false
) {
    private val tag = "TerminalSession"
    val emulator = TerminalEmulator(rows, cols) {
        onRedraw?.invoke()
    }
    private var ptyProcess: PtyProcess? = null
    private var readerThread: Thread? = null
    @Volatile
    var isRunning = false
        private set

    var onSessionFinished: ((Int) -> Unit)? = null
    var onCloseRequested: (() -> Unit)? = null
    var exitCode: Int? = null
        private set
    var title = "Miauterm"
    @Volatile var accountName: String = "root"
        private set
    fun promptLabel(): String = sessionPromptLabel(accountName)

    /** 1-based position of this session in the drawer, for notification texts. */
    var numberProvider: (() -> Int)? = null

    /** Where the shell starts instead of the home directory (a session brought back), if it still exists. */
    var startDirectory: String? = null

    /** Output shown, dimmed, before the shell starts: the end of a session brought back. */
    var restoredOutput: String? = null

    /** The shell's directory now (not known for root sessions, whose shell is under su). */
    fun currentDirectory(): String? {
        val pid = ptyProcess?.pid ?: return null
        if (!isRunning) return startDirectory
        return try {
            android.system.Os.readlink("/proc/$pid/cwd").takeIf { it.startsWith("/") }
        } catch (e: Exception) {
            null
        }
    }

    /** The end of the main screen's text, for [SessionStore]. */
    fun transcript(): String = emulator.lock.withLock {
        SessionStore.trimOutput(emulator.buffer.transcript(SessionStore.MAX_LINES))
    }

    fun start() {
        if (isRunning) return
        var stage = "preparing session"
        try {
            val shell: String
            val args: Array<String>
            val ptyEnv: Array<String>
            val cwd: File
            if (failsafeMode) {
                stage = "preparing Android shell"
                accountName = "shell"
                shell = if (File("/system/bin/sh").canExecute()) "/system/bin/sh" else "/bin/sh"
                args = arrayOf("-i")
                val home = context.filesDir
                cwd = home
                ptyEnv = failsafeEnvironment(home, context.cacheDir)
                DebianRuntime.recordRuntimeEvent(context, "Failsafe session requested; home=${home.absolutePath}")
            } else {
                val root = Environment.getCortexRoot(context)
                val shellPath = DebianRuntime.shellPathForSession(root)
                val launcher = File(context.filesDir, "proot/launcher.sh")
                val account = DebianRuntime.selectedAccount(context, root)
                accountName = account.name
                val rootCwd = File(root, account.home.removePrefix("/"))
                stage = "checking Linux rootfs files"
                DebianRuntime.recordRuntimeEvent(
                    context,
                    "Session start requested; failsafe=$failsafeMode root=${root.absolutePath}"
                )
                check(root.isDirectory && rootCwd.isDirectory) {
                    "Debian storage is missing at ${root.absolutePath}. Restart Miauterm to install the bundled Debian system."
                }
                check(shellPath != null && shellPath.isFile && shellPath.canExecute()) {
                    "No valid executable ARM64 Debian shell was found under ${root.absolutePath} " +
                        "(checked /usr/bin/bash, /bin/bash, /usr/bin/sh, /bin/sh). " +
                        "Open Settings > Linux system to reset Debian."
                }

                stage = "preparing PRoot launcher"
                val prootShell = DebianRuntime.shellCommand(context)
                check(launcher.isFile && launcher.canExecute()) {
                    "PRoot launcher could not be created at ${launcher.absolutePath}."
                }
                val requestedDirectory = startDirectory
                    ?.takeIf { it.startsWith(root.absolutePath) }
                    ?.removePrefix(root.absolutePath)
                    ?.ifEmpty { account.home } ?: account.home
                val selectedHome = account.home.trimEnd('/').ifEmpty { "/" }
                val otherAccountHomes = DebianRuntime.accounts(root)
                    .asSequence()
                    .map { it.home.trimEnd('/').ifEmpty { "/" } }
                    .filter { it != selectedHome }
                val guestDirectory = if (otherAccountHomes.any { home ->
                        requestedDirectory == home || requestedDirectory.startsWith("$home/")
                    }
                ) account.home else requestedDirectory
                shell = prootShell
                args = DebianRuntime.shellArguments(context, guestDirectory, failsafeMode)
                ptyEnv = DebianRuntime.environment(context, failsafeMode)
                cwd = rootCwd
                restoredOutput?.let { showRestoredOutput(it) }
                restoredOutput = null
            }

            stage = "creating Android terminal PTY"
            ptyProcess = PtyProcess.create(
                cmd = shell,
                args = args,
                envVars = ptyEnv,
                cwd = cwd.absolutePath,
                rows = rows,
                cols = cols,
                widthPx = widthPx,
                heightPx = heightPx
            )
            val process = ptyProcess
            if (process == null) {
                val message = "Android did not create a terminal PTY. Check Android logcat for MiautermPty errors."
                DebianRuntime.recordRuntimeEvent(context, "Session startup failed at $stage: $message")
                showStartupFailure(message)
                Log.e(tag, message)
                return
            }

            isRunning = true
            val startTime = System.currentTimeMillis()
            DebianRuntime.recordRuntimeEvent(context, "PTY created; pid=${process.pid}; launching PRoot")
            emulator.onTitleChange = { newTitle -> this.title = newTitle }
            emulator.onClipboardSet = { text -> TerminalBridge.onClipboardRequest(context, text) }
            emulator.onCommandFinished = { durationMs, exitCode ->
                TerminalBridge.onCommandFinished(context, numberProvider?.invoke() ?: 1, durationMs, exitCode)
            }
            emulator.onSendResponse = { response -> write(response) }

            readerThread = thread(start = true, name = "Miauterm-PtyReader") {
                val buffer = ByteArray(4096)
                try {
                    val stream = process.inputStream
                    while (isRunning) {
                        val read = stream.read(buffer)
                        if (read <= 0) break
                        emulator.processInput(buffer, 0, read)
                    }
                } catch (e: Exception) {
                    if (isRunning) Log.e(tag, "Error reading from PTY: ${e.message}", e)
                } finally {
                    val code = process.waitFor()
                    exitCode = code
                    val duration = System.currentTimeMillis() - startTime
                    DebianRuntime.recordRuntimeEvent(context, "Terminal process exited; code=$code durationMs=$duration")
                    Log.i(tag, "Process exited with code $code after ${duration}ms")
                    isRunning = false
                    if (code != 0) {
                        val msg = "\r\n[Linux session exited with code $code - see Settings > Debian system > diagnostic log]\r\n"
                        emulator.processInput(msg.toByteArray(), 0, msg.length)
                    } else if (duration <= 1500) {
                        val msg = "\r\n[Debian shell exited immediately - see Settings > Debian system > diagnostic log]\r\n"
                        emulator.processInput(msg.toByteArray(), 0, msg.length)
                    }
                    onSessionFinished?.invoke(code)
                }
            }
        } catch (t: Throwable) {
            val detail = t.message?.takeIf { it.isNotBlank() } ?: t.javaClass.simpleName
            val message = "Failed while $stage: $detail"
            DebianRuntime.recordRuntimeEvent(context, "Session startup failed at $stage: $detail")
            Log.e(tag, message, t)
            showStartupFailure(message)
        }
    }

    private fun showStartupFailure(message: String) {
        val output = "\r\n\u001b[31mMiauterm could not start the Linux system\u001b[0m\r\n$message\r\n"
        val bytes = output.toByteArray(Charsets.UTF_8)
        emulator.processInput(bytes, 0, bytes.size)
    }

    private fun showRestoredOutput(output: String) {
        val sb = StringBuilder()
        if (output.isNotEmpty()) {
            sb.append("\u001b[2m").append(output.replace("\n", "\r\n")).append("\u001b[0m\r\n")
        }
        sb.append("\u001b[2m── ").append(context.getString(R.string.session_restored)).append(" ──\u001b[0m\r\n")
        val bytes = sb.toString().toByteArray(Charsets.UTF_8)
        emulator.processInput(bytes, 0, bytes.size)
    }

    private val writeExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "Cortex-PtyWriter").apply { isDaemon = true }
    }

    fun write(bytes: ByteArray) {
        if (!isRunning || writeExecutor.isShutdown) return
        try {
            writeExecutor.execute {
                if (!isRunning) return@execute
                try {
                    ptyProcess?.outputStream?.let { os ->
                        os.write(bytes)
                        os.flush()
                    }
                } catch (e: IOException) {
                    if (isRunning) {
                        Log.e(tag, "Error writing to PTY: ${e.message}")
                    }
                }
            }
        } catch (_: java.util.concurrent.RejectedExecutionException) {
            // Executor shut down concurrently during destroy()
        }
    }

    fun write(text: String) {
        write(text.toByteArray(Charsets.UTF_8))
    }

    // Whether this session is on screen in the focused window. Main thread only.
    private var isFocused = false

    /** Called when the session gains or loses focus; tells the program if it asked (mode 1004). */
    fun setFocused(focused: Boolean) {
        if (focused == isFocused) return
        isFocused = focused
        emulator.focusReport(focused)?.let { write(it) }
    }

    fun updateDimensions(newRows: Int, newCols: Int, newWidthPx: Int, newHeightPx: Int) {
        if (newRows <= 0 || newCols <= 0) return
        this.rows = newRows
        this.cols = newCols
        this.widthPx = newWidthPx
        this.heightPx = newHeightPx

        emulator.resize(newRows, newCols)
        ptyProcess?.resize(newRows, newCols, newWidthPx, newHeightPx)
    }

    fun awaitProcessExit(timeoutMs: Long) {
        try { readerThread?.join(timeoutMs) } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
    }

    fun destroy() {
        isRunning = false
        writeExecutor.shutdownNow()
        ptyProcess?.destroy()
        try {
            readerThread?.interrupt()
        } catch (_: Exception) {}
    }
}

internal fun failsafeEnvironment(home: File, cache: File): Array<String> = arrayOf(
    "HOME=${home.absolutePath}",
    "PWD=${home.absolutePath}",
    "PS1=${home.absolutePath} \$ ",
    "PATH=/system/bin:/system/xbin",
    "TERM=xterm-256color",
    "COLORTERM=truecolor",
    "LANG=C.UTF-8",
    "LC_ALL=C.UTF-8",
    "TMPDIR=${cache.absolutePath}"
)
