package org.cortex.terminal.api

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

class ApiRequest(val token: String, val command: String, val args: List<String>, val stdin: String?)

object ApiProtocol {
    const val MAGIC = "MIAUTERM-CMD 1"
    private const val MAX_LINE = 512
    const val MAX_ARGS = 64
    const val MAX_ARG_BYTES = 64 * 1024
    const val MAX_STDIN_BYTES = 1024 * 1024

    class ProtocolException(message: String) : IOException(message)

    class Unauthorized : IOException("the request was not authorized")

    fun read(input: InputStream, authorize: (String) -> Boolean = { true }): ApiRequest {
        if (readLine(input) != MAGIC) throw ProtocolException("not a Miauterm command request")
        val token = readLine(input)
        if (!authorize(token)) throw Unauthorized()
        val command = readLine(input)
        if (command.isEmpty() || command.length > 64) throw ProtocolException("bad command")
        val count = readLine(input).toIntOrNull() ?: throw ProtocolException("bad argument count")
        if (count < 0 || count > MAX_ARGS) throw ProtocolException("too many arguments")
        val args = ArrayList<String>(count)
        repeat(count) {
            args.add(readBlock(input, readLength(input, MAX_ARG_BYTES)))
        }
        val stdinLength = readLength(input, MAX_STDIN_BYTES, allowNone = true)
        val stdin = if (stdinLength < 0) null else readBlock(input, stdinLength)
        return ApiRequest(token, command, args, stdin)
    }

    fun writeResponse(out: OutputStream, status: Int, output: String) {
        out.write("$status\n".toByteArray(Charsets.UTF_8))
        out.write(output.toByteArray(Charsets.UTF_8))
        out.flush()
    }

    fun tokenMatches(given: String, expected: String): Boolean =
        MessageDigest.isEqual(given.toByteArray(Charsets.UTF_8), expected.toByteArray(Charsets.UTF_8))

    private fun readLine(input: InputStream): String {
        val buf = ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b < 0) throw ProtocolException("request ends early")
            if (b == '\n'.code) break
            if (buf.size() >= MAX_LINE) throw ProtocolException("line too long")
            buf.write(b)
        }
        return buf.toString("UTF-8")
    }

    private fun readLength(input: InputStream, max: Int, allowNone: Boolean = false): Int {
        val line = readLine(input)
        if (allowNone && line == "-1") return -1
        val n = line.toIntOrNull() ?: throw ProtocolException("bad length")
        if (n < 0 || n > max) throw ProtocolException("too much data")
        return n
    }

    private fun readBlock(input: InputStream, length: Int): String {
        val bytes = ByteArray(length)
        var off = 0
        while (off < length) {
            val n = input.read(bytes, off, length - off)
            if (n < 0) throw ProtocolException("request ends early")
            off += n
        }
        return String(bytes, Charsets.UTF_8)
    }
}

class ApiArgs private constructor(private val values: Map<String, String>, private val flags: Set<String>, val operands: List<String>) {

    fun value(vararg names: String): String? = names.firstNotNullOfOrNull { values[it] }

    fun flag(vararg names: String): Boolean = names.any { it in flags }

    companion object {

        fun parse(args: List<String>, withValue: Set<String> = emptySet(), flagNames: Set<String> = emptySet()): ApiArgs {
            val values = HashMap<String, String>()
            val flags = HashSet<String>()
            val operands = ArrayList<String>()
            var i = 0
            while (i < args.size) {
                val a = args[i]
                when {
                    a == "--" -> {
                        operands.addAll(args.subList(i + 1, args.size))
                        break
                    }
                    a.startsWith("--") && a.contains('=') -> {
                        val name = a.substringBefore('=')
                        if (name !in withValue) throw IllegalArgumentException("unknown option $name")
                        values[name] = a.substringAfter('=')
                    }
                    a in withValue -> {
                        if (i + 1 >= args.size) throw IllegalArgumentException("$a needs a value")
                        values[a] = args[i + 1]
                        i++
                    }
                    a in flagNames -> flags.add(a)
                    a.length > 1 && a.startsWith("-") -> throw IllegalArgumentException("unknown option $a")
                    else -> operands.add(a)
                }
                i++
            }
            return ApiArgs(values, flags, operands)
        }
    }
}

object ApiPaths {

    private val hostPrefixes = listOf(
        "/sdcard", "/storage", "/data", "/system", "/system_ext", "/apex", "/vendor", "/product", "/odm", "/oem"
    )

    fun toAndroid(rootfs: String, path: String): String {
        val p = if (path.startsWith("/")) path else "/$path"
        if (hostPrefixes.any { p == it || p.startsWith("$it/") }) return p
        return rootfs.trimEnd('/') + p
    }

    fun isUri(target: String): Boolean =
        Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://").containsMatchIn(target) ||
            listOf("mailto:", "tel:", "sms:", "geo:", "market:", "intent:").any { target.startsWith(it) }
}

object Json {
    fun obj(vararg fields: Pair<String, Any?>): String =
        fields.joinToString(prefix = "{\n", separator = ",\n", postfix = "\n}\n") { (k, v) -> "  ${str(k)}: ${value(v)}" }

    private fun value(v: Any?): String = when (v) {
        null -> "null"
        is Boolean, is Int, is Long -> v.toString()
        is Float -> if (v.isFinite()) v.toString() else "null"
        is Double -> if (v.isFinite()) v.toString() else "null"
        else -> str(v.toString())
    }

    fun str(s: String): String {
        val sb = StringBuilder("\"")
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (c < ' ') sb.append(String.format("\\u%04x", c.code)) else sb.append(c)
            }
        }
        return sb.append('"').toString()
    }
}
