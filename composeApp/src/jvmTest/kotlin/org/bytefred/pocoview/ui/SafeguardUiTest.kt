package org.bytefred.pocoview.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performMultiModalInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.rightClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.withKeyDown
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.bytefred.pocoview.core.Pt
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Package D in the real GUI: menus and Escape, the canvas context menu, "Save changes?". */
@OptIn(ExperimentalTestApi::class)
class SafeguardUiTest {
    private fun state() = AppState(CoroutineScope(SupervisorJob() + Dispatchers.Default))

    @BeforeTest
    fun clearPrefs() = clearInMemoryPrefs()

    @Test
    fun viewCheckItemsKeepTheMenuOpenLockOrderTypeClosesIt() = runComposeUiTest {
        val s = state()
        setContent { App(s, DialogKind.NONE) }
        onNodeWithTag("menu-View").performClick()
        onNodeWithText("Point labels", substring = true).performClick()
        onNodeWithText("Line arrangement", substring = true).performClick()
        waitForIdle()
        assertFalse(s.showLabels)
        assertFalse(s.showLines)
        assertEquals("View", s.openMenu)
        onNodeWithText("landscape", substring = true).performClick()
        waitForIdle()
        assertEquals(LayoutMode.LANDSCAPE, s.layoutMode)
        assertEquals("View", s.openMenu)

        onNodeWithTag("menu-Edit").performClick()
        onNodeWithText("   Lock order type").performClick()
        waitForIdle()
        assertTrue(s.fixOrderType)
        assertNull(s.openMenu)
    }

    @Test
    fun rightClickOnAPointOffersSelectAndAnUndoableDelete() = runComposeUiTest {
        val s = state()
        s.loadPoints("t", listOf(Pt(0, 0), Pt(100, 0), Pt(0, 100), Pt(60, 70)))
        val view = ViewTransform()
        setContent { PointCanvas(s, view, Modifier.size(400.dp)) }
        waitForIdle()
        val p1 = view.toScreen(s.points[1])

        // empty space: no menu, nothing selected or added
        onNodeWithTag("canvas").performMouseInput { rightClick(Offset(5f, 5f)) }
        waitForIdle()
        onNodeWithText("Delete").assertDoesNotExist()
        assertEquals(4, s.points.size)

        // on a point: the menu, and the right-click itself does not select
        onNodeWithTag("canvas").performMouseInput { rightClick(p1) }
        waitForIdle()
        assertNull(s.selected)
        // headed with the point's label
        onNodeWithText("Point 1").assertExists()
        onNodeWithText("Select").performClick()
        waitForIdle()
        assertEquals(1, s.selected)

        val before = s.points
        onNodeWithTag("canvas").performMouseInput { rightClick(p1) }
        onNodeWithText("Delete").performClick()
        waitForIdle()
        assertEquals(3, s.points.size)
        s.undo()
        assertEquals(before, s.points)

        // a left click still selects
        onNodeWithTag("canvas").performMouseInput { click(view.toScreen(s.points[2])) }
        waitForIdle()
        assertEquals(2, s.selected)
    }

    @Test
    fun ctrlClickOpensTheContextMenuOnAMac() = runComposeUiTest {
        val s = state()
        s.loadPoints("t", listOf(Pt(0, 0), Pt(100, 0), Pt(0, 100), Pt(60, 70)))
        val view = ViewTransform()
        setContent { PointCanvas(s, view, Modifier.size(400.dp)) }
        waitForIdle()
        onNodeWithTag("canvas").performMultiModalInput {
            key { keyDown(Key.CtrlLeft) }
            mouse { click(view.toScreen(s.points[1])) }
            key { keyUp(Key.CtrlLeft) }
        }
        waitForIdle()
        if (ctrlClickIsRightClick) {
            onNodeWithText("Point 1").assertExists()
            assertNull(s.selected)
        } else {
            onNodeWithText("Point 1").assertDoesNotExist()
            assertEquals(1, s.selected)
        }
    }

