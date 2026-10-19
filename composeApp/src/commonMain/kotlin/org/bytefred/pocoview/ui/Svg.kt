package org.bytefred.pocoview.ui

import androidx.compose.ui.graphics.Color
import org.bytefred.pocoview.core.Pt
import org.bytefred.pocoview.core.Shape
import org.bytefred.pocoview.core.Structure

/** SVG of the point set with the given structure layers; fits into a 1000x1000 box, y up. */
fun pointSetSvg(points: List<Pt>, layers: List<Pair<Color, List<Structure>>>, labels: Boolean): String {
    val size = 1000.0
    val margin = 40.0
    val minX = points.minOfOrNull { it.x } ?: 0
    val maxX = points.maxOfOrNull { it.x } ?: 1
    val minY = points.minOfOrNull { it.y } ?: 0
    val maxY = points.maxOfOrNull { it.y } ?: 1
    val s = (size - 2 * margin) / maxOf(1L, maxX - minX, maxY - minY)
    fun x(p: Pt) = margin + (p.x - minX) * s
    fun y(p: Pt) = size - margin - (p.y - minY) * s
    fun f(v: Double) = ((v * 10).toLong() / 10.0).toString()
    return buildString {
        append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 1000 1000\" width=\"1000\" height=\"1000\">\n")
        append("<rect width=\"1000\" height=\"1000\" fill=\"white\"/>\n")
        for ((color, list) in layers) for (st in list) {
            val v = st.vertices.map { points[it] }
            when (st.shape) {
                Shape.POLYGON -> append(
                    "<polygon points=\"${v.joinToString(" ") { "${f(x(it))},${f(y(it))}" }}\" " +
                        "fill=\"${color.toHex()}\" fill-opacity=\"0.13\" stroke=\"${color.toHex()}\" stroke-width=\"1.5\"/>\n"
                )
                Shape.SEGMENTS -> for (i in 0 until v.size - 1 step 2) append(
                    "<line x1=\"${f(x(v[i]))}\" y1=\"${f(y(v[i]))}\" x2=\"${f(x(v[i + 1]))}\" y2=\"${f(y(v[i + 1]))}\" " +
                        "stroke=\"${color.toHex()}\" stroke-opacity=\"0.5\" stroke-width=\"1.5\"/>\n"
                )
            }
        }
        points.forEachIndexed { i, p ->
            val fill = tokenColor(p.color)?.toHex() ?: "black"
            append("<circle cx=\"${f(x(p))}\" cy=\"${f(y(p))}\" r=\"6\" fill=\"$fill\"/>\n")
            if (labels) append("<text x=\"${f(x(p) + 8)}\" y=\"${f(y(p) - 8)}\" font-size=\"14\" font-family=\"sans-serif\">$i</text>\n")
        }
        append("</svg>\n")
    }
}
