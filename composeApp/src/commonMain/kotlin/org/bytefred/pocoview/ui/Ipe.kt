package org.bytefred.pocoview.ui

import androidx.compose.ui.graphics.Color
import org.bytefred.pocoview.core.Pt
import org.bytefred.pocoview.core.Shape
import org.bytefred.pocoview.core.Structure
import kotlin.math.round

/** Ipe colour attribute "r g b". */
private fun Color.ipe(): String = "${fmt(red.toDouble(), 3)} ${fmt(green.toDouble(), 3)} ${fmt(blue.toDouble(), 3)}"

/**
 * Ipe 7 document of the point set: a 500 x 500 pt box on an A4 page, y up, drawn with the Display
 * settings in [style] (canvas pixels become half points, like the 1000-wide SVG box). Layers: "lines"
 * (the line arrangement, clipped to the box; only if shown), "incident" (the highlighted lines through
 * the selected point), "structures" (polygons filled translucently with a separate outline, segments
 * stroked; [emphasized] layer indices bold and opaque), "points" (marks, coloured by the point colour
 * token) and "labels" (if shown).
 * The style sheet defines the mark and the opacities used, so the file does not depend on Ipe's
 * basic style sheet; colours, pens and mark sizes are absolute.
 */
fun pointSetIpe(
    points: List<Pt>,
    layers: List<Pair<Color, List<Structure>>>,
    style: ExportStyle = ExportStyle(),
    emphasized: Set<Int> = emptySet(),
): String {
    // A4 is 595 x 842 pt
    val fr = ExportFrame(points, 500.0, 20.0, yDown = false, offsetX = 47.5, offsetY = 171.0)
    // pen width: the SVG box is 1000 wide, this one 500
    val penScale = 0.5
    fun percent(alpha: Float) = round(alpha * 100.0).toInt().coerceIn(1, 100)
    val lines = style.lines
    val sel = style.selected?.takeIf { it in points.indices }
    val highlighted = style.highlighted?.takeIf { sel != null }
    val opacities = (listOf(13, 50, 70, 100) + listOfNotNull(lines?.let { percent(it.alpha) }, highlighted?.let { percent(it.alpha) }))
        .distinct().sorted()
    fun pos(p: Pt) = "${fmt(fr.x(p), 3)} ${fmt(fr.y(p), 3)}"
    val body = StringBuilder()
    var layer: String? = null
    /** `layer="..."` on the first object of a new layer. */
    fun layerAttr(name: String): String = if (layer == name) "" else { layer = name; " layer=\"$name\"" }
    fun lineLayer(name: String, ls: LineStyle, list: List<DoubleArray>) {
        for (l in list) body.append(
            "<path${layerAttr(name)} stroke=\"${ls.color.ipe()}\" pen=\"${fmt(ls.width * penScale, 3)}\" opacity=\"${percent(ls.alpha)}%\">\n" +
                "${fmt(l[0], 3)} ${fmt(l[1], 3)} m\n${fmt(l[2], 3)} ${fmt(l[3], 3)} l\n</path>\n"
        )
    }

    if (lines != null) lineLayer("lines", lines, fr.arrangement(points, style.separate))
    if (highlighted != null && sel != null) lineLayer("incident", highlighted, fr.incident(points, sel))
    layers.forEachIndexed { li, (color, list) ->
        val bold = li in emphasized
        val pen = fmt(style.structurePen(bold) * penScale, 3)
        for (st in list.drawable(points.size)) {
            val v = st.vertices.map { points[it] }
            when (st.shape) {
                // Ipe's opacity applies to fill and stroke alike: the fill and the outline are two paths
                Shape.POLYGON -> if (v.isNotEmpty()) {
                    val outline = v.mapIndexed { i, p -> "${pos(p)} ${if (i == 0) "m" else "l"}\n" }.joinToString("") + "h\n</path>\n"
                    body.append("<path${layerAttr("structures")} fill=\"${color.ipe()}\" opacity=\"13%\">\n$outline")
                    body.append("<path stroke=\"${color.ipe()}\" pen=\"$pen\" opacity=\"70%\">\n$outline")
                }
                Shape.SEGMENTS -> for (i in 0 until v.size - 1 step 2) body.append(
                    "<path${layerAttr("structures")} stroke=\"${color.ipe()}\" pen=\"$pen\" opacity=\"${if (bold) "100%" else "50%"}\">\n" +
                        "${pos(v[i])} m\n${pos(v[i + 1])} l\n</path>\n"
                )
            }
        }
    }
    // the disk symbol has radius 0.6 * size
    val markSize = fmt(style.pointRadius * penScale / 0.6, 3)
    for (p in points) {
        val c = tokenColor(p.color)?.ipe() ?: "0 0 0"
        body.append("<use${layerAttr("points")} name=\"mark/disk(sx)\" pos=\"${pos(p)}\" size=\"$markSize\" stroke=\"$c\"/>\n")
    }
    val labelOffset = (style.pointRadius + 2) * penScale
    if (style.labels) points.forEachIndexed { i, p ->
        body.append(
            "<text${layerAttr("labels")} transformations=\"translations\" pos=\"${fmt(fr.x(p) + labelOffset, 3)} ${fmt(fr.y(p) + labelOffset, 3)}\" " +
                "stroke=\"0 0 0\" type=\"label\" valign=\"baseline\">$i</text>\n"
        )
    }
    val used = listOfNotNull(
        "lines".takeIf { lines != null }, "incident".takeIf { highlighted != null }, "structures", "points", "labels".takeIf { style.labels },
    )
    return buildString {
        append("<?xml version=\"1.0\"?>\n<!DOCTYPE ipe SYSTEM \"ipe.dtd\">\n<ipe version=\"70218\" creator=\"PoCoView\">\n")
        append("<ipestyle name=\"pocoview\">\n")
        append("<symbol name=\"mark/disk(sx)\" transformations=\"translations\">\n<path fill=\"sym-stroke\">\n0.6 0 0 0.6 0 0 e\n</path>\n</symbol>\n")
        for (o in opacities) append("<opacity name=\"$o%\" value=\"${fmt(o / 100.0, 2)}\"/>\n")
        append("</ipestyle>\n<page>\n")
        for (l in used) append("<layer name=\"$l\"/>\n")
        append("<view layers=\"${used.joinToString(" ")}\" active=\"points\"/>\n")
        append(body)
        append("</page>\n</ipe>\n")
    }
}
