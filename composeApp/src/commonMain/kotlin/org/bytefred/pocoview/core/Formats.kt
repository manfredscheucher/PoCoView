package org.bytefred.pocoview.core

class FormatException(message: String) : Exception(message)

/**
 * Point-set file formats for loading (compatible with pyotlib2):
 *  - text (`.txt`, `.asc`, `.psz`, all the same format): one point per line `x y` or
 *    `x y color` (color = one token). Optionally the first line is the point count n, then
 *    exactly n point lines follow; several sets (each with its count line) may follow each
 *    other. Lines starting with `#` are comments. Written always with count lines.
 *  - `json`: one object `{"points": [[x, y], [x, y, "color"], ...]}` per line, one line per
 *    set; other keys are ignored. A single object may also span several lines (pretty printed).
 *  - `b08`/`pb08`, `b16`/`pb16`, `b32`/`pb32`: Graz order type database, unsigned
 *    little-endian x1 y1 x2 y2 ... without delimiters; n is taken from the file name
 *    ([pointCountFromFileName]). Read only.
 */
enum class PointFormat(val extension: String, val binaryBytes: Int = 0) {
    TXT("txt"), JSON("json"), B08("b08", 1), B16("b16", 2), B32("b32", 4);

    val isBinary get() = binaryBytes > 0

    companion object {
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
val LOAD_EXTENSIONS = listOf("json", "txt", "asc", "psz", "b08", "b16", "b32", "pb08", "pb16", "pb32")

/** A colour token survives the text format: one word, no separators. */
fun isColorToken(s: String): Boolean = s.isNotEmpty() && !Regex("[\\s,;]").containsMatchIn(s)

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
        PointFormat.JSON -> readJsonSets(bytes.decodeToString())
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
 * Pasted content, format detected automatically: the trimmed text starts with `{` and ends
 * with `}` -> JSON ([readJsonSets]), otherwise text ([readTextSets]). May yield several sets.
 */
fun importPointSets(text: String): List<List<Pt>> {
    val t = text.trim()
    val sets = if (t.startsWith('{') && t.endsWith('}')) readJsonSets(t) else readTextSets(t)
    if (sets.isEmpty() || sets.any { it.isEmpty() }) throw FormatException("No points found")
    sets.forEach(::checkRange)
    return sets
}

/** Transitional single-set variant of [importPointSets]; fails if the content holds several sets. */
@Deprecated("Use importPointSets", ReplaceWith("importPointSets(text)"))
fun importPointSet(text: String): List<Pt> {
    val sets = importPointSets(text)
    if (sets.size != 1) throw FormatException("The content holds ${sets.size} point sets")
    return sets[0]
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
fun readJson(text: String): List<Pt> = jsonPoints(parseJson(text))

private fun jsonPoints(value: Any?): List<Pt> {
    val root = value as? Map<*, *> ?: throw FormatException("JSON: expected an object {\"points\": [...]}")
    if ("points" !in root) throw FormatException("JSON: missing key \"points\"")
    val list = root["points"] as? List<*> ?: throw FormatException("JSON: \"points\" must be an array")
    return list.mapIndexed { i, v ->
        val c = v as? List<*>
        val ok = c != null && c.size in 2..3 && c[0] is Long && c[1] is Long && (c.size == 2 || c[2] is String)
        if (!ok) throw FormatException("JSON: point ${i + 1} must be [x, y] or [x, y, \"color\"] with integers x, y")
        val color = c!!.getOrNull(2) as String?
        if (color != null && !isColorToken(color)) throw FormatException("JSON: point ${i + 1}: colour must be one word (no spaces, ',' or ';')")
        Pt(c[0] as Long, c[1] as Long, color)
    }
}

/**
 * JSON content with possibly several sets: either one object (it may span several lines,
 * pretty printed) or one object per line, one set per line. Blank lines are ignored.
 */
fun readJsonSets(text: String): List<List<Pt>> {
    val whole = try {
        parseJson(text)
    } catch (e: FormatException) {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.size < 2) throw e
        // first line is no complete JSON value -> most likely one broken multi-line object
        val first = try { parseJson(lines[0]) } catch (_: FormatException) { throw e }
        return lines.mapIndexed { i, line ->
            try {
                jsonPoints(if (i == 0) first else parseJson(line))
            } catch (le: FormatException) {
                throw FormatException("Line ${i + 1}: ${le.message}")
            }
        }
    }
    return listOf(jsonPoints(whole))
}

/** JSON point set on one line: `{"points": [[x, y], [x, y, "color"], ...]}` + newline. */
fun writeJson(points: List<Pt>): String = buildString {
    append("{\"points\": [")
    points.forEachIndexed { i, q ->
        if (i > 0) append(", ")
        append('[').append(q.x).append(", ").append(q.y)
        if (q.color != null) append(", ").append(jsonString(q.color))
        append(']')
    }
    append("]}\n")
}

/** Several sets as JSON, one line per set. */
fun writeJsonSets(sets: List<List<Pt>>): String = sets.joinToString("") { writeJson(it) }

/** Text point set: the count line n, then one line `x y [color]` per point. */
fun writeText(points: List<Pt>): String = buildString {
    append(points.size).append('\n')
    for (q in points) {
        append(q.x).append(' ').append(q.y)
        if (q.color != null) append(' ').append(q.color)
        append('\n')
    }
}

/** Several sets as text, each with its count line, one after another. */
fun writeTextSets(sets: List<List<Pt>>): String = sets.joinToString("") { writeText(it) }

/** Export / save all [sets] in [fmt] into one string. */
fun exportPointSets(sets: List<List<Pt>>, fmt: ExportFormat): String = when (fmt) {
    ExportFormat.JSON -> writeJsonSets(sets)
    ExportFormat.TXT -> writeTextSets(sets)
}
