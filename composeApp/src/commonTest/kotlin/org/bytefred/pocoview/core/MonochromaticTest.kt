package org.bytefred.pocoview.core

import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MonochromaticTest {
    private fun randomColoured(n: Int, rnd: Random): List<Pt> {
        while (true) {
            val p = randomPointSet(n, 60, rnd).map { it.copy(color = if (rnd.nextBoolean()) "R" else "B") }
            if (isGeneralPosition(p)) return p
        }
    }

    private fun inside(p: List<Pt>, hull: List<Int>, q: Int) =
        hull.indices.all { i -> orient(p[hull[i]], p[hull[(i + 1) % hull.size]], p[q]) > 0 }

    /** k-subsets I, all of one colour, with no other point inside conv(I) (and convex position for holes). */
    private fun bruteMono(p: List<Pt>, k: Int, holesOnly: Boolean, selected: Int? = null): Long =
        BruteForce.subsets(p.size, k).count { s ->
            val h = convexHull(p, s.toList())
            s.map { p[it].color }.toSet().size == 1 &&
                (selected == null || selected in s) &&
                (!holesOnly || h.size == k) &&
                p.indices.none { q -> q !in s && inside(p, h, q) }
        }.toLong()

    private fun segmentsCross(a: Pt, b: Pt, c: Pt, d: Pt) =
        orient(a, b, c) * orient(a, b, d) < 0 && orient(c, d, a) * orient(c, d, b) < 0

    private fun permutations(l: List<Int>): List<List<Int>> =
        if (l.size <= 1) listOf(l) else l.flatMap { x -> permutations(l - x).map { listOf(x) + it } }

    /** Even-odd test, exact (general position: no point on an edge or a vertex's horizontal). */
    private fun insidePolygon(p: List<Pt>, cyc: List<Int>, z: Pt): Boolean {
        var c = false
        for (i in cyc.indices) {
            val a = p[cyc[i]]
            val b = p[cyc[(i + cyc.size - 1) % cyc.size]]
            if ((a.y > z.y) != (b.y > z.y)) {
                // z left of the edge crossing at height z.y
                val s = orient(if (a.y < b.y) a else b, if (a.y < b.y) b else a, z)
                if (s > 0) c = !c
            }
        }
        return c
    }

    /** Simple empty monochromatic polygons with k vertices, each counted once. */
    private fun bruteGeneral(p: List<Pt>, k: Int): Long {
        var count = 0L
        for (s in BruteForce.subsets(p.size, k)) {
            if (s.map { p[it].color }.toSet().size != 1) continue
            val seen = HashSet<Set<Set<Int>>>()
            for (perm in permutations(s.drop(1))) {
                val cyc = listOf(s[0]) + perm
                if (!seen.add(cyc.indices.map { setOf(cyc[it], cyc[(it + 1) % k]) }.toSet())) continue
                val simple = (0 until k).all { i ->
                    (i + 2 until k).all { j ->
                        (j + 1) % k == i || !segmentsCross(p[cyc[i]], p[cyc[(i + 1) % k]], p[cyc[j]], p[cyc[(j + 1) % k]])
                    }
                }
                if (simple && p.indices.none { it !in s && insidePolygon(p, cyc, p[it]) }) count++
            }
        }
        return count
    }

    @Test
    fun dpMatchesBruteForce() = runTest {
        val rnd = Random(11)
        repeat(30) { round ->
            val n = 5 + round % 7
            val p = randomColoured(n, rnd)
            val sel = rnd.nextInt(n)
            for (k in 3..6) for (holesOnly in listOf(true, false)) {
                val c = countMonochromatic(p, k, holesOnly, sel)
                val what = "k=$k holes=$holesOnly $p"
                assertEquals(bruteMono(p, k, holesOnly), c.total, what)
                assertEquals(bruteMono(p, k, holesOnly, sel), c.incident, "incident $what")
            }
        }
    }

    @Test
    fun generalHolesMatchBruteForce() = runTest {
        val rnd = Random(5)
        repeat(15) { round ->
            val p = randomColoured(5 + round % 5, rnd)
            for (k in 4..5) assertEquals(bruteGeneral(p, k), monochromaticGeneralHoles(p, k).size.toLong(), "k=$k $p")
        }
    }

    @Test
    fun enumerationMatchesCounts() = runTest {
        val rnd = Random(3)
        repeat(12) { round ->
            val n = 7 + round % 5
            val p = randomColoured(n, rnd)
            for (sel in listOf(null, rnd.nextInt(n))) for (k in 3..6) for (holesOnly in listOf(true, false)) {
                val c = countMonochromatic(p, k, holesOnly, sel)
                val list = enumerateMonochromatic(p, k, holesOnly, sel, 10_000)
                assertEquals(if (sel == null) c.total else c.incident, list.size.toLong())
                for (poly in list) {
                    // the hull vertices, in CCW order
                    assertEquals(convexHull(p, poly.toList()).size, poly.size)
                    for (i in poly.indices) assertTrue(orient(p[poly[i]], p[poly[(i + 1) % poly.size]], p[poly[(i + 2) % poly.size]]) > 0)
                    val members = p.indices.filter { it in poly || inside(p, poly.toList(), it) }
                    assertEquals(k, members.size)
                    assertEquals(1, members.map { p[it].color }.toSet().size)
                    if (sel != null) assertTrue(sel in members)
                }
                assertEquals(list.size, list.map { it.toList() }.toSet().size)
            }
        }
    }

    @Test
    fun uncolouredSetsAreRejected() = runTest {
        val p = listOf(Pt(0, 0, "R"), Pt(10, 0, "R"), Pt(5, 9))
        val prop = ALL_PROPERTIES.first { it.id == "monohole3" }
        assertEquals("needs a coloured set", prop.skipReason(p))
        assertNull(prop.skipReason(p.map { it.copy(color = "R") }))
        assertFailsWith<IllegalArgumentException> { prop.evaluate(p, null, false, Ticker()) }
        assertEquals(1L, prop.evaluate(p.map { it.copy(color = "R") }, null, true, Ticker()).count)
    }

    @Test
    fun propertyIdsAndIncidence() = runTest {
        val ids = ALL_PROPERTIES.map { it.id }
        for (id in listOf("monohole3", "monohole4", "monohole5", "monogenhole4", "monogenhole5", "monoisland4", "monoisland5", "monoisland6"))
            assertTrue(id in ids, id)
        val p = randomColoured(11, Random(8))
        for (prop in ALL_PROPERTIES.filter { it.id.startsWith("mono") }) {
            val all = prop.evaluate(p, null, true, Ticker())
            assertEquals(all.count, all.structures!!.size.toLong(), prop.id)
            val r = prop.evaluate(p, 4, true, Ticker())
            assertEquals(r.incident, r.structures!!.size.toLong(), prop.id)
        }
    }

    /** Reference counts from old pyotlib (scripts/probe/probe_mono_pyotlib.py in pyotlib2, seed 2026). */
    private val pyotlib: List<Pair<List<Pt>, Map<String, Long>>> = listOf(
        listOf(Pt(327, 514, "B"), Pt(974, 524, "B"), Pt(662, 880, "R"), Pt(975, 105, "R"), Pt(905, 228, "R"), Pt(916, 615, "R"), Pt(636, 569, "B"), Pt(430, 802, "R"), Pt(586, 560, "B")) to
            mapOf("monohole3" to 9L, "monoisland3" to 9L, "monohole4" to 1L, "monoisland4" to 2L, "monohole5" to 0L, "monoisland5" to 0L, "monohole6" to 0L, "monoisland6" to 0L, "monogenhole4" to 6L, "monogenhole5" to 1L),
        listOf(Pt(834, 912, "B"), Pt(701, 502, "R"), Pt(695, 321, "B"), Pt(215, 406, "B"), Pt(257, 356, "B"), Pt(951, 365, "R"), Pt(824, 385, "B"), Pt(763, 525, "R"), Pt(653, 78, "B")) to
            mapOf("monohole3" to 11L, "monoisland3" to 11L, "monohole4" to 2L, "monoisland4" to 4L, "monohole5" to 0L, "monoisland5" to 1L, "monohole6" to 0L, "monoisland6" to 0L, "monogenhole4" to 10L, "monogenhole5" to 5L),
        listOf(Pt(472, 432, "B"), Pt(92, 408, "B"), Pt(912, 599, "R"), Pt(567, 510, "R"), Pt(888, 119, "R"), Pt(435, 518, "R"), Pt(821, 768, "R"), Pt(887, 614, "B"), Pt(896, 502, "B"), Pt(407, 938, "R"), Pt(537, 264, "B")) to
            mapOf("monohole3" to 16L, "monoisland3" to 16L, "monohole4" to 3L, "monoisland4" to 6L, "monohole5" to 0L, "monoisland5" to 1L, "monohole6" to 0L, "monoisland6" to 0L, "monogenhole4" to 14L, "monogenhole5" to 5L),
        listOf(Pt(870, 907, "B"), Pt(124, 641, "B"), Pt(717, 467, "B"), Pt(690, 480, "R"), Pt(731, 951, "B"), Pt(982, 558, "B"), Pt(257, 573, "R"), Pt(411, 685, "R"), Pt(315, 561, "R"), Pt(818, 662, "B")) to
            mapOf("monohole3" to 13L, "monoisland3" to 13L, "monohole4" to 2L, "monoisland4" to 5L, "monohole5" to 0L, "monoisland5" to 1L, "monohole6" to 0L, "monoisland6" to 0L, "monogenhole4" to 13L, "monogenhole5" to 5L),
        listOf(Pt(949, 501, "B"), Pt(368, 943, "B"), Pt(204, 266, "B"), Pt(709, 497, "R"), Pt(477, 523, "B"), Pt(785, 846, "B"), Pt(509, 827, "R"), Pt(444, 571, "B"), Pt(679, 929, "B"), Pt(34, 851, "B"), Pt(297, 324, "R"), Pt(468, 938, "R"), Pt(831, 100, "B")) to
            mapOf("monohole3" to 35L, "monoisland3" to 35L, "monohole4" to 10L, "monoisland4" to 16L, "monohole5" to 1L, "monoisland5" to 4L, "monohole6" to 0L, "monoisland6" to 0L, "monogenhole4" to 41L, "monogenhole5" to 53L),
        listOf(Pt(460, 938, "B"), Pt(14, 94, "B"), Pt(0, 462, "R"), Pt(594, 559, "R"), Pt(954, 856, "B"), Pt(85, 624, "B"), Pt(812, 203, "R"), Pt(253, 123, "R"), Pt(635, 45, "B"), Pt(769, 559, "R")) to
            mapOf("monohole3" to 15L, "monoisland3" to 15L, "monohole4" to 6L, "monoisland4" to 6L, "monohole5" to 1L, "monoisland5" to 1L, "monohole6" to 0L, "monoisland6" to 0L, "monogenhole4" to 6L, "monogenhole5" to 1L),
        listOf(Pt(897, 709, "R"), Pt(516, 297, "B"), Pt(86, 427, "R"), Pt(684, 163, "R"), Pt(957, 138, "B"), Pt(802, 801, "B"), Pt(936, 600, "R"), Pt(313, 519, "R"), Pt(421, 399, "B"), Pt(827, 152, "B"), Pt(532, 826, "B"), Pt(201, 385, "B"), Pt(14, 981, "B")) to
            mapOf("monohole3" to 44L, "monoisland3" to 44L, "monohole4" to 28L, "monoisland4" to 35L, "monohole5" to 11L, "monoisland5" to 20L, "monohole6" to 2L, "monoisland6" to 7L, "monogenhole4" to 60L, "monogenhole5" to 85L),
        listOf(Pt(509, 971, "R"), Pt(477, 585, "B"), Pt(633, 824, "R"), Pt(174, 157, "R"), Pt(605, 532, "R"), Pt(211, 444, "R"), Pt(638, 344, "R"), Pt(469, 612, "B"), Pt(238, 694, "R")) to
            mapOf("monohole3" to 14L, "monoisland3" to 14L, "monohole4" to 2L, "monoisland4" to 5L, "monohole5" to 0L, "monoisland5" to 0L, "monohole6" to 0L, "monoisland6" to 0L, "monogenhole4" to 15L, "monogenhole5" to 9L),
    )

    @Test
    fun matchesPyotlib() = runTest {
        for ((p, expected) in pyotlib) for ((id, v) in expected) {
            val k = id.last().digitToInt()
            val got = when {
                id.startsWith("monogenhole") -> monochromaticGeneralHoles(p, k).size.toLong()
                else -> countMonochromatic(p, k, id.startsWith("monohole"), null).total
            }
            assertEquals(v, got, "$id $p")
        }
    }
}
