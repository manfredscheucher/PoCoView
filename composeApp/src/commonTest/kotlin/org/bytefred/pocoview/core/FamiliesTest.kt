package org.bytefred.pocoview.core

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FamiliesTest {
    @Test
    fun hortonSizeRoundsUpToPowerOfTwo() {
        assertEquals(1, HortonFamily.size(1))
        assertEquals(2, HortonFamily.size(2))
        assertEquals(4, HortonFamily.size(3))
        assertEquals(32, HortonFamily.size(17))
        assertEquals(64, HortonFamily.size(64))
        for (n in listOf(1, 3, 5, 16, 33)) assertEquals(HortonFamily.size(n), HortonFamily.generate(n).size)
        assertFailsWith<IllegalArgumentException> { HortonFamily.size(0) }
    }

    @Test
    fun hortonMaxSize() {
        assertEquals(HORTON_MAX, HortonFamily.maxN)
        val h = HortonFamily.generate(HORTON_MAX)
        assertTrue(h.all { it.inRange() })
        assertEquals(h.size, h.toSet().size)
        assertFailsWith<IllegalArgumentException> { HortonFamily.generate(HORTON_MAX + 1) }
    }

    @Test
    fun hortonSetsHaveNo7Holes() = runTest {
        for (n in listOf(8, 16, 32, 64)) {
            val h = HortonFamily.generate(n)
            assertTrue(isGeneralPosition(h), "n=$n general position")
            if (n >= 32) assertEquals(0L, PolygonProperty(7, emptyOnly = true).evaluate(h, null, false, Ticker()).count, "n=$n")
        }
        // H(64) is the largest one checked here; the larger ones are at least in general position
        assertTrue(isGeneralPosition(HortonFamily.generate(HORTON_MAX)))
    }

    @Test
    fun convexPositionOnMomentCurve() = runTest {
        assertEquals(listOf(Pt(0, 0), Pt(1, 1), Pt(2, 4), Pt(3, 9)), ConvexFamily.generate(4))
        assertEquals(10, ConvexFamily.size(10))
        assertEquals(10, convexHull(ConvexFamily.generate(10)).size)
        val max = ConvexFamily.maxN
        assertTrue((max - 1).toLong() * (max - 1) <= COORD_LIMIT && max.toLong() * max > COORD_LIMIT)
        assertTrue(ConvexFamily.generate(max).all { it.inRange() })
        assertFailsWith<IllegalArgumentException> { ConvexFamily.generate(max + 1) }
        assertFailsWith<IllegalArgumentException> { ConvexFamily.generate(0) }
    }

    @Test
    fun familiesHaveReferences() {
        assertEquals(listOf("horton", "convex"), FAMILIES.map { it.id })
        assertTrue(FAMILIES.all { it.references.isNotEmpty() && it.name.isNotBlank() && it.description.isNotBlank() })
    }

    private companion object {
        const val HORTON_MAX = 256
    }
}
