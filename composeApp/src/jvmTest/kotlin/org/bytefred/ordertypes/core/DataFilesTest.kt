package org.bytefred.ordertypes.core

import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/** Checks against the bundled Graz order type database and PoSeZo example sets (data/). */
class DataFilesTest {
    private val data = generateSequence(File("").absoluteFile) { it.parentFile }
        .map { File(it, "data") }.first { File(it, "otypes").isDirectory }

    private fun load(rel: String) = File(data, rel).let { readPointSets(it.name, it.readBytes()) }

    @Test
    fun numberOfOrderTypes() {
        val expected = mapOf(3 to 1, 4 to 2, 5 to 3, 6 to 16, 7 to 135, 8 to 3315)
        for ((n, count) in expected) assertEquals(count, load("otypes/otypes0$n.b08").size, "n=$n")
    }

    @Test
    fun minimumCrossingNumbers() = runTest {
        // rectilinear crossing number cr(K_n): 0, 0, 1, 3, 9, 19 for n = 3..8
        val expected = mapOf(3 to 0L, 4 to 0L, 5 to 1L, 6 to 3L, 7 to 9L, 8 to 19L)
        for ((n, cr) in expected) {
            val min = load("otypes/otypes0$n.b08").minOf { countConvexPolygons(it, 4, false, null).total }
            assertEquals(cr, min, "n=$n")
        }
    }

    @Test
    fun posezoExamples() = runTest {
        suspend fun count(file: String, k: Int, empty: Boolean) =
            countConvexPolygons(load("posezo/$file").single(), k, empty, null).total
        assertEquals(1L, count("n10_c1_1_convex_5_hole.psz", 5, true))
        assertEquals(6L, count("n14_c1_6_convex_5_holes.psz", 5, true))
        assertEquals(9L, count("n15_c1_9_convex_5_holes.psz", 5, true))
        assertEquals(0L, count("n29_c1_no_convex_6_hole.psz", 6, true))
        assertEquals(153L, count("n12_c1_min_crossing_number_153.psz", 4, false))
        assertEquals(1029L, count("n18_c1_min_crossing_number_1029.psz", 4, false))
    }
}
