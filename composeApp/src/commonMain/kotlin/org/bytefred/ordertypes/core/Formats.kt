package org.bytefred.ordertypes.core

class FormatException(message: String) : Exception(message)

/**
 * Point-set file formats (compatible with pyotlib2):
 *  - `asc` / `psz` / `txt`: a line with n, then n lines `x y [COLOR]`; several sets may
 *    follow each other. A file without the n line (just `x y` lines) is one set.
 *    Lines starting with `#` are comments.
 *  - `json`: `[[x,y],...]` (one set) or `[[[x,y],...],...]` (several sets); several
 *    top-level arrays (pyotlib2 writes one per line) are several sets.
 *  - `b08`/`pb08`, `b16`/`pb16`, `b32`/`pb32`: Graz order type database, unsigned
 *    little-endian x1 y1 x2 y2 ... without delimiters; n is taken from the file name
 *    ([pointCountFromFileName]).
 */
enum class PointFormat(val extension: String, val binaryBytes: Int = 0) {
    ASC("asc"), PSZ("psz"), JSON("json"), PB08("b08", 1), PB16("b16", 2), PB32("b32", 4);

    val isBinary get() = binaryBytes > 0

    companion object {
        fun forFileName(name: String): PointFormat? =
            when (name.substringAfterLast('.', "").lowercase()) {
                "asc", "txt" -> ASC
                "psz" -> PSZ
                "json" -> JSON
                "b08", "pb08" -> PB08
                "b16", "pb16" -> PB16
                "b32", "pb32" -> PB32
                else -> null
            }
    }
}

/**
 * n as encoded in a file name: an explicit `n<digits>` (e.g. `points_n12.b08`, `n10_c1.psz`)
 * wins, otherwise the first 1-2 digit number (Graz style `otypes05.b08` -> 5).
 */
fun pointCountFromFileName(name: String): Int? {
    val base = name.substringAfterLast('/').substringBeforeLast('.')
    Regex("""(?<![A-Za-z])n(\d{1,3})(?!\d)""").find(base)?.let { return it.groupValues[1].toInt() }
    return Regex("""(?<!\d)(\d{1,2})(?!\d)""").find(base)?.value?.toInt()
}

/** File name for saving [n] points in [fmt] under [base], so that binary files can be read back. */
fun saveFileName(base: String, n: Int, fmt: PointFormat): String {
    val b = if (!fmt.isBinary || pointCountFromFileName(base) == n) base
    else base.replace(Regex("""_?n\d{1,3}$"""), "") + "_n$n"
    return "$b.${fmt.extension}"
}

fun readPointSets(name: String, bytes: ByteArray): List<List<Pt>> {
    val fmt = PointFormat.forFileName(name)
        ?: throw FormatException("Unknown file type: $name (use .asc, .psz, .json, .b08, .b16, .b32)")
    val sets = when {
        fmt.isBinary -> {
            val n = pointCountFromFileName(name)?.takeIf { it > 0 }
                ?: throw FormatException("Cannot infer n from the file name '$name' (e.g. otypes05.b08 or points_n12.b08)")
            readBinary(bytes, n, fmt.binaryBytes)
        }
        fmt == PointFormat.JSON -> readJson(bytes.decodeToString())
        else -> readAsc(bytes.decodeToString())
    }
    if (sets.isEmpty()) throw FormatException("No point set found in $name")
    for (s in sets) {
        if (s.any { !it.inRange() })
            throw FormatException("Coordinates exceed ±$COORD_LIMIT (exact arithmetic limit)")
    }
    return sets
}

fun readBinary(bytes: ByteArray, n: Int, width: Int): List<List<Pt>> {
    require(n > 0)
    val per = 2 * n * width
    if (bytes.size % per != 0)
        throw FormatException("File size ${bytes.size} is not a multiple of $per (n=$n, $width byte(s) per coordinate)")
    fun value(off: Int): Long {
        var v = 0L
        for (b in 0 until width) v = v or ((bytes[off + b].toLong() and 0xFF) shl (8 * b))
        return v
    }
    return (0 until bytes.size / per).map { s ->
        List(n) { i ->
            val off = s * per + 2 * i * width
            Pt(value(off), value(off + width))
        }
    }
}

private val SEPARATORS = Regex("""[\s,;]+""")

