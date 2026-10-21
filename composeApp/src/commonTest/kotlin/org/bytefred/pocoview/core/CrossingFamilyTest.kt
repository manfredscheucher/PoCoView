package org.bytefred.pocoview.core

import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CrossingFamilyTest {
    private fun points(json: String) = readJson("{\"points\": $json}")

    private fun assertFamily(p: List<Pt>, f: List<Pair<Int, Int>>) {
        for (i in f.indices) for (j in i + 1 until f.size)
            assertTrue(segmentsCross(p[f[i].first], p[f[i].second], p[f[j].first], p[f[j].second]), "$f")
    }

    /** Largest k such that some k segments pairwise cross, by exhaustive search. */
    private fun bruteForce(p: List<Pt>, incidentTo: Int?): Int {
        val segs = BruteForce.subsets(p.size, 2).toList()
        var best = 0
        fun extend(chosen: List<IntArray>, from: Int) {
            val ok = incidentTo == null || chosen.any { incidentTo in it }
            if (ok) best = maxOf(best, chosen.size)
            for (t in from until segs.size) {
                val s = segs[t]
                if (chosen.all { c -> segmentsCross(p[c[0]], p[c[1]], p[s[0]], p[s[1]]) }) extend(chosen + listOf(s), t + 1)
            }
        }
        extend(emptyList(), 0)
        return best
    }

    @Test
    fun matchesBruteForceOnRandomSets() = runTest {
        val rnd = Random(7)
        repeat(30) {
            val p = randomPointSet(4 + rnd.nextInt(6), 100, rnd)
            val sel = rnd.nextInt(p.size)
            val f = crossingFamilies(p, sel)
            assertFamily(p, f.maximum)
            assertFamily(p, f.incident!!)
            assertTrue(f.incident!!.any { it.first == sel || it.second == sel })
            assertEquals(bruteForce(p, null), f.maximum.size, "$p")
            assertEquals(bruteForce(p, sel), f.incident!!.size, "$p sel=$sel")
        }
    }

    @Test
    fun smallCases() = runTest {
        assertEquals(0, crossingFamilies(listOf(Pt(0, 0)), null).maximum.size)
        assertEquals(1, crossingFamilies(listOf(Pt(0, 0), Pt(1, 0), Pt(0, 1)), null).maximum.size)
        assertEquals(10, crossingFamilies(ConvexFamily.generate(20), null).maximum.size)
    }

    /** Sets from Aichholzer et al., arXiv:2109.10705v2, figures 2, 5, 6, 7. */
    @Test
    fun paperSets() = runTest {
        val sets = mapOf(
            3 to "[[61,66],[61,0],[57,5],[54,9],[47,40],[41,37],[37,44],[30,53],[46,61],[18,55],[55,65],[11,60],[9,61],[0,66]]",
            4 to "[[595,113],[0,0],[2,7],[8,21],[49,179],[473,131],[465,139],[96,225],[198,205],[115,280],[235,242],[103,321],[242,266],[584,118],[135,339],[150,393],[104,531],[105,537],[56,595],[55,598]]",
            5 to "[[209,744],[670,181],[651,180],[623,177],[597,176],[574,176],[231,699],[244,673],[213,734],[383,321],[333,404],[391,244],[301,488],[365,232],[211,737],[287,417],[229,278],[219,370],[178,191],[151,203],[31,58],[18,33],[16,28],[6,10],[0,0]]",
            8 to "[[2224,4433],[0,0],[7304,38530],[7545,38066],[8274,36484],[8302,36458],[10655,34228],[10691,34391],[10528,33497],[10556,33588],[8258,20319],[8533,20186],[4139,8748],[5417,13389],[10911,25180],[10855,25659],[7366,16484],[7348,16387],[13099,28954],[13294,28632],[12196,26876],[12735,26620],[11565,24122],[11659,24037],[10835,22540],[10840,22180],[15773,25151],[16106,24769],[15522,21847],[15566,21869],[17009,22875],[16974,22938],[28133,19374],[28398,19171],[28665,18969],[28950,18792],[37654,17997],[37079,18180],[38254,17739],[37758,17959],[2401,48451]]",
        )
        for ((cf, json) in sets) {
            val p = points(json)
            val f = crossingFamilies(p, 0)
            assertEquals(cf, f.maximum.size, "n=${p.size}")
            assertFamily(p, f.maximum)
            assertFamily(p, f.incident!!)
        }
    }

    @Test
    fun propertyDrawsOneFamilyAsSegments() = runTest {
        val prop = ALL_PROPERTIES.single { it.id == "crossfam" }
        assertTrue(prop.emphasized)
        val p = ConvexFamily.generate(8)
        val r = prop.evaluate(p, null, true, Ticker())
        assertEquals(4L, r.count)
        assertEquals(null, r.incident)
        val s = r.structures!!.single()
        assertEquals(Shape.SEGMENTS, s.shape)
        assertEquals(8, s.vertices.size)
        val ri = prop.evaluate(p, 3, true, Ticker())
        assertEquals(4L, ri.incident)
        assertTrue(3 in ri.structures!!.single().vertices.toList())
    }
}
