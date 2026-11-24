package org.bytefred.pocoview.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ExamplesTest {
    @Test
    fun groupsAndCounts() {
        val catalog = loadExamples()
        assertEquals(listOf("database" to "Database", "extremal" to "Extremal point sets"), catalog.supergroups.map { it.id to it.title })
        val (database, extremal) = catalog.supergroups
        assertEquals(listOf("Order types" to 157), database.groups.map { g -> g.title to g.orderTypes.sumOf { it.sets.size } })
        assertEquals(listOf("Order type database"), database.links.map { it.title })
        assertEquals(
            listOf(
                "Holes and convex gons" to 9, "Crossing number" to 2, "Bicolored sets" to 6,
                "Crossing families" to 5, "Universal point sets" to 6, "Miscellaneous" to 3,
            ),
            extremal.groups.map { it.title to it.examples.size },
        )
        assertTrue(extremal.groups.all { it.orderTypes.isEmpty() })
        for (g in catalog.groups) for (e in g.examples) assertEquals(g.title, e.group, e.name)
        assertEquals(listOf("PoSeZo (Point Set Zoo)"), catalog.links.map { it.title })
        val n13 = catalog.examples.first { it.name == "n13-c1-min-convex-3-4-5-holes" }
        assertEquals(
            listOf("https://scheucher.dev/files/theses/bachelors_thesis_2013.pdf",
                "https://www.eurogiga-compose.eu/posezo/n13_c1_min_convex_3_4_5_holes/n13_c1_min_convex_3_4_5_holes.php"),
            n13.links.map { it.url },
        )
        // no link to the PoSeZo main page per example, no dead links
        for (e in catalog.examples) for (l in e.links) {
            assertTrue(l.url != "https://www.eurogiga-compose.eu/posezo.php" && "markov/sixgon" !in l.url, "${e.name}: ${l.url}")
        }
    }

    @Test
    fun orderTypeDatabaseIsBundled() {
        val files = loadExamples().orderTypes
        // the numbers of order types of 3..7 points (file size / (n * 2 bytes))
        assertEquals(listOf(3 to 1, 4 to 2, 5 to 3, 6 to 16, 7 to 135), files.map { it.n to it.sets.size })
        for (f in files) {
            assertEquals("otypes0${f.n}.b08", f.file)
            for (s in f.sets) assertEquals(f.n, s.size)
        }
    }

    @Test
    fun indexSchema() {
        val set = """{"name": "n3-c1-t", "description": "d", "group": "G", "points": [[0, 0], [1, 0], [0, 1]]}"""
        val index = """{"supergroups": [{"id": "s", "title": "S", "description": "about S",
            "links": [{"title": "site", "url": "https://example.org"}],
            "groups": [{"id": "g", "title": "G", "files": ["a/t.json", "b/otypes03.b08"]}, {"id": "h", "title": "H", "files": []}]}]}"""
        val c = parseIndex(index, mapOf("a/t.json" to set), mapOf("b/otypes03.b08" to "000080ff00ff"))
        val sg = c.supergroups.single()
        assertEquals("about S", sg.description)
        assertEquals(listOf("site"), sg.links.map { it.title })
        assertEquals(emptyList(), c.links)
        val g = sg.groups.first()
        assertEquals(null, g.description)
        assertEquals(listOf("n3-c1-t"), g.examples.map { it.name })
        assertEquals(listOf(3), g.orderTypes.map { it.n })
        // ids must be unique (they are the tree keys)
        assertFailsWith<FormatException> { parseIndex(index.replace("\"id\": \"h\"", "\"id\": \"g\""), mapOf("a/t.json" to set), mapOf("b/otypes03.b08" to "000080ff00ff")) }
        assertFailsWith<FormatException> { parseIndex(index, emptyMap(), mapOf("b/otypes03.b08" to "000080ff00ff")) }
        // an empty order type file is a format error, not a NoSuchElementException
        val empty = assertFailsWith<FormatException> { parseIndex(index, mapOf("a/t.json" to set), mapOf("b/otypes03.b08" to "")) }
        assertEquals("No point set found in otypes03.b08", empty.message)
    }

    @Test
    fun propertiesKeyIsParsedAndChecked() {
        val base = """{"name": "n3-c1-t", "description": "d", "group": "g", "points": [[0, 0], [1, 0], [0, 1]]"""
        assertEquals(emptyList(), parseExample("$base}", "t.json").properties)
        assertEquals(listOf("triangulations"), parseExample("$base, \"properties\": [\"triangulations\"]}", "t.json").properties)
        assertFailsWith<FormatException> { parseExample("$base, \"properties\": [\"nope\"]}", "t.json") }
    }

    @Test
    fun everySetIsDistinctAndInGeneralPosition() {
        for (e in loadExamples().examples) {
            assertEquals(e.points.size, e.points.map { it.x to it.y }.toSet().size, "duplicates in ${e.name}")
            assertTrue(isGeneralPosition(e.points), "not in general position: ${e.name}")
            assertTrue(e.points.all { it.inRange() }, "out of range: ${e.name}")
            val n = Regex("^n0*(\\d+)").find(e.name)!!.groupValues[1].toInt()
            assertEquals(n, e.points.size, "n of ${e.name}")
        }
    }

    @Test
    fun spotChecks() {
        val all = loadExamples().examples.associateBy { it.name }
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
