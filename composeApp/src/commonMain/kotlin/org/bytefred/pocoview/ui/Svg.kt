package org.bytefred.pocoview.ui

import androidx.compose.ui.graphics.Color
import org.bytefred.pocoview.core.Pt
import org.bytefred.pocoview.core.Shape
import org.bytefred.pocoview.core.Structure
import kotlin.math.abs
import kotlin.math.round

/** Style of the line arrangement in exports (the canvas settings). */
data class LineStyle(val color: Color = Color(0xFF1C1B1F), val alpha: Float = 0.18f, val width: Float = 1f)

/**
 * The Display settings an export draws with, as on the canvas (sizes in canvas pixels = SVG units).
 * [lines]: the line arrangement, null = not shown. [selected]: the selected point; its lines are left
 * out of the arrangement unless [incident] is NORMAL, and with HIGHLIGHT drawn in [incidentStyle].
 * [structureWidth]: factor on the structure pen widths (the canvas line width).
 */
data class ExportStyle(
    val labels: Boolean = true,
    val pointRadius: Float = 6f,
    val structureWidth: Float = 1f,
    val lines: LineStyle? = null,
    val selected: Int? = null,
    val incident: IncidentLines = IncidentLines.HIDE,
    val incidentStyle: LineStyle = LineStyle(Color(0xFFE53935), 1f, 2f),
) {
    /** The point whose lines are left out of the arrangement (hide or highlight). */
    val separate: Int? get() = selected.takeIf { incident != IncidentLines.NORMAL }

    /** The style of the lines through the selected point if they are highlighted. */
    val highlighted: LineStyle? get() = incidentStyle.takeIf { lines != null && selected != null && incident == IncidentLines.HIGHLIGHT }

    /** Structure pen width, as on the canvas: 1.5 or (emphasized) 3.5 times the line width. */
    fun structurePen(bold: Boolean) = (if (bold) 3.5 else 1.5) * structureWidth
}

/** The structures with every vertex a valid point index (the canvas skips the others, too). */
internal fun List<Structure>.drawable(n: Int) = filter { st -> st.vertices.all { it in 0 until n } }

/**
 * Maps the points into a [size] x [size] box with [margin] on all sides, keeping the aspect ratio;
 * [yDown]: screen coordinates (SVG) instead of y up (Ipe). [offsetX], [offsetY]: position of the box.
 */
internal class ExportFrame(
    points: List<Pt>, val size: Double, margin: Double, private val yDown: Boolean,
    val offsetX: Double = 0.0, val offsetY: Double = 0.0,
) {
    private val minX = points.minOfOrNull { it.x } ?: 0
    private val minY = points.minOfOrNull { it.y } ?: 0
    private val left = margin
    private val scale = (size - 2 * margin) /
        maxOf(1L, (points.maxOfOrNull { it.x } ?: 1) - minX, (points.maxOfOrNull { it.y } ?: 1) - minY)

    fun x(p: Pt) = offsetX + left + (p.x - minX) * scale
    fun y(p: Pt) = if (yDown) offsetY + size - left - (p.y - minY) * scale else offsetY + left + (p.y - minY) * scale

    /** The line through [a] and [b] clipped to the box, as x1, y1, x2, y2; null for a == b. */
    fun clippedLine(a: Pt, b: Pt): DoubleArray? {
        if (a.x == b.x && a.y == b.y) return null
        val ax = x(a)
        val ay = y(a)
        val dx = x(b) - ax
        val dy = y(b) - ay
        // Liang-Barsky for the whole line ax + t dx, ay + t dy
        var t0 = Double.NEGATIVE_INFINITY
        var t1 = Double.POSITIVE_INFINITY
        fun clip(d: Double, lo: Double, hi: Double, s: Double) {
            if (d == 0.0) return
            val u = (lo - s) / d
            val v = (hi - s) / d
            t0 = maxOf(t0, minOf(u, v))
            t1 = minOf(t1, maxOf(u, v))
        }
        clip(dx, offsetX, offsetX + size, ax)
        clip(dy, offsetY, offsetY + size, ay)
        if (t0 >= t1) return null
        return doubleArrayOf(ax + t0 * dx, ay + t0 * dy, ax + t1 * dx, ay + t1 * dy)
    }

    /** All lines through two points, clipped to the box; lines through [hidden] are left out. */
    fun arrangement(points: List<Pt>, hidden: Int? = null): List<DoubleArray> = buildList {
        for (i in points.indices) if (i != hidden)
            for (j in i + 1 until points.size) if (j != hidden) clippedLine(points[i], points[j])?.let(::add)
    }

    /** The lines through point [sel] and every other point, clipped to the box. */
    fun incident(points: List<Pt>, sel: Int): List<DoubleArray> =
        points.indices.filter { it != sel }.mapNotNull { clippedLine(points[sel], points[it]) }
}

