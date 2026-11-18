package org.bytefred.pocoview.ui

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
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

/** A long press on a touch screen opens the point menu like a right-click (web gestures). */
@OptIn(ExperimentalTestApi::class)
class LongPressUiTest {
    private val web = MenuGestures(rightClick = true, longPress = true)

    private fun state() = AppState(CoroutineScope(SupervisorJob() + Dispatchers.Default)).apply {
        loadPoints("t", listOf(Pt(0, 0), Pt(100, 0), Pt(0, 100), Pt(60, 70)))
    }

    @BeforeTest
    fun clearPrefs() = clearInMemoryPrefs()

    private fun ComposeUiTest.show(s: AppState, view: ViewTransform, gestures: MenuGestures = web) {
        setContent {
            CompositionLocalProvider(LocalMenuGestures provides gestures) { PointCanvas(s, view, Modifier.size(400.dp)) }
        }
        waitForIdle()
    }

    @Test
    fun longPressOnAPointOpensItsMenuAndTheReleaseDoesNotSelect() = runComposeUiTest {
        val s = state()
        val view = ViewTransform()
        show(s, view)
        val before = s.points
        onNodeWithTag("canvas").performTouchInput { longClick(view.toScreen(s.points[1])) }
        waitForIdle()
        onNodeWithText("Point 1").assertExists()
        assertNull(s.selected)
        assertEquals(before, s.points)
        onNodeWithText("Delete").performClick()
        waitForIdle()
        assertEquals(3, s.points.size)
    }

    @Test
    fun longPressInAddModeDoesNotAddOrSelect() = runComposeUiTest {
        val s = state()
        s.tool = Tool.ADD
        val view = ViewTransform()
        show(s, view)
        onNodeWithTag("canvas").performTouchInput { longClick(view.toScreen(s.points[2])) }
        waitForIdle()
        onNodeWithText("Point 2").assertExists()
        assertNull(s.selected)
        assertEquals(4, s.points.size)
    }

    @Test
    fun pressThenMoveDragsThePointAndHoldingAfterwardsOpensNoMenu() = runComposeUiTest {
        val s = state()
        val view = ViewTransform()
        show(s, view)
        val p1 = view.toScreen(s.points[1])
        onNodeWithTag("canvas").performTouchInput {
            down(p1)
            moveBy(Offset(30f, 0f))
            // held still well past the long-press timeout while dragging
            advanceEventTime(2 * viewConfiguration.longPressTimeoutMillis)
            moveBy(Offset(30f, 0f))
            up()
        }
        waitForIdle()
        onNodeWithText("Point 1").assertDoesNotExist()
        assertEquals(view.toWorld(p1 + Offset(60f, 0f)), s.points[1])
    }

    @Test
    fun longPressOnEmptySpaceOpensNoMenuAndTapsAsBefore() = runComposeUiTest {
        val s = state()
        s.tool = Tool.ADD
        val view = ViewTransform()
        show(s, view)
        onNodeWithTag("canvas").performTouchInput { longClick(Offset(390f, 10f)) }
        waitForIdle()
        onNodeWithText("Delete").assertDoesNotExist()
        assertEquals(5, s.points.size)
    }

    @Test
    fun longPressInDeleteModeOpensTheMenuAndDoesNotDelete() = runComposeUiTest {
        val s = state()
        s.tool = Tool.DELETE
        val view = ViewTransform()
        show(s, view)
        onNodeWithTag("canvas").performTouchInput { longClick(view.toScreen(s.points[1])) }
        waitForIdle()
        onNodeWithText("Point 1").assertExists()
        assertEquals(4, s.points.size)
    }

    @Test
    fun aHoldJustBelowTheTimeoutIsATap() = runComposeUiTest {
        val s = state()
        val view = ViewTransform()
        show(s, view)
        onNodeWithTag("canvas").performTouchInput {
            down(view.toScreen(s.points[2]))
            advanceEventTime(viewConfiguration.longPressTimeoutMillis - 50)
            up()
        }
        waitForIdle()
        onNodeWithText("Point 2").assertDoesNotExist()
        assertEquals(2, s.selected)
    }

    @Test
    fun aShortTapStillSelects() = runComposeUiTest {
        val s = state()
        val view = ViewTransform()
        show(s, view)
        onNodeWithTag("canvas").performTouchInput { click(view.toScreen(s.points[3])) }
        waitForIdle()
        onNodeWithText("Point 3").assertDoesNotExist()
        assertEquals(3, s.selected)
    }

    @Test
    fun onTheDesktopALongPressIsAnOrdinaryClick() = runComposeUiTest {
        val s = state()
        val view = ViewTransform()
        show(s, view, platformMenuGestures)
        assertFalse(platformMenuGestures.longPress)
        onNodeWithTag("canvas").performTouchInput { longClick(view.toScreen(s.points[1])) }
        waitForIdle()
        onNodeWithText("Point 1").assertDoesNotExist()
        assertEquals(1, s.selected)
    }
}
