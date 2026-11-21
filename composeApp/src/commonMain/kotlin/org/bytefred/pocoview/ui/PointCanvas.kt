package org.bytefred.pocoview.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import org.bytefred.pocoview.core.ALL_PROPERTIES
import org.bytefred.pocoview.core.Pt
import org.bytefred.pocoview.core.Shape
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** World (data coordinates, y up) <-> screen (pixels, y down): screen = origin + (x, -y) * scale. */
class ViewTransform {
    var scale by mutableDoubleStateOf(1.0)
    var ox by mutableDoubleStateOf(0.0)
    var oy by mutableDoubleStateOf(0.0)
    var size by mutableStateOf(IntSize.Zero)

    /** The [AppState.fitRequest] this view was last fitted for: a re-created canvas keeps the zoom. */
    var fittedRequest = -1

    /**
     * The user zoomed or panned since the last fit: a new canvas size keeps the view. Until then the
     * view is refitted on every size change. Editing points (drag, add, grid refinement) does not count.
     */
    var userAdjusted = false
        private set

    /** A point is being dragged: a size change keeps the view, so the picture does not jump under it. */
    var dragging = false

    fun toScreen(p: Pt) = Offset((ox + p.x * scale).toFloat(), (oy - p.y * scale).toFloat())
    fun toWorld(s: Offset) = Pt(((s.x - ox) / scale).roundToLong(), ((oy - s.y) / scale).roundToLong())
    fun worldX(s: Offset) = (s.x - ox) / scale
    fun worldY(s: Offset) = (oy - s.y) / scale

    /** The coordinates were multiplied by 2^[doublings]: zoom out by as much, so the picture stays. */
    fun coordinatesDoubled(doublings: Int) {
        repeat(doublings) { scale /= 2 }
    }

    fun zoomAt(anchor: Offset, factor: Double) {
        userAdjusted = true
        val f = factor.coerceIn(0.02, 50.0)
        val newScale = (scale * f).coerceIn(1e-7, 1e4)
        val k = newScale / scale
        ox = anchor.x - (anchor.x - ox) * k
        oy = anchor.y - (anchor.y - oy) * k
        scale = newScale
    }

    fun pan(dx: Double, dy: Double) {
        userAdjusted = true
        ox += dx
        oy += dy
    }

    /**
     * New canvas size (window resize, a widget opened or closed, a divider moved): refit to [points]
     * unless the user zoomed or panned since the last fit or a drag is running; then the view keeps
     * its centre.
     */
    fun resize(newSize: IntSize, points: List<Pt>) {
        // keep the last real size while the canvas is squeezed to nothing
        if (newSize.width == 0 || newSize.height == 0) return
        val old = size
        size = newSize
        if (old == IntSize.Zero || newSize == old) return
        if (!userAdjusted && !dragging) fit(points)
        else {
            ox += (newSize.width - old.width) / 2.0
            oy += (newSize.height - old.height) / 2.0
        }
    }

    fun zoomCenter(factor: Double) = zoomAt(Offset(size.width / 2f, size.height / 2f), factor)

    fun fit(points: List<Pt>) {
        if (size.width == 0 || size.height == 0) return
        userAdjusted = false
        if (points.isEmpty()) { scale = 1.0; ox = size.width / 2.0; oy = size.height / 2.0; return }
        val minX = points.minOf { it.x }.toDouble()
        val maxX = points.maxOf { it.x }.toDouble()
        val minY = points.minOf { it.y }.toDouble()
        val maxY = points.maxOf { it.y }.toDouble()
        val w = maxOf(1.0, maxX - minX)
        val h = maxOf(1.0, maxY - minY)
        scale = min(size.width * 0.85 / w, size.height * 0.85 / h)
        ox = size.width / 2.0 - (minX + maxX) / 2 * scale
        oy = size.height / 2.0 + (minY + maxY) / 2 * scale
    }
}

/**
 * Default property colours by registry position, as many as [ALL_PROPERTIES], so none repeats. No
 * red: that is the crossing families' colour ([AppState.EMPHASIZED_COLOR]), which they use instead.
 */
private val PALETTE = listOf(
    Color(0xFF1E88E5), Color(0xFF1B5E20), Color(0xFF43A047), Color(0xFFFB8C00), Color(0xFF8E24AA),
    Color(0xFF00ACC1), Color(0xFF6D4C41), Color(0xFFD81B60), Color(0xFF7CB342), Color(0xFF3949AB),
    Color(0xFFFDD835), Color(0xFF546E7A), Color(0xFF00897B), Color(0xFFAD1457), Color(0xFF5D4037),
    Color(0xFF0277BD), Color(0xFFA1887F), Color(0xFF827717), Color(0xFFBA68C8), Color(0xFFFF8A65),
    Color(0xFF90A4AE), Color(0xFF004D40), Color(0xFFF06292),
)

