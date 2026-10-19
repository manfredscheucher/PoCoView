package org.bytefred.ordertypes.core

import kotlinx.coroutines.test.runTest
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CellHoppingTest {
    private suspend fun crossings(p: List<Pt>) = countConvexPolygons(p, 4, false, null).total

    @Test
    fun findsFewerCrossingsThanConvexPosition() = runTest {
        // convex position has the maximum number of crossings, C(8,4) = 70
        val octagon = List(8) { i ->
            val a = 2 * PI * i / 8
            Pt(500 + (400 * cos(a)).roundToLong(), 500 + (400 * sin(a)).roundToLong())
        }
        assertEquals(70L, crossings(octagon))
        val r = assertNotNull(CellHopMinimizer(::crossings, rnd = Random(1)).minimize(octagon))
        assertTrue(crossings(r) < 70)
        assertTrue(isGeneralPosition(r))
        // stays on the qtvis canvas = bounding box of the start
        val xs = octagon.minOf { it.x }..octagon.maxOf { it.x }
        val ys = octagon.minOf { it.y }..octagon.maxOf { it.y }
        assertTrue(r.all { it.x in xs && it.y in ys })
    }

    @Test
    fun failedSearchReturnsNullAndReportsTheStart() = runTest {
        // triangle + 2 interior points: 1 crossing, the optimum for n = 5 (cr(K5) = 1)
        val p = listOf(Pt(0, 0), Pt(1000, 0), Pt(500, 1000), Pt(450, 300), Pt(560, 320))
        assertEquals(1L, crossings(p))
        var last: List<Pt>? = null
        val r = CellHopMinimizer(::crossings, CellHopParams(trials = 1), Random(2), onProgress = { last = it }).minimize(p)
        assertEquals(null, r)
        assertEquals(p, last)
    }

    @Test
    fun zeroCountReturnsStartImmediately() = runTest {
        val tri = listOf(Pt(0, 0), Pt(10, 0), Pt(0, 10))
        assertEquals(tri, CellHopMinimizer(::crossings).minimize(tri))
    }
}
