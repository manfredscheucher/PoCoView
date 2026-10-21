package org.bytefred.pocoview.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FormatsAndOptimizeTest {
    private val pts = listOf(Pt(3, 7, "B"), Pt(250, 0, "R"), Pt(9, -200))

    @Test
    fun textRoundtripWithAndWithoutCountLine() {
        for (countLine in listOf(true, false)) {
            val t = writeText(pts, countLine)
            assertEquals(pts, importPointSet(t))
            assertEquals(listOf(pts), readPointSets("x.txt", t.encodeToByteArray()))
        }
        assertEquals("3\n3 7 B\n250 0 R\n9 -200\n", writeText(pts, countLine = true))
        assertEquals("3 7 B\n250 0 R\n9 -200\n", exportPointSet(pts, ExportFormat.TXT, countLine = false))
    }

    @Test
    fun countLineChecksum() {
        assertEquals(listOf(Pt(1, 2), Pt(3, 4)), importPointSet("2\n1 2\n3 4\n\n\n"))
        assertFailsWith<FormatException> { importPointSet("3\n1 2\n3 4\n") }
        assertFailsWith<FormatException> { importPointSet("1\n1 2\n3 4\n") }
        assertFailsWith<FormatException> { importPointSet("-3\n1 2\n") }
    }

    @Test
    fun textFilesMayHoldSeveralSets() {
        val two = (writeText(pts, true) + writeText(pts.reversed(), true)).encodeToByteArray()
        for (ext in listOf("txt", "asc", "psz")) assertEquals(listOf(pts, pts.reversed()), readPointSets("x.$ext", two))
        assertEquals(listOf(listOf(Pt(1, 2), Pt(3, 4))), readTextSets("# comment\n1 2\n3 4\n"))
        assertFailsWith<FormatException> { readPointSets("x.txt", "5\n1 2\n".encodeToByteArray()) }
    }

    @Test
    fun malformedTextGivesFormatExceptions() {
        assertFailsWith<FormatException> { importPointSet("1 x\n") }
        assertFailsWith<FormatException> { importPointSet("1 2 red blue\n") }
        assertFailsWith<FormatException> { importPointSet("1\n") }
        assertFailsWith<FormatException> { importPointSet("") }
        assertEquals(listOf(Pt(1, 2), Pt(3, 4)), readText("\uFEFF2\n1 2\n3 4\n"))
        assertEquals(listOf(Pt(1, 2), Pt(3, 4)), readText("1,2\n3,4\n"))
    }

    @Test
    fun jsonWriterLayoutAndRoundtrip() {
        val p = listOf(Pt(160, 7359), Pt(1768, 6530, "red"))
        val expected = "{\n  \"points\": [\n    [160, 7359],\n    [1768, 6530, \"red\"]\n  ]\n}\n"
        assertEquals(expected, writeJson(p))
        assertEquals(expected, exportPointSet(p, ExportFormat.JSON, countLine = true))
        assertEquals(p, importPointSet(expected))
        assertEquals(listOf(p), readPointSets("a.json", expected.encodeToByteArray()))
        assertEquals(pts, importPointSet(writeJson(pts)))
        assertEquals(emptyList(), readJson(writeJson(emptyList())))
    }

    @Test
    fun jsonIsStrict() {
        assertEquals(listOf(Pt(1, 2), Pt(-3, 4, "blue")),
            importPointSet("  {\"name\": \"x\", \"points\": [[1, 2],\n [-3, 4, \"blue\"]]}\n"))
        for (bad in listOf(
            "{}", "{\"points\": 3}", "{\"points\": [[1.5, 2]]}", "{\"points\": [[1, 2, 3]]}",
            "{\"points\": [[1]]}", "{\"points\": [[1, 2, \"r\", 4]]}", "{\"points\": [[1, 2]]", "{\"points\": [[1, 2]]} x}",
        )) assertFailsWith<FormatException>(bad) { readJson(bad) }
        // arrays are not JSON point sets (the old JSON-lines format is gone)
        assertFailsWith<FormatException> { readPointSets("a.json", "[[1,2],[3,4]]".encodeToByteArray()) }
        // not starting with '{': read as text, which fails
        assertFailsWith<FormatException> { importPointSet("[[1,2],[3,4]]") }
    }

    @Test
    fun coordinateLimit() {
        assertFailsWith<FormatException> { importPointSet("${COORD_LIMIT + 1} 0\n") }
        assertFailsWith<FormatException> { importPointSet("{\"points\": [[0, ${-COORD_LIMIT - 1}]]}") }
        assertEquals(listOf(Pt(COORD_LIMIT, -COORD_LIMIT)), importPointSet("$COORD_LIMIT ${-COORD_LIMIT}"))
    }

    @Test
    fun grazLayoutIsLittleEndian() {
        // first 4-point order type of otypes04.b08 (pyotlib2 data)
        val bytes = byteArrayOf(0x00, 0x00, 0x01, 0xfe.toByte(), 0xff.toByte(), 0xff.toByte(), 0xfe.toByte(), 0x01)
        val p = listOf(Pt(0, 0), Pt(1, 254), Pt(255, 255), Pt(254, 1))
        assertEquals(listOf(p), readBinary(bytes, 4, 1))
        assertEquals(listOf(p, p), readPointSets("otypes04.b08", bytes + bytes))
        val wide = byteArrayOf(0, 0, 0, 0, 1, 0, 0xfe.toByte(), 0, 0xff.toByte(), 0, 0xff.toByte(), 0, 0xfe.toByte(), 0, 1, 0)
        assertEquals(listOf(p), readPointSets("set_n4.b16", wide))
    }

    @Test
    fun fileTypes() {
        assertEquals(PointFormat.TXT, PointFormat.forFileName("a.psz"))
        assertEquals(PointFormat.TXT, PointFormat.forFileName("a.ASC"))
        assertEquals(PointFormat.B32, PointFormat.forFileName("a.pb32"))
        assertEquals(null, PointFormat.forFileName("a.svg"))
        for (e in LOAD_EXTENSIONS) assertTrue(PointFormat.forFileName("x.$e") != null, e)
        assertEquals("pts.json", saveFileName("pts", ExportFormat.JSON))
        assertEquals("pts.txt", saveFileName("pts", ExportFormat.TXT))
        assertFailsWith<FormatException> { readPointSets("a.svg", ByteArray(0)) }
    }

    @Test
    fun nFromFileName() {
        assertEquals(5, pointCountFromFileName("otypes05.b08"))
        assertEquals(10, pointCountFromFileName("otypes10.b16"))
        assertEquals(10, pointCountFromFileName("set3_n10.b08"))
        assertEquals(29, pointCountFromFileName("n29_c1_no_convex_6_hole.psz"))
        assertFailsWith<FormatException> { readPointSets("foo.b08", ByteArray(4)) }
        assertFailsWith<FormatException> { readPointSets("x0.b08", ByteArray(4)) }
    }

    @Test
    fun randomSetOnTooSmallGridFailsFast() {
        assertFailsWith<IllegalStateException> { randomPointSet(50, 4, Random(1)) }
    }
}
