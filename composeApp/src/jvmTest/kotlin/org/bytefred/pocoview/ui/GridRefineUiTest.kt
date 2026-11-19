package org.bytefred.pocoview.ui

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.click
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
import kotlin.test.assertTrue

/**
 * "Snap with grid refinement" with real drags and clicks. The open triangle (0,0), (2,0), (0,2)
 * has no lattice point inside: a release at (0.6, 0.6) rounds to (1, 1) on its hypotenuse.
 */
@OptIn(ExperimentalTestApi::class)
class GridRefineUiTest {
    private val triangle = listOf(Pt(0, 0), Pt(2, 0), Pt(0, 2))

    private fun state(p: List<Pt>) = AppState(CoroutineScope(SupervisorJob() + Dispatchers.Default)).apply {
        loadPoints("t", p)
    }

    @BeforeTest
    fun clearPrefs() = clearInMemoryPrefs()

    private fun ComposeUiTest.show(s: AppState, view: ViewTransform) {
        setContent { PointCanvas(s, view, Modifier.size(400.dp)) }
        waitForIdle()
    }

    private fun screen(view: ViewTransform, x: Double, y: Double) =
        Offset((view.ox + x * view.scale).toFloat(), (view.oy - y * view.scale).toFloat())

    private fun ComposeUiTest.drag(view: ViewTransform, from: Offset, x: Double, y: Double) {
        onNodeWithTag("canvas").performTouchInput {
            down(from)
            moveTo(screen(view, x, y))
            up()
        }
        waitForIdle()
    }

    @Test
    fun offTheReleaseStaysOnTheNearestGridPoint() = runComposeUiTest {
        val s = state(triangle + Pt(5, 5))
        val view = ViewTransform()
        show(s, view)
        val scale = view.scale
        drag(view, view.toScreen(s.points[3]), 0.6, 0.6)
        assertEquals(triangle + Pt(1, 1), s.points)
        assertEquals(scale, view.scale)
    }

    @Test
    fun onTheReleaseDoublesIntoTheCellAndThePictureStays() = runComposeUiTest {
        val s = state(triangle + Pt(5, 5))
        s.refineGrid = true
        val view = ViewTransform()
        show(s, view)
        val scale = view.scale
        val corners = triangle.map { view.toScreen(it) }
        drag(view, view.toScreen(s.points[3]), 0.6, 0.6)
        assertEquals(listOf(Pt(0, 0), Pt(4, 0), Pt(0, 4), Pt(1, 1)), s.points)
        assertEquals(scale / 2, view.scale)
        assertEquals(corners, s.points.take(3).map { view.toScreen(it) })
        assertTrue(s.collinear.isEmpty())
        // the drag with its refinement is one undo step
        s.undo()
        assertEquals(triangle + Pt(5, 5), s.points)
    }

    @Test
    fun onAReleaseInACellWithAGridPointChangesNothingElse() = runComposeUiTest {
        val big = listOf(Pt(0, 0), Pt(10, 0), Pt(0, 10), Pt(20, 20))
        val s = state(big)
        s.refineGrid = true
        val view = ViewTransform()
        show(s, view)
        val scale = view.scale
        drag(view, view.toScreen(s.points[3]), 3.3, 3.2)
        assertEquals(big.take(3) + Pt(3, 3), s.points)
        assertEquals(scale, view.scale)
    }

    @Test
    fun addOffRefusesAndAddOnRefines() = runComposeUiTest {
        val s = state(triangle)
        s.tool = Tool.ADD
        val view = ViewTransform()
        show(s, view)
        onNodeWithTag("canvas").performTouchInput { click(screen(view, 0.6, 0.6)) }
        waitForIdle()
        assertEquals(triangle, s.points)

        s.refineGrid = true
        val scale = view.scale
        onNodeWithTag("canvas").performTouchInput { click(screen(view, 0.6, 0.6)) }
        waitForIdle()
        assertEquals(listOf(Pt(0, 0), Pt(4, 0), Pt(0, 4), Pt(1, 1)), s.points)
        assertEquals(scale / 2, view.scale)
        assertEquals(3, s.selected)
    }

    @Test
    fun noRoomKeepsThePositionAndSaysSo() {
        val p = triangle + Pt(5, 5) + Pt((1 shl 29) - 3, (1 shl 29) - 10)
        val s = state(p)
        s.refineGrid = true
        s.beginDrag(3)
        assertEquals(0, s.endDrag(3, 0.6, 0.6))
        assertEquals(p, s.points)
        assertEquals(AppState.NO_ROOM, s.status?.text)
    }

    @Test
    fun theCheckboxIsOffByDefaultRememberedAndExplained() = runComposeUiTest {
        val s = AppState(CoroutineScope(SupervisorJob() + Dispatchers.Default))
        assertFalse(s.refineGrid)
        setContent { WidgetContent(s, Widget.DISPLAY) {} }
        waitForIdle()
        onNodeWithText(REFINE_GRID_LABEL).assertExists()
        onNodeWithTag("refineGrid").assertIsOff().performClick()
        waitForIdle()
        onNodeWithTag("refineGrid").assertIsOn()
        assertTrue(s.refineGrid)
        s.saveDisplayPrefs()
        assertTrue(AppState(CoroutineScope(SupervisorJob() + Dispatchers.Default)).refineGrid)

        onNodeWithTag("info").performClick()
        waitForIdle()
        onNodeWithText(REFINE_GRID_INFO).assertExists()

        s.resetDisplay()
        assertFalse(s.refineGrid)
        assertFalse(AppState(CoroutineScope(SupervisorJob() + Dispatchers.Default)).refineGrid)
    }
}
