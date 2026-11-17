package org.bytefred.pocoview.ui

import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.bytefred.pocoview.core.ExportFormat
import org.bytefred.pocoview.core.Pt
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Package D: display settings remembered across starts, unsaved changes, status message lifetime. */
class RememberAndSafeguardTest {
    private val triangle = listOf(Pt(0, 0), Pt(10, 0), Pt(0, 10))

    private fun state() = AppState(CoroutineScope(SupervisorJob() + Dispatchers.Default))

    // the tests share one in-memory pref store (-Dpocoview.prefs=memory): each starts empty
    @BeforeTest
    fun clearPrefs() = clearInMemoryPrefs()

    @Test
    fun displaySettingsSurviveARestart() {
        val s = state()
        s.layoutMode = LayoutMode.PORTRAIT
        s.changeMaxColumns(3)
        s.toggleWidget(Widget.DISPLAY)
        s.pointRadius = 9.5f
        s.lineColor = Color(0xFF123456)
        s.lineAlpha = 0.5f
        s.selectedScalePercent = 250
        s.selectedColor = Color(0xFF00FF00)
        s.incidentLines = IncidentLines.HIGHLIGHT
        s.incidentLineColor = Color(0xFF0000FF)
        s.incidentLineWidth = 3f
        s.showLines = false
        s.showLabels = false
        s.wideWeights = mapOf(null to 3f, Widget.DISPLAY to 0.5f)
        s.stackedWeights = mapOf(Widget.PROPERTIES to 2f)
        s.setToggle("triangulations", PropToggle(count = true, draw = false))
        s.setPropertyColor("hole5", Color(0xFFABCDEF))
        s.saveDisplayPrefs()

        val t = state()
        assertEquals(s.displayPrefs(), t.displayPrefs())
        assertEquals(LayoutMode.PORTRAIT, t.layoutMode)
        assertEquals(listOf(Widget.PROPERTIES, Widget.OPTIMIZE, Widget.DISPLAY), t.openWidgets.toList())
        assertEquals(9.5f, t.pointRadius)
        assertEquals(IncidentLines.HIGHLIGHT, t.incidentLines)
        assertEquals(mapOf(null to 3f, Widget.DISPLAY to 0.5f), t.wideWeights)
        assertEquals(PropToggle(count = true, draw = false), t.toggles["triangulations"])
        assertEquals(Color(0xFFABCDEF), t.colorOf("hole5"))
    }

    @Test
    fun corruptPrefsGiveDefaultsOrClampedValues() {
        savePref(LAYOUT_PREF, "SIDEWAYS")
        savePref(MAX_WIDGETS_PREF, "99")
        savePref(OPEN_WIDGETS_PREF, "FOO,DATA,DATA,,DISPLAY")
        savePref(POINT_SIZE_PREF, "NaN")
        savePref(LINE_ALPHA_PREF, "1e9")
        savePref(SELECTED_SIZE_PREF, "-5")
        savePref(SELECTED_COLOR_PREF, "#12")
        savePref(INCIDENT_WIDTH_PREF, "Infinity")
        savePref(LINES_PREF, "yes")
        savePref(WIDE_WEIGHTS_PREF, "canvas=NaN,PROPERTIES=-1,DATA=1e9,FOO=2,DISPLAY")
        savePref(STACKED_WEIGHTS_PREF, "garbage")
        savePref(togglePrefKey("hole5"), "true")
        savePref(colorPrefKey("hole5"), "red")

        val s = state()
        assertEquals(LayoutMode.AUTO, s.layoutMode)
        assertEquals(Widget.entries.size, s.maxColumns)
        assertEquals(listOf(Widget.DATA, Widget.DISPLAY), s.openWidgets.toList())
        assertEquals(6f, s.pointRadius)
        assertEquals(1f, s.lineAlpha)
        assertEquals(100, s.selectedScalePercent)
        assertEquals(AppState.DEFAULT_SELECTED_COLOR, s.selectedColor)
        assertEquals(2f, s.incidentLineWidth)
        assertTrue(s.showLines)
        assertEquals(mapOf<Widget?, Float>(Widget.DATA to 100f), s.wideWeights)
        assertTrue(s.stackedWeights.isEmpty())
        assertEquals(PropToggle(count = true, draw = true), s.toggles["hole5"])
        assertNull(s.propertyColors["hole5"])
    }

    @Test
    fun noOpenWidgetIsRemembered() {
        val s = state()
        for (w in s.openWidgets.toList()) s.toggleWidget(w)
        s.saveDisplayPrefs()
        assertTrue(state().openWidgets.isEmpty())
    }

    @Test
    fun triangulationsStayOffUnlessTheUserSwitchedThemOn() {
        val s = state()
        assertFalse(s.toggles.getValue("triangulations").count)
        // an example switching them on is not the user's choice
        s.loadPoints("ex", triangle, showProperties = listOf("triangulations"))
        s.saveDisplayPrefs()
        assertFalse(state().toggles.getValue("triangulations").count)
        // the user's choice wins over the default
        state().setShown("triangulations", true)
        assertTrue(state().toggles.getValue("triangulations").count)
    }

