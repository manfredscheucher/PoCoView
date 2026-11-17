package org.bytefred.pocoview.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * Wraps a scrolling area and draws scrollbars (ktoy's, the same on desktop and in the browser):
 * on the right edge for [vScroll], and at the bottom for [hScroll]. A thumb's length reflects the
 * visible fraction, its position the scroll offset, and it can be dragged (mouse or finger). Each
 * bar shows only when there is something to scroll.
 */
@Composable
fun ScrollbarBox(
    vScroll: ScrollState?,
    modifier: Modifier = Modifier,
    hScroll: ScrollState? = null,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier.testTag("scroll-area")) {
        content()
        if (vScroll != null) ScrollThumb(vScroll, vertical = true)
        if (hScroll != null) ScrollThumb(hScroll, vertical = false)
    }
}

/** A vertically scrolling column with a scrollbar on the right. */
@Composable
fun ScrollColumn(
    modifier: Modifier = Modifier,
    state: ScrollState = rememberScrollState(),
    content: @Composable ColumnScope.() -> Unit,
) {
    ScrollbarBox(state, modifier) {
        Column(Modifier.verticalScroll(state).padding(end = 10.dp), content = content)
    }
}

/** One draggable thumb along the right (vertical) or bottom edge. */
@Composable
private fun BoxWithConstraintsScope.ScrollThumb(state: ScrollState, vertical: Boolean) {
    val max = state.maxValue
    if (max <= 0) return
    val trackPx = (if (vertical) constraints.maxHeight else constraints.maxWidth).toFloat()
    val thumbFraction = (trackPx / (trackPx + max)).coerceIn(0.08f, 1f)
    val thumbPx = trackPx * thumbFraction
    val travel = trackPx - thumbPx
    val offsetPx = travel * (state.value.toFloat() / max)
    // moving the thumb by 1 px scrolls the content by max / travel px
    val drag = rememberDraggableState { delta ->
        if (travel > 0f) state.dispatchRawDelta(delta * max / travel)
    }
    with(LocalDensity.current) {
        val shape = Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(Color(0x66000000))
            .draggable(drag, if (vertical) Orientation.Vertical else Orientation.Horizontal)
        Box(
            if (vertical) Modifier
                .align(Alignment.TopEnd)
                .offset(y = offsetPx.toDp())
                .width(THUMB_THICKNESS)
                .height(thumbPx.toDp())
                .then(shape)
                .testTag("vscroll-thumb")
            else Modifier
                .align(Alignment.BottomStart)
                .offset(x = offsetPx.toDp())
                .height(THUMB_THICKNESS)
                .width(thumbPx.toDp())
                .then(shape)
                .testTag("hscroll-thumb"),
        )
    }
}

private val THUMB_THICKNESS = 6.dp
