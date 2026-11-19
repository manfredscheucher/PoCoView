package org.bytefred.pocoview.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** "Snap with grid refinement": snapWithRefinement. */
class GridRefineTest {
    private val unitTriangle = listOf(Pt(0, 0), Pt(1, 0), Pt(0, 1))

    private fun placed(s: Snap) = assertIs<Snap.Placed>(s)

    @Test
    fun aCellWithoutLatticePointsIsReachedAfterTwoDoublings() {
        // the open unit triangle has no lattice point; at x4 (1, 1) is inside
        val r = placed(snapWithRefinement(unitTriangle + Pt(5, 5), 3, 0.3, 0.3))
        assertEquals(2, r.doublings)
        assertEquals(listOf(Pt(0, 0), Pt(4, 0), Pt(0, 4), Pt(1, 1)), r.points)
        // as intended: the moved point lies inside the triangle
        assertTrue(inTriangle(r.points[0], r.points[1], r.points[2], r.points[3]))
    }

    @Test
    fun addingAlsoRefines() {
        val r = placed(snapWithRefinement(unitTriangle, null, 0.3, 0.3))
        assertEquals(2, r.doublings)
        assertEquals(listOf(Pt(0, 0), Pt(4, 0), Pt(0, 4), Pt(1, 1)), r.points)
    }

    @Test
    fun noDoublingWhenACornerFitsAndTheNearestIsTaken() {
        val big = listOf(Pt(0, 0), Pt(10, 0), Pt(0, 10))
        assertEquals(Snap.Placed(big + Pt(3, 3), 0), snapWithRefinement(big, null, 3.4, 3.3))
        assertEquals(Snap.Placed(big + Pt(4, 3), 0), snapWithRefinement(big, null, 3.6, 3.3))
    }

    @Test
    fun aCornerOutsideTheCellIsSkippedForTheNextNearest() {
        val big = listOf(Pt(0, 0), Pt(10, 0), Pt(0, 10))
        // nearest corner (5, 5) is on the line x + y = 10; next is (5, 4)
        assertEquals(Snap.Placed(big + Pt(5, 4), 0), snapWithRefinement(big, null, 4.9, 4.8))
    }

    @Test
    fun theColourOfAMovedPointIsKept() {
        val p = listOf(Pt(0, 0), Pt(10, 0), Pt(0, 10), Pt(20, 20, "R"))
        assertEquals(Pt(3, 3, "R"), placed(snapWithRefinement(p, 3, 3.2, 3.1)).points[3])
    }

    @Test
    fun lockKeepsTheCellOrGivesUp() {
        val p = listOf(Pt(0, 0), Pt(10, 0), Pt(0, 10), Pt(2, 2))
        // across the line x + y = 10: outside the locked cell
        assertEquals(Snap.OutsideLockedCell, snapWithRefinement(p, 3, 6.0, 6.0, lock = true))
        assertEquals(Snap.Placed(p.take(3) + Pt(3, 4), 0), snapWithRefinement(p, 3, 3.2, 3.9, lock = true))
    }

    @Test
    fun noRoomBeyondTheCoordinateLimit() {
        val p = unitTriangle + Pt(COORD_LIMIT, COORD_LIMIT - 7)
        assertEquals(Snap.NoRoom, snapWithRefinement(p, null, 0.3, 0.3))
    }

    private fun sign(a: Pt, b: Pt, x: Double, y: Double): Int {
        val d = (b.x - a.x).toDouble() * (y - a.y) - (b.y - a.y).toDouble() * (x - a.x)
        return if (d > 0) 1 else if (d < 0) -1 else 0
    }

    @Test
    fun randomReleasesAlwaysFindTheAimedCellBeforeTheCap() {
        val rnd = Random(7)
        var maxDoublings = 0
        repeat(2000) {
            val n = rnd.nextInt(3, 11)
            val p = randomPointSet(n, rnd.nextLong(12, 40), rnd)
            val i: Int? = if (rnd.nextBoolean()) rnd.nextInt(n) else null
            val others = if (i == null) p else p.filterIndexed { j, _ -> j != i }
            val x = rnd.nextDouble(-10.0, 50.0)
            val y = rnd.nextDouble(-10.0, 50.0)
            val r = snapWithRefinement(p, i, x, y)
            assertIs<Snap.Placed>(r, "n=$n i=$i ($x, $y) $p")
            assertTrue(r.doublings < MAX_DOUBLINGS)
            maxDoublings = maxOf(maxDoublings, r.doublings)
            val f = 1L shl r.doublings
            val qi = i ?: n
            val q = r.points[qi]
            val rest = r.points.filterIndexed { j, _ -> j != qi }
            // the other points are only scaled, the new one is in the cell of (x, y)
            assertEquals(others.map { Pt(it.x * f, it.y * f) }, rest.map { Pt(it.x, it.y) })
            for (j in others.indices) for (k in j + 1 until others.size) {
                val o = orient(rest[j], rest[k], q)
                val t = sign(others[j], others[k], x, y)
                assertTrue(o != 0 && (t == 0 || o == t))
            }
        }
        assertTrue(maxDoublings > 0, "the random cases should include refinements")
    }
}
