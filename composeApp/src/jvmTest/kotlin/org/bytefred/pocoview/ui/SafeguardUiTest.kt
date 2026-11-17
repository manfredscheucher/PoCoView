package org.bytefred.pocoview.ui

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.rightClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.bytefred.pocoview.core.Pt
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Package D in the real GUI: menu check items, the canvas context menu, closing with unsaved changes. */
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
        onNodeWithText("Cancel").performClick()
        waitForIdle()
        assertEquals(1, closed)

        closes++
        waitForIdle()
        onNodeWithText("Save…").performClick()
        waitForIdle()
        onNodeWithText("Save JSON").assertExists()
        onNodeWithText("Save text").assertExists()
        onNodeWithText("Save changes?").assertDoesNotExist()
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
}
