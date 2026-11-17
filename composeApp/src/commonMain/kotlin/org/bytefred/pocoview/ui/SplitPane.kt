package org.bytefred.pocoview.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** A resize cursor for a divider; horizontal = the divider moves left/right. */
expect fun resizePointerIcon(horizontal: Boolean): PointerIcon

/**
 * One panel of a [SplitPane], keyed so its share survives other panels coming and going;
 * [defaultWeight] is its share while it was never dragged.
 */
class SplitPanel<K>(val key: K, val defaultWeight: Float = 1f, val content: @Composable (Modifier) -> Unit)

/**
 * Moves the divider after panel [index] by [deltaPx]: panel [index] grows and
 * panel index+1 shrinks by the same amount, the other panels stay put. Neither
 * of the two may get smaller than [minPx]; [totalPx] is the space the panels
 * share (without the dividers). Pure, so it is unit-tested.
 */
fun dragSplit(weights: List<Float>, index: Int, deltaPx: Float, totalPx: Float, minPx: Float): List<Float> {
    if (index !in 0 until weights.size - 1 || totalPx <= 0f) return weights
    val pxPerWeight = totalPx / weights.sum()
    val a = weights[index] * pxPerWeight
    val b = weights[index + 1] * pxPerWeight
    val newA = (a + deltaPx).coerceIn(minOf(a, minPx), maxOf(a, a + b - minPx))
    return weights.toMutableList().apply {
        this[index] = newA / pxPerWeight
        this[index + 1] = (a + b - newA) / pxPerWeight
    }
}

/**
 * Panels side by side ([horizontal]) or stacked, with a draggable divider
 * between neighbours. The shares live in [weights] (per panel key, so the state
 * keeps them while panels come and go); a key missing there gets its panel's
 * [SplitPanel.defaultWeight].
 */
@Composable
fun <K> SplitPane(
    panels: List<SplitPanel<K>>,
    weights: Map<K, Float>,
    onWeightsChange: (Map<K, Float>) -> Unit,
    horizontal: Boolean,
    modifier: Modifier = Modifier,
) {
    val ws = panels.map { weights[it.key] ?: it.defaultWeight }
    // the latest shares, also updated by each drag event: several events can
    // arrive before the next recomposition, and each must build on the last
    val latest = remember { arrayOf(ws) }
    latest[0] = ws
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val dividers = DIVIDER_SIZE * (panels.size - 1)
        val totalPx = with(density) { ((if (horizontal) maxWidth else maxHeight) - dividers).toPx() }
        val minPx = with(density) { MIN_PANEL_SIZE.toPx() }
        val divider: @Composable (Int) -> Unit = { i ->
            val state = rememberDraggableState { delta ->
                val moved = dragSplit(latest[0], i, delta, totalPx, minPx)
                latest[0] = moved
                onWeightsChange(weights + panels.map { it.key }.zip(moved))
            }
            Box(
                Modifier
                    .then(if (horizontal) Modifier.width(DIVIDER_SIZE).fillMaxHeight() else Modifier.height(DIVIDER_SIZE).fillMaxWidth())
                    .testTag("divider-$i")
                    .pointerHoverIcon(resizePointerIcon(horizontal))
                    .draggable(state, if (horizontal) Orientation.Horizontal else Orientation.Vertical),
                contentAlignment = Alignment.Center,
            ) {
                // a thin visible line in the middle of the wider grab area
                Box(
                    Modifier
                        .then(if (horizontal) Modifier.width(2.dp).fillMaxHeight(0.1f) else Modifier.height(2.dp).fillMaxWidth(0.1f))
                        .background(Color(0xFFBBBBBB))
                )
            }
        }
        if (horizontal) {
            Row(Modifier.fillMaxSize()) {
                panels.forEachIndexed { i, p ->
                    key(p.key) {
                        if (i > 0) divider(i - 1)
                        p.content(Modifier.weight(ws[i]).fillMaxHeight())
                    }
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                panels.forEachIndexed { i, p ->
                    key(p.key) {
                        if (i > 0) divider(i - 1)
                        p.content(Modifier.weight(ws[i]).fillMaxWidth())
                    }
                }
            }
        }
    }
}

private val DIVIDER_SIZE = 12.dp
private val MIN_PANEL_SIZE = 80.dp
