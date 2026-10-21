package org.bytefred.pocoview.core

import kotlinx.coroutines.test.runTest
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests of the literal pyotlib2 coord_optimizer ports. Reference values come from
 * pyotlib2/scripts/probe/probe_legacy_optimizer_reference.py (run with the pyotlib2 venv).
 */
class LegacyOptimizersTest {
    private fun pts(vararg c: Long) = c.toList().chunked(2).map { Pt(it[0], it[1]) }
    private fun coords(p: List<Pt>) = p.map { it.x to it.y }
    private fun bits(p: List<Pt>): Int {
        val d = maxOf(p.maxOf { it.x } - p.minOf { it.x }, p.maxOf { it.y } - p.minOf { it.y })
        var b = 0
        var m = 1L
        while (1 + d > m) { b++; m *= 2 }
        return b
    }

    private val p7 = pts(0, 0, 1000, 37, 980, 1010, 13, 990, 300, 420, 610, 333, 450, 700)
    private val p9 = pts(12, 3, 97, 15, 55, 88, 31, 47, 70, 60, 5, 70, 88, 91, 40, 10, 63, 33)

    @Test
    fun wideOrientationIsExact() {
        val r = Random(3)
        repeat(2000) {
            val c = LongArray(6) { r.nextLong(-COORD_LIMIT, COORD_LIMIT + 1) }
            assertEquals(orient(Pt(c[0], c[1]), Pt(c[2], c[3]), Pt(c[4], c[5])), orientWide(c[0], c[1], c[2], c[3], c[4], c[5]))
        }
        // large: scaling by 2^30 keeps the sign; collinear stays 0
        val s = 1L shl 30
        repeat(500) {
            val c = LongArray(6) { r.nextLong(-COORD_LIMIT, COORD_LIMIT + 1) }
            assertEquals(orient(Pt(c[0], c[1]), Pt(c[2], c[3]), Pt(c[4], c[5])), orientWide(c[0] * 2 * s, c[1] * 2 * s, c[2] * 2 * s, c[3] * 2 * s, c[4] * 2 * s, c[5] * 2 * s))
        }
        assertEquals(0, orientWide(0, 0, WIDE_LIMIT, WIDE_LIMIT, -WIDE_LIMIT, -WIDE_LIMIT))
        assertEquals(-1, orientWide(0, 0, WIDE_LIMIT, WIDE_LIMIT - 1, -WIDE_LIMIT, -WIDE_LIMIT))
        assertEquals(1, orientWide(0, 0, WIDE_LIMIT, WIDE_LIMIT, -WIDE_LIMIT, -WIDE_LIMIT + 1))
    }

    @Test
    fun balancePointsMatchesPython() {
        val p = pts(3, 40, 10, 7, 0, 0, 5, 100)
        assertEquals(listOf(30L to 40L, 100L to 7L, 0L to 0L, 50L to 100L), coords(balancePoints(p)))
        assertEquals(listOf(90L to 120L, 300L to 21L, 0L to 0L, 150L to 300L), coords(balancePoints(p, 3)))
        assertEquals(
            listOf(900L to 360L, 30L to 630L, 0L to 0L, 15L to 900L),
            coords(balancePoints(pts(300, 4, 10, 7, 0, 0, 5, 10), 3)),
        )
    }

    @Test
    fun computeSymmetricPsMatchesPython() {
        assertEquals(
            listOf(10L to 0L, -5L to 9L, -5L to -9L, 7L to 3L, -6L to 5L, -1L to -8L, 0L to 0L),
            coords(computeSymmetricPs(pts(10, 0, 0, 0, 0, 0, 7, 3, 0, 0, 0, 0, 5, 5), 3)),
        )
        assertEquals(
            listOf(9L to 2L, -2L to 9L, -9L to -2L, 2L to -9L, 1L to -6L, 6L to 1L, -1L to 6L, -6L to -1L, 0L to 0L),
            coords(computeSymmetricPs(pts(9, 2, 0, 0, 0, 0, 0, 0, 1, -6, 0, 0, 0, 0, 0, 0, 4, 4), 4)),
        )
        assertEquals(
            listOf(100L to 1L, 49L to 87L, -51L to 86L, -100L to -1L, -49L to -87L, 51L to -86L),
            coords(computeSymmetricPs(pts(100, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0), 6)),
        )
    }

    @Test
    fun beautifyPointSetMatchesPython() = runTest {
        assertEquals(
            listOf(246L to 0L, 1466L to 11L, 1284L to 826L, 0L to 891L, 566L to 381L, 984L to 300L, 722L to 690L),
            coords(beautifyPointSet(p7)),
        )
        assertEquals(
            listOf(428L to 0L, 4668L to 89L, 4586L to 3551L, 0L to 3601L, 1715L to 1566L, 3134L to 1243L, 2337L to 2801L),
            coords(beautifyPointSet(p7, maxIter = 5, goodBits = 12)),
        )
        assertEquals(
            listOf(6L to 0L, 88L to 8L, 48L to 138L, 19L to 43L, 60L to 53L, 0L to 61L, 82L to 124L, 30L to 6L, 53L to 29L),
            coords(beautifyPointSet(p9)),
        )
        assertEquals(
            listOf(205L to 0L, 2882L to 247L, 1579L to 3272L, 686L to 1378L, 2013L to 1762L, 0L to 2017L, 2608L to 2883L, 1033L to 194L, 1769L to 930L),
            coords(beautifyPointSet(p9, maxIter = 5, goodBits = 12)),
        )
    }