/** Default highlight colour of a property (by its position in the registry). */
fun defaultPropertyColor(index: Int): Color = PALETTE[index.coerceAtLeast(0) % PALETTE.size]

/** Colours offered by the colour chooser. */
val CHOOSER_COLORS: List<Color> = PALETTE + listOf(
    Color(0xFF000000), Color(0xFF616161), Color(0xFF9E9E9E), Color(0xFFBDBDBD),
    Color(0xFFE53935), Color(0xFF880E4F), Color(0xFF1A237E), Color(0xFFE65100),
)

/** Colour of a point colour token (PoSeZo bicoloured sets: B / R). */
fun tokenColor(token: String?): Color? = when (token?.uppercase()) {
    "R" -> Color(0xFFC62828)
    "B" -> Color(0xFF1565C0)
    "G" -> Color(0xFF2E7D32)
    else -> null
}

/** Ctrl+click opens the context menu like a right-click (macOS). */
expect val ctrlClickIsRightClick: Boolean

private const val HIT_RADIUS = 16f
private const val TOUCH_SLOP = 4f

@Composable
fun PointCanvas(state: AppState, view: ViewTransform, modifier: Modifier = Modifier, onInteract: () -> Unit = {}) {
    val st by rememberUpdatedState(state)
    val textMeasurer = rememberTextMeasurer()
    val onSurface = MaterialTheme.colorScheme.onSurface

    // fit on request and once the canvas has a size; a re-created canvas (layout switch) does not
    // fit again for the same request (later size changes refit in ViewTransform.resize unless the
    // user zoomed or panned)
    val sized = view.size.width > 0 && view.size.height > 0
    LaunchedEffect(state.fitRequest, sized) {
        if (sized && view.fittedRequest != state.fitRequest) {
            view.fit(state.points)
            view.fittedRequest = state.fitRequest
        }
    }

    // the point whose context menu is open (right-click or long press on it) and where it opens
    val gestures = LocalMenuGestures.current
    var pointMenu by remember { mutableStateOf<Pair<Int, Offset>?>(null) }

    fun hit(pos: Offset): Int? {
        var best: Int? = null
        var bestD = HIT_RADIUS
        st.points.forEachIndexed { i, p ->
            val d = (view.toScreen(p) - pos).getDistance()
            if (d <= bestD) { bestD = d; best = i }
        }
        return best
    }

    Box(modifier.fillMaxSize()) {
        Canvas(
            Modifier
                .fillMaxSize()
                .testTag("canvas")
                .clipToBounds()
                .background(MaterialTheme.colorScheme.surface)
                .onSizeChanged { view.resize(it, st.points) }
                .pointerInput(gestures) {
                    // a gesture cancelled mid-drag (canvas re-created) must not leave the view in drag mode
                    try {
                        awaitPointerEventScope {
                            var down: Offset? = null
                            var last = Offset.Zero
                            var dragPoint: Int? = null
                            var hitAtDown: Int? = null
                            var moved = false
                            var dragStarted = false
                            var pinchDist = 0f
                            var pinchCenter = Offset.Zero
                            // a one-finger press held still until then is a long press (event time)
                            var longPressAt: Long? = null
                            var lastTime = 0L
                            while (true) {
                                val deadline = longPressAt?.takeIf { down != null && !moved }
                                val ev = if (deadline == null) awaitPointerEvent()
                                else withTimeoutOrNull((deadline - lastTime).coerceAtLeast(0L)) { awaitPointerEvent() }
                                if (ev == null) {
                                    // long press: a point's context menu like a right-click, the release then does
                                    // nothing; on empty space no menu, the release is a tap as before
                                    longPressAt = null
                                    val at = down
                                    val i = if (at != null) hit(at) else null
                                    if (at != null && i != null) { pointMenu = i to at; down = null; dragPoint = null }
                                    continue
                                }
                                lastTime = ev.changes.first().uptimeMillis
                                val pressed = ev.changes.filter { it.pressed }
                                when (ev.type) {
                                    PointerEventType.Scroll -> {
                                        val c = ev.changes.first()
                                        // mouse wheels report about 1 per notch; some browsers report pixels
                                        val dy = c.scrollDelta.y.toDouble()
                                        val notches = (if (abs(dy) >= 10) dy / 100 else dy).coerceIn(-5.0, 5.0)
                                        view.zoomAt(c.position, 1.15.pow(-notches))
                                        c.consume()
                                    }
                                    PointerEventType.Press -> {
                                        if (st.consumeMenuPress()) {
                                            // the press only closes the open menu (its popup is not focusable)
                                            st.openMenu = null
                                            down = null; dragPoint = null
                                        } else {
                                            onInteract()
                                            if (gestures.rightClick && (ev.buttons.isSecondaryPressed || ctrlClickIsRightClick && ev.keyboardModifiers.isCtrlPressed)) {
                                                // right-click: the context menu of the point under it, nothing on empty space
                                                down = null; dragPoint = null
                                                if (pressed.size == 1) hit(pressed[0].position)?.let { pointMenu = it to pressed[0].position }
                                            } else if (pressed.size == 1) {
                                                val pos = pressed[0].position
                                                down = pos; last = pos; moved = false; dragStarted = false
                                                view.dragging = false
                                                hitAtDown = hit(pos)
                                                dragPoint = if (st.tool != Tool.DELETE) hitAtDown else null
                                                longPressAt = if (gestures.longPress) lastTime + viewConfiguration.longPressTimeoutMillis else null
                                            } else if (pressed.size == 2) {
                                                // second finger: switch to pinch zoom + two-finger pan
                                                dragPoint = null; moved = true
                                                pinchDist = (pressed[0].position - pressed[1].position).getDistance()
                                                pinchCenter = (pressed[0].position + pressed[1].position) / 2f
                                            }
                                        }
                                        ev.changes.forEach { it.consume() }
                                    }
                                    PointerEventType.Move -> {
                                        if (pressed.size >= 2) {
                                            val d = (pressed[0].position - pressed[1].position).getDistance()
                                            val center = (pressed[0].position + pressed[1].position) / 2f
                                            view.pan((center.x - pinchCenter.x).toDouble(), (center.y - pinchCenter.y).toDouble())
                                            if (pinchDist > 0f && d > 0f) view.zoomAt(center, (d / pinchDist).toDouble())
                                            pinchDist = d
                                            pinchCenter = center
                                        } else if (pressed.size == 1 && down != null) {
                                            val pos = pressed[0].position
                                            if (!moved && (pos - down).getDistance() > TOUCH_SLOP) moved = true
                                            if (moved) {
                                                val dp = dragPoint
                                                if (dp != null) {
                                                    if (!dragStarted) { st.beginDrag(dp); dragStarted = true; view.dragging = true }
                                                    st.dragTo(dp, view.toWorld(pos))
                                                } else {
                                                    view.pan((pos.x - last.x).toDouble(), (pos.y - last.y).toDouble())
                                                }
                                            }
                                            last = pos
                                        }
                                        ev.changes.forEach { it.consume() }
                                    }
                                    PointerEventType.Release -> {
                                        if (pressed.isEmpty()) {
                                            val start = down
                                            if (start != null && !moved) {
                                                val h = hitAtDown
                                                when (st.tool) {
                                                    Tool.MOVE -> st.select(h)
                                                    Tool.ADD -> if (h != null) st.select(h)
                                                    else view.coordinatesDoubled(st.addPointAt(view.worldX(start), view.worldY(start)))
                                                    Tool.DELETE -> if (h != null) st.deletePoint(h)
                                                }
                                            }
                                            val dp = dragPoint
                                            if (start != null && dragStarted && dp != null) {
                                                val pos = ev.changes.first().position
                                                view.coordinatesDoubled(st.endDrag(dp, view.worldX(pos), view.worldY(pos)))
                                            }
                                            down = null; dragPoint = null; pinchDist = 0f
                                            view.dragging = false
                                        } else {
                                            // back from pinch to one finger: continue panning from here
                                            last = pressed[0].position
                                            pinchDist = 0f
                                        }
                                        ev.changes.forEach { it.consume() }
                                    }
                                }
                            }
                        }
                    } finally {
                        view.dragging = false
                    }
                }
        ) {
            val pts = state.points
            val scr = pts.map { view.toScreen(it) }
            val sel = state.selected

            if (state.showLines) {
                val separate = sel.takeIf { state.incidentLines != IncidentLines.NORMAL }
                drawArrangement(scr, separate, state.lineColor.copy(alpha = state.lineAlpha), state.lineWidth)
                if (sel != null && sel in scr.indices && state.incidentLines == IncidentLines.HIGHLIGHT)
                    drawIncidentLines(scr, sel, state.incidentLineColor, state.incidentLineWidth)
            }

            if (state.highlight) drawStructures(state, scr)

            scr.forEachIndexed { i, s ->
                val r = if (i == sel) state.pointRadius * state.selectedScalePercent / 100f else state.pointRadius
                val fill = if (i == sel) state.selectedColor else tokenColor(pts[i].color) ?: onSurface
                drawCircle(fill, r, s)
                if (i in state.collinear) drawCircle(Color(0xFFE53935), r + 4f, s, style = Stroke(3f))
                if (i == sel) drawCircle(state.selectedColor, r + 5f, s, style = Stroke(3f))
                if (state.showLabels) {
                    // only labels inside the canvas: drawText needs room right of / below its position
                    val at = s + Offset(r + 2f, -r - 14f)
                    if (at.x >= 0f && at.y >= 0f && at.x < size.width - 1f && at.y < size.height - 1f)
                        drawText(textMeasurer, "$i", at, TextStyle(color = onSurface, fontSize = 11.sp))
                }
            }
        }
        pointMenu?.let { (i, at) ->
            // a zero-size anchor at the pointer: the menu opens right there
            Box(Modifier.offset { IntOffset(at.x.roundToInt(), at.y.roundToInt()) }) {
                DropdownMenu(expanded = true, onDismissRequest = { pointMenu = null }) {
                    // numbered like the point labels
                    MenuHeading("Point $i")
                    // the points may have changed meanwhile (an operation): only a valid index
                    DropdownMenuItem(text = { Text("Select") }, onClick = {
                        pointMenu = null
                        if (i in st.points.indices) st.select(i)
                    })
                    DropdownMenuItem(text = { Text("Delete") }, onClick = { pointMenu = null; st.deletePoint(i) })
                }
            }
        }
    }
}

