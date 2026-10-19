package org.bytefred.pocoview.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.bytefred.pocoview.core.CellHopParams
import org.bytefred.pocoview.core.PointFormat
import org.bytefred.pocoview.core.Pt
import org.bytefred.pocoview.core.orderTypeSignature
import org.bytefred.pocoview.core.writePointSets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppStateTest {
    private val square = listOf(Pt(0, 0), Pt(10, 0), Pt(10, 10), Pt(0, 10))
    private val pentagon = listOf(Pt(0, 0), Pt(10, 0), Pt(13, 9), Pt(5, 15), Pt(-3, 9))

    private fun state() = AppState(CoroutineScope(SupervisorJob() + Dispatchers.Default))

    private fun AppState.load(p: List<Pt>) = open("t.asc", writePointSets(PointFormat.ASC, listOf(p)))

    private fun AppState.done(id: String) = assertIs<PropState.Done>(results[id], "$id: ${results[id]}").result

    @Test
    fun countAndIncidentMode() = runBlocking<Unit> {
        val s = state()
        s.load(pentagon + Pt(5, 6))
        s.awaitIdle()
        val total = s.done("hole3").count
        assertNull(s.done("hole3").incident)
        s.setToggle("hole3", PropToggle(count = true, draw = true))
        s.changeHighlight(true)
        s.changeIncidentOnly(true)
        s.select(5)
        s.awaitIdle()
        val r = s.done("hole3")
        assertEquals(total, r.count)
        // the interior point forms an empty triangle with every pair of hull points: C(5,2)
        assertEquals(10L, r.incident)
        assertTrue(r.structures!!.all { 5 in it.vertices })
    }

    @Test
    fun countModeComputesNoStructures() = runBlocking<Unit> {
        val s = state()
        s.load(pentagon)
        s.changeHighlight(false)
        s.awaitIdle()
        assertNull(s.done("hole5").structures)
        assertEquals(1L, s.done("hole5").count)
    }

    @Test
    fun dragIsOneUndoStepAndFixOrderTypeRejects() {
        val s = state()
        s.load(square)
        s.beginDrag(0)
        assertTrue(s.dragTo(0, Pt(1, 1)))
        assertTrue(s.dragTo(0, Pt(2, 1)))
        s.undo()
        assertEquals(square, s.points)
        s.redo()
        assertEquals(Pt(2, 1), s.points[0])

        s.fixOrderType = true
        val sig = orderTypeSignature(s.points)
        s.beginDrag(0)
        // across the diagonal through points 1 and 3: changes the order type
        assertFalse(s.dragTo(0, Pt(9, 9)))
        assertEquals(sig, orderTypeSignature(s.points))
    }

    @Test
    fun addRejectsCollinearAndDeleteKeepsSelectionValid() {
        val s = state()
        s.load(square)
        s.addPoint(Pt(5, 5)) // on both diagonals
        assertEquals(4, s.points.size)
        s.addPoint(Pt(5, 3))
        assertEquals(5, s.points.size)
        assertEquals(4, s.selected)
        s.deleteSelected()
        assertNull(s.selected)
        assertEquals(square, s.points)
    }

    @Test
    fun multiSetFileNavigationAndSaveAll() {
        val s = state()
        s.open("two.asc", writePointSets(PointFormat.ASC, listOf(square, pentagon)))
        assertEquals(2, s.sets.size)
        s.showSet(1)
        assertEquals(pentagon, s.points)
        s.beginDrag(0)
        s.dragTo(0, Pt(1, 0))
        s.showSet(0)
        val all = s.encode(PointFormat.ASC, allSets = true)!!.decodeToString()
        assertTrue(all.contains("1 0\n"), "edited set 2 must be saved: $all")
    }

    @Test
    fun saveOutOfRangeBinaryGivesMessage() {
        val s = state()
        s.load(listOf(Pt(0, 0), Pt(1000, 0), Pt(0, 1000)))
        assertNull(s.encode(PointFormat.PB08, allSets = false))
        assertTrue(s.message!!.contains("does not fit"))
    }

    @Test
    fun collinearSetSkipsProperties() = runBlocking<Unit> {
        val s = state()
        s.load(listOf(Pt(0, 0), Pt(1, 1), Pt(2, 2), Pt(5, 0)))
        s.awaitIdle()
        assertEquals(setOf(0, 1, 2), s.collinear)
        assertIs<PropState.Skipped>(s.results["hole3"])
    }

    @Test
    fun coordinateOptimizersKeepOrderType() = runBlocking<Unit> {
        val s = state()
        s.randomSet(10, 1_000_000)
        val sig = orderTypeSignature(s.points)
        s.coordSettings = CoordSettings(iter2 = 200)
        for (m in MinimizeMethod.entries) {
            s.minimizeMethod = m
            s.minimize()
            s.awaitIdle()
            assertEquals(sig, orderTypeSignature(s.points), m.name)
        }
        for (m in BeautifyMethod.entries) {
            s.beautifyMethod = m
            s.beautify()
            s.awaitIdle()
            assertEquals(sig, orderTypeSignature(s.points), m.name)
        }
        assertTrue(s.points.maxOf { maxOf(it.x, it.y) } < 1_000_000)
    }

    @Test
    fun undoDoesNotCrossSets() {
        // regression: undo after "next" restored set 0 into set 1
        val s = state()
        s.open("two.asc", writePointSets(PointFormat.ASC, listOf(square, pentagon)))
        s.beginDrag(0)
        s.dragTo(0, Pt(1, 0))
        s.showSet(1)
        assertFalse(s.canUndo)
        s.undo()
        assertEquals(pentagon, s.points)
        s.showSet(0)
        assertEquals(Pt(1, 0), s.points[0])
    }

    @Test
    fun rejectedDragRecordsNoUndoAndNoDuplicates() {
        val s = state()
        s.load(square)
        s.fixOrderType = true
        s.beginDrag(0)
        assertFalse(s.dragTo(0, Pt(9, 9)))
        assertFalse(s.canUndo)
        s.fixOrderType = false
        assertFalse(s.dragTo(0, Pt(10, 0)), "must not move onto another point")
    }

    @Test
    fun binarySaveNameReadsBack() {
        val s = state()
        s.open("otypes04.b08", writePointSets(PointFormat.PB08, listOf(square, square)))
        s.addPoint(Pt(5, 3))
        val name = s.saveName(PointFormat.PB08)
        val bytes = s.encode(PointFormat.PB08, allSets = false)!!
        val back = state()
        back.open(name, bytes)
        assertEquals(s.points, back.points)
        // sets with different n cannot share one binary file
        assertNull(s.encode(PointFormat.PB08, allSets = true))
    }

    @Test
    fun editCancelsRunningOperation() = runBlocking<Unit> {
        val s = state()
        s.randomSet(12, 1000)
        s.optimizeProperty = "crossings"
        s.searchFlowId = "brownian"
        s.loop = true
        s.searchProperty()
        assertTrue(s.busy != null)
        s.addPoint(Pt(100_000, 100_001))
        assertNull(s.busy)
        val after = s.points
        s.awaitIdle()
        assertEquals(after, s.points)
    }

    @Test
    fun badFilesGiveMessagesNotCrashes() {
        val s = state()
        val before = s.points
        s.open("bad.asc", "-3\n1 2\n".encodeToByteArray())
        s.open("x0.b08", ByteArray(4))
        s.open("bad.json", "[[1.5, 2]]".encodeToByteArray())
        s.applyCoordinatesText("-3\n")
        assertEquals(before, s.points)
        assertTrue(s.message != null)
    }

    @Test
    fun incidentOnlyOffCountsNoIncident() = runBlocking<Unit> {
        val s = state()
        s.load(pentagon + Pt(5, 6))
        s.changeHighlight(true)
        s.setToggle("hole3", PropToggle(count = true, draw = true))
        s.select(5)
        s.awaitIdle()
        assertNull(s.done("hole3").incident)
        assertEquals(s.done("hole3").count.toInt(), s.done("hole3").structures!!.size)
        s.setShown("hole3", false)
        assertNull(s.results["hole3"])
    }

    @Test
    fun cellHopMinimizerRunsAsOneUndoStep() = runBlocking<Unit> {
        val s = state()
        s.load(listOf(Pt(900, 500), Pt(783, 783), Pt(500, 900), Pt(217, 783), Pt(100, 500), Pt(217, 217), Pt(500, 100), Pt(783, 217)))
        s.cellHop("crossings", CellHopParams())
        s.awaitIdle()
        assertTrue(s.message!!.startsWith("Crossing number: 70 →"), s.message)
        s.undo()
        assertFalse(s.canUndo)
    }

    @Test
    fun failedCellHopLeavesNoUndoStep() = runBlocking<Unit> {
        val s = state()
        // triangle + 2 interior points: 1 crossing, the optimum for n = 5
        s.load(listOf(Pt(0, 0), Pt(1000, 0), Pt(500, 1000), Pt(450, 300), Pt(560, 320)))
        val before = s.points
        s.cellHop("crossings", CellHopParams(trials = 1))
        s.awaitIdle()
        assertTrue(s.message!!.contains("no configuration below 1"), s.message)
        assertEquals(before, s.points)
        assertFalse(s.canUndo)
    }

    @Test
    fun propertySearchStopsAtFirstImprovement() = runBlocking<Unit> {
        val s = state()
        for (flow in listOf("pointwalk", "brownian")) {
            // convex octagon: 70 crossings, the maximum
            s.load(listOf(Pt(900, 500), Pt(783, 783), Pt(500, 900), Pt(217, 783), Pt(100, 500), Pt(217, 217), Pt(500, 100), Pt(783, 217)))
            s.optimizeProperty = "crossings"
            s.searchFlowId = flow
            s.searchProperty()
            s.awaitIdle()
            assertTrue(s.message!!.startsWith("Crossing number: 70 →"), "$flow: ${s.message}")
            assertTrue(s.canUndo)
        }
    }
}
