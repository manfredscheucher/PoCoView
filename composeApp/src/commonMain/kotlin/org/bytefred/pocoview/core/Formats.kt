package org.bytefred.pocoview.core

class FormatException(message: String) : Exception(message)

/**
 * Point-set file formats for loading (compatible with pyotlib2):
 *  - text (`.txt`, `.asc`, `.psz`, all the same format): one point per line `x y` or
 *    `x y color` (color = one token). Optionally the first line is the point count n, then
 *    exactly n point lines follow. In files, several sets (each with its count line) may
 *    follow each other. Lines starting with `#` are comments.
 *  - `json`: one object `{"points": [[x, y], [x, y, "color"], ...]}`; other keys are ignored.
 *  - `b08`/`pb08`, `b16`/`pb16`, `b32`/`pb32`: Graz order type database, unsigned
 *    little-endian x1 y1 x2 y2 ... without delimiters; n is taken from the file name
 *    ([pointCountFromFileName]). Read only.
 */
enum class PointFormat(val extension: String, val binaryBytes: Int = 0) {
    TXT("txt"), JSON("json"), B08("b08", 1), B16("b16", 2), B32("b32", 4);

    val isBinary get() = binaryBytes > 0

    companion object {
        @Deprecated("Transitional until Menus.kt uses ExportFormat / importPointSet; then remove", ReplaceWith("TXT"))
        val ASC = TXT

        fun forFileName(name: String): PointFormat? =
            when (name.substringAfterLast('.', "").lowercase()) {
                "txt", "asc", "psz" -> TXT
                "json" -> JSON
                "b08", "pb08" -> B08
                "b16", "pb16" -> B16
                "b32", "pb32" -> B32
                else -> null
            }
    }
}

/** File name extensions accepted when loading a file. */
val LOAD_EXTENSIONS = listOf("json", "txt", "asc", "psz", "b08", "b16", "b32")

/** Formats for export and saving. */
enum class ExportFormat(val extension: String) { JSON("json"), TXT("txt") }

/**
 * n as encoded in a file name: an explicit `n<digits>` (e.g. `points_n12.b08`, `n10_c1.psz`)
 * wins, otherwise the first 1-2 digit number (Graz style `otypes05.b08` -> 5).
 */
fun pointCountFromFileName(name: String): Int? {
    val base = name.substringAfterLast('/').substringBeforeLast('.')
    Regex("""(?<![A-Za-z])n(\d{1,3})(?!\d)""").find(base)?.let { return it.groupValues[1].toInt() }
    return Regex("""(?<!\d)(\d{1,2})(?!\d)""").find(base)?.value?.toInt()
}

/** File name for saving under [base] in [fmt]. */
fun saveFileName(base: String, fmt: ExportFormat): String = "$base.${fmt.extension}"

/** Reads a file (format from the file name); text files may contain several sets. */
fun readPointSets(name: String, bytes: ByteArray): List<List<Pt>> {
    val fmt = PointFormat.forFileName(name)
        ?: throw FormatException("Unknown file type: $name (use ${LOAD_EXTENSIONS.joinToString { ".$it" }})")
    val sets = when (fmt) {
        PointFormat.TXT -> readTextSets(bytes.decodeToString())
        PointFormat.JSON -> listOf(readJson(bytes.decodeToString()))
        else -> {
            val n = pointCountFromFileName(name)?.takeIf { it > 0 }
                ?: throw FormatException("Cannot infer n from the file name '$name' (e.g. otypes05.b08 or points_n12.b08)")
            readBinary(bytes, n, fmt.binaryBytes)
        }
    }
    if (sets.isEmpty()) throw FormatException("No point set found in $name")
    sets.forEach(::checkRange)
    return sets
}

/**
 * Pasted point set, format detected automatically: the trimmed text starts with `{` and ends
 * with `}` -> JSON, otherwise text (with or without count line).
 */
fun importPointSet(text: String): List<Pt> {
    val t = text.trim()
    val p = if (t.startsWith('{') && t.endsWith('}')) readJson(t) else readText(t)
    if (p.isEmpty()) throw FormatException("No points found")
    checkRange(p)
    return p
}

