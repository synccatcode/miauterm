package org.cortex.terminal.emulator

import java.util.ArrayDeque

class TerminalBuffer(var rows: Int, var cols: Int, private val maxHistory: Int = 5000) {
    val history = ArrayDeque<TerminalRow>()
    private var historyGeneration = 0
    private var historyArrayCache: Array<TerminalRow>? = null
    private var historyArrayCacheSize = -1
    private var historyArrayCacheGeneration = -1

    private fun getHistoryRow(index: Int): TerminalRow? {
        if (index !in 0 until history.size) return null
        var cache = historyArrayCache
        if (cache == null || historyArrayCacheSize != history.size || historyArrayCacheGeneration != historyGeneration) {
            cache = history.toTypedArray()
            historyArrayCache = cache
            historyArrayCacheSize = history.size
            historyArrayCacheGeneration = historyGeneration
        }
        return if (index in cache.indices) cache[index] else null
    }
    var screen = Array(rows) { TerminalRow(cols) }
        private set

    private var screenGeneration = 0

    var cursorRow = 0
    var cursorCol = 0
    var isCursorVisible = true

    var scrollTop = 0
    var scrollBottom = rows - 1

    var currentFg = TerminalColor.DEFAULT_FG
    var currentBg = TerminalColor.DEFAULT_BG
    var currentStyle: Byte = 0

    var currentLink: String? = null

    private var alternateScreen: Array<TerminalRow>? = null
    private var savedCursorRow = 0
    private var savedCursorCol = 0
    var isAlternate = false
        private set
    var isWrapPending = false

    var reflowOnResize = true

    fun remapColors(remap: TerminalColor.Remap) {
        for (row in screen) row.remapColors(remap)
        alternateScreen?.forEach { it.remapColors(remap) }
        for (row in history) row.remapColors(remap)
        currentFg = remap.mapFg(currentFg)
        currentBg = remap.mapBg(currentBg)
    }

    fun reset() {
        alternateScreen = null
        isAlternate = false
        screen = Array(rows) { TerminalRow(cols) }
        screenGeneration++
        cursorRow = 0
        cursorCol = 0
        savedCursorRow = 0
        savedCursorCol = 0
        scrollTop = 0
        scrollBottom = rows - 1
        currentFg = TerminalColor.DEFAULT_FG
        currentBg = TerminalColor.DEFAULT_BG
        currentStyle = 0
        currentLink = null
        isCursorVisible = true
        isWrapPending = false
        clearSearchHighlights()
        clearUrlHighlights()
    }

    fun resize(newRows: Int, newCols: Int) {
        if (newRows == rows && newCols == cols) return
        if (newRows <= 0 || newCols <= 0) return
        if (newCols != cols && !isAlternate && reflowOnResize) {
            reflow(newRows, newCols)
            return
        }

        val newScreen = Array(newRows) { TerminalRow(newCols) }

        if (newRows < rows) {

            val linesToShift = if (cursorRow >= newRows) cursorRow - newRows + 1 else 0

            if (!isAlternate && linesToShift > 0) {
                for (r in 0 until linesToShift) {
                    val hRow = TerminalRow(cols)
                    hRow.copyFrom(screen[r])
                    history.addLast(hRow)
                    if (history.size > maxHistory) {
                        history.removeFirst()
                    }
                }
                historyGeneration++
                historyArrayCache = null
            }

            for (r in 0 until newRows) {
                val srcR = r + linesToShift
                if (srcR in 0 until rows) {
                    newScreen[r].copyFrom(screen[srcR])
                }
            }

            cursorRow = (cursorRow - linesToShift).coerceIn(0, newRows - 1)
        } else {

            val linesToAdd = newRows - rows
            val restoredCount = if (!isAlternate) minOf(linesToAdd, history.size) else 0

            if (restoredCount > 0) {
                for (r in 0 until restoredCount) {
                    val hRow = history.removeLast()
                    newScreen[restoredCount - 1 - r].copyFrom(hRow)
                }
                historyGeneration++
                historyArrayCache = null
            }

            for (r in 0 until rows) {
                newScreen[r + restoredCount].copyFrom(screen[r])
            }

            cursorRow = (cursorRow + restoredCount).coerceIn(0, newRows - 1)
        }

        if (isAlternate && alternateScreen != null) {
            val oldAlt = alternateScreen!!
            val newAlt = Array(newRows) { TerminalRow(newCols) }
            val minR = minOf(oldAlt.size, newRows)
            for (r in 0 until minR) {
                newAlt[r].copyFrom(oldAlt[r])
            }
            alternateScreen = newAlt
        }

        screen = newScreen
        screenGeneration++
        rows = newRows
        cols = newCols
        scrollTop = 0
        scrollBottom = rows - 1
        cursorCol = cursorCol.coerceIn(0, cols - 1)
    }

