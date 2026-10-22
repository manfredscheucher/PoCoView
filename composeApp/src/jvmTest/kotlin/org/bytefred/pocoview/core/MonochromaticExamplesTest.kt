package org.bytefred.pocoview.core

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.measureTime

/** The bicolored examples' claims, plus reference counts from old pyotlib (probe_mono_pyotlib.py). */
class MonochromaticExamplesTest {
    private suspend fun count(p: List<Pt>, id: String): Long {
        val prop = ALL_PROPERTIES.first { it.id == id }
        return prop.evaluate(p, null, false, Ticker()).count
    }

    private val expected = mapOf(
        "n8-c2-no-monochromatic-3-hole" to mapOf("monohole3" to 0L),
        "n22-c2-no-monochromatic-4-hole" to mapOf(
            "monohole3" to 77L, "monohole4" to 0L, "monogenhole4" to 0L, "monogenhole5" to 0L, "monoisland4" to 0L,
        ),
        "n35-c2-no-monochromatic-4-island" to mapOf(
            "monohole3" to 200L, "monohole4" to 0L, "monoisland4" to 0L, "monogenhole4" to 266L, "monogenhole5" to 296L,
        ),
        "n46-c2-no-monochromatic-convex-4-hole" to mapOf(
            "monohole3" to 401L, "monohole4" to 0L, "monoisland4" to 121L, "monogenhole4" to 901L, "monogenhole5" to 2113L,
        ),
        "n61-c2-no-monochromatic-5-island" to mapOf(
            "monohole3" to 975L, "monohole4" to 244L, "monohole5" to 0L, "monoisland4" to 343L, "monoisland5" to 0L,
            "monogenhole4" to 2069L, "monogenhole5" to 3440L,
        ),
        "n64-c2-no-monochromatic-5-island" to mapOf(
            "monohole3" to 1062L, "monohole4" to 255L, "monohole5" to 0L, "monoisland4" to 361L, "monoisland5" to 0L,
            "monogenhole4" to 2351L, "monogenhole5" to 4163L,
        ),
    )

    @Test
    fun claimsAndPyotlibCounts() = runTest {
        val all = loadExamples().categories.flatMap { it.examples }.associateBy { it.name }
        for ((name, props) in expected) {
            val p = all.getValue(name).points
            for ((id, v) in props) assertEquals(v, count(p, id), "$name $id")
        }
    }

    @Test
    fun timingsOnN64() = runTest {
        val p = loadExamples().categories.flatMap { it.examples }.first { it.name == "n64-c2-no-monochromatic-5-island" }.points
        for (prop in ALL_PROPERTIES.filter { it.id.startsWith("mono") }) {
            lateinit var r: PropertyResult
            val t = measureTime { r = prop.evaluate(p, null, true, Ticker()) }
            println("TIMING n=64 ${prop.id}: count=${r.count} structures=${r.structures?.size} in $t")
        }
    }
}
