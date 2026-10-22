package org.bytefred.pocoview.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FormatsAndOptimizeTest {
    private val pts = listOf(Pt(3, 7, "B"), Pt(250, 0, "R"), Pt(9, -200))

    private fun importOne(text: String): List<Pt> = importPointSets(text).single()

    @Test
    fun textAlwaysWritesCountLineAndRoundtrips() {
        val t = writeText(pts)
        assertEquals("3\n3 7 B\n250 0 R\n9 -200\n", t)
        assertEquals(t, exportPointSets(listOf(pts), ExportFormat.TXT))
        assertEquals(pts, importOne(t))
        assertEquals(listOf(pts), readPointSets("x.txt", t.encodeToByteArray()))
        // plain lines without count line are still read as one set
        assertEquals(listOf(pts), importPointSets("3 7 B\n250 0 R\n9 -200\n"))
    }

    @Test
    fun severalSetsRoundtripInBothFormats() {
        val sets = listOf(pts, pts.reversed(), listOf(Pt(0, 0), Pt(5, 1)))
        for (fmt in ExportFormat.entries) {
            val out = exportPointSets(sets, fmt)
            assertEquals(sets, readPointSets("x.${fmt.extension}", out.encodeToByteArray()), fmt.name)
            assertEquals(sets, importPointSets(out), fmt.name)
        }
        assertEquals("3\n3 7 B\n250 0 R\n9 -200\n2\n0 0\n5 1\n", writeTextSets(listOf(pts, sets[2])))
        assertEquals(
            "{\"points\": [[3, 7, \"B\"], [250, 0, \"R\"], [9, -200]]}\n{\"points\": [[0, 0], [5, 1]]}\n",
            exportPointSets(listOf(pts, sets[2]), ExportFormat.JSON),
        )
    }

    @Test
    fun deprecatedSingleImportRejectsSeveralSets() {
    }

    @Test
    fun countLineChecksum() {
        assertEquals(listOf(Pt(1, 2), Pt(3, 4)), importOne("2\n1 2\n3 4\n\n\n"))
        assertFailsWith<FormatException> { importPointSets("3\n1 2\n3 4\n") }
        assertFailsWith<FormatException> { importPointSets("1\n1 2\n3 4\n") }
        assertFailsWith<FormatException> { importPointSets("-3\n1 2\n") }
    }

    @Test
    fun textFilesMayHoldSeveralSets() {
        val two = (writeText(pts) + writeText(pts.reversed())).encodeToByteArray()
        for (ext in listOf("txt", "asc", "psz")) assertEquals(listOf(pts, pts.reversed()), readPointSets("x.$ext", two))
        assertEquals(listOf(listOf(Pt(1, 2), Pt(3, 4))), readTextSets("# comment\n1 2\n3 4\n"))
        assertFailsWith<FormatException> { readPointSets("x.txt", "5\n1 2\n".encodeToByteArray()) }
    }

    @Test
    fun malformedTextGivesFormatExceptions() {
        assertFailsWith<FormatException> { importPointSets("1 x\n") }
        assertFailsWith<FormatException> { importPointSets("1 2 red blue\n") }
        assertFailsWith<FormatException> { importPointSets("1\n") }
        assertFailsWith<FormatException> { importPointSets("") }
        assertFailsWith<FormatException> { importPointSets("0\n") }
        assertEquals(listOf(Pt(1, 2), Pt(3, 4)), readText("\uFEFF2\n1 2\n3 4\n"))
        assertEquals(listOf(Pt(1, 2), Pt(3, 4)), readText("1,2\n3,4\n"))
    }

    @Test
    fun jsonWriterLayoutAndRoundtrip() {
        val p = listOf(Pt(160, 7359), Pt(1768, 6530, "red"))
        val expected = "{\"points\": [[160, 7359], [1768, 6530, \"red\"]]}\n"
        assertEquals(expected, writeJson(p))
        assertEquals(expected, exportPointSets(listOf(p), ExportFormat.JSON))
        assertEquals(p, importOne(expected))
        assertEquals(listOf(p), readPointSets("a.json", expected.encodeToByteArray()))
        assertEquals(pts, importOne(writeJson(pts)))
        assertEquals(emptyList(), readJson(writeJson(emptyList())))
        assertEquals("{\"points\": []}\n", writeJson(emptyList()))
    }

    @Test
    fun jsonLinesAndPrettySingleObject() {
        val pretty = "{\n  \"points\": [\n    [160, 7359],\n    [1768, 6530, \"red\"]\n  ]\n}\n"
        val p = listOf(Pt(160, 7359), Pt(1768, 6530, "red"))
        assertEquals(listOf(p), readJsonSets(pretty))
        assertEquals(listOf(p), importPointSets(pretty))
        assertEquals(listOf(p), readPointSets("a.json", pretty.encodeToByteArray()))
        val lines = "{\"points\": [[1, 2], [3, 4]]}\n\n{\"name\": \"x\", \"points\": [[5, 6, \"B\"]]}\n"
        assertEquals(listOf(listOf(Pt(1, 2), Pt(3, 4)), listOf(Pt(5, 6, "B"))), readJsonSets(lines))
        assertEquals(2, importPointSets(lines).size)
        // every line must be a complete, valid object
        for (bad in listOf(
            "{\"points\": [[1, 2]]}\nx", "{\"points\": [[1, 2]]}\n{\"points\": [[1.5, 2]]}",
            "{\"points\": [[1, 2]]}\n[[1, 2]]", "{\n  \"points\": [[1, 2]]\n", "{\"points\": [[1, 2]]} {\"points\": [[3, 4]]}",
        )) assertFailsWith<FormatException>(bad) { readJsonSets(bad) }
        val e = assertFailsWith<FormatException> { readJsonSets("{\"points\": [[1, 2]]}\n{\"points\": [[1, 2, 3]]}") }
        assertTrue(e.message!!.startsWith("Line 2:"), e.message)
    }

    @Test
    fun jsonIsStrict() {
        assertEquals(listOf(Pt(1, 2), Pt(-3, 4, "blue")),
            importOne("  {\"name\": \"x\", \"points\": [[1, 2],\n [-3, 4, \"blue\"]]}\n"))
        for (bad in listOf(
            "{}", "{\"points\": 3}", "{\"points\": [[1.5, 2]]}", "{\"points\": [[1, 2, 3]]}",
            "{\"points\": [[1]]}", "{\"points\": [[1, 2, \"r\", 4]]}", "{\"points\": [[1, 2]]", "{\"points\": [[1, 2]]} x}",
        )) assertFailsWith<FormatException>(bad) { readJson(bad) }
        // arrays are not JSON point sets (the old JSON-lines format is gone)
        assertFailsWith<FormatException> { readPointSets("a.json", "[[1,2],[3,4]]".encodeToByteArray()) }
        // not starting with '{': read as text, which fails
        assertFailsWith<FormatException> { importPointSets("[[1,2],[3,4]]") }
    }

    @Test
    fun coordinateLimit() {
        assertFailsWith<FormatException> { importPointSets("${COORD_LIMIT + 1} 0\n") }
        assertFailsWith<FormatException> { importPointSets("{\"points\": [[0, ${-COORD_LIMIT - 1}]]}") }
        assertEquals(listOf(Pt(COORD_LIMIT, -COORD_LIMIT)), importOne("$COORD_LIMIT ${-COORD_LIMIT}"))
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