    private fun isBlankCell(row: TerminalRow, c: Int): Boolean =
        row.chars[c] == ' ' && row.bgColors[c] == TerminalColor.DEFAULT_BG &&
            (row.styles[c].toInt() and 6) == 0 && row.linkAt(c) == null

    private fun contentLength(row: TerminalRow): Int {
        var n = row.cols
        while (n > 0 && isBlankCell(row, n - 1)) n--
        return n
    }

    private fun reflow(newRows: Int, newCols: Int) {
        var lastRow = cursorRow.coerceIn(0, rows - 1)
        for (r in rows - 1 downTo lastRow + 1) {
            if (contentLength(screen[r]) > 0) {
                lastRow = r
                break
            }
        }
        val src = ArrayList<TerminalRow>(history.size + lastRow + 1)
        src.addAll(history)
        for (r in 0..lastRow) src.add(screen[r])
        val cursorSrc = history.size + cursorRow.coerceIn(0, rows - 1)
        val topSrc = history.size

        val out = ArrayList<TerminalRow>(src.size + 8)
        var newCursorIndex = 0
        var newCursorCol = 0
        var newWrapPending = false
        var newTopIndex = 0
        var i = 0
        while (i < src.size) {

            var j = i
            while (j < src.size - 1 && src[j].isWrapped) j++

            val segment = IntArray(j - i + 1) { k -> if (i + k < j) src[i + k].cols else contentLength(src[i + k]) }
            var length = 0
            var cursorOffset = -1
            var topOffset = -1
            for (k in i..j) {
                if (k == topSrc) topOffset = length
                if (k == cursorSrc) cursorOffset = length + cursorCol.coerceIn(0, src[k].cols - 1)
                length += segment[k - i]
            }

            var cursorPos = -1
            var wrapPending = false
            if (cursorOffset >= 0) {
                if (isWrapPending) {
                    val next = cursorOffset + 1
                    if (next % newCols == 0) {
                        cursorPos = cursorOffset
                        wrapPending = true
                    } else {
                        cursorPos = next
                    }
                } else {
                    cursorPos = cursorOffset
                }
            }
            var needed = maxOf(1, (length + newCols - 1) / newCols)
            if (cursorPos >= 0) needed = maxOf(needed, cursorPos / newCols + 1)

            val base = out.size
            var srcRow = i
            var srcCol = 0
            for (n in 0 until needed) {
                val row = TerminalRow(newCols)
                var c = 0
                var copied = n * newCols
                while (c < newCols && copied < length) {

                    while (srcCol >= segment[srcRow - i]) {
                        srcRow++
                        srcCol = 0
                    }
                    val from = src[srcRow]
                    val ch = from.chars[srcCol]
                    if (ch == TerminalRow.ASTRAL) {
                        row.setAstral(c, from.astralAt(srcCol), from.fgColors[srcCol], from.bgColors[srcCol], from.styles[srcCol], from.linkAt(srcCol))
                    } else {
                        row.setChar(c, ch, from.fgColors[srcCol], from.bgColors[srcCol], from.styles[srcCol], from.linkAt(srcCol))
                    }
                    c++
                    srcCol++
                    copied++
                }
                row.isWrapped = n < needed - 1
                out.add(row)
            }
            if (cursorPos >= 0) {
                newCursorIndex = base + cursorPos / newCols
                newCursorCol = cursorPos % newCols
                newWrapPending = wrapPending
            }
            if (topOffset >= 0) newTopIndex = base + topOffset / newCols
            i = j + 1
        }

        var screenStart = minOf(newTopIndex, newCursorIndex)
        if (newCursorIndex - screenStart >= newRows) screenStart = newCursorIndex - newRows + 1

        if (out.size - screenStart > newRows) {
            screenStart = maxOf(screenStart, minOf(newCursorIndex, out.size - newRows))
        }

        history.clear()
        for (k in 0 until screenStart) history.addLast(out[k])
        while (history.size > maxHistory) history.removeFirst()
        historyGeneration++
        historyArrayCache = null

        screen = Array(newRows) { k ->
            val idx = screenStart + k
            if (idx < out.size) out[idx] else TerminalRow(newCols)
        }
        screenGeneration++
        rows = newRows
        cols = newCols
        scrollTop = 0
        scrollBottom = rows - 1
        cursorRow = (newCursorIndex - screenStart).coerceIn(0, rows - 1)
        cursorCol = newCursorCol.coerceIn(0, cols - 1)
        isWrapPending = newWrapPending
    }

