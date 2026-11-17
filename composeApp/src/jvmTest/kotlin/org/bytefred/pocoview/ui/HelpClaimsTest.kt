package org.bytefred.pocoview.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.bytefred.pocoview.core.ALL_PROPERTIES
import org.bytefred.pocoview.core.CROSSING_FAMILY_MAX_N
import org.bytefred.pocoview.core.MonochromaticGeneralHoleProperty
import org.bytefred.pocoview.core.MonochromaticProperty
import org.bytefred.pocoview.core.PolygonProperty
import org.bytefred.pocoview.core.STRUCTURE_LIMIT
import org.bytefred.pocoview.core.TriangulationProperty
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

/** The numbers the Help pages state, checked against the code (as ktoy's HelpClaimsTest). */
class HelpClaimsTest {
    private val general = helpText(HelpPage.GENERAL).joinToString(" ")
    private val properties = helpText(HelpPage.PROPERTIES).joinToString(" ")

    private fun assertSays(text: String, claim: String) = assertTrue(claim in text, "Help should say \"$claim\"")

    /** "k = 3 to 7" for a range of k, "k = 4, 5" for two values. */
    private fun kText(ks: List<Int>): String =
        if (ks.size == 2) "k = ${ks[0]}, ${ks[1]}" else "k = ${ks.first()} to ${ks.last()}"

    @Test
    fun limits() {
        assertSays(properties, "At most $STRUCTURE_LIMIT structures per property")
        assertSays(properties, "Up to $CROSSING_FAMILY_MAX_N points")
        assertSays(properties, "only up to ${TriangulationProperty().maxN} points")
        assertSays(general, "up to $LAMBDA_MATRIX_MAX_N points, the λ matrix")
    }

    @Test
    fun kRanges() {
        val polygons = ALL_PROPERTIES.filterIsInstance<PolygonProperty>()
        val mono = ALL_PROPERTIES.filterIsInstance<MonochromaticProperty>()
        assertSays(properties, "k-holes: empty convex k-gons (no point inside), " + kText(polygons.filter { it.emptyOnly }.map { it.k }))
        assertSays(properties, "Convex k-gons: k points in convex position, " + kText(polygons.filter { !it.emptyOnly }.map { it.k }))
        assertSays(properties, "Convex k-holes (" + kText(mono.filter { it.holesOnly }.map { it.k }) + ")")
        assertSays(properties, "general k-holes (" + kText(ALL_PROPERTIES.filterIsInstance<MonochromaticGeneralHoleProperty>().map { it.k }) + ")")
        assertSays(properties, "k-island (" + kText(mono.filter { !it.holesOnly }.map { it.k }) + ")")
    }

    @BeforeTest
    fun clearPrefs() = clearInMemoryPrefs()

    @Test
    fun widgets() {
        assertSays(general, "keys 1 to ${Widget.entries.size}")
        val maxColumns = AppState(CoroutineScope(SupervisorJob() + Dispatchers.Default)).maxColumns
        assertSays(general, "By default at most ${listOf("one", "two", "three", "four")[maxColumns - 1]} are open")
    }
}