private fun checkRange(p: List<Pt>) {
    if (p.any { !it.inRange() }) throw FormatException("Coordinates exceed ±$COORD_LIMIT (exact arithmetic limit)")
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

private fun textLines(text: String): List<String> =
    text.removePrefix("﻿").lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }

private fun isCountLine(line: String) = line.split(SEPARATORS).size == 1

private fun parsePoint(line: String): Pt {
    val t = line.split(SEPARATORS)
    if (t.size !in 2..3) throw FormatException("Expected 'x y' or 'x y color', got '$line'")
    val x = t[0].toLongOrNull() ?: throw FormatException("Not an integer: '${t[0]}'")
    val y = t[1].toLongOrNull() ?: throw FormatException("Not an integer: '${t[1]}'")
    return Pt(x, y, t.getOrNull(2))
}

private fun countOf(line: String): Int =
    line.toIntOrNull()?.takeIf { it >= 0 } ?: throw FormatException("Expected the point count, got '$line'")

/**
 * One point set in the text format: lines `x y [color]`, optionally preceded by a line with
 * the point count n; then exactly n point lines must follow (checksum).
 */
fun readText(text: String): List<Pt> {
    val lines = textLines(text)
    if (lines.isEmpty() || !isCountLine(lines[0])) return lines.map(::parsePoint)
    val n = countOf(lines[0])
    if (lines.size - 1 != n)
        throw FormatException("The first line says $n points, but ${lines.size - 1} point lines follow")
    return lines.drop(1).map(::parsePoint)
}

/**
 * Text file with possibly several sets: either plain `x y [color]` lines (one set), or a
 * sequence of (count line, that many point lines).
 */
fun readTextSets(text: String): List<List<Pt>> {
    val lines = textLines(text)
    if (lines.isEmpty()) return emptyList()
    if (!isCountLine(lines[0])) return listOf(lines.map(::parsePoint))
    val sets = ArrayList<List<Pt>>()
    var i = 0
    while (i < lines.size) {
        val n = countOf(lines[i])
        if (i + 1 + n > lines.size)
            throw FormatException("Count line says $n points, but only ${lines.size - i - 1} lines follow")
        sets.add(lines.subList(i + 1, i + 1 + n).map(::parsePoint))
        i += n + 1
    }
    return sets
}

/** Strict JSON point set: an object with key "points", an array of [x, y] or [x, y, "color"]. */
fun readJson(text: String): List<Pt> {
    val root = parseJson(text) as? Map<*, *> ?: throw FormatException("JSON: expected an object {\"points\": [...]}")
    if ("points" !in root) throw FormatException("JSON: missing key \"points\"")
    val list = root["points"] as? List<*> ?: throw FormatException("JSON: \"points\" must be an array")
    return list.mapIndexed { i, v ->
        val c = v as? List<*>
        val ok = c != null && c.size in 2..3 && c[0] is Long && c[1] is Long && (c.size == 2 || c[2] is String)
        if (!ok) throw FormatException("JSON: point ${i + 1} must be [x, y] or [x, y, \"color\"] with integers x, y")
        Pt(c!![0] as Long, c[1] as Long, c.getOrNull(2) as String?)
    }
}

/** JSON point set, one point per line. */
fun writeJson(points: List<Pt>): String = buildString {
    append("{\n  \"points\": [")
    if (points.isEmpty()) append("]\n}\n") else {
        append('\n')
        points.forEachIndexed { i, q ->
            append("    [").append(q.x).append(", ").append(q.y)
            if (q.color != null) append(", ").append(jsonString(q.color))
            append(']')
            if (i < points.size - 1) append(',')
            append('\n')
        }
        append("  ]\n}\n")
    }
}

/** Text point set: lines `x y [color]`; with [countLine] the first line is the number of points. */
fun writeText(points: List<Pt>, countLine: Boolean): String = buildString {
    if (countLine) append(points.size).append('\n')
    for (q in points) {
        append(q.x).append(' ').append(q.y)
        if (q.color != null) append(' ').append(q.color)
        append('\n')
    }
}

/** Export / save in [fmt]; [countLine] only applies to [ExportFormat.TXT]. */
fun exportPointSet(points: List<Pt>, fmt: ExportFormat, countLine: Boolean): String = when (fmt) {
    ExportFormat.JSON -> writeJson(points)
    ExportFormat.TXT -> writeText(points, countLine)
}
