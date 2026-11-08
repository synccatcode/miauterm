package org.cortex.terminal.emulator

import android.graphics.Color
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CharsetDecoder
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class TerminalEmulator(
    rows: Int,
    cols: Int,
    var onScreenUpdate: (() -> Unit)? = null
) {
    val lock = ReentrantLock()
    val buffer = TerminalBuffer(rows, cols)

    private val utf8Decoder: CharsetDecoder = StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPLACE)
        .onUnmappableCharacter(CodingErrorAction.REPLACE)
    private var charBuffer: CharBuffer = CharBuffer.allocate(4096)

    private enum class State {
        NORMAL, ESCAPE, CSI, OSC, CHARSET, STRING_IGNORE
    }

    private var state = State.NORMAL
    private val csiParams = ArrayList<Int>()
    private var csiCurrentParam = 0
    private var csiHasParam = false
    private var csiPrefix: Char? = null
    private var csiIntermediate: Char? = null
    private val oscBuffer = StringBuilder()

    internal companion object {
        const val MAX_CSI_PARAMS = 32
        const val MAX_CSI_PARAM = 65535
        const val MAX_OSC_CHARS = 8192

        const val MAX_OSC52_CHARS = 350_000
        const val MAX_OSC52_BYTES = 256 * 1024
        const val MAX_LINK_CHARS = 2048

        const val SYNC_UPDATE_TIMEOUT_MS = 150L
    }

    var onTitleChange: ((String) -> Unit)? = null
    var onBell: (() -> Unit)? = null

    var onClipboardSet: ((String) -> Unit)? = null

    var onCommandFinished: ((durationMs: Long, exitCode: Int?) -> Unit)? = null

    internal var monotonicMs: () -> Long = { System.nanoTime() / 1_000_000L }
    private var commandStartMs = -1L
    private var oscOverflow = false
    var onSendResponse: ((String) -> Unit)? = null
    var isApplicationCursorKeys = false

    enum class MouseMode {
        OFF,
        X10,
        VT200,
        CELL_MOTION,
        ALL_MOTION
    }

    var mouseMode = MouseMode.OFF
    var isMouseSgr = false
    var isMouseUrxvt = false
    var isMouseUtf8 = false

    val isMouseTrackingActive: Boolean
        get() = mouseMode != MouseMode.OFF

    @Volatile
    var isBracketedPasteMode = false
        private set

    @Volatile
    var isFocusReportingMode = false
        private set

    enum class CursorShape { BLOCK, UNDERLINE, BAR }

    @Volatile
    var cursorShape = CursorShape.BLOCK
        private set

    @Volatile
    var isCursorBlinking = false
        private set

    @Volatile
    private var syncUpdateSinceMs = -1L

    @Volatile
    private var syncFrameEnded = false

    fun synchronizedUpdateRemainingMs(): Long {
        val since = syncUpdateSinceMs
        if (since < 0L || syncFrameEnded) return 0L
        return (SYNC_UPDATE_TIMEOUT_MS - (monotonicMs() - since)).coerceAtLeast(0L)
    }

    fun focusReport(focused: Boolean): String? {
        if (!isFocusReportingMode) return null
        return if (focused) "\u001b[I" else "\u001b[O"
    }

    fun bracketedPaste(text: String): String? {
        if (!isBracketedPasteMode) return null
        val clean = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c == '\r' || c == '\n' -> {
                    clean.append('\r')
                    if (c == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                }
                c == '\t' -> clean.append(c)
                c < ' ' || c.code in 0x7f..0x9f -> {}
                else -> clean.append(c)
            }
            i++
        }
        val body = clean.trimEnd('\r')
        if (body.isEmpty()) return ""
        return "\u001b[200~$body\u001b[201~"
    }

    fun getMouseScrollSequence(isUp: Boolean, col: Int, row: Int): String {
        val button = if (isUp) 64 else 65
        return if (isMouseSgr) {
            "\u001b[<${button};${col};${row}M"
        } else if (isMouseUrxvt) {
            "\u001b[${button + 32};${col};${row}M"
        } else {
            "\u001b[M" + (32 + button).toChar() + (32 + col).toChar() + (32 + row).toChar()
        }
    }

    fun getMouseClickSequence(button: Int, col: Int, row: Int): String {
        return if (isMouseSgr) {
            "\u001b[<${button};${col};${row}M\u001b[<${button};${col};${row}m"
        } else if (isMouseUrxvt) {
            "\u001b[${button + 32};${col};${row}M\u001b[${3 + 32};${col};${row}M"
        } else {
            val press = "\u001b[M" + (32 + button).toChar() + (32 + col).toChar() + (32 + row).toChar()
            val release = "\u001b[M" + (32 + 3).toChar() + (32 + col).toChar() + (32 + row).toChar()
            press + release
        }
    }

    fun resize(rows: Int, cols: Int) {
        lock.withLock {
            buffer.resize(rows, cols)
        }
        onScreenUpdate?.invoke()
    }

    fun remapColors(remap: TerminalColor.Remap) {
        lock.withLock {
            buffer.remapColors(remap)
        }
        onScreenUpdate?.invoke()
    }

    private val utf8ByteBuffer: ByteBuffer = ByteBuffer.allocate(16384)

    fun processInput(bytes: ByteArray, offset: Int, length: Int) {
        if (length <= 0 || offset < 0 || offset + length > bytes.size) return
        lock.withLock {
            syncFrameEnded = false
            var pos = offset
            val end = offset + length
            while (pos < end) {
                val toPut = minOf(end - pos, utf8ByteBuffer.remaining())
                if (toPut <= 0) {
                    utf8ByteBuffer.clear()
                    continue
                }
                utf8ByteBuffer.put(bytes, pos, toPut)
                pos += toPut
                utf8ByteBuffer.flip()

                if (charBuffer.capacity() < utf8ByteBuffer.remaining() * 2) {
                    charBuffer = CharBuffer.allocate(utf8ByteBuffer.remaining() * 2 + 1024)
                }

                charBuffer.clear()
                utf8Decoder.decode(utf8ByteBuffer, charBuffer, false)
                charBuffer.flip()

                val chars = charBuffer.array()
                val limit = charBuffer.arrayOffset() + charBuffer.limit()
                var i = charBuffer.arrayOffset() + charBuffer.position()
                while (i < limit) {
                    val c = chars[i]
                    if (state == State.NORMAL && c >= ' ') {
                        var j = i + 1
                        while (j < limit && chars[j] >= ' ') j++
                        buffer.writeRun(chars, i, j)
                        i = j
                    } else {
                        processChar(c)
                        i++
                    }
                }
                charBuffer.position(charBuffer.limit())

                utf8ByteBuffer.compact()
            }
        }
        onScreenUpdate?.invoke()
    }

    private fun processChar(c: Char) {
        when (state) {
            State.NORMAL -> handleNormal(c)
            State.ESCAPE -> handleEscape(c)
            State.CSI -> handleCsi(c)
            State.OSC -> handleOsc(c)
            State.CHARSET -> handleCharset(c)
            State.STRING_IGNORE -> handleStringIgnore(c)
        }
    }

    private var stringIgnoreEscaped = false

    private fun handleStringIgnore(c: Char) {
        if (c == '\u0007') {
            state = State.NORMAL
            stringIgnoreEscaped = false
        } else if (c == '\u001b') {
            stringIgnoreEscaped = true
        } else if (stringIgnoreEscaped && c == '\\') {
            state = State.NORMAL
            stringIgnoreEscaped = false
        } else {
            stringIgnoreEscaped = false
        }
    }

    private fun handleCharset(@Suppress("UNUSED_PARAMETER") c: Char) {

        state = State.NORMAL
    }

    private fun handleNormal(c: Char) {
        when (c) {
            '\u001b' -> state = State.ESCAPE
            '\r' -> buffer.carriageReturn()
            '\n' -> buffer.newLine()
            '\b' -> {
                buffer.cursorCol = (buffer.cursorCol - 1).coerceAtLeast(0)
                buffer.isWrapPending = false
            }
            '\t' -> {
                val nextTab = (buffer.cursorCol / 8 + 1) * 8
                buffer.cursorCol = nextTab.coerceAtMost(buffer.cols - 1)
                buffer.isWrapPending = false
            }
            '\u0007' -> onBell?.invoke()
            else -> {
                if (c >= ' ') {
                    buffer.writeChar(c)
                }
            }
        }
    }

    private var savedCursorRow = 0
    private var savedCursorCol = 0

    private fun handleEscape(c: Char) {
        when (c) {
            '[' -> {
                state = State.CSI
                csiParams.clear()
                csiCurrentParam = 0
                csiHasParam = false
                csiPrefix = null
                csiIntermediate = null
            }
            ']' -> {
                state = State.OSC
                oscBuffer.setLength(0)
                oscOverflow = false
            }
            '(', ')', '*', '+' -> {

                state = State.CHARSET
            }
            '_', 'P', '^', 'X' -> {

                state = State.STRING_IGNORE
                stringIgnoreEscaped = false
            }
            'c' -> {
                buffer.reset()
                commandStartMs = -1L
                savedCursorRow = 0
                savedCursorCol = 0
                isApplicationCursorKeys = false
                mouseMode = MouseMode.OFF
                isMouseSgr = false
                isMouseUrxvt = false
                isMouseUtf8 = false
                isBracketedPasteMode = false
                isFocusReportingMode = false
                cursorShape = CursorShape.BLOCK
                isCursorBlinking = false
                syncUpdateSinceMs = -1L
                state = State.NORMAL
            }
            '7' -> {
                savedCursorRow = buffer.cursorRow
                savedCursorCol = buffer.cursorCol
                state = State.NORMAL
            }
            '8' -> {
                buffer.cursorRow = savedCursorRow.coerceIn(0, buffer.rows - 1)
                buffer.cursorCol = savedCursorCol.coerceIn(0, buffer.cols - 1)
                buffer.isWrapPending = false
                state = State.NORMAL
            }
            'M' -> {
                if (buffer.cursorRow == buffer.scrollTop) {
                    buffer.scrollDown(buffer.scrollTop, buffer.scrollBottom)
                } else {
                    buffer.cursorRow = (buffer.cursorRow - 1).coerceAtLeast(0)
                }
                buffer.isWrapPending = false
                state = State.NORMAL
            }
            'D' -> {
                buffer.newLine()
                state = State.NORMAL
            }
            'E' -> {
                buffer.carriageReturn()
                buffer.newLine()
                state = State.NORMAL
            }
            '=', '>' -> {
                state = State.NORMAL
            }
            'H' -> {
                state = State.NORMAL
            }
            else -> {
                state = State.NORMAL
            }
        }
    }

    private fun handleCsi(c: Char) {

        if (csiParams.isEmpty() && !csiHasParam && (c == '?' || c == '>' || c == '=' || c == '<' || c == '!')) {
            csiPrefix = c
            return
        }

        if (c in '0'..'9') {

            val digit = c - '0'
            csiCurrentParam = if (csiCurrentParam > (MAX_CSI_PARAM - digit) / 10) {
                MAX_CSI_PARAM
            } else {
                csiCurrentParam * 10 + digit
            }
            csiHasParam = true
            return
        }

        if (c == ';' || c == ':') {
            if (csiParams.size < MAX_CSI_PARAMS) {
                csiParams.add(if (csiHasParam) csiCurrentParam else 0)
            }
            csiCurrentParam = 0
            csiHasParam = false
            return
        }

        if (c in ' '..'/' && c != ';') {
            csiIntermediate = c
            return
        }

        if (c in '@'..'~') {
            if (csiHasParam && csiParams.size < MAX_CSI_PARAMS) {
                csiParams.add(csiCurrentParam)
            }
            executeCsi(c)
            state = State.NORMAL
            return
        }

        if (c == '\u001b') {
            state = State.ESCAPE
            return
        }

        state = State.NORMAL
    }

    private fun executeCsi(cmd: Char) {
        val p1 = if (csiParams.isNotEmpty()) csiParams[0] else 0
        val p2 = if (csiParams.size > 1) csiParams[1] else 0

        if (csiIntermediate != null) {
            when {
                csiIntermediate == ' ' && cmd == 'q' && csiPrefix == null -> setCursorStyle(p1)
                csiIntermediate == '$' && cmd == 'p' -> reportMode(p1)
            }
            return
        }

        when (cmd) {
            'A' -> {
                val count = if (p1 == 0) 1 else p1
                buffer.cursorRow = (buffer.cursorRow - count).coerceAtLeast(0)
                buffer.isWrapPending = false
            }
            'B' -> {
                val count = if (p1 == 0) 1 else p1
                buffer.cursorRow = (buffer.cursorRow + count).coerceAtMost(buffer.rows - 1)
                buffer.isWrapPending = false
            }
            'C' -> {
                val count = if (p1 == 0) 1 else p1
                buffer.cursorCol = (buffer.cursorCol + count).coerceAtMost(buffer.cols - 1)
                buffer.isWrapPending = false
            }
            'D' -> {
                val count = if (p1 == 0) 1 else p1
                buffer.cursorCol = (buffer.cursorCol - count).coerceAtLeast(0)
                buffer.isWrapPending = false
            }
            'E' -> {
                val count = if (p1 == 0) 1 else p1
                buffer.cursorRow = (buffer.cursorRow + count).coerceAtMost(buffer.rows - 1)
                buffer.cursorCol = 0
                buffer.isWrapPending = false
            }
            'F' -> {
                val count = if (p1 == 0) 1 else p1
                buffer.cursorRow = (buffer.cursorRow - count).coerceAtLeast(0)
                buffer.cursorCol = 0
                buffer.isWrapPending = false
            }
            'G' -> {
                val col = if (p1 == 0) 1 else p1
                buffer.cursorCol = (col - 1).coerceIn(0, buffer.cols - 1)
                buffer.isWrapPending = false
            }
            'H', 'f' -> {
                val row = if (p1 == 0) 1 else p1
                val col = if (p2 == 0) 1 else p2
                buffer.cursorRow = (row - 1).coerceIn(0, buffer.rows - 1)
                buffer.cursorCol = (col - 1).coerceIn(0, buffer.cols - 1)
                buffer.isWrapPending = false
            }
            'I' -> {
                val count = (if (p1 == 0) 1 else p1).coerceAtMost(buffer.cols)
                for (k in 0 until count) {
                    val nextTab = (buffer.cursorCol / 8 + 1) * 8
                    buffer.cursorCol = nextTab.coerceAtMost(buffer.cols - 1)
                }
                buffer.isWrapPending = false
            }
            'J' -> {
                buffer.isWrapPending = false
                buffer.eraseInDisplay(p1)
            }
            'K' -> {
                buffer.isWrapPending = false
                buffer.eraseInLine(p1)
            }
            'L' -> {
                val count = (if (p1 == 0) 1 else p1).coerceAtMost(buffer.rows)
                for (i in 0 until count) {
                    buffer.scrollDown(buffer.cursorRow, buffer.scrollBottom)
                }
                buffer.isWrapPending = false
            }
            'M' -> {
                val count = (if (p1 == 0) 1 else p1).coerceAtMost(buffer.rows)
                for (i in 0 until count) {
                    buffer.scrollUp(buffer.cursorRow, buffer.scrollBottom)
                }
                buffer.isWrapPending = false
            }
            'P' -> {
                val count = (if (p1 == 0) 1 else p1).coerceAtMost(buffer.cols)
                buffer.isWrapPending = false
                val r = buffer.cursorRow.coerceIn(0, buffer.rows - 1)
                buffer.screen[r].deleteChars(buffer.cursorCol, count, buffer.currentFg, buffer.currentBg)
            }
            '@' -> {
                val count = (if (p1 == 0) 1 else p1).coerceAtMost(buffer.cols)
                buffer.isWrapPending = false
                val r = buffer.cursorRow.coerceIn(0, buffer.rows - 1)
                buffer.screen[r].insertChars(buffer.cursorCol, count, buffer.currentFg, buffer.currentBg)
            }
            'S' -> {
                val count = (if (p1 == 0) 1 else p1).coerceAtMost(buffer.rows)
                for (i in 0 until count) {
                    buffer.scrollUp(buffer.scrollTop, buffer.scrollBottom)
                }
            }
            'T' -> {
                val count = (if (p1 == 0) 1 else p1).coerceAtMost(buffer.rows)
                for (i in 0 until count) {
                    buffer.scrollDown(buffer.scrollTop, buffer.scrollBottom)
                }
            }
            'X' -> {
                val count = (if (p1 == 0) 1 else p1).coerceAtMost(buffer.cols)
                val r = buffer.cursorRow
                if (r in 0 until buffer.rows) {
                    val endCol = (buffer.cursorCol + count).coerceAtMost(buffer.cols)
                    for (c in buffer.cursorCol until endCol) {
                        buffer.screen[r].setChar(c, ' ', buffer.currentFg, buffer.currentBg, 0)
                    }
                }
                buffer.isWrapPending = false
            }
            'Z' -> {
                val count = (if (p1 == 0) 1 else p1).coerceAtMost(buffer.cols)
                for (k in 0 until count) {
                    val prevTab = if (buffer.cursorCol % 8 == 0) buffer.cursorCol - 8 else (buffer.cursorCol / 8) * 8
                    buffer.cursorCol = prevTab.coerceAtLeast(0)
                }
                buffer.isWrapPending = false
            }
            'd' -> {
                val row = if (p1 == 0) 1 else p1
                buffer.cursorRow = (row - 1).coerceIn(0, buffer.rows - 1)
                buffer.isWrapPending = false
            }
            'm' -> {

                if (csiPrefix == null) handleSgr()
            }
            'n' -> {
                if (csiPrefix == null) {
                    if (p1 == 6) {
                        val row = (buffer.cursorRow + 1).coerceIn(1, buffer.rows)
                        val col = (buffer.cursorCol + 1).coerceIn(1, buffer.cols)
                        onSendResponse?.invoke("\u001b[${row};${col}R")
                    } else if (p1 == 5) {
                        onSendResponse?.invoke("\u001b[0n")
                    }
                }
            }
            'c' -> {
                if (csiPrefix == '>') {
                    onSendResponse?.invoke("\u001b[>0;10;0c")
                } else {
                    onSendResponse?.invoke("\u001b[?62;1;2;6;7;8;9c")
                }
            }
            't' -> {
                if (p1 == 18) {
                    onSendResponse?.invoke("\u001b[8;${buffer.rows};${buffer.cols}t")
                }
            }
            'r' -> {
                if (csiPrefix != null) return
                if (csiParams.isEmpty()) {
                    buffer.scrollTop = 0
                    buffer.scrollBottom = buffer.rows - 1
                } else {
                    val top = (p1 - 1).coerceIn(0, buffer.rows - 1)
                    val bottom = if (p2 == 0) buffer.rows - 1 else (p2 - 1).coerceIn(top, buffer.rows - 1)
                    buffer.scrollTop = top
                    buffer.scrollBottom = bottom
                }
                buffer.cursorRow = 0
                buffer.cursorCol = 0
                buffer.isWrapPending = false
            }
            'h' -> {
                val params = if (csiParams.isEmpty()) listOf(0) else csiParams
                for (p in params) {
                    if (csiPrefix == '?') {
                        when (p) {
                            1 -> isApplicationCursorKeys = true
                            9 -> mouseMode = MouseMode.X10
                            25 -> buffer.isCursorVisible = true
                            47, 1047, 1049 -> buffer.useAlternateScreen(true)
                            1000, 1001 -> mouseMode = MouseMode.VT200
                            1002 -> mouseMode = MouseMode.CELL_MOTION
                            1003 -> mouseMode = MouseMode.ALL_MOTION
                            1004 -> isFocusReportingMode = true
                            1005 -> isMouseUtf8 = true
                            1006 -> isMouseSgr = true
                            1015 -> isMouseUrxvt = true
                            2004 -> isBracketedPasteMode = true
                            2026 -> if (syncUpdateSinceMs < 0L) syncUpdateSinceMs = monotonicMs()
                        }
                    }
                }
            }
            'l' -> {
                val params = if (csiParams.isEmpty()) listOf(0) else csiParams
                for (p in params) {
                    if (csiPrefix == '?') {
                        when (p) {
                            1 -> isApplicationCursorKeys = false
                            9, 1000, 1001, 1002, 1003 -> mouseMode = MouseMode.OFF
                            25 -> buffer.isCursorVisible = false
                            47, 1047, 1049 -> buffer.useAlternateScreen(false)
                            1004 -> isFocusReportingMode = false
                            1005 -> isMouseUtf8 = false
                            1006 -> isMouseSgr = false
                            1015 -> isMouseUrxvt = false
                            2004 -> isBracketedPasteMode = false
                            2026 -> if (syncUpdateSinceMs >= 0L) {
                                syncUpdateSinceMs = -1L
                                syncFrameEnded = true
                            }
                        }
                    }
                }
            }

            's' -> if (csiPrefix == null) {
                savedCursorRow = buffer.cursorRow
                savedCursorCol = buffer.cursorCol
            }
            'u' -> if (csiPrefix == null) {
                buffer.cursorRow = savedCursorRow.coerceIn(0, buffer.rows - 1)
                buffer.cursorCol = savedCursorCol.coerceIn(0, buffer.cols - 1)
                buffer.isWrapPending = false
            }
        }
    }

    private fun setCursorStyle(ps: Int) {
        val shape = when (ps) {
            0, 1, 2 -> CursorShape.BLOCK
            3, 4 -> CursorShape.UNDERLINE
            5, 6 -> CursorShape.BAR
            else -> return
        }
        cursorShape = shape
        isCursorBlinking = ps % 2 == 1
    }

    private fun reportMode(mode: Int) {
        if (csiPrefix != '?') {
            onSendResponse?.invoke("\u001b[$mode;0\$y")
            return
        }
        fun flag(set: Boolean) = if (set) 1 else 2
        val status = when (mode) {
            1 -> flag(isApplicationCursorKeys)
            7 -> 3
            9 -> flag(mouseMode == MouseMode.X10)
            25 -> flag(buffer.isCursorVisible)
            47, 1047, 1049 -> flag(buffer.isAlternate)
            1000, 1001 -> flag(mouseMode == MouseMode.VT200)
            1002 -> flag(mouseMode == MouseMode.CELL_MOTION)
            1003 -> flag(mouseMode == MouseMode.ALL_MOTION)
            1004 -> flag(isFocusReportingMode)
            1005 -> flag(isMouseUtf8)
            1006 -> flag(isMouseSgr)
            1015 -> flag(isMouseUrxvt)
            2004 -> flag(isBracketedPasteMode)
            2026 -> flag(syncUpdateSinceMs >= 0L)
            else -> 0
        }
        onSendResponse?.invoke("\u001b[?$mode;$status\$y")
    }

    private fun handleSgr() {
        if (csiParams.isEmpty()) {
            buffer.currentFg = TerminalColor.DEFAULT_FG
            buffer.currentBg = TerminalColor.DEFAULT_BG
            buffer.currentStyle = 0
            return
        }

        var i = 0
        while (i < csiParams.size) {
            when (val code = csiParams[i]) {
                0 -> {
                    buffer.currentFg = TerminalColor.DEFAULT_FG
                    buffer.currentBg = TerminalColor.DEFAULT_BG
                    buffer.currentStyle = 0
                }
                1 -> buffer.currentStyle = (buffer.currentStyle.toInt() or 1).toByte()
                4 -> buffer.currentStyle = (buffer.currentStyle.toInt() or 2).toByte()
                7 -> buffer.currentStyle = (buffer.currentStyle.toInt() or 4).toByte()
                22 -> buffer.currentStyle = (buffer.currentStyle.toInt() and 1.inv()).toByte()
                24 -> buffer.currentStyle = (buffer.currentStyle.toInt() and 2.inv()).toByte()
                27 -> buffer.currentStyle = (buffer.currentStyle.toInt() and 4.inv()).toByte()
                in 30..37 -> buffer.currentFg = TerminalColor.ANSI_COLORS[code - 30]
                38 -> {
                    if (i + 2 < csiParams.size && csiParams[i + 1] == 5) {
                        val colorIdx = csiParams[i + 2].coerceIn(0, 255)
                        buffer.currentFg = TerminalColor.COLOR_PALETTE[colorIdx]
                        i += 2
                    } else if (i + 4 < csiParams.size && csiParams[i + 1] == 2) {
                        val r = csiParams[i + 2].coerceIn(0, 255)
                        val g = csiParams[i + 3].coerceIn(0, 255)
                        val b = csiParams[i + 4].coerceIn(0, 255)
                        buffer.currentFg = Color.rgb(r, g, b)
                        i += 4
                    }
                }
                39 -> buffer.currentFg = TerminalColor.DEFAULT_FG
                in 40..47 -> buffer.currentBg = TerminalColor.ANSI_COLORS[code - 40]
                48 -> {
                    if (i + 2 < csiParams.size && csiParams[i + 1] == 5) {
                        val colorIdx = csiParams[i + 2].coerceIn(0, 255)
                        buffer.currentBg = TerminalColor.COLOR_PALETTE[colorIdx]
                        i += 2
                    } else if (i + 4 < csiParams.size && csiParams[i + 1] == 2) {
                        val r = csiParams[i + 2].coerceIn(0, 255)
                        val g = csiParams[i + 3].coerceIn(0, 255)
                        val b = csiParams[i + 4].coerceIn(0, 255)
                        buffer.currentBg = Color.rgb(r, g, b)
                        i += 4
                    }
                }
                49 -> buffer.currentBg = TerminalColor.DEFAULT_BG
                in 90..97 -> buffer.currentFg = TerminalColor.ANSI_COLORS[code - 90 + 8]
                in 100..107 -> buffer.currentBg = TerminalColor.ANSI_COLORS[code - 100 + 8]
            }
            i++
        }
    }

    private fun handleOsc(c: Char) {
        if (c == '\u0007' || (c == '\\' && oscBuffer.endsWith("\u001b"))) {
            val fullStr = oscBuffer.toString().removeSuffix("\u001b")
            val overflow = oscOverflow
            oscOverflow = false
            state = State.NORMAL
            dispatchOsc(fullStr, overflow)
        } else {
            val limit = if (oscBuffer.startsWith("52;")) MAX_OSC52_CHARS else MAX_OSC_CHARS
            if (oscBuffer.length < limit) {
                oscBuffer.append(c)
            } else {
                oscOverflow = true
            }
        }
    }

    private fun dispatchOsc(full: String, overflow: Boolean) {
        val sep = full.indexOf(';')
        if (sep < 0) return
        val arg = full.substring(sep + 1)
        when (full.substring(0, sep)) {
            "0", "2" -> onTitleChange?.invoke(arg)

            "8" -> if (!overflow) handleHyperlink(arg)
            "52" -> if (!overflow) handleClipboard(arg)
            "133" -> handleShellIntegration(arg)
        }
    }

    private fun handleHyperlink(arg: String) {
        val idx = arg.indexOf(';')
        if (idx < 0) return
        val uri = arg.substring(idx + 1)
        buffer.currentLink = if (uri.isEmpty() || uri.length > MAX_LINK_CHARS) {
            null
        } else if (uri.all { it.code in 0x21..0x7e }) {
            uri
        } else {
            null
        }
    }

    private fun handleClipboard(arg: String) {
        val idx = arg.indexOf(';')
        if (idx < 0) return
        val data = arg.substring(idx + 1)
        if (data.isEmpty() || data == "?") return
        val bytes = Base64Lenient.decode(data, MAX_OSC52_BYTES) ?: return
        if (bytes.isEmpty()) return
        onClipboardSet?.invoke(String(bytes, StandardCharsets.UTF_8))
    }

    private fun handleShellIntegration(arg: String) {
        when (arg.firstOrNull()) {
            'C' -> commandStartMs = monotonicMs()
            'D' -> {
                buffer.currentLink = null
                val start = commandStartMs
                commandStartMs = -1L
                if (start >= 0L) {
                    val exit = arg.split(';').getOrNull(1)?.toIntOrNull()
                    onCommandFinished?.invoke((monotonicMs() - start).coerceAtLeast(0L), exit)
                }
            }
        }
    }
}
