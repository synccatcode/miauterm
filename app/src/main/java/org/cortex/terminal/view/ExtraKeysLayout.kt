package org.cortex.terminal.view

object ExtraKeysLayout {
    const val MAX_ROWS = 4
    const val MAX_KEYS = 48

    enum class Special(val label: String) {
        ESC("ESC"), TAB("⇆"), CTRL("CTRL"), ALT("ALT"),
        UP("↑"), DOWN("↓"), LEFT("←"), RIGHT("→"),
        HOME("HOME"), END("END"), PGUP("PGUP"), PGDN("PGDN"),
        INS("INS"), DEL("DEL"), BKSP("⌫"), ENTER("↵"), SPACE("␣"),
        MENU("☰"), FIND("FIND"), PASTE("PASTE"), KEYBOARD("⌨"),
        F1("F1"), F2("F2"), F3("F3"), F4("F4"), F5("F5"), F6("F6"),
        F7("F7"), F8("F8"), F9("F9"), F10("F10"), F11("F11"), F12("F12");

        val repeats: Boolean get() = this == UP || this == DOWN || this == LEFT || this == RIGHT || this == BKSP || this == DEL
    }

    sealed class Key {
        abstract val label: String
    }

    data class SpecialKey(val special: Special, override val label: String = special.label) : Key()

    data class TextKey(val text: String, override val label: String = text) : Key()

    data class Result(val rows: List<List<Key>>, val error: String? = null)

    val DEFAULT_TEXT = """
        |[
        |  ["ESC", "/", "-", "HOME", "UP", "END", "PGUP"],
        |  ["TAB", "CTRL", "ALT", "LEFT", "DOWN", "RIGHT", "PGDN"]
        |]
        |""".trimMargin()

    val DEFAULT_ROWS: List<List<Key>> by lazy { parse(DEFAULT_TEXT).rows }

    fun parse(text: String): Result {
        val nestedArray = nestedArrayContent(text)
        if (startsNestedArray(nestedArray)) return parseNestedArray(nestedArray)
        val rows = mutableListOf<List<Key>>()
        text.lines().forEachIndexed { index, rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEachIndexed
            val where = "line ${index + 1}"
            val tokens = try {
                tokenize(line)
            } catch (e: IllegalArgumentException) {
                return failed("$where: ${e.message}")
            }
            val keys = mutableListOf<Key>()
            for (token in tokens) {
                keys += try {
                    keyOf(token)
                } catch (e: IllegalArgumentException) {
                    return failed("$where: ${e.message}")
                }
            }
            if (keys.size > MAX_KEYS) return failed("$where: ${keys.size} keys, at most $MAX_KEYS fit in a row")
            rows += keys
        }
        if (rows.isEmpty()) return failed("no keys")
        if (rows.size > MAX_ROWS) return failed("${rows.size} rows, at most $MAX_ROWS")
        return Result(rows)
    }

    fun isNestedArrayFormat(text: String): Boolean = startsNestedArray(nestedArrayContent(text))

    fun appendKey(text: String, value: String, textKey: Boolean): String {
        val parsed = parse(text)
        require(parsed.error == null) { parsed.error ?: "invalid layout" }
        val lastRow = parsed.rows.last()
        require(lastRow.size < MAX_KEYS) { "at most $MAX_KEYS keys fit in a row" }

        val nestedArray = isNestedArrayFormat(text)
        val token = if (nestedArray || textKey) {
            "\"${escapeNestedArrayString(value)}\""
        } else {
            value
        }
        val updated = if (nestedArray) {
            val rowEnd = lastNestedRowEnd(text)
                ?: throw IllegalArgumentException("nested array is incomplete")
            val before = text.substring(0, rowEnd).trimEnd()
            val separator = if (before.lastOrNull() == ',') " " else ", "
            "$before$separator$token${text.substring(rowEnd)}"
        } else {
            val lines = text.lines().toMutableList()
            val lastKeyLine = lines.indexOfLast { it.trim().isNotEmpty() && !it.trim().startsWith("#") }
            if (lastKeyLine >= 0) lines[lastKeyLine] = lines[lastKeyLine].trimEnd() + " " + token
            else lines += token
            lines.joinToString("\n")
        }
        val updatedResult = parse(updated)
        require(updatedResult.error == null) { updatedResult.error ?: "invalid layout" }
        return updated
    }

    private fun lastNestedRowEnd(text: String): Int? {
        var depth = 0
        var lastRowEnd: Int? = null
        var quote: Char? = null
        var escaped = false
        var comment = false
        var lineStart = 0

        text.forEachIndexed { index, char ->
            if (char == '\n') {
                comment = false
                lineStart = index + 1
                return@forEachIndexed
            }
            if (comment) return@forEachIndexed
            if (quote != null) {
                if (escaped) {
                    escaped = false
                } else if (char == '\\') {
                    escaped = true
                } else if (char == quote) {
                    quote = null
                }
                return@forEachIndexed
            }
            if (char == '#' && text.substring(lineStart, index).isBlank()) {
                comment = true
                return@forEachIndexed
            }
            if (char == '"' || char == '\'') {
                quote = char
            } else if (char == '[') {
                depth++
            } else if (char == ']') {
                if (depth == 2) lastRowEnd = index
                depth--
            }
        }
        return lastRowEnd
    }

