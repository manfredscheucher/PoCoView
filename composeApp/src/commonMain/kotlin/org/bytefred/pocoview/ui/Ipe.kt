package org.bytefred.pocoview.ui

import androidx.compose.ui.graphics.Color
import org.bytefred.pocoview.core.Pt
import org.bytefred.pocoview.core.Shape
import org.bytefred.pocoview.core.Structure
import kotlin.math.round

/** Ipe colour attribute "r g b". */
private fun Color.ipe(): String = "${fmt(red.toDouble(), 3)} ${fmt(green.toDouble(), 3)} ${fmt(blue.toDouble(), 3)}"

/**
 * Ipe 7 document of the point set: a 500 x 500 pt box on an A4 page, y up. Layers: "lines" (the
 * line arrangement in [lines] style, clipped to the box; null = no lines), "structures" (polygons
 * filled translucently, segments stroked; [emphasized] layer indices bold and opaque), "points"
 * (marks, coloured by the point colour token) and "labels" (if [labels]).
 * The style sheet defines the mark and the opacities used, so the file does not depend on Ipe's
 * basic style sheet; colours are absolute RGB.
 */
fun pointSetIpe(
    points: List<Pt>,
    layers: List<Pair<Color, List<Structure>>>,
    labels: Boolean,
    lines: LineStyle? = null,
    emphasized: Set<Int> = emptySet(),
): String {
    // A4 is 595 x 842 pt
    val fr = ExportFrame(points, 500.0, 20.0, yDown = false, offsetX = 47.5, offsetY = 171.0)
    // pen width: the SVG box is 1000 wide, this one 500
    val penScale = 0.5
    val lineOpacity = lines?.let { round(it.alpha * 100.0).toInt().coerceIn(1, 100) }
    val opacities = (listOf(13, 50, 100) + listOfNotNull(lineOpacity)).distinct().sorted()
    fun pos(p: Pt) = "${fmt(fr.x(p), 3)} ${fmt(fr.y(p), 3)}"
    val body = StringBuilder()
    var layer: String? = null
    /** `layer="..."` on the first object of a new layer. */
    fun layerAttr(name: String): String = if (layer == name) "" else { layer = name; " layer=\"$name\"" }

    if (lines != null) {
        for (l in fr.arrangement(points)) body.append(
            "<path${layerAttr("lines")} stroke=\"${lines.color.ipe()}\" pen=\"${fmt(lines.width * penScale, 3)}\" opacity=\"$lineOpacity%\">\n" +
                "${fmt(l[0], 3)} ${fmt(l[1], 3)} m\n${fmt(l[2], 3)} ${fmt(l[3], 3)} l\n</path>\n"
        )
    }
    layers.forEachIndexed { li, (color, list) ->
        val bold = li in emphasized
        val pen = fmt((if (bold) 4.0 else 1.5) * penScale, 3)
        for (st in list) {
            val v = st.vertices.map { points[it] }
            when (st.shape) {
                Shape.POLYGON -> if (v.isNotEmpty()) body.append(
                    "<path${layerAttr("structures")} stroke=\"${color.ipe()}\" fill=\"${color.ipe()}\" pen=\"$pen\" opacity=\"13%\">\n" +
                        v.mapIndexed { i, p -> "${pos(p)} ${if (i == 0) "m" else "l"}\n" }.joinToString("") + "h\n</path>\n"
                )
                Shape.SEGMENTS -> for (i in 0 until v.size - 1 step 2) body.append(
                    "<path${layerAttr("structures")} stroke=\"${color.ipe()}\" pen=\"$pen\" opacity=\"${if (bold) "100%" else "50%"}\">\n" +
                        "${pos(v[i])} m\n${pos(v[i + 1])} l\n</path>\n"
                )
            }
        }
    }
    for (p in points) {
        val c = tokenColor(p.color)?.ipe() ?: "0 0 0"
        body.append("<use${layerAttr("points")} name=\"mark/disk(sx)\" pos=\"${pos(p)}\" size=\"normal\" stroke=\"$c\"/>\n")
    }
    if (labels) points.forEachIndexed { i, p ->
        body.append(
            "<text${layerAttr("labels")} transformations=\"translations\" pos=\"${fmt(fr.x(p) + 3, 3)} ${fmt(fr.y(p) + 3, 3)}\" " +
                "stroke=\"0 0 0\" type=\"label\" valign=\"baseline\">$i</text>\n"
        )
    }
    val used = listOfNotNull("lines".takeIf { lines != null }, "structures", "points", "labels".takeIf { labels })
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
