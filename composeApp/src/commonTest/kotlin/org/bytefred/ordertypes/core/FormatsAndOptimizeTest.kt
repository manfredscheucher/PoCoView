package org.bytefred.ordertypes.core

import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FormatsAndOptimizeTest {
    private val pts = listOf(Pt(3, 7, "B"), Pt(250, 0, "R"), Pt(9, 200))

    @Test
    fun ascRoundtripKeepsColors() {
        val bytes = writePointSets(PointFormat.ASC, listOf(pts, pts.reversed()))
        assertEquals(listOf(pts, pts.reversed()), readPointSets("x.asc", bytes))
    }

    @Test
    fun headerlessTextIsOneSet() {
        assertEquals(listOf(listOf(Pt(1, 2), Pt(3, 4))), readAsc("# comment\n1 2\n3 4\n"))
    }

    @Test
    fun jsonRoundtrip() {
        val plain = pts.map { it.copy(color = null) }
        assertEquals(listOf(plain), readPointSets("a.json", writePointSets(PointFormat.JSON, listOf(plain))))
    }

    @Test
    fun binaryRoundtripAndRangeCheck() {
        val plain = pts.map { it.copy(color = null) }
        for (fmt in listOf(PointFormat.PB08, PointFormat.PB16, PointFormat.PB32)) {
            val bytes = writePointSets(fmt, listOf(plain, plain))
            assertEquals(listOf(plain, plain), readPointSets("otypes03.${fmt.extension}", bytes))
        }
        assertFailsWith<FormatException> { writePointSets(PointFormat.PB08, listOf(listOf(Pt(256, 0)))) }
    }

    @Test
    fun grazLayoutIsLittleEndian() {
        // first 4-point order type of otypes04.b08 (pyotlib2 data)
        val bytes = byteArrayOf(0x00, 0x00, 0x01, 0xfe.toByte(), 0xff.toByte(), 0xff.toByte(), 0xfe.toByte(), 0x01)
        assertEquals(listOf(Pt(0, 0), Pt(1, 254), Pt(255, 255), Pt(254, 1)), readBinary(bytes, 4, 1)[0])
        assertContentEquals(bytes, writeBinary(readBinary(bytes, 4, 1), 1))
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
    fun saveFileNameLetsBinaryFilesBeReadBack() {
        assertEquals("otypes06.b08", saveFileName("otypes06", 6, PointFormat.PB08))
        assertEquals("otypes06_n7.b08", saveFileName("otypes06", 7, PointFormat.PB08))
        assertEquals("otypes06_n8.b16", saveFileName("otypes06_n7", 8, PointFormat.PB16))
        assertEquals("points_n12.b32", saveFileName("points", 12, PointFormat.PB32))
        assertEquals("points.asc", saveFileName("points", 12, PointFormat.ASC))
        for (n in listOf(3, 7, 12)) assertEquals(n, pointCountFromFileName(saveFileName("otypes06", n, PointFormat.PB08)))
    }

    @Test
    fun malformedTextGivesFormatExceptions() {
        assertFailsWith<FormatException> { readAsc("-3\n1 2\n") }
        assertFailsWith<FormatException> { readAsc("5\n1 2\n") }
        assertFailsWith<FormatException> { readAsc("1 x\n") }
        assertEquals(listOf(listOf(Pt(1, 2), Pt(3, 4))), readAsc("\uFEFF2\n1 2\n3 4\n"))
        assertEquals(listOf(listOf(Pt(1, 2), Pt(3, 4))), readAsc("1,2\n3,4\n"))
    }

    @Test
    fun jsonVariants() {
        val one = listOf(Pt(1, 2), Pt(-3, 4))
        assertEquals(listOf(one), readJson("[[1,2],[-3,4]]"))
        assertEquals(listOf(one), readJson("[\n  [1, 2],\n  [-3, 4]\n]\n"))
        assertEquals(listOf(one, one), readJson("[[[1,2],[-3,4]],[[1,2],[-3,4]]]"))
        assertEquals(listOf(one, one), readJson("[[1,2],[-3,4]]\n[[1,2],[-3,4]]\n"))
        assertFailsWith<FormatException> { readJson("[[1.5,2.5]]") }
        assertFailsWith<FormatException> { readJson("[[1,2],[3,4]") }
        assertFailsWith<FormatException> { readJson("[[1,2,3]]") }
    }

    @Test
    fun beautifyAndMinimizeKeepTheOrderType() = runTest {
        val rnd = Random(3)
        repeat(10) {
            val p = randomPointSet(10, 100_000, rnd)
            val sig = orderTypeSignature(p)
            val b = beautifyStep(p)
            assertEquals(sig, orderTypeSignature(b))
            var m = p
            var steps = 0
            while (true) {
                val next = minimizeStep(m) ?: break
                assertEquals(sig, orderTypeSignature(next))
                assertTrue(extent(next) < extent(m))
                m = next
                steps++
            }
            assertTrue(steps > 5, "minimizer made only $steps steps, extent ${extent(m)}")
        }
    }

    @Test
    fun beautifyNeverEnlargesTheBoundingBox() = runTest {
        // regression: unbounded cells of hull points used to push points to ±2^28
        val triangle = listOf(Pt(0, 0), Pt(10, 0), Pt(5, 5))
        val b = beautifyStep(triangle)
        assertTrue(b.all { it.x in 0..10 && it.y in 0..5 }, "$b")
        val rnd = Random(11)
        repeat(20) {
            val p = randomPointSet(10, 1000, rnd)
            assertTrue(extent(beautifyStep(p)) <= extent(p))
        }
        // n < 3: nothing to do (and no collapse into a duplicate)
        val two = listOf(Pt(0, 0), Pt(7, 3))
        assertEquals(two, beautifyStep(two))
    }

    @Test
    fun axisRangeContainsCurrentValueAndIsTight() {
        val p = randomPointSet(8, 1000, Random(5))
        for (i in p.indices) for (xAxis in listOf(true, false)) {
            val bounds = axisBounds(p, xAxis)
            val r = axisRange(p, i, xAxis)
            val cur = if (xAxis) p[i].x else p[i].y
            assertTrue(cur in r && r.first in bounds && r.last in bounds)
            fun at(v: Long) = if (xAxis) p[i].copy(x = v) else p[i].copy(y = v)
            // either the cell ends there or the bounding box does
            if (r.first > bounds.first) assertTrue(!sameOrderTypeAfterMove(p, i, at(r.first - 1)))
            if (r.last < bounds.last) assertTrue(!sameOrderTypeAfterMove(p, i, at(r.last + 1)))
        }
    }

    @Test
    fun randomSetOnTooSmallGridFailsFast() {
        assertFailsWith<IllegalStateException> { randomPointSet(50, 4, Random(1)) }
    }

    @Test
    fun randomMoveKeepsGeneralPosition() {
        val p = randomPointSet(12, 1000, Random(9))
        repeat(50) { assertTrue(isGeneralPosition(assertNotNull(randomMove(p, 30, Random(it))))) }
    }
}