/** The full line through every pair of points; lines through [hidden] are left out (qtvis). */
private fun DrawScope.drawArrangement(scr: List<Offset>, hidden: Int?, color: Color, width: Float) {
    val reach = (size.width + size.height) * 2f
    for (i in scr.indices) {
        if (i == hidden) continue
        for (j in i + 1 until scr.size) {
            if (j == hidden) continue
            val a = scr[i]
            val b = scr[j]
            val len = hypot((b.x - a.x).toDouble(), (b.y - a.y).toDouble()).toFloat()
            if (len < 1e-3f) continue
            val d = (b - a) / len
            // extend far enough beyond the canvas from wherever the points are
            val ext = reach + (a - Offset(size.width / 2, size.height / 2)).getDistance()
            drawLine(color, a - d * ext, b + d * ext, strokeWidth = width)
        }
    }
}

/** The lines through point [sel] and every other point. */
private fun DrawScope.drawIncidentLines(scr: List<Offset>, sel: Int, color: Color, width: Float) {
    val reach = (size.width + size.height) * 2f
    val a = scr[sel]
    for (j in scr.indices) {
        if (j == sel) continue
        val b = scr[j]
        val len = hypot((b.x - a.x).toDouble(), (b.y - a.y).toDouble()).toFloat()
        if (len < 1e-3f) continue
        val d = (b - a) / len
        val ext = reach + (a - Offset(size.width / 2, size.height / 2)).getDistance()
        drawLine(color, a - d * ext, b + d * ext, strokeWidth = width)
    }
}

