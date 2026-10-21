package org.bytefred.pocoview.ui

import androidx.compose.ui.graphics.Color
import org.bytefred.pocoview.core.Pt
import org.bytefred.pocoview.core.Shape
import org.bytefred.pocoview.core.Structure
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExportTest {
    private val pts = listOf(Pt(0, 0), Pt(100, 0, "R"), Pt(30, 80), Pt(60, 20))
    private val layers = listOf(
        Color(0xFF1E88E5) to listOf(Structure(intArrayOf(0, 1, 2))),
        Color(0xFFE53935) to listOf(Structure(intArrayOf(0, 2, 1, 3), Shape.SEGMENTS)),
    )

    private fun parse(xml: String) = DocumentBuilderFactory.newInstance().apply {
        setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
    }.newDocumentBuilder().parse(ByteArrayInputStream(xml.encodeToByteArray())).documentElement

    private fun Element.all(tag: String) = getElementsByTagName(tag).let { l -> List(l.length) { l.item(it) as Element } }

    @Test
    fun svgWithLines() {
        val svg = parse(pointSetSvg(pts, layers, labels = true, lines = LineStyle(Color.Black, 0.25f, 2f), emphasized = setOf(1)))
        assertEquals(4, svg.all("circle").size)
        // 6 arrangement lines + 2 segments
        assertEquals(8, svg.all("line").size)
        for (l in svg.all("line")) for (a in listOf("x1", "y1", "x2", "y2"))
            assertTrue(l.getAttribute(a).toDouble() in -0.05..1000.05, "${l.getAttribute(a)} inside the view box")
        assertEquals("0.25", svg.all("g").single().getAttribute("stroke-opacity"))
        assertEquals("4", svg.all("line").last().getAttribute("stroke-width"))
        assertEquals(4, svg.all("text").size)
        // no lines without a style
        assertEquals(2, parse(pointSetSvg(pts, layers, labels = false)).all("line").size)
    }

    @Test
    fun clippedLinesEndOnTheBorder() {
        val fr = ExportFrame(pts, 1000.0, 40.0, yDown = true)
        for (l in fr.arrangement(pts)) {
            fun onBorder(x: Double, y: Double) = listOf(x, 1000 - x, y, 1000 - y).any { kotlin.math.abs(it) < 1e-6 }
            assertTrue(onBorder(l[0], l[1]) && onBorder(l[2], l[3]), l.toList().toString())
        }
    }

    @Test
    fun ipeDocument() {
        val xml = pointSetIpe(pts, layers, labels = true, lines = LineStyle(alpha = 0.18f), emphasized = setOf(1))
        assertTrue(xml.startsWith("<?xml version=\"1.0\"?>\n<!DOCTYPE ipe SYSTEM \"ipe.dtd\">\n<ipe version=\"70218\" creator=\"PoCoView\">"))
        val ipe = parse(xml)
        assertEquals("ipe", ipe.tagName)
        val marks = ipe.all("use")
        assertEquals(4, marks.size)
        assertTrue(marks.all { it.getAttribute("name") == "mark/disk(sx)" && it.getAttribute("pos").split(" ").size == 2 })
        // 6 lines + 1 polygon + 2 segments, plus the mark symbol's path
        assertEquals(10, ipe.all("path").size)
        assertEquals(4, ipe.all("text").size)
        assertEquals(listOf("lines", "structures", "points", "labels"), ipe.all("layer").map { it.getAttribute("name") })
        val opacities = ipe.all("opacity").map { it.getAttribute("name") }.toSet()
        for (p in ipe.all("path")) if (p.hasAttribute("opacity")) assertTrue(p.getAttribute("opacity") in opacities)
        assertTrue("18%" in opacities)
        // n marks, no lines and labels
        val plain = parse(pointSetIpe(pts, emptyList(), labels = false))
        assertEquals(4, plain.all("use").size)
        assertEquals(1, plain.all("path").size)
        assertEquals(listOf("structures", "points"), plain.all("layer").map { it.getAttribute("name") })
    }
}
