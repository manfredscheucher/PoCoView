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

    /** All lines through two points, clipped to the box. */
    fun arrangement(points: List<Pt>): List<DoubleArray> = buildList {
        for (i in points.indices) for (j in i + 1 until points.size) clippedLine(points[i], points[j])?.let(::add)
    }
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
 * SVG of the point set with the given structure layers; fits into a 1000x1000 box, y up.
 * [lines]: draw the line arrangement (all lines through two points, clipped to the box) in this
 * style; null = no lines. [emphasized]: indices of [layers] drawn bold and opaque.
 */
fun pointSetSvg(
    points: List<Pt>,
    layers: List<Pair<Color, List<Structure>>>,
    labels: Boolean,
    lines: LineStyle? = null,
    emphasized: Set<Int> = emptySet(),
): String {
    val fr = ExportFrame(points, 1000.0, 40.0, yDown = true)
    fun x(p: Pt) = fmt(fr.x(p))
    fun y(p: Pt) = fmt(fr.y(p))
    return buildString {
        append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 1000 1000\" width=\"1000\" height=\"1000\">\n")
        append("<rect width=\"1000\" height=\"1000\" fill=\"white\"/>\n")
        if (lines != null) {
            append("<g stroke=\"${lines.color.toHex()}\" stroke-opacity=\"${fmt(lines.alpha.toDouble(), 2)}\" stroke-width=\"${fmt(lines.width.toDouble(), 2)}\">\n")
            for (l in fr.arrangement(points))
                append("<line x1=\"${fmt(l[0])}\" y1=\"${fmt(l[1])}\" x2=\"${fmt(l[2])}\" y2=\"${fmt(l[3])}\"/>\n")
            append("</g>\n")
        }
        layers.forEachIndexed { li, (color, list) ->
            val bold = li in emphasized
            for (st in list) {
                val v = st.vertices.map { points[it] }
                when (st.shape) {
                    Shape.POLYGON -> append(
                        "<polygon points=\"${v.joinToString(" ") { "${x(it)},${y(it)}" }}\" " +
                            "fill=\"${color.toHex()}\" fill-opacity=\"0.13\" stroke=\"${color.toHex()}\" stroke-width=\"${if (bold) 4 else 1.5}\"/>\n"
                    )
                    Shape.SEGMENTS -> for (i in 0 until v.size - 1 step 2) append(
                        "<line x1=\"${x(v[i])}\" y1=\"${y(v[i])}\" x2=\"${x(v[i + 1])}\" y2=\"${y(v[i + 1])}\" " +
                            "stroke=\"${color.toHex()}\" stroke-opacity=\"${if (bold) 1 else 0.5}\" stroke-width=\"${if (bold) 4 else 1.5}\"/>\n"
                    )
                }
            }
        }
        points.forEachIndexed { i, p ->
            val fill = tokenColor(p.color)?.toHex() ?: "black"
            append("<circle cx=\"${x(p)}\" cy=\"${y(p)}\" r=\"6\" fill=\"$fill\"/>\n")
            if (labels) append("<text x=\"${fmt(fr.x(p) + 8)}\" y=\"${fmt(fr.y(p) - 8)}\" font-size=\"14\" font-family=\"sans-serif\">$i</text>\n")
        }
        append("</svg>\n")
    }
}
