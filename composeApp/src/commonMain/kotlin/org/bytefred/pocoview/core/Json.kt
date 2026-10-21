package org.bytefred.pocoview.core

/**
 * Small strict JSON reader: objects become [Map] (insertion order), arrays [List], integers
 * [Long], other numbers [Double], strings [String], true/false [Boolean], null null.
 * Errors are [FormatException]s with the position.
 */
fun parseJson(text: String): Any? {
    val p = JsonParser(text.removePrefix("﻿"))
    val v = p.value()
    p.skipWs()
    if (p.i < text.removePrefix("﻿").length) throw FormatException("Unexpected text after the JSON value at position ${p.i}")
    return v
}

/** JSON string literal for [s]. */
fun jsonString(s: String): String = buildString {
    append('"')
    for (c in s) when (c) {
        '"' -> append("\\\"")
        '\\' -> append("\\\\")
        '\n' -> append("\\n")
        '\r' -> append("\\r")
        '\t' -> append("\\t")
        else -> if (c < ' ') append("\\u" + c.code.toString(16).padStart(4, '0')) else append(c)
    }
    append('"')
}

private class JsonParser(val s: String) {
    var i = 0
    private var depth = 0

    fun skipWs() { while (i < s.length && s[i].isWhitespace()) i++ }

    private fun fail(msg: String): Nothing = throw FormatException("JSON: $msg at position $i")

    fun value(): Any? {
        skipWs()
        if (i >= s.length) fail("unexpected end")
        return when (val c = s[i]) {
            '{' -> nested { obj() }
            '[' -> nested { arr() }
            '"' -> str()
            't' -> literal("true", true)
            'f' -> literal("false", false)
            'n' -> literal("null", null)
            else -> if (c == '-' || c.isDigit()) num() else fail("unexpected character '$c'")
        }
    }

    /** Nesting limit, so deeply nested input fails with a message instead of a stack overflow. */
    private inline fun <T> nested(f: () -> T): T {
        if (++depth > MAX_DEPTH) fail("nested deeper than $MAX_DEPTH levels")
        try { return f() } finally { depth-- }
    }

    private fun literal(word: String, v: Any?): Any? {
        if (!s.startsWith(word, i)) fail("unexpected character '${s[i]}'")
        i += word.length
        return v
    }

    private fun obj(): Map<String, Any?> {
        i++
        val m = LinkedHashMap<String, Any?>()
        skipWs()
        if (s.getOrNull(i) == '}') { i++; return m }
        while (true) {
            skipWs()
            if (s.getOrNull(i) != '"') fail("expected a key")
            val k = str()
            skipWs()
            if (s.getOrNull(i) != ':') fail("expected ':'")
            i++
            if (k in m) fail("duplicate key \"$k\"")
            m[k] = value()
            skipWs()
            when (s.getOrNull(i)) {
                ',' -> i++
                '}' -> { i++; return m }
                else -> fail("expected ',' or '}'")
            }
        }
    }

    private fun arr(): List<Any?> {
        i++
        val l = ArrayList<Any?>()
        skipWs()
        if (s.getOrNull(i) == ']') { i++; return l }
        while (true) {
            l.add(value())
            skipWs()
            when (s.getOrNull(i)) {
                ',' -> i++
                ']' -> { i++; return l }
                else -> fail("expected ',' or ']'")
            }
        }
    }

    private fun str(): String {
        i++
        val b = StringBuilder()
        while (true) {
            if (i >= s.length) fail("unterminated string")
            val c = s[i++]
            when {
                c == '"' -> return b.toString()
                c == '\\' -> {
                    if (i >= s.length) fail("unterminated string")
                    when (val e = s[i++]) {
                        '"', '\\', '/' -> b.append(e)
                        'b' -> b.append('\b')
                        'f' -> b.append('\u000C')
                        'n' -> b.append('\n')
                        'r' -> b.append('\r')
                        't' -> b.append('\t')
                        'u' -> {
                            val hex = if (i + 4 <= s.length) s.substring(i, i + 4) else ""
                            if (hex.length != 4 || !hex.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) fail("bad \\u escape")
                            b.append(hex.toInt(16).toChar())
                            i += 4
                        }
                        else -> fail("bad escape '\\$e'")
                    }
                }
                c < ' ' -> fail("control character in string")
                else -> b.append(c)
            }
        }
    }

    private fun num(): Any {
        val start = i
        if (s[i] == '-') i++
        if (i >= s.length || !s[i].isDigit()) fail("bad number")
        if (s[i] == '0' && i + 1 < s.length && s[i + 1].isDigit()) fail("leading zero")
        while (i < s.length && s[i].isDigit()) i++
        var integer = true
        if (i < s.length && s[i] == '.') {
            integer = false; i++
            while (i < s.length && s[i].isDigit()) i++
        }
        if (i < s.length && (s[i] == 'e' || s[i] == 'E')) {
            integer = false; i++
            if (i < s.length && (s[i] == '+' || s[i] == '-')) i++
            while (i < s.length && s[i].isDigit()) i++
        }
        val t = s.substring(start, i)
        return if (integer) t.toLongOrNull() ?: fail("integer out of range") else t.toDoubleOrNull() ?: fail("bad number")
    }
}

private const val MAX_DEPTH = 64
