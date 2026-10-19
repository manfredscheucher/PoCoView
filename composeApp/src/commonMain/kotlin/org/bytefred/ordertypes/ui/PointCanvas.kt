package org.bytefred.ordertypes.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import org.bytefred.ordertypes.core.ALL_PROPERTIES
import org.bytefred.ordertypes.core.Pt
import org.bytefred.ordertypes.core.Shape
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToLong

/** World (data coordinates, y up) <-> screen (pixels, y down): screen = origin + (x, -y) * scale. */
class ViewTransform {
    var scale by mutableDoubleStateOf(1.0)
    var ox by mutableDoubleStateOf(0.0)
    var oy by mutableDoubleStateOf(0.0)
    var size by mutableStateOf(IntSize.Zero)

    fun toScreen(p: Pt) = Offset((ox + p.x * scale).toFloat(), (oy - p.y * scale).toFloat())
    fun toWorld(s: Offset) = Pt(((s.x - ox) / scale).roundToLong(), ((oy - s.y) / scale).roundToLong())

    fun zoomAt(anchor: Offset, factor: Double) {
        val f = factor.coerceIn(0.02, 50.0)
        val newScale = (scale * f).coerceIn(1e-7, 1e4)
        val k = newScale / scale
        ox = anchor.x - (anchor.x - ox) * k
        oy = anchor.y - (anchor.y - oy) * k
        scale = newScale
    }

    fun zoomCenter(factor: Double) = zoomAt(Offset(size.width / 2f, size.height / 2f), factor)