    @Test
    fun closingAsksOnlyWithUnsavedChanges() = runComposeUiTest {
        val s = state()
        var closes by mutableIntStateOf(0)
        var closed = 0
        setContent { App(s, DialogKind.NONE, closes, onClose = { closed++ }) }
        closes++
        waitForIdle()
        assertEquals(1, closed)

        s.deletePoint(0)
        closes++
        waitForIdle()
        onNodeWithText("Save changes?").assertExists()
        onNodeWithText("Your changes to “n13-c1-min-convex-3-4-5-holes” are not saved.").assertExists()
        onNodeWithText("Save JSON").assertExists()
        onNodeWithText("Save text").assertExists()
        onNodeWithText("Cancel").performClick()
        waitForIdle()
        onNodeWithText("Save changes?").assertDoesNotExist()
        assertEquals(1, closed)

        closes++
        waitForIdle()
        onNodeWithText("Discard").performClick()
        waitForIdle()
        assertEquals(2, closed)
    }

    @Test
    fun ctrlOAndCtrlSWorkOnTheCanvasAndWhileTyping() = runComposeUiTest {
        val s = state()
        s.toggleWidget(Widget.DATA)
        s.deletePoint(0)
        setContent { App(s, DialogKind.NONE) }
        // the menu entries show their keys
        onNodeWithTag("menu-Point Set").performClick()
        onNodeWithText("Ctrl+O").assertExists()
        onNodeWithText("Ctrl+S").assertExists()
        onNodeWithTag("menu-bar").performKeyInput { pressKey(Key.Escape) }
        waitForIdle()
        onNodeWithTag("canvas").performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.S) } }
        onNodeWithText("Save file").assertExists()
        onNodeWithText("Cancel").performClick()
        waitForIdle()
        // Open file goes through "Save changes?" like the menu entry
        onNodeWithTag("canvas").performKeyInput { withKeyDown(Key.MetaLeft) { pressKey(Key.O) } }
        onNodeWithText("Save changes?").assertExists()
        onNodeWithText("Cancel").performClick()
        waitForIdle()
        // also while a text field has focus (the other shortcuts are off then)
        onNodeWithTag("coordinates").performClick()
        waitForIdle()
        assertTrue(s.textFieldsFocused > 0)
        onNodeWithTag("coordinates").performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.S) } }
        onNodeWithText("Save file").assertExists()
        onNodeWithText("Cancel").performClick()
        waitForIdle()
        // Cmd+O (on macOS a text field takes Ctrl+O itself: a new line, as in every Mac text field)
        onNodeWithTag("coordinates").performKeyInput { withKeyDown(Key.MetaLeft) { pressKey(Key.O) } }
        onNodeWithText("Save changes?").assertExists()
        assertNull(s.coordinatesDraft)
    }

    @Test
    fun loadAsksSaveChangesOnTopOfTheDialog() = runComposeUiTest {
        val s = state()
        s.deletePoint(0)
        val before = s.points
        setContent { App(s, DialogKind.RANDOM) }
        onNodeWithText("Generate").performClick()
        waitForIdle()
        onNodeWithText("Save changes?").assertExists()
        assertEquals(before, s.points)
        onNodeWithText("Discard").performClick()
        waitForIdle()
        assertEquals("random", s.fileName)
        onNodeWithText("Save changes?").assertDoesNotExist()
    }

    @Test
    fun aDraftAloneOffersOnlyDiscardAndCancel() = runComposeUiTest {
        val s = state()
        s.editCoordinates("1 2\n3 4\n5 7")
        setContent { App(s, DialogKind.RANDOM) }
        onNodeWithText("Generate").performClick()
        waitForIdle()
        onNodeWithText("Save changes?").assertExists()
        onNodeWithText("The coordinates edited in the Data widget are not applied.").assertExists()
        onNodeWithText("Save JSON").assertDoesNotExist()
        onNodeWithText("Discard").performClick()
        waitForIdle()
        assertEquals("random", s.fileName)
        assertNull(s.coordinatesDraft)
    }

    @Test
    fun closeRequestWhileTheSaveDialogIsOpenShowsSaveChanges() = runComposeUiTest {
        val s = state()
        var closes by mutableIntStateOf(0)
        var closed = 0
        s.deletePoint(0)
        setContent { App(s, DialogKind.SAVE, closes, onClose = { closed++ }) }
        closes++
        waitForIdle()
        onNodeWithText("Save changes?").assertExists()
        // the Save dialog made way for it
        onNodeWithText("Save file").assertDoesNotExist()
        onAllNodesWithText("Save JSON").assertCountEquals(1)
        onNodeWithText("Discard").performClick()
        waitForIdle()
        assertEquals(1, closed)
    }

    @Test
    fun escapeClosesTheMenuAfterAViewCheckItem() = runComposeUiTest {
        val s = state()
        setContent { App(s, DialogKind.NONE) }
        onNodeWithTag("menu-View").performClick()
        // opening a widget re-lays out the canvas, which used to take the focus
        onNodeWithText("Data (3)", substring = true).performClick()
        waitForIdle()
        assertTrue(Widget.DATA in s.openWidgets)
        assertEquals("View", s.openMenu)
        onNodeWithTag("menu-bar").performKeyInput { pressKey(Key.Escape) }
        waitForIdle()
        assertNull(s.openMenu)
    }

    @Test
    fun shortcutsAreIgnoredWhileAMenuIsOpen() = runComposeUiTest {
        val s = state()
        setContent { App(s, DialogKind.NONE) }
        onNodeWithTag("menu-Edit").performClick()
        waitForIdle()
        onNodeWithTag("menu-bar").performKeyInput { pressKey(Key.L) }
        waitForIdle()
        assertTrue(s.showLines)
        assertEquals("Edit", s.openMenu)
        onNodeWithTag("menu-bar").performKeyInput { pressKey(Key.Escape) }
        onNodeWithTag("menu-bar").performKeyInput { pressKey(Key.L) }
        waitForIdle()
        assertFalse(s.showLines)
    }

    @Test
    fun aCanvasClickWhileAMenuIsOpenOnlyClosesIt() = runComposeUiTest {
        val s = state()
        s.tool = Tool.ADD
        setContent { App(s, DialogKind.NONE) }
        val n = s.points.size
        onNodeWithTag("menu-Point Set").performClick()
        waitForIdle()
        assertEquals("Point Set", s.openMenu)
        // bottom right, away from the menu's popup
        val empty = Offset(470f, 620f)
        onNodeWithTag("canvas").performMouseInput { click(empty) }
        waitForIdle()
        assertNull(s.openMenu)
        assertEquals(n, s.points.size)
        // only the closing click is swallowed: the next one adds right away
        onNodeWithTag("canvas").performMouseInput { click(empty) }
        waitForIdle()
        assertEquals(n + 1, s.points.size, s.status?.text)
    }

    @Test
    fun aRecreatedCanvasKeepsTheZoom() = runComposeUiTest {
        val s = state()
        val view = ViewTransform()
        // a layout switch composes the canvas in another parent: a new canvas, the same view
        var stacked by mutableStateOf(false)
        setContent {
            if (stacked) Column { PointCanvas(s, view, Modifier.size(400.dp)) }
            else Row { PointCanvas(s, view, Modifier.size(400.dp)) }
        }
        waitForIdle()
        view.zoomCenter(2.0)
        val zoomed = view.scale
        stacked = true
        waitForIdle()
        assertEquals(zoomed, view.scale)
        // a fit request still fits
        s.requestFit()
        waitForIdle()
        assertNotEquals(zoomed, view.scale)
    }
}
