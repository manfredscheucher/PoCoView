package org.bytefred.ordertypes.core

import kotlinx.coroutines.test.runTest
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals

class TriangulationsTest {
    private fun convex(n: Int) = List(n) { i ->
        val a = 2 * PI * i / n
        Pt((1000 * cos(a)).roundToLong(), (1000 * sin(a)).roundToLong())
    }

    @Test
    fun convexPositionGivesCatalanNumbers() = runTest {
        val catalan = longArrayOf(1, 1, 2, 5, 14, 42, 132, 429, 1430)
        for (n in 3..10) assertEquals(catalan[n - 2], countTriangulations(convex(n)), "n=$n")
    }

    @Test
    fun smallSetsWithInteriorPoints() = runTest {
        // triangle + interior point: one triangulation
        assertEquals(1L, countTriangulations(listOf(Pt(0, 0), Pt(10, 0), Pt(5, 10), Pt(5, 3))))
        // square + interior point: the point has degree 4 (one triangulation) or degree 3
        // inside the triangle of either diagonal (two more): 3 triangulations
        assertEquals(3L, countTriangulations(listOf(Pt(0, 0), Pt(10, 0), Pt(10, 10), Pt(0, 10), Pt(5, 4))))
    }
}