private fun DrawScope.drawStructures(state: AppState, scr: List<Offset>) {
    for (prop in ALL_PROPERTIES) {
        val t = state.toggles[prop.id] ?: continue
        if (!t.count || !t.draw) continue
        val res = (state.results[prop.id] as? PropState.Done)?.result ?: continue
        val list = res.structures ?: continue
        val c = state.colorOf(prop.id)
        // emphasized properties (crossing families): opaque and bold
        val strong = prop.emphasized
        val width = (if (strong) 3.5f else 1.5f) * state.lineWidth
        // (with "incident only", the structures are already restricted to the selected point)
        for (s in list) {
            if (s.vertices.any { it !in scr.indices }) continue
            when (s.shape) {
                Shape.POLYGON -> {
                    val path = Path().apply {
                        moveTo(scr[s.vertices[0]].x, scr[s.vertices[0]].y)
                        for (v in 1 until s.vertices.size) lineTo(scr[s.vertices[v]].x, scr[s.vertices[v]].y)
                        close()
                    }
                    drawPath(path, c.copy(alpha = 0.13f))
                    drawPath(path, c.copy(alpha = 0.7f), style = Stroke(width))
                }
                Shape.SEGMENTS -> for (v in 0 until s.vertices.size - 1 step 2)
                    drawLine(c.copy(alpha = if (strong) 1f else 0.5f), scr[s.vertices[v]], scr[s.vertices[v + 1]], strokeWidth = width)
            }
        }
    }
}