fun readAsc(text: String): List<List<Pt>> {
    val lines = text.removePrefix("\uFEFF").lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
    if (lines.isEmpty()) return emptyList()
    fun parsePoint(line: String): Pt {
        val t = line.split(SEPARATORS)
        if (t.size < 2) throw FormatException("Expected 'x y', got '$line'")
        val x = t[0].toLongOrNull() ?: throw FormatException("Not an integer: '${t[0]}'")
        val y = t[1].toLongOrNull() ?: throw FormatException("Not an integer: '${t[1]}'")
        return Pt(x, y, t.getOrNull(2))
    }
    val headerFirst = lines[0].split(SEPARATORS).size == 1
    if (!headerFirst) return listOf(lines.map(::parsePoint))
    val sets = ArrayList<List<Pt>>()
    var i = 0
    while (i < lines.size) {
        val n = lines[i].toIntOrNull()?.takeIf { it >= 0 }
            ?: throw FormatException("Expected the point count, got '${lines[i]}'")
        if (i + 1 + n > lines.size)
            throw FormatException("Header says $n points, but only ${lines.size - i - 1} lines follow")
        sets.add(lines.subList(i + 1, i + 1 + n).map(::parsePoint))
        i += n + 1
    }
    return sets
}

fun readJson(text: String): List<List<Pt>> {
    val values = JsonArrays(text.removePrefix("\uFEFF")).parseAll()
    fun point(v: Any): Pt {
        val c = v as? List<*>
        if (c == null || c.size != 2 || c.any { it !is Long }) throw FormatException("Expected a point [x, y], got $v")
        return Pt(c[0] as Long, c[1] as Long)
    }
    fun set(v: Any): List<Pt> = (v as? List<*>)?.filterNotNull()?.map(::point) ?: throw FormatException("Expected a list of points")
    return values.flatMap { top ->
        val l = top as? List<*> ?: throw FormatException("Expected an array")
        // [[x,y],...] is one set, [[[x,y],...],...] several
        if (l.firstOrNull() is List<*> && (l.first() as List<*>).firstOrNull() is List<*>) l.filterNotNull().map(::set)
        else listOf(set(l))
    }
}

/** Minimal JSON reader for nested arrays of integers (all the point formats need). */
private class JsonArrays(val s: String) {
    var i = 0

    fun parseAll(): List<Any> {
        val res = ArrayList<Any>()
        while (true) {
            skipWs()
            if (i >= s.length) return res
            res.add(value())
        }
    }

    private fun skipWs() { while (i < s.length && s[i].isWhitespace()) i++ }

    private fun value(): Any {
        skipWs()
        if (i >= s.length) throw FormatException("Unexpected end of JSON")
        if (s[i] == '[') {
            i++
            val list = ArrayList<Any>()
            skipWs()
            if (i < s.length && s[i] == ']') { i++; return list }
            while (true) {
                list.add(value())
                skipWs()
                when (s.getOrNull(i)) {
                    ',' -> i++
                    ']' -> { i++; return list }
                    else -> throw FormatException("Expected ',' or ']' at position $i")
                }
            }
        }
        val start = i
        if (s[i] == '-') i++
        while (i < s.length && s[i].isDigit()) i++
        if (i < s.length && (s[i] == '.' || s[i] == 'e' || s[i] == 'E'))
            throw FormatException("Only integer coordinates are supported (position $start)")
        return s.substring(start, i).toLongOrNull() ?: throw FormatException("Unexpected character at position $start")
    }
}

fun writePointSets(fmt: PointFormat, sets: List<List<Pt>>): ByteArray = when {
    fmt.isBinary -> writeBinary(sets, fmt.binaryBytes)
    fmt == PointFormat.JSON ->
        sets.joinToString("") { s -> s.joinToString(",", "[", "]\n") { "[${it.x},${it.y}]" } }.encodeToByteArray()
    else -> sets.joinToString("") { s ->
        buildString {
            append(s.size).append('\n')
            for (q in s) {
                append(q.x).append(' ').append(q.y)
                if (q.color != null) append(' ').append(q.color)
                append('\n')
            }
        }
    }.encodeToByteArray()
}

fun writeBinary(sets: List<List<Pt>>, width: Int): ByteArray {
    val max = (1L shl (8 * width)) - 1
    val bad = sets.flatten().firstOrNull { it.x !in 0..max || it.y !in 0..max }
    if (bad != null)
        throw FormatException("Coordinate (${bad.x}, ${bad.y}) does not fit into $width byte(s) (0..$max); " +
            "minimize the coordinates or use a wider format")
    val out = ByteArray(sets.sumOf { it.size } * 2 * width)
    var off = 0
    for (s in sets) for (q in s) for (v in longArrayOf(q.x, q.y)) {
        for (b in 0 until width) out[off + b] = ((v shr (8 * b)) and 0xFF).toByte()
        off += width
    }
    return out
}
