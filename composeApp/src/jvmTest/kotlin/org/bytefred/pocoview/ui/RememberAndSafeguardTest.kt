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
import java.io.File
import kotlin.io.path.createTempDirectory
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
        s.lineWidth = 3.5f
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
        assertEquals(3.5f, t.lineWidth)
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
    fun saveChangesAsksOnlyWithUnsavedChanges() {
        val s = state()
        var runs = 0
        // the start set and a fresh load are clean: the action runs right away
        s.leave { runs++ }
        assertEquals(1, runs)
        s.loadPoints("t", triangle)
        s.leave { runs++ }
        assertEquals(2, runs)
        assertNull(s.pendingLeave)

        s.addPoint(Pt(7, 7))
        assertTrue(s.unsavedChanges)
        s.leave { runs++ }
        assertEquals(2, runs)
        assertNotNull(s.pendingLeave)
        s.cancelLeave()
        assertNull(s.pendingLeave)

        // undo back to the loaded points: nothing to lose
        s.undo()
        assertFalse(s.unsavedChanges)
        s.leave { runs++ }
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
        assertEquals("Saved points.txt", s.status?.text)
        // edits in another set of a multi-set file count too
        s.loadSets("two", listOf(triangle, triangle.map { Pt(it.x + 1, it.y) }))
        s.showSet(1)
        assertFalse(s.unsavedChanges)
        s.deletePoint(0)
        s.showSet(0)
        assertTrue(s.unsavedChanges)
    }

    @Test
    fun saveChangesFlowSaveDiscardCancel() {
        val s = state()
        var quits = 0
        s.deletePoint(0)

        // Cancel, and a cancelled or failed save (null), cancel the action
        s.leave { quits++ }
        s.cancelLeave()
        s.leave { quits++ }
        s.beginSave(ExportFormat.JSON)
        s.fileSaved(null)
        assertNull(s.pendingLeave)
        assertEquals(0, quits)
        assertTrue(s.unsavedChanges)

        // Discard goes on without saving
        s.leave { quits++ }
        s.discardAndLeave()
        assertEquals(1, quits)
        assertTrue(s.unsavedChanges)

        // a format button: saves, then goes on
        s.leave { quits++ }
        s.beginSave(ExportFormat.TXT)
        s.fileSaved("points.txt")
        assertEquals(2, quits)
        assertNull(s.pendingLeave)
        assertFalse(s.unsavedChanges)
    }

    @Test
    fun aFailedSaveKeepsTheChangesAndDoesNotQuit() {
        val dir = createTempDirectory("pocoview").toFile()
        val s = state()
        var quits = 0
        s.deletePoint(0)
        s.leave { quits++ }
        // a directory cannot be written as a file
        writeFile(dir, s.beginSave(ExportFormat.JSON), onError = { s.error = it }, onSaved = s::fileSaved)
        assertTrue(s.error!!.startsWith("Cannot save ${dir.name}: "), s.error)
        assertTrue(s.unsavedChanges)
        assertNull(s.pendingLeave)
        assertEquals(0, quits)

        var opened = false
        readFile(File(dir, "missing.json"), onError = { s.error = it }) { _, _ -> opened = true }
        assertFalse(opened)
        assertTrue(s.error!!.startsWith("Cannot open missing.json: "), s.error)
        dir.delete()
    }

    @Test
    fun anUnappliedCoordinatesDraftIsAnUnsavedChange() {
        val s = state()
        assertFalse(s.unsavedChanges)
        s.coordinatesDraft = s.coordinatesText()
        assertFalse(s.unsavedChanges, "a draft equal to the points changes nothing")
        s.coordinatesDraft = s.coordinatesText() + "\n1 2"
        assertTrue(s.unsavedChanges)
        assertFalse(s.pointsUnsaved)

        // saving the points does not save the draft: "Save changes?" stays
        var runs = 0
        s.leave { runs++ }
        s.beginSave(ExportFormat.JSON)
        s.fileSaved("points.json")
        assertEquals(0, runs)
        assertNotNull(s.pendingLeave)
        s.discardAndLeave()
        assertEquals(1, runs)

        s.coordinatesDraft = null
        assertFalse(s.unsavedChanges)
    }

    @Test
    fun resetToDefaultsAlsoResetsTheDividers() {
        val s = state()
        s.wideWeights = mapOf(null to 3f, Widget.PROPERTIES to 0.5f)
        s.stackedWeights = mapOf(Widget.OPTIMIZE to 2f)
        s.setPropertyColor("hole5", Color(0xFFABCDEF))
        s.saveDisplayPrefs()
        assertNotNull(loadPref(WIDE_WEIGHTS_PREF))

        s.resetDisplay()
        assertTrue(s.wideWeights.isEmpty())
        assertTrue(s.stackedWeights.isEmpty())
        assertNull(loadPref(WIDE_WEIGHTS_PREF))
        assertNull(loadPref(STACKED_WEIGHTS_PREF))
        assertTrue(state().wideWeights.isEmpty())
        // property colours are not display settings
        assertEquals(Color(0xFFABCDEF), state().colorOf("hole5"))
    }

    @Test
    fun lineWidthIsRemembered() {
        val s = state()
        s.lineWidth = 2.5f
        s.saveDisplayPrefs()
        assertEquals(2.5f, state().lineWidth)
        // clamped to the slider range
        savePref(LINE_WIDTH_PREF, "99")
        assertEquals(AppState.LINE_WIDTH_RANGE.endInclusive, state().lineWidth)
        s.resetDisplay()
        assertEquals(1f, state().lineWidth)
    }

    @Test
    fun infoMessagesDisappearResultsStay() {
        val scope = TestScope()
        val s = AppState(scope)
        s.loadPoints("t", triangle)
        assertTrue(s.status!!.transient)
        scope.advanceTimeBy(AppState.INFO_DURATION_MS - 100)
        assertNotNull(s.status?.text)
        scope.advanceTimeBy(200)
        scope.runCurrent()
        assertNull(s.status?.text)

        // an optimizer's result (here: it cannot run) stays
        s.optimizeProperty = "triangulations"
        s.loadPoints("h", org.bytefred.pocoview.core.HortonFamily.generate(64))
        s.minimizeWrtProperty()
        assertFalse(s.status!!.transient)
        scope.advanceTimeBy(10 * AppState.INFO_DURATION_MS)
        scope.runCurrent()
        assertTrue(s.status!!.text.contains("n >"), s.status?.text)

        // a newer info is not cut short by an older one's timer
        s.showInfo("Copied")
        scope.advanceTimeBy(AppState.INFO_DURATION_MS / 2)
        s.showInfo("Saved x.json")
        scope.advanceTimeBy(AppState.INFO_DURATION_MS / 2 + 100)
        scope.runCurrent()
        assertEquals("Saved x.json", s.status?.text)
    }
}
