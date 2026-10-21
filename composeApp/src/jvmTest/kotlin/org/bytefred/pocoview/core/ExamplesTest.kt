package org.bytefred.pocoview.core

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExamplesTest {
    @Test
    fun groupsAndCounts() = runTest {
        val groups = loadExamples()
        assertEquals(
            listOf("Singletons" to 20, "Crossing families" to 4, "Universal point sets" to 6, "Miscellaneous" to 2),
            groups.map { it.title to it.examples.size },
        )
        assertEquals("Collected on PoSeZo (https://www.eurogiga-compose.eu/posezo.php)", groups[0].note)
        for (g in groups) for (e in g.examples) assertEquals(g.title, e.group, e.name)
    }

    @Test
    fun everySetIsDistinctAndInGeneralPosition() = runTest {
        for (e in loadExamples().flatMap { it.examples }) {
            assertEquals(e.points.size, e.points.map { it.x to it.y }.toSet().size, "duplicates in ${e.name}")
            assertTrue(isGeneralPosition(e.points), "not in general position: ${e.name}")
            assertTrue(e.points.all { it.inRange() }, "out of range: ${e.name}")
            val n = Regex("^n0*(\\d+)").find(e.name)!!.groupValues[1].toInt()
            assertEquals(n, e.points.size, "n of ${e.name}")
        }
    }

    @Test
    fun spotChecks() = runTest {
        val all = loadExamples().flatMap { it.examples }.associateBy { it.name }
        val bi = all.getValue("n8-c2-no-monochromatic-3-hole").points
        assertEquals(Pt(0, 0, "B"), bi[0])
        assertEquals(Pt(40, 10, "R"), bi[2])
        // BLUE/RED tokens in the original file are stored as the app's B/R
        assertEquals(setOf("B", "R"), all.getValue("n35-c2-no-monochromatic-4-island").points.map { it.color }.toSet())
        assertEquals(null, all.getValue("n6-c1-non-regular-triangulation").points[0].color)
        assertEquals(Pt(61, 66), all.getValue("n14-c1-no-4-crossing-family").points[0])
        assertEquals(Pt(69, 255), all.getValue("n5-c1-5-universal").points[0])
        assertEquals(Pt(30430, 8698), all.getValue("n10-c1-10-universal").points.last())
        val l2 = all.getValue("n17-c1-universal-4-connected-k11-17").points
        assertEquals(Pt(612, 666), l2.first())
        assertEquals(Pt(980, 0), l2.last())
        assertEquals(Pt(122, 197), all.getValue("n12-c1-11-universal").points.last())
        assertEquals("https://arxiv.org/abs/2109.10705", all.getValue("n41-c1-no-9-crossing-family").url)
    }
}