    fun useAlternateScreen(enable: Boolean) {
        if (enable && !isAlternate) {
            alternateScreen = screen
            screen = Array(rows) { TerminalRow(cols) }
            screenGeneration++
            savedCursorRow = cursorRow
            savedCursorCol = cursorCol
            cursorRow = 0
            cursorCol = 0
            scrollTop = 0
            scrollBottom = rows - 1
            isWrapPending = false
            isAlternate = true
        } else if (!enable && isAlternate) {
            alternateScreen?.let { screen = it; screenGeneration++ }
            alternateScreen = null
            cursorRow = savedCursorRow.coerceIn(0, rows - 1)
            cursorCol = savedCursorCol.coerceIn(0, cols - 1)
            scrollTop = 0
            scrollBottom = rows - 1
            isWrapPending = false
            isAlternate = false
        }
    }

    var wideCharacters = true

    fun writeChar(c: Char) {
        if (wideCharacters && c.code >= CharWidth.FIRST_NOT_NARROW) {
            writeRun(charArrayOf(c), 0, 1)
        } else {
            writeCell(c)
        }
    }

    private fun writeCell(c: Char) {
        if (isWrapPending) {
            if (cursorRow in 0 until rows) {
                screen[cursorRow].isWrapped = true
            }
            newLine()
            cursorCol = 0
            isWrapPending = false
        }
        val r = cursorRow.coerceIn(0, rows - 1)
        screen[r].setChar(cursorCol, c, currentFg, currentBg, currentStyle, currentLink)
        if (cursorCol >= cols - 1) {
            isWrapPending = true
        } else {
            cursorCol++
        }
    }

    fun writeRun(src: CharArray, start: Int, end: Int) {
        if (!wideCharacters) {
            writeNarrow(src, start, end)
            return
        }
        var i = start
        while (i < end) {
            var j = i
            while (j < end && src[j].code < CharWidth.FIRST_NOT_NARROW) j++
            if (j > i) {
                writeNarrow(src, i, j)
                i = j
            } else {
                i = writeOther(src, i, end)
            }
        }
    }

    private fun writeNarrow(src: CharArray, start: Int, end: Int) {
        var i = start
        while (i < end) {
            if (isWrapPending) {
                if (cursorRow in 0 until rows) {
                    screen[cursorRow].isWrapped = true
                }
                newLine()
                cursorCol = 0
                isWrapPending = false
            }
            val space = cols - cursorCol
            if (space <= 1) {
                writeCell(src[i++])
                continue
            }
            val n = minOf(end - i, space)
            val r = cursorRow.coerceIn(0, rows - 1)
            screen[r].setChars(cursorCol, src, i, n, currentFg, currentBg, currentStyle, currentLink)
            i += n
            if (cursorCol + n - 1 >= cols - 1) {
                cursorCol = cols - 1
                isWrapPending = true
            } else {
                cursorCol += n
            }
        }
    }

