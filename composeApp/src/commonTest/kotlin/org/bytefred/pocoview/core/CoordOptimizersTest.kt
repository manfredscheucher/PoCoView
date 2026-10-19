package org.bytefred.pocoview.core

import kotlinx.coroutines.test.runTest
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CoordOptimizersTest {
    private fun sig(p: List<Pt>) = orderTypeSignature(p)

    /** Reference values computed with pyotlib2 `filter_exit_edges` (scripts/probe/probe_exit_edges_reference.py there). */
    @Test
    fun exitEdgesMatchPyotlib2() {
        fun pts(vararg c: Int) = c.toList().chunked(2).map { Pt(it[0].toLong(), it[1].toLong()) }
        fun ours(p: List<Pt>) = exitEdgeWitnesses(Orientations(p)).map { (e, w) -> Triple(e.first, e.second, w.sorted()) }.sortedWith(
            compareBy({ it.first }, { it.second }),
        )
        assertEquals(
            listOf(Triple(0, 2, listOf(1)), Triple(0, 4, listOf(5)), Triple(1, 3, listOf(2)), Triple(1, 5, listOf(0)), Triple(2, 4, listOf(3)), Triple(3, 5, listOf(4))),
            ours(pts(2, 0, 4, 0, 6, 2, 4, 5, 2, 5, 0, 2)),
        )
        assertEquals(
            listOf(
                Triple(0, 1, listOf(7)), Triple(0, 2, listOf(3)), Triple(0, 8, listOf(7)), Triple(1, 2, listOf(4, 6)),
                Triple(1, 3, listOf(8)), Triple(1, 5, listOf(4, 8)), Triple(2, 8, listOf(4)), Triple(5, 6, listOf(2)),
                Triple(5, 7, listOf(3)), Triple(6, 7, listOf(4)), Triple(6, 8, listOf(4)),
            ),
            ours(pts(12, 3, 97, 15, 55, 88, 31, 47, 70, 60, 5, 70, 88, 91, 40, 10, 63, 33)),
        )
        assertEquals(
            listOf(Triple(0, 2, listOf(3)), Triple(0, 4, listOf(3)), Triple(1, 2, listOf(4)), Triple(1, 3, listOf(4))),
            ours(pts(0, 0, 1000, 0, 500, 1000, 450, 300, 560, 320)),
        )
    }

    @Test
    fun exitEdgePropertyCountsAndIncidentWitness() = runTest {
        val hex = listOf(Pt(2, 0), Pt(4, 0), Pt(6, 2), Pt(4, 5), Pt(2, 5), Pt(0, 2))
        val all = ExitEdgeProperty().evaluate(hex, null, true, Ticker())
        assertEquals(6L, all.count)
        assertEquals(6, all.structures!!.size)
        // point 1 is the witness of exactly the edge (0, 2)
        val inc = ExitEdgeProperty().evaluate(hex, 1, true, Ticker())
        assertEquals(1L, inc.incident)
        assertEquals(listOf(0, 2), inc.structures!!.single().vertices.toList())
    }

    @Test
    fun exitTriplesDetermineTheOrderType() {
        // every labeled order type with the same exit-triple orientations is the same order type:
        // check on random sets that the exit triples are a subset of the triples and nonempty
        val rnd = Random(4)
        repeat(20) {
            val p = randomPointSet(9, 500, rnd)
            val o = Orientations(p)
            val t = exitTriples(o)
            assertTrue(t.isNotEmpty())
            assertTrue(t.all { (a, b, c) -> o[a, b, c] != 0 })
        }
    }

    @Test
    fun minimizeCoordsKeepsOrderTypeAndShrinks() = runTest {
        val rnd = Random(3)
        repeat(8) {
            val p = randomPointSet(10, 1 shl 20, rnd)
            val m = minimizeCoords(p, rnd = Random(it))
            assertEquals(sig(p), sig(m))
            assertTrue(bitsOf(m) < bitsOf(p), "bits ${bitsOf(p)} -> ${bitsOf(m)}")
            assertTrue(m.minOf { it.x } == 0L && m.minOf { it.y } == 0L)
        }
    }

    @Test
    fun beautifyCoordsKeepsOrderType() = runTest {
        val rnd = Random(5)
        repeat(8) {
            val p = randomPointSet(9, 10_000, rnd)
            val b = beautifyCoords(p)
            assertEquals(sig(p), sig(b))
        }
    }

    @Test
    fun beautify2KeepsOrderTypeAndHull() = runTest {
        val p = randomPointSet(8, 1000, Random(6))
        val b = beautify2Coords(p, iter1 = 2, iter2 = 200, minimize = false)
        assertEquals(sig(p), sig(b))
    }

    @Test
    fun nelderMeadFindsMinimumOfQuadratic() = runTest {
        val x = nelderMead({ (it[0] - 3).let { a -> a * a } + (it[1] + 1).let { b -> b * b } }, doubleArrayOf(0.0, 0.0), maxIter = 1000, maxFun = 2000)
        assertTrue(abs(x[0] - 3) < 1e-3 && abs(x[1] + 1) < 1e-3, "${x.toList()}")
    }
}
