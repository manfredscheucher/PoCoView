package org.bytefred.pocoview.core

import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PolygonsTest {
    private val square = listOf(Pt(0, 0), Pt(10, 0), Pt(10, 10), Pt(0, 10))
    private val squareCenter = square + Pt(5, 4)

    @Test
    fun orientationSigns() {
        assertEquals(1, orient(Pt(0, 0), Pt(1, 0), Pt(0, 1)))
        assertEquals(-1, orient(Pt(0, 0), Pt(0, 1), Pt(1, 0)))
        assertEquals(0, orient(Pt(0, 0), Pt(1, 1), Pt(2, 2)))
    }

    @Test
    fun orientationAtCoordinateLimitDoesNotOverflow() {
        val l = COORD_LIMIT
        assertEquals(1, orient(Pt(-l, -l), Pt(l, -l), Pt(l, l)))
        assertEquals(-1, orient(Pt(-l, -l), Pt(l, l), Pt(l, -l)))
    }

    @Test
    fun hullAndLayers() {
        assertEquals(4, convexHull(squareCenter).size)
        assertEquals(listOf(4, 1), onionLayers(squareCenter).map { it.size })
    }

    @Test
    fun smallKnownCounts() = runTest {
        assertEquals(1L, countConvexPolygons(square, 4, false, null).total)
        assertEquals(4L, countConvexPolygons(square, 3, true, null).total)
        assertEquals(BruteForce.countPolygons(squareCenter, 3, true), countConvexPolygons(squareCenter, 3, true, null).total)
        // the center lies outside exactly two corner triangles: two convex empty quadrilaterals
        assertEquals(2L, countConvexPolygons(squareCenter, 4, true, null).total)
    }

    @Test
    fun dpMatchesBruteForceOnRandomSets() = runTest {
        val rnd = Random(42)
        repeat(25) { round ->
            val n = 5 + round % 6
            val p = randomPointSet(n, 60, rnd)
            val sel = rnd.nextInt(n)
            for (k in 3..6) for (emptyOnly in listOf(true, false)) {
                val c = countConvexPolygons(p, k, emptyOnly, sel)
                assertEquals(BruteForce.countPolygons(p, k, emptyOnly), c.total, "total k=$k empty=$emptyOnly $p")
                assertEquals(BruteForce.countPolygons(p, k, emptyOnly, sel), c.incident, "incident k=$k empty=$emptyOnly $p")
            }
        }
    }

    @Test
    fun enumerationMatchesCountsAndIsConvex() = runTest {
        val rnd = Random(7)
        repeat(10) {
            val p = randomPointSet(9, 100, rnd)
            val sel = rnd.nextInt(9)
            for (k in 3..6) for (emptyOnly in listOf(true, false)) {
                val c = countConvexPolygons(p, k, emptyOnly, sel)
                val all = enumerateConvexPolygons(p, k, emptyOnly, null, Int.MAX_VALUE)
                val inc = enumerateConvexPolygons(p, k, emptyOnly, sel, Int.MAX_VALUE)
                assertEquals(c.total, all.size.toLong())
                assertEquals(c.incident, inc.size.toLong())
                assertTrue(inc.all { sel in it })
                for (poly in all) for (i in poly.indices)
                    assertTrue(orient(p[poly[i]], p[poly[(i + 1) % k]], p[poly[(i + 2) % k]]) > 0, "not CCW convex")
                assertEquals(all.size, all.map { it.sorted() }.toSet().size, "duplicates")
            }
        }
    }

    @Test
    fun enumerationRespectsLimit() = runTest {
        val p = randomPointSet(12, 1000, Random(1))
        assertEquals(10, enumerateConvexPolygons(p, 4, false, null, 10).size)
    }

    @Test
    fun crossingNumberOfSmallSets() = runTest {
        // a convex position n-gon has C(n,4) crossings
        val hexagon = listOf(Pt(2, 0), Pt(4, 0), Pt(6, 2), Pt(4, 5), Pt(2, 5), Pt(0, 2))
        assertEquals(15L, CrossingProperty().evaluate(hexagon, null, false, Ticker()).count)
    }
}