    @Test
    fun resetToDefaultsForgetsTheStoredSettings() {
        val s = state()
        s.pointRadius = 15f
        s.layoutMode = LayoutMode.LANDSCAPE
        s.changeMaxColumns(3)
        s.toggleWidget(Widget.DATA)
        s.saveDisplayPrefs()
        assertEquals(15f, state().pointRadius)

        s.touchWidget(Widget.DATA)
        s.resetDisplay()
        val t = state()
        assertEquals(6f, t.pointRadius)
        assertEquals(LayoutMode.AUTO, t.layoutMode)
        assertNull(loadPref(POINT_SIZE_PREF))
        // the widgets the reset closed (max. 2) are stored as usual
        assertEquals(s.openWidgets.toList(), t.openWidgets.toList())
        assertEquals(2, t.openWidgets.size)
    }

    @Test
    fun replaceAsksOnlyWithUnsavedChanges() {
        val s = state()
        var runs = 0
        // the start set and a fresh load are clean: the action runs right away
        s.confirmReplace("Load") { runs++ }
        assertEquals(1, runs)
        s.loadPoints("t", triangle)
        s.confirmReplace("Load") { runs++ }
        assertEquals(2, runs)
        assertNull(s.pendingReplace)

        s.addPoint(Pt(7, 7))
        assertTrue(s.unsavedChanges)
        s.confirmReplace("Import") { runs++ }
        assertEquals(2, runs)
        assertEquals("Import", s.pendingReplace?.action)
        s.pendingReplace = null

        // undo back to the loaded points: nothing to lose
        s.undo()
        assertFalse(s.unsavedChanges)
        s.confirmReplace("Load") { runs++ }
        assertEquals(3, runs)
    }

    @Test
    fun savingMakesTheSetsClean() {
        val s = state()
        s.deletePoint(0)
        s.beginSave(ExportFormat.JSON)
        s.fileSaved(null)
        assertTrue(s.unsavedChanges, "a cancelled save saves nothing")
        s.beginSave(ExportFormat.TXT)
        s.fileSaved("points.txt")
        assertFalse(s.unsavedChanges)
        assertEquals("Saved points.txt", s.message)
        // edits in another set of a multi-set file count too
        s.loadSets("two", listOf(triangle, triangle.map { Pt(it.x + 1, it.y) }))
        s.showSet(1)
        assertFalse(s.unsavedChanges)
        s.deletePoint(0)
        s.showSet(0)
        assertTrue(s.unsavedChanges)
    }

    @Test
    fun closeAsksOnlyWithUnsavedChanges() {
        val s = state()
        var quits = 0
        s.requestClose { quits++ }
        assertEquals(1, quits)

        s.deletePoint(0)
        s.requestClose { quits++ }
        assertNotNull(s.pendingClose)
        s.cancelClose()
        assertNull(s.pendingClose)
        assertEquals(1, quits)

        s.requestClose { quits++ }
        s.discardAndClose()
        assertEquals(2, quits)

        // Save…: a cancelled save cancels the close, a successful one quits
        s.requestClose { quits++ }
        s.beginSave(ExportFormat.JSON)
        s.fileSaved(null)
        assertNull(s.pendingClose)
        assertEquals(2, quits)
        s.requestClose { quits++ }
        s.beginSave(ExportFormat.JSON)
        s.fileSaved("points.json")
        assertEquals(3, quits)
        assertNull(s.pendingClose)
    }

    @Test
    fun infoMessagesDisappearResultsStay() {
        val scope = TestScope()
        val s = AppState(scope)
        s.loadPoints("t", triangle)
        assertTrue(s.status!!.transient)
        scope.advanceTimeBy(AppState.INFO_DURATION_MS - 100)
        assertNotNull(s.message)
        scope.advanceTimeBy(200)
        scope.runCurrent()
        assertNull(s.message)

        // an optimizer's result (here: it cannot run) stays
        s.optimizeProperty = "triangulations"
        s.loadPoints("h", org.bytefred.pocoview.core.HortonFamily.generate(64))
        s.minimizeWrtProperty()
        assertFalse(s.status!!.transient)
        scope.advanceTimeBy(10 * AppState.INFO_DURATION_MS)
        scope.runCurrent()
        assertTrue(s.message!!.contains("n >"), s.message)

        // a newer info is not cut short by an older one's timer
        s.showInfo("Copied")
        scope.advanceTimeBy(AppState.INFO_DURATION_MS / 2)
        s.showInfo("Saved x.json")
        scope.advanceTimeBy(AppState.INFO_DURATION_MS / 2 + 100)
        scope.runCurrent()
        assertEquals("Saved x.json", s.message)
    }
}