    private fun escapeNestedArrayString(value: String): String = buildString {
        value.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\t' -> append("\\t")
                '\r' -> append("\\r")
                '\u001b' -> append("\\e")
                else -> append(char)
            }
        }
    }

    private fun startsNestedArray(value: String): Boolean = Regex("^\\[\\s*\\[").containsMatchIn(value)

    private fun nestedArrayContent(text: String): String =
        text.lineSequence()
            .filterNot { it.trimStart().startsWith("#") }
            .joinToString("\n")
            .trim()
            .removePrefix("extra-keys").trimStart().removePrefix("=").trim()

    private fun parseNestedArray(value: String): Result {
        var index = 0
        val rows = mutableListOf<List<Key>>()

        fun skipWhitespace() {
            while (index < value.length && value[index].isWhitespace()) index++
        }

        fun expect(char: Char) {
            if (index >= value.length || value[index] != char) {
                throw IllegalArgumentException("invalid nested array")
            }
            index++
        }

        fun readString(): String {
            if (index >= value.length || (value[index] != '"' && value[index] != '\'')) {
                throw IllegalArgumentException("row ${rows.size + 1} must contain quoted keys")
            }
            val quote = value[index++]
            val result = StringBuilder()
            while (index < value.length) {
                val char = value[index++]
                if (char == quote) return result.toString()
                if (char == '\\' && index < value.length) {
                    val escaped = value[index++]
                    result.append(
                        when (escaped) {
                            'n' -> '\n'
                            't' -> '\t'
                            'r' -> '\r'
                            'e' -> '\u001b'
                            '\\', '"', '\'' -> escaped
                            else -> "\\$escaped"
                        }
                    )
                } else {
                    result.append(char)
                }
            }
            throw IllegalArgumentException("a quote is not closed")
        }

        return try {
            skipWhitespace()
            expect('[')
            skipWhitespace()
            while (index < value.length && value[index] != ']') {
                if (rows.size >= MAX_ROWS) throw IllegalArgumentException("layout must have 1 to $MAX_ROWS rows")
                expect('[')
                skipWhitespace()
                val row = mutableListOf<Key>()
                while (true) {
                    if (row.size >= MAX_KEYS) {
                        throw IllegalArgumentException("row ${rows.size + 1} has more than $MAX_KEYS keys")
                    }
                    val token = readString()
                    val special = Special.values().firstOrNull { it.name.equals(token, ignoreCase = true) }
                    row += if (special != null) SpecialKey(special) else TextKey(token, labelFor(token))
                    skipWhitespace()
                    if (index < value.length && value[index] == ',') {
                        index++
                        skipWhitespace()
                        if (index < value.length && value[index] == ']') {
                            index++
                            break
                        }
                    } else {
                        expect(']')
                        break
                    }
                }
                rows += row
                skipWhitespace()
                if (index < value.length && value[index] == ',') {
                    index++
                    skipWhitespace()
                } else if (index >= value.length || value[index] != ']') {
                    throw IllegalArgumentException("invalid nested array")
                }
            }
            expect(']')
            skipWhitespace()
            if (index != value.length || rows.isEmpty()) throw IllegalArgumentException("invalid nested array")
            Result(rows)
        } catch (error: IllegalArgumentException) {
            failed(error.message ?: "invalid nested array")
        }
    }

    private fun failed(error: String) = Result(emptyList(), error)

    private class Token(val text: String, val quoted: Boolean, val label: String?)

    private fun tokenize(line: String): List<Token> {
        val tokens = mutableListOf<Token>()
        var i = 0
        while (i < line.length) {
            if (line[i].isWhitespace()) {
                i++
                continue
            }

            var label: String? = null
            val start = i
            while (i < line.length && !line[i].isWhitespace() && line[i] != '"' && line[i] != '=') i++
            if (i < line.length && line[i] == '=' && i > start && i + 1 < line.length && !line[i + 1].isWhitespace()) {
                label = line.substring(start, i)
                i++
            } else {
                i = start
            }
            if (line[i] == '"') {
                val sb = StringBuilder()
                i++
                var closed = false
                while (i < line.length) {
                    val c = line[i]
                    if (c == '"') {
                        closed = true
                        i++
                        break
                    }
                    if (c == '\\' && i + 1 < line.length) {
                        sb.append(
                            when (val e = line[i + 1]) {
                                'n' -> '\n'
                                't' -> '\t'
                                'e' -> '\u001b'
                                'r' -> '\r'
                                '\\', '"' -> e
                                else -> throw IllegalArgumentException("unknown escape \\$e")
                            }
                        )
                        i += 2
                        continue
                    }
                    sb.append(c)
                    i++
                }
                if (!closed) throw IllegalArgumentException("a quote is not closed")
                if (sb.isEmpty()) throw IllegalArgumentException("empty text \"\"")
                if (i < line.length && !line[i].isWhitespace()) throw IllegalArgumentException("a space is missing after the quote")
                tokens += Token(sb.toString(), quoted = true, label = label)
            } else {
                val s = i
                while (i < line.length && !line[i].isWhitespace()) i++
                tokens += Token(line.substring(s, i), quoted = false, label = label)
            }
        }
        return tokens
    }

    private fun keyOf(token: Token): Key {
        val key = if (token.quoted) {
            TextKey(token.text, labelFor(token.text))
        } else {
            val special = Special.values().firstOrNull { it.name.equals(token.text, ignoreCase = true) }
            when {
                special != null -> SpecialKey(special)
                token.text.any { it.isLetterOrDigit() } && token.text.length > 1 ->
                    throw IllegalArgumentException("unknown key ${token.text} (text goes in quotes: \"${token.text}\")")
                else -> TextKey(token.text)
            }
        }
        val label = token.label ?: return key
        return when (key) {
            is SpecialKey -> key.copy(label = label)
            is TextKey -> key.copy(label = label)
        }
    }

    private fun labelFor(text: String): String {
        val shown = text.replace("\n", "↵").replace("\t", "⇆").replace("\u001b", "⎋").replace("\r", "↵")
        return if (shown.length > 6) shown.take(5) + "…" else shown
    }
}
