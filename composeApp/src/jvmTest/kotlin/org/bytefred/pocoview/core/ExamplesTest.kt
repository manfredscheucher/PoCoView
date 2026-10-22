package org.bytefred.pocoview.core

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExamplesTest {
    @Test
    fun groupsAndCounts() = runTest {
        val catalog = loadExamples()
        assertEquals(
            listOf(
                "Holes and convex gons" to 9, "Crossing number" to 2, "Bicolored sets" to 6,
                "Crossing families" to 5, "Universal point sets" to 6, "Miscellaneous" to 3,
            ),
            catalog.categories.map { it.title to it.examples.size },
        )
        for (g in catalog.categories) for (e in g.examples) assertEquals(g.title, e.group, e.name)
        assertEquals(2, catalog.links.size)
        assertEquals(3, catalog.links.first { "ordertypes" in it.url }.papers.size)
        val n13 = catalog.categories.flatMap { it.examples }.first { it.name == "n13-c1-min-convex-3-4-5-holes" }
        assertEquals(
            listOf("https://scheucher.dev/files/theses/bachelors_thesis_2013.pdf",
                "https://www.eurogiga-compose.eu/posezo/n13_c1_min_convex_3_4_5_holes/n13_c1_min_convex_3_4_5_holes.php"),
            n13.links.map { it.url },
        )
        // no link to the PoSeZo main page per example, no dead links
        for (e in catalog.categories.flatMap { it.examples }) for (l in e.links) {
            assertTrue(l.url != "https://www.eurogiga-compose.eu/posezo.php" && "markov/sixgon" !in l.url, "${e.name}: ${l.url}")
        }
    }

    @Test
    fun everySetIsDistinctAndInGeneralPosition() = runTest {
        for (e in loadExamples().categories.flatMap { it.examples }) {
            assertEquals(e.points.size, e.points.map { it.x to it.y }.toSet().size, "duplicates in ${e.name}")
            assertTrue(isGeneralPosition(e.points), "not in general position: ${e.name}")
            assertTrue(e.points.all { it.inRange() }, "out of range: ${e.name}")
            val n = Regex("^n0*(\\d+)").find(e.name)!!.groupValues[1].toInt()
            assertEquals(n, e.points.size, "n of ${e.name}")
        }
    }

    @Test
    fun spotChecks() = runTest {
        val all = loadExamples().categories.flatMap { it.examples }.associateBy { it.name }
        val bi = all.getValue("n8-c2-no-monochromatic-3-hole").points
        assertEquals(Pt(0, 0, "B"), bi[0])
        assertEquals(Pt(40, 10, "R"), bi[2])
        // BLUE/RED tokens in the original file are stored as the app's B/R
        assertEquals(setOf("B", "R"), all.getValue("n35-c2-no-monochromatic-4-island").points.map { it.color }.toSet())
        assertEquals(null, all.getValue("n10-c1-1-convex-5-hole").points[0].color)
        assertEquals(Pt(61, 66), all.getValue("n14-c1-no-4-crossing-family").points[0])
        assertEquals(Pt(69, 255), all.getValue("n5-c1-5-universal").points[0])
        assertEquals(Pt(30430, 8698), all.getValue("n10-c1-10-universal").points.last())
        val l2 = all.getValue("n17-c1-universal-4-connected-k11-17").points
        assertEquals(Pt(612, 666), l2.first())
        assertEquals(Pt(980, 0), l2.last())
        assertEquals(Pt(122, 197), all.getValue("n12-c1-11-universal").points.last())
        assertEquals(listOf("https://arxiv.org/abs/2109.10705"), all.getValue("n41-c1-no-9-crossing-family").links.map { it.url })
    }
}