    fun fit(points: List<Pt>) {
        if (size.width == 0 || size.height == 0) return
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

private val PALETTE = listOf(
    Color(0xFF1E88E5), Color(0xFFE53935), Color(0xFF43A047), Color(0xFFFB8C00), Color(0xFF8E24AA),
    Color(0xFF00ACC1), Color(0xFF6D4C41), Color(0xFFD81B60), Color(0xFF7CB342), Color(0xFF3949AB),
    Color(0xFFFDD835), Color(0xFF546E7A), Color(0xFF00897B),
)

/** Stable drawing colour of a property (by its position in the registry). */
fun propertyColor(index: Int): Color = PALETTE[index % PALETTE.size]

/** Colour of a point colour token (PoSeZo bicoloured sets: B / R). */
fun tokenColor(token: String?): Color? = when (token?.uppercase()) {
    "R" -> Color(0xFFC62828)
    "B" -> Color(0xFF1565C0)
    "G" -> Color(0xFF2E7D32)
    else -> null
}

private const val HIT_RADIUS = 16f
private const val TOUCH_SLOP = 4f

@Composable
fun PointCanvas(state: AppState, view: ViewTransform, modifier: Modifier = Modifier, onInteract: () -> Unit = {}) {
    val st by rememberUpdatedState(state)
    val textMeasurer = rememberTextMeasurer()
    val onSurface = MaterialTheme.colorScheme.onSurface
    val lineColor = onSurface.copy(alpha = 0.18f)

    // fit on request and once the canvas has a size; a later resize keeps the user's zoom
    val sized = view.size.width > 0 && view.size.height > 0
    LaunchedEffect(state.fitRequest, sized) { if (sized) view.fit(state.points) }

    fun hit(pos: Offset): Int? {
        var best: Int? = null
        var bestD = HIT_RADIUS
        st.points.forEachIndexed { i, p ->
            val d = (view.toScreen(p) - pos).getDistance()
            if (d <= bestD) { bestD = d; best = i }
        }
        return best
    }

    Canvas(
        modifier
            .fillMaxSize()
            .clipToBounds()
            .background(MaterialTheme.colorScheme.surface)
            .onSizeChanged { view.size = it }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    var down: Offset? = null
                    var last = Offset.Zero
                    var dragPoint: Int? = null
                    var hitAtDown: Int? = null
                    var moved = false
                    var dragStarted = false
                    var pinchDist = 0f
                    var pinchCenter = Offset.Zero
                    while (true) {
                        val ev = awaitPointerEvent()
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
                                onInteract()
                                if (pressed.size == 1) {
                                    val pos = pressed[0].position
                                    down = pos; last = pos; moved = false; dragStarted = false
                                    hitAtDown = hit(pos)
                                    dragPoint = if (st.tool != Tool.DELETE) hitAtDown else null
                                } else if (pressed.size == 2) {
                                    // second finger: switch to pinch zoom + two-finger pan
                                    dragPoint = null; moved = true
                                    pinchDist = (pressed[0].position - pressed[1].position).getDistance()
                                    pinchCenter = (pressed[0].position + pressed[1].position) / 2f
                                }
                                ev.changes.forEach { it.consume() }
                            }
                            PointerEventType.Move -> {
                                if (pressed.size >= 2) {
                                    val d = (pressed[0].position - pressed[1].position).getDistance()
                                    val center = (pressed[0].position + pressed[1].position) / 2f
                                    view.ox += (center.x - pinchCenter.x).toDouble()
                                    view.oy += (center.y - pinchCenter.y).toDouble()
                                    if (pinchDist > 0f && d > 0f) view.zoomAt(center, (d / pinchDist).toDouble())
                                    pinchDist = d
                                    pinchCenter = center
                                } else if (pressed.size == 1 && down != null) {
                                    val pos = pressed[0].position
                                    if (!moved && (pos - down).getDistance() > TOUCH_SLOP) moved = true
                                    if (moved) {
                                        val dp = dragPoint
                                        if (dp != null) {
                                            if (!dragStarted) { st.beginDrag(dp); dragStarted = true }
                                            st.dragTo(dp, view.toWorld(pos))
                                        } else {
                                            view.ox += (pos.x - last.x).toDouble()
                                            view.oy += (pos.y - last.y).toDouble()
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
                                            Tool.ADD -> if (h != null) st.select(h) else st.addPoint(view.toWorld(start))
                                            Tool.DELETE -> if (h != null) st.deletePoint(h)
                                        }
                                    }
                                    down = null; dragPoint = null; pinchDist = 0f
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
            }
    ) {
        val pts = state.points
        val scr = pts.map { view.toScreen(it) }
        val sel = state.selected

        if (state.showLines) drawArrangement(scr, sel.takeIf { state.hideSelectedLines }, lineColor)

        if (state.mode != Mode.COUNT) drawStructures(state, scr)

        val r = 6f
        scr.forEachIndexed { i, s ->
            val fill = tokenColor(pts[i].color) ?: onSurface
            drawCircle(fill, r, s)
            if (i in state.collinear) drawCircle(Color(0xFFE53935), r + 4f, s, style = Stroke(3f))
            if (i == sel) drawCircle(Color(0xFFFF9800), r + 5f, s, style = Stroke(3f))
            if (state.showLabels) {
                drawText(textMeasurer, "$i", s + Offset(r + 2f, -r - 14f), TextStyle(color = onSurface, fontSize = 11.sp))
            }
        }
    }
}

/** The full line through every pair of points; lines through [hidden] are left out (qtvis). */
private fun DrawScope.drawArrangement(scr: List<Offset>, hidden: Int?, color: Color) {
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
            drawLine(color, a - d * ext, b + d * ext, strokeWidth = 1f)
        }
    }
}

private fun DrawScope.drawStructures(state: AppState, scr: List<Offset>) {
    ALL_PROPERTIES.forEachIndexed { idx, prop ->
        val t = state.toggles[prop.id] ?: return@forEachIndexed
        if (!t.count || !t.draw) return@forEachIndexed
        val res = (state.results[prop.id] as? PropState.Done)?.result ?: return@forEachIndexed
        val list = res.structures ?: return@forEachIndexed
        val c = propertyColor(idx)
        val sel = state.selected.takeIf { state.mode == Mode.INCIDENT }
        for (s in list) {
            if (s.vertices.any { it !in scr.indices }) continue
            // incident mode "highlight": incident structures strong, the others faint
            val incident = sel != null && sel in s.vertices
            val faint = sel != null && !incident
            if (faint && state.incidentOnly) continue
            val fillAlpha = if (faint) 0.05f else if (incident && !state.incidentOnly) 0.3f else 0.13f
            val strokeAlpha = if (faint) 0.25f else 0.7f
            val width = if (incident && !state.incidentOnly) 2.5f else 1.5f
            when (s.shape) {
                Shape.POLYGON -> {
                    val path = Path().apply {
                        moveTo(scr[s.vertices[0]].x, scr[s.vertices[0]].y)
                        for (v in 1 until s.vertices.size) lineTo(scr[s.vertices[v]].x, scr[s.vertices[v]].y)
                        close()
                    }
                    drawPath(path, c.copy(alpha = fillAlpha))
                    drawPath(path, c.copy(alpha = strokeAlpha), style = Stroke(width))
                }
                Shape.SEGMENTS -> for (v in 0 until s.vertices.size - 1 step 2)
                    drawLine(c.copy(alpha = strokeAlpha), scr[s.vertices[v]], scr[s.vertices[v + 1]], strokeWidth = width)
            }
        }
    }
}