    private fun writeOther(src: CharArray, start: Int, end: Int): Int {
        val c = src[start]
        if (c == TerminalRow.ASTRAL) {

            writeNarrow(charArrayOf('\uFFFD'), 0, 1)
            return start + 1
        }
        if (Character.isSurrogate(c)) {
            if (Character.isHighSurrogate(c) && start + 1 < end && Character.isLowSurrogate(src[start + 1])) {

                val cp = Character.toCodePoint(c, src[start + 1])
                when (CharWidth.of(cp)) {
                    2 -> writeWide(c, src[start + 1])
                    1 -> writeAstral(cp)
                }
                return start + 2
            }

            writeNarrow(charArrayOf('\uFFFD'), 0, 1)
            return start + 1
        }
        when (CharWidth.of(c)) {
            0 -> return start + 1
            2 -> {
                writeWide(c, TerminalRow.WIDE_TAIL)
                return start + 1
            }
        }
        var j = start + 1
        while (j < end && !Character.isSurrogate(src[j]) && src[j] != TerminalRow.ASTRAL && CharWidth.of(src[j]) == 1) j++
        writeNarrow(src, start, j)
        return j
    }

    private fun writeAstral(cp: Int) {
        if (isWrapPending) {
            if (cursorRow in 0 until rows) {
                screen[cursorRow].isWrapped = true
            }
            newLine()
            cursorCol = 0
            isWrapPending = false
        }
        val r = cursorRow.coerceIn(0, rows - 1)
        screen[r].setAstral(cursorCol, cp, currentFg, currentBg, currentStyle, currentLink)
        if (cursorCol >= cols - 1) {
            isWrapPending = true
        } else {
            cursorCol++
        }
    }

    private fun writeWide(head: Char, tail: Char) {
        if (cols < 2) return
        if (isWrapPending) {
            if (cursorRow in 0 until rows) {
                screen[cursorRow].isWrapped = true
            }
            newLine()
            cursorCol = 0
            isWrapPending = false
        }
        if (cursorCol >= cols - 1) {

            val r = cursorRow.coerceIn(0, rows - 1)
            screen[r].setChar(cursorCol, TerminalRow.WIDE_TAIL, currentFg, currentBg, 0)
            screen[r].isWrapped = true
            newLine()
            cursorCol = 0
        }
        val r = cursorRow.coerceIn(0, rows - 1)
        screen[r].setWide(cursorCol, head, tail, currentFg, currentBg, currentStyle, currentLink)
        if (cursorCol + 2 >= cols) {
            cursorCol = cols - 1
            isWrapPending = true
        } else {
            cursorCol += 2
        }
    }

    fun carriageReturn() {
        isWrapPending = false
        cursorCol = 0
    }

    fun newLine() {
        isWrapPending = false
        if (cursorRow in scrollTop..scrollBottom) {
            if (cursorRow == scrollBottom) {
                scrollUp(scrollTop, scrollBottom)
            } else {
                cursorRow++
            }
        } else {
            if (cursorRow >= rows - 1) {
                scrollUp(0, rows - 1)
            } else {
                cursorRow++
            }
        }
    }

    fun scrollUp(top: Int, bottom: Int) {
        val t = top.coerceIn(0, rows - 1)
        val b = bottom.coerceIn(t, rows - 1)

        val departing = screen[t]
        var incoming: TerminalRow? = departing

        if (t == 0 && b == rows - 1 && !isAlternate) {
            history.addLast(departing)

            incoming = if (history.size > maxHistory) history.removeFirst().takeIf { it.cols == cols } else null
            historyGeneration++
            historyArrayCache = null
        }

        System.arraycopy(screen, t + 1, screen, t, b - t)
        val bottomRow = incoming ?: TerminalRow(cols)
        bottomRow.clear(currentFg, currentBg)
        screen[b] = bottomRow
        screenGeneration++
    }

    fun scrollDown(top: Int, bottom: Int) {
        val t = top.coerceIn(0, rows - 1)
        val b = bottom.coerceIn(t, rows - 1)
        val departing = screen[b]
        System.arraycopy(screen, t, screen, t + 1, b - t)
        departing.clear(currentFg, currentBg)
        screen[t] = departing
        screenGeneration++
    }