    @Test
    fun patternSearchMatchesPython() = runTest {
        val r1 = minimizePatternSearch(p7)
        assertEquals(listOf(0L to 0L, 17L to 25L, 26L to 42L, 15L to 32L, 6L to 10L, 16L to 25L, 23L to 39L), coords(r1.points))
        assertEquals(0, r1.blowupLeft)
        val r2 = minimizePatternSearch(p7, maxTrials = 300, scale = 3, blowup = 2)
        assertEquals(listOf(1308L to 2143L, 2520L to 0L, 1309L to 2149L, 0L to 4466L, 1308L to 2144L, 2312L to 368L, 392L to 3772L), coords(r2.points))
        assertEquals(0, r2.blowupLeft)
        val r3 = minimizePatternSearch(p9)
        assertEquals(listOf(0L to 0L, 35L to 23L, 16L to 50L, 8L to 26L, 25L to 37L, 8L to 27L, 34L to 51L, 2L to 2L, 18L to 25L), coords(r3.points))
        val r4 = minimizePatternSearch(p9, maxTrials = 300, scale = 3, blowup = 2)
        assertEquals(listOf(0L to 0L, 206L to 154L, 187L to 252L, 118L to 160L, 191L to 231L, 126L to 171L, 235L to 285L, 1L to 1L, 133L to 159L), coords(r4.points))
    }

    @Test
    fun minimizeIntegerCoordinatesKeepsOrderTypeAndShrinks() = runTest {
        val rnd = Random(11)
        val inputs = listOf(p7, p9) + List(6) { randomPointSet(8 + it, 5000, rnd) }
        for (p in inputs) {
            val col = p.mapIndexed { i, q -> q.copy(color = "c$i") }
            for (variant in 0..3) {
                val r = when (variant) {
                    0 -> minimizeIntegerCoordinates(col, rnd = rnd)
                    1 -> minimizeIntegerCoordinates(col, maxTrials = 5, rnd = rnd)
                    2 -> minimizeIntegerCoordinates(col, maxTrials = 5, sheering = true, rnd = rnd)
                    else -> minimizeIntegerCoordinates(col, maxTrials = 5, minimizeDiameter = true, rnd = rnd)
                }
                assertEquals(orderTypeSignature(col), orderTypeSignature(r))
                assertTrue(bits(r) <= bits(col))
                assertEquals(col.map { it.color }, r.map { it.color })
                assertEquals(0L, r.minOf { it.x })
                assertEquals(0L, r.minOf { it.y })
            }
        }
        // the Python probe got 10 -> 4 bits for p7 with the defaults (random, so only roughly)
        assertTrue(bits(minimizeIntegerCoordinates(p7, rnd = Random(2))) <= 6)
    }

    @Test
    fun symmetricModeStaysSymmetric() = runTest {
        val base = pts(1000, 37, 0, 0, 0, 0, 300, 420, 0, 0, 0, 0, 610, -333, 0, 0, 0, 0, 0, 0)
        val sym = computeSymmetricPs(base, 3)
        assertTrue(isGeneralPosition(sym))
        val r = minimizeIntegerCoordinates(sym, maxTrials = 3, symmetric = 3, rnd = Random(7))
        assertEquals(orderTypeSignature(sym), orderTypeSignature(r))
        assertEquals(r, computeSymmetricPs(r, 3))
        assertTrue(bits(r) < bits(sym))
    }

    @Test
    fun patternSearchShrinksAndKeepsOrderType() = runTest {
        val rnd = Random(21)
        repeat(4) {
            val p = randomPointSet(8, 3000, rnd)
            val r = minimizePatternSearch(p, maxTrials = 500).points
            assertEquals(orderTypeSignature(p), orderTypeSignature(r))
            assertTrue(bits(r) < bits(p))
        }
    }

    @Test
    fun myPatternSearchFindsMinimum() = runTest {
        val r = myPatternSearch({ x -> (x[0] - 3) * (x[0] - 3) + (x[1] + 1) * (x[1] + 1) }, doubleArrayOf(0.0, 0.0), 1.0, maxIter = 1000)
        assertTrue(abs(r.x0[0] - 3) < 1e-6 && abs(r.x0[1] + 1) < 1e-6)
    }

    @Test
    fun minimizeWithEnsurePropertyMayChangeOrderType() = runTest {
        val r = Random(3)
        val p = List(9) { Pt(r.nextLong(0, 1_000_000), r.nextLong(0, 1_000_000)) }
        val count: suspend (List<Pt>, Ticker) -> Long = { q, t -> CrossingProperty().evaluate(q, null, false, t).count }
        val res = minimizeIntegerCoordinates(p, maxTrials = 2, rnd = Random(1), ensureProperty = count)
        assertTrue(bits(res) < bits(p))
        val keep = minimizeIntegerCoordinates(p, maxTrials = 2, rnd = Random(1))
        assertEquals(orderTypeSignature(p), orderTypeSignature(keep))
    }
}
