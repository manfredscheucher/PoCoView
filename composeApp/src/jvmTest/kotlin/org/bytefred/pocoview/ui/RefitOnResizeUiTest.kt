package org.bytefred.pocoview.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.IntSize
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
import kotlin.test.assertTrue

/** The view is refitted when the canvas size changes, until the user zooms or pans. */
@OptIn(ExperimentalTestApi::class)
class RefitOnResizeUiTest {
    private fun state() = AppState(CoroutineScope(SupervisorJob() + Dispatchers.Default))

    @BeforeTest
    fun clearPrefs() = clearInMemoryPrefs()

    private fun ComposeUiTest.show(s: AppState, view: ViewTransform) {
        setContent { Box(Modifier.size(1000.dp, 700.dp)) { App(s, DialogKind.NONE, initialView = view) } }
        waitForIdle()
    }

    private fun ComposeUiTest.key(k: Key) {
        onNodeWithTag("menu-bar").performKeyInput { pressKey(k) }
        waitForIdle()
    }

    /** Every point is drawn inside the canvas. */
    private fun assertInside(s: AppState, view: ViewTransform) {
        for (p in s.points) {
            val q = view.toScreen(p)
            assertTrue(q.x in 0f..view.size.width.toFloat() && q.y in 0f..view.size.height.toFloat(), "$p at $q, canvas ${view.size}")
        }
    }

    /** The view is exactly a fit of the points to the current canvas. */
    private fun assertFitted(s: AppState, view: ViewTransform) {
        val fresh = ViewTransform().apply { resize(view.size, s.points); fit(s.points) }
        assertEquals(fresh.scale, view.scale, 1e-9 * fresh.scale)
        assertEquals(fresh.ox, view.ox, 1e-6)
        assertEquals(fresh.oy, view.oy, 1e-6)
    }

    @Test
    fun openingAndClosingWidgetsRefits() = runComposeUiTest {
        val s = state()
        s.changeMaxColumns(4)
        val view = ViewTransform()
        show(s, view)
        assertFitted(s, view)
        val sizes = mutableSetOf(view.size)
        // Data and Display open (4 columns), then everything closes again, then portrait
        for (k in listOf(Key.Three, Key.Four, Key.One, Key.Two, Key.Three, Key.Four, Key.One)) {
            key(k)
            sizes += view.size
            assertInside(s, view)
            assertFitted(s, view)
        }
        s.layoutMode = LayoutMode.PORTRAIT
        waitForIdle()
        sizes += view.size
        assertInside(s, view)
        assertFitted(s, view)
        assertTrue(sizes.size >= 4, "the canvas size changed: $sizes")
    }

    @Test
    fun afterAZoomOrPanTheViewIsKept() = runComposeUiTest {
        val s = state()
        val view = ViewTransform()
        show(s, view)
        key(Key.Equals)
        val scale = view.scale
        val w0 = view.size.width
        key(Key.One)
        assertNotEquals(w0, view.size.width)
        assertEquals(scale, view.scale)
        assertTrue(view.userAdjusted)

        // Fit starts auto-fitting again
        key(Key.Zero)
        assertFalse(view.userAdjusted)
        val fitted = view.scale
        key(Key.One)
        assertNotEquals(fitted, view.scale)
        assertFitted(s, view)

        // panning (dragging empty space) counts as well
        onNodeWithTag("canvas").performTouchInput { down(Offset(5f, 5f)); moveTo(Offset(60f, 40f)); up() }
        waitForIdle()
        assertTrue(view.userAdjusted)
        val ox = view.ox
        val w = view.size.width
        val panned = view.scale
        key(Key.Two)
        assertNotEquals(w, view.size.width)
        assertEquals(ox + (view.size.width - w) / 2.0, view.ox, 1e-6)
        assertEquals(panned, view.scale)
    }

    @Test
    fun theWheelCountsAsZoom() = runComposeUiTest {
        val s = state()
        val view = ViewTransform()
        show(s, view)
        onNodeWithTag("canvas").performMouseInput { moveTo(Offset(100f, 100f)); scroll(-1f) }
        waitForIdle()
        assertTrue(view.userAdjusted)
        val scale = view.scale
        val w = view.size.width
        key(Key.One)
        assertNotEquals(w, view.size.width)
        assertEquals(scale, view.scale)
    }

    @Test
    fun editingDoesNotCountAsZoomOrPan() = runComposeUiTest {
        val s = state()
        s.loadPoints("t", listOf(Pt(0, 0), Pt(100, 0), Pt(0, 100), Pt(30, 30)))
        val view = ViewTransform()
        show(s, view)
        // drag point 3 far out to the right edge of the canvas, then add a point
        val from = view.toScreen(s.points[3])
        onNodeWithTag("canvas").performTouchInput { down(from); moveTo(Offset(view.size.width - 3f, from.y)) }
        waitForIdle()
        assertTrue(view.dragging)
        onNodeWithTag("canvas").performTouchInput { up() }
        waitForIdle()
        assertFalse(view.dragging)
        assertNotEquals(Pt(30, 30), s.points[3])
        assertFalse(view.userAdjusted)
        s.tool = Tool.ADD
        onNodeWithTag("canvas").performMouseInput { click(Offset(view.size.width / 2f, 10f)) }
        waitForIdle()
        assertEquals(5, s.points.size)
        assertFalse(view.userAdjusted)
        // a resize now fits the edited points
        key(Key.One)
        assertInside(s, view)
        assertFitted(s, view)
    }

    @Test
    fun aResizeDuringADragKeepsTheView() {
        val pts = listOf(Pt(0, 0), Pt(100, 0), Pt(0, 100))
        val view = ViewTransform()
        view.resize(IntSize(400, 400), pts)
        view.fit(pts)
        view.dragging = true
        val scale = view.scale
        val ox = view.ox
        view.resize(IntSize(300, 400), pts)
        assertEquals(scale, view.scale)
        assertEquals(ox - 50.0, view.ox)
        assertFalse(view.userAdjusted)
        view.dragging = false
        view.resize(IntSize(200, 400), pts)
        assertNotEquals(scale, view.scale)
    }
}