    fun eraseInDisplay(mode: Int) {
        val r = cursorRow.coerceIn(0, rows - 1)
        val c = cursorCol.coerceIn(0, cols - 1)
        when (mode) {
            0 -> {
                screen[r].let { row ->
                    for (col in c until cols) {
                        row.setChar(col, ' ', currentFg, currentBg, 0)
                    }
                }
                for (row in r + 1 until rows) {
                    screen[row].clear(currentFg, currentBg)
                }
            }
            1 -> {
                for (row in 0 until r) {
                    screen[row].clear(currentFg, currentBg)
                }
                screen[r].let { row ->
                    for (col in 0..c) {
                        row.setChar(col, ' ', currentFg, currentBg, 0)
                    }
                }
            }
            2, 3 -> {
                for (row in 0 until rows) {
                    screen[row].clear(currentFg, currentBg)
                }
                if (mode == 3 && !isAlternate) {
                    history.clear()
                    historyGeneration++
                    historyArrayCache = null
                }
            }
        }
    }

    fun eraseInLine(mode: Int) {
        isWrapPending = false
        val r = cursorRow.coerceIn(0, rows - 1)
        val row = screen[r]
        when (mode) {
            0 -> {
                for (c in cursorCol.coerceIn(0, cols - 1) until cols) {
                    row.setChar(c, ' ', currentFg, currentBg, 0)
                }
                row.isWrapped = false
            }
            1 -> {
                for (c in 0..cursorCol.coerceAtMost(cols - 1)) {
                    row.setChar(c, ' ', currentFg, currentBg, 0)
                }
            }
            2 -> {
                row.clear(currentFg, currentBg)
            }
        }
    }

    fun getVisibleRow(screenRowIndex: Int, scrollOffset: Int): TerminalRow {
        if (isAlternate) {
            return if (screenRowIndex in 0 until rows) screen[screenRowIndex] else TerminalRow(cols)
        }
        val totalHistory = history.size
        val effectiveIndex = screenRowIndex - scrollOffset
        return if (effectiveIndex < 0) {
            val historyIndex = totalHistory + effectiveIndex
            getHistoryRow(historyIndex) ?: TerminalRow(cols)
        } else if (effectiveIndex in 0 until rows) {
            screen[effectiveIndex]
        } else {
            TerminalRow(cols)
        }
    }

    data class SearchHit(val row: Int, val startCol: Int, val endCol: Int)

    fun clearSearchHighlights() {
        for (r in history) r.clearSearchFlags()
        for (r in screen) r.clearSearchFlags()
        alternateScreen?.forEach { it.clearSearchFlags() }
    }

    fun clearUrlHighlights() {
        for (r in history) r.clearUrlFlags()
        for (r in screen) r.clearUrlFlags()
        alternateScreen?.forEach { it.clearUrlFlags() }
    }

    fun lastRowsWithIndex(count: Int): List<Pair<Int, TerminalRow>> {
        val screenCount = minOf(rows, screen.size)
        val fromScreen = minOf(count, screenCount).coerceAtLeast(0)
        val fromHistory = (count - fromScreen).coerceIn(0, history.size)
        val out = ArrayList<Pair<Int, TerminalRow>>(fromHistory + fromScreen)
        val newest = history.descendingIterator()
        for (k in 1..fromHistory) out.add(Pair(-k, newest.next()))
        out.reverse()
        for (i in screenCount - fromScreen until screenCount) out.add(Pair(i, screen[i]))
        return out
    }

    private var rowsCache: ArrayList<Pair<Int, TerminalRow>> = ArrayList()
    private var cachedHistorySize = -1
    private var cachedHistoryGeneration = -1
    private var cachedScreenRows = -1
    private var cachedGeneration = -1