/** [decimals] decimals (rounded), no exponent notation. */
internal fun fmt(v: Double, decimals: Int = 1): String {
    var f = 1L
    repeat(decimals) { f *= 10 }
    val r = round(v * f).toLong()
    val sign = if (r < 0) "-" else ""
    val a = abs(r)
    return if (decimals == 0) "$sign$a" else "$sign${a / f}.${(a % f).toString().padStart(decimals, '0')}"
}

/**
 * SVG of the point set with the given structure layers; fits into a 1000x1000 box, y up, drawn with
 * the Display settings in [style] (the canvas look; points black instead of the theme colour, on
 * white). [emphasized]: indices of [layers] drawn bold and opaque.
 */
fun pointSetSvg(
    points: List<Pt>,
    layers: List<Pair<Color, List<Structure>>>,
    style: ExportStyle = ExportStyle(),
    emphasized: Set<Int> = emptySet(),
): String {
    val fr = ExportFrame(points, 1000.0, 40.0, yDown = true)
    fun x(p: Pt) = fmt(fr.x(p))
    fun y(p: Pt) = fmt(fr.y(p))
    fun lineGroup(sb: StringBuilder, ls: LineStyle, lines: List<DoubleArray>) {
        sb.append("<g stroke=\"${ls.color.toHex()}\" stroke-opacity=\"${fmt(ls.alpha.toDouble(), 2)}\" stroke-width=\"${fmt(ls.width.toDouble(), 2)}\">\n")
        for (l in lines) sb.append("<line x1=\"${fmt(l[0])}\" y1=\"${fmt(l[1])}\" x2=\"${fmt(l[2])}\" y2=\"${fmt(l[3])}\"/>\n")
        sb.append("</g>\n")
    }
    val r = fmt(style.pointRadius.toDouble(), 2)
    return buildString {
        append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 1000 1000\" width=\"1000\" height=\"1000\">\n")
        append("<rect width=\"1000\" height=\"1000\" fill=\"white\"/>\n")
        style.lines?.let { lineGroup(this, it, fr.arrangement(points, style.separate)) }
        val sel = style.selected?.takeIf { it in points.indices }
        style.highlighted?.let { ls -> if (sel != null) lineGroup(this, ls, fr.incident(points, sel)) }
        layers.forEachIndexed { li, (color, list) ->
            val bold = li in emphasized
            val pen = fmt(style.structurePen(bold), 2)
            for (st in list.drawable(points.size)) {
                val v = st.vertices.map { points[it] }
                when (st.shape) {
                    Shape.POLYGON -> append(
                        "<polygon points=\"${v.joinToString(" ") { "${x(it)},${y(it)}" }}\" " +
                            "fill=\"${color.toHex()}\" fill-opacity=\"0.13\" stroke=\"${color.toHex()}\" stroke-opacity=\"0.7\" stroke-width=\"$pen\"/>\n"
                    )
                    Shape.SEGMENTS -> for (i in 0 until v.size - 1 step 2) append(
                        "<line x1=\"${x(v[i])}\" y1=\"${y(v[i])}\" x2=\"${x(v[i + 1])}\" y2=\"${y(v[i + 1])}\" " +
                            "stroke=\"${color.toHex()}\" stroke-opacity=\"${if (bold) 1 else 0.5}\" stroke-width=\"$pen\"/>\n"
                    )
                }
            }
        }
        points.forEachIndexed { i, p ->
            val fill = tokenColor(p.color)?.toHex() ?: "black"
            append("<circle cx=\"${x(p)}\" cy=\"${y(p)}\" r=\"$r\" fill=\"$fill\"/>\n")
            if (style.labels) {
                val d = style.pointRadius + 2.0
                append("<text x=\"${fmt(fr.x(p) + d)}\" y=\"${fmt(fr.y(p) - d)}\" font-size=\"14\" font-family=\"sans-serif\">$i</text>\n")
            }
        }
        append("</svg>\n")
    }
}
