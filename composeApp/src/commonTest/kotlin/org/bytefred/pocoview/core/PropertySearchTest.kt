package org.bytefred.pocoview.core

import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PropertySearchTest {
    /** 8 points in convex position: every 4-subset is convex, C(8, 4) = 70 crossings. */
    private val octagon = listOf(
        Pt(10, 0), Pt(7, 7), Pt(0, 10), Pt(-7, 7), Pt(-10, 0), Pt(-7, -7), Pt(0, -10), Pt(7, -7),
    )

    private suspend fun crossings(p: List<Pt>): Long = countConvexPolygons(p, 4, false, null).total

    private class Run(val values: List<Long>, val improved: Boolean)

    /**
     * Runs [flow] on the octagon, checking every report (general position, within COORD_LIMIT,
     * reported value = true count, never worse than the start), and stops at the first
     * improvement or after [maxReports] reports.
     */
    private suspend fun runOnOctagon(flow: PropertySearchFlow, seed: Int, maxReports: Int, values: Map<String, Any> = emptyMap()): Run {
        val start = crossings(octagon)
        assertEquals(70, start)
        val seen = ArrayList<Long>()
        var improved = false
        flow.run(octagon, ::crossings, values, Random(seed)) { p, v ->
            assertEquals(octagon.size, p.size, flow.id)
            assertTrue(isGeneralPosition(p), "${flow.id}: reported a configuration not in general position")
            assertTrue(p.all { it.inRange() }, "${flow.id}: coordinates beyond COORD_LIMIT")
            assertEquals(crossings(p), v, "${flow.id}: reported value is not the count")
            assertTrue(v <= start, "${flow.id}: reported $v > start $start")
            seen += v
            if (v < start) improved = true
            !improved && seen.size < maxReports
        }
        return Run(seen, improved)
    }

    @Test
    fun registryHasDistinctIdsAndTypedDefaults() {
        assertEquals(PROPERTY_SEARCH_FLOWS.size, PROPERTY_SEARCH_FLOWS.map { it.id }.toSet().size)
        for (f in PROPERTY_SEARCH_FLOWS) assertEquals(f.params.size, f.params.map { it.name }.toSet().size, f.id)
        assertEquals(30, PointWalkFlow.params.first { it.name == "goodbits" }.default)
        assertEquals(10000, BrownianMinimizationFlow.params.first { it.name == "maxtrials" }.default)
    }

    @Test
    fun everyFlowImprovesCrossingsFromConvexPosition() = runTest {
        for (flow in PROPERTY_SEARCH_FLOWS) for (seed in 1..3) {
            val r = runOnOctagon(flow, seed, maxReports = 2000)
            assertTrue(r.improved, "${flow.id} (seed $seed): no improvement within ${r.values.size} reports")
        }
    }

    @Test
    fun pointWalkVariantsAndBrownianWithoutMindiam() = runTest {
        val variants = listOf(
            PointWalkFlow to mapOf<String, Any>("acceleration" to 1.0, "goodbits" to 12),
            PointWalkLocCrFlow to mapOf<String, Any>("acceleration" to 0.5),
            BrownianMinimizationFlow to mapOf<String, Any>("mindiam" to false),
            BrownianMinimizationFlow to mapOf<String, Any>("mindiam" to false, "continue" to true, "bounddiam" to -1),
        )
        for ((flow, values) in variants) {
            val r = runOnOctagon(flow, 7, maxReports = 2000, values = values)
            assertTrue(r.improved, "${flow.id} $values: no improvement")
        }
    }

    @Test
    fun simulatedAnnealingReportsTrueCounts() = runTest {
        var reports = 0
        PointWalkFlow.run(octagon, ::crossings, mapOf("siman" to true, "T0" to 10.0), Random(3)) { p, v ->
            assertTrue(isGeneralPosition(p))
            assertEquals(crossings(p), v)
            ++reports < 300
        }
        assertEquals(300, reports)
    }

    @Test
    fun everyFlowStopsWhenReportReturnsFalse() = runTest {
        for (flow in PROPERTY_SEARCH_FLOWS) {
            var calls = 0
            flow.run(octagon, ::crossings, emptyMap(), Random(5)) { _, _ ->
                calls++
                false
            }
            assertEquals(1, calls, flow.id)
        }
    }

    @Test
    fun pointMoveReportsEachOrderTypeOnce() = runTest {
        val seen = ArrayList<String>()
        PointMoveFlow.run(octagon, ::crossings, emptyMap(), Random(1)) { p, _ ->
            seen += orderTypeSignature(p)
            true
        }
        assertEquals(seen.size, seen.toSet().size)
        assertTrue(seen.size > 1)
    }
}