    private fun rebuildRowsCacheIfNeeded() {
        val screenCount = minOf(rows, screen.size)
        if (cachedHistorySize == history.size &&
            cachedHistoryGeneration == historyGeneration &&
            cachedScreenRows == screenCount &&
            cachedGeneration == screenGeneration) return
        val needed = history.size + screenCount
        if (rowsCache.size != needed) {
            rowsCache = ArrayList(needed)
        } else {
            rowsCache.clear()
        }
        var idx = -history.size
        val it = history.iterator()
        while (it.hasNext()) {
            rowsCache.add(Pair(idx, it.next()))
            idx++
        }
        for (i in 0 until screenCount) {
            rowsCache.add(Pair(i, screen[i]))
        }
        cachedHistorySize = history.size
        cachedHistoryGeneration = historyGeneration
        cachedScreenRows = screenCount
        cachedGeneration = screenGeneration
    }

    fun allRowsWithIndex(): List<Pair<Int, TerminalRow>> {
        rebuildRowsCacheIfNeeded()
        return rowsCache
    }

    fun rowForIndex(bufferRow: Int): TerminalRow? {
        if (bufferRow < 0 && bufferRow < -history.size) return null
        if (bufferRow >= rows) return null
        rebuildRowsCacheIfNeeded()
        var lo = 0
        var hi = rowsCache.size - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val key = rowsCache[mid].first
            when {
                key == bufferRow -> return rowsCache[mid].second
                key < bufferRow -> lo = mid + 1
                else -> hi = mid - 1
            }
        }
        return null
    }

    fun markSearchHits(hits: List<SearchHit>, currentIndex: Int) {
        clearSearchHighlights()
        var lastRowIndex = Int.MIN_VALUE
        var cachedRow: TerminalRow? = null
        for ((idx, hit) in hits.withIndex()) {
            if (hit.row != lastRowIndex) {
                cachedRow = rowForIndex(hit.row)
                lastRowIndex = hit.row
            }
            val row = cachedRow ?: continue
            for (c in hit.startCol..hit.endCol) {
                if (c in 0 until row.cols) {
                    row.isSearchMatch[c] = true
                    if (idx == currentIndex) row.isSearchCurrent[c] = true
                }
            }
        }
    }

    fun transcript(maxLines: Int): String {
        val main = (if (isAlternate) alternateScreen else null) ?: screen
        val lines = ArrayList<String>()
        val line = StringBuilder()
        fun add(row: TerminalRow) {
            if (row.isWrapped) {
                row.appendText(line, 0, row.cols)
            } else {
                line.append(row.getText())
                lines.add(line.toString())
                line.setLength(0)
            }
        }
        for (row in history) add(row)
        for (row in main) add(row)
        if (line.isNotEmpty()) lines.add(line.toString())
        while (lines.isNotEmpty() && lines[lines.size - 1].isBlank()) lines.removeAt(lines.size - 1)
        return lines.subList(maxOf(0, lines.size - maxLines), lines.size).joinToString("\n")
    }

    fun getSelectedText(r1: Int, c1: Int, r2: Int, c2: Int): String {
        val (startRow, startCol, endRow, endCol) = if (r1 < r2 || (r1 == r2 && c1 <= c2)) {
            listOf(r1, c1, r2, c2)
        } else {
            listOf(r2, c2, r1, c1)
        }
        val sb = StringBuilder()
        for (r in startRow..endRow) {
            val row = (if (r < 0) {
                val hIdx = history.size + r
                getHistoryRow(hIdx)
            } else if (r in 0 until rows) {
                screen[r]
            } else null) ?: continue

            var cStart = if (r == startRow) startCol.coerceIn(0, row.cols - 1) else 0
            var cEnd = if (r == endRow) endCol.coerceIn(0, row.cols - 1) else row.cols - 1

            if (row.hasWide) {
                if (row.isTail(cStart)) cStart--
                if (row.isWideHead(cEnd)) cEnd++
            }

            if (row.isWrapped) {
                row.appendText(sb, cStart, cEnd + 1)
            } else {
                var lastChar = cEnd
                while (lastChar >= cStart && row.chars[lastChar] == ' ') {
                    lastChar--
                }
                row.appendText(sb, cStart, lastChar + 1)
                if (r != endRow) {
                    sb.append("\n")
                }
            }
        }
        return sb.toString()
    }
}
