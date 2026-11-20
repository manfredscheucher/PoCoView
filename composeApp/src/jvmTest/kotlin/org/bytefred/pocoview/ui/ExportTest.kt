package org.bytefred.pocoview.ui

import androidx.compose.ui.graphics.Color
import org.bytefred.pocoview.core.Pt
import org.bytefred.pocoview.core.Shape
import org.bytefred.pocoview.core.Structure
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
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
        val svg = parse(pointSetSvg(pts, layers, ExportStyle(lines = LineStyle(Color.Black, 0.25f, 2f)), emphasized = setOf(1)))
        assertEquals(4, svg.all("circle").size)
        // 6 arrangement lines + 2 segments
        assertEquals(8, svg.all("line").size)
        for (l in svg.all("line")) for (a in listOf("x1", "y1", "x2", "y2"))
            assertTrue(l.getAttribute(a).toDouble() in -0.05..1000.05, "${l.getAttribute(a)} inside the view box")
        assertEquals("0.25", svg.all("g").single().getAttribute("stroke-opacity"))
        // emphasized: 3.5 times the line width, as on the canvas
        assertEquals("3.50", svg.all("line").last().getAttribute("stroke-width"))
        assertEquals(4, svg.all("text").size)
        // no lines without a style
        assertEquals(2, parse(pointSetSvg(pts, layers, ExportStyle(labels = false))).all("line").size)
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
        val xml = pointSetIpe(pts, layers, ExportStyle(lines = LineStyle(alpha = 0.18f)), emphasized = setOf(1))
        assertTrue(xml.startsWith("<?xml version=\"1.0\"?>\n<!DOCTYPE ipe SYSTEM \"ipe.dtd\">\n<ipe version=\"70218\" creator=\"PoCoView\">"))
        val ipe = parse(xml)
        assertEquals("ipe", ipe.tagName)
        val marks = ipe.all("use")
        assertEquals(4, marks.size)
        assertTrue(marks.all { it.getAttribute("name") == "mark/disk(sx)" && it.getAttribute("pos").split(" ").size == 2 })
        // 6 lines + 1 polygon (fill and outline) + 2 segments, plus the mark symbol's path
        assertEquals(11, ipe.all("path").size)
        assertEquals(4, ipe.all("text").size)
        assertEquals(listOf("lines", "structures", "points", "labels"), ipe.all("layer").map { it.getAttribute("name") })
        val opacities = ipe.all("opacity").map { it.getAttribute("name") }.toSet()
        for (p in ipe.all("path")) if (p.hasAttribute("opacity")) assertTrue(p.getAttribute("opacity") in opacities)
        assertTrue("18%" in opacities)
        // n marks, no lines and labels
        val plain = parse(pointSetIpe(pts, emptyList(), ExportStyle(labels = false)))
        assertEquals(4, plain.all("use").size)
        assertEquals(1, plain.all("path").size)
        assertEquals(listOf("structures", "points"), plain.all("layer").map { it.getAttribute("name") })
    }

    private val drawLines = LineStyle(Color.Black, 0.25f, 1f)

    @Test
    fun svgUsesPointSizeAndLineWidth() {
        val svg = parse(pointSetSvg(pts, layers, ExportStyle(pointRadius = 11f, structureWidth = 2f), emphasized = setOf(1)))
        assertTrue(svg.all("circle").all { it.getAttribute("r") == "11.00" })
        assertEquals("3.00", svg.all("polygon").single().getAttribute("stroke-width"))
        assertEquals("0.7", svg.all("polygon").single().getAttribute("stroke-opacity"))
        assertEquals("7.00", svg.all("line").last().getAttribute("stroke-width"))
    }

    @Test
    fun selectedPointLines() {
        fun svg(mode: IncidentLines, selected: Int? = 2) =
            parse(pointSetSvg(pts, emptyList(), ExportStyle(lines = drawLines, selected = selected, incident = mode)))
        // hide: the 3 lines through the selected point are left out
        assertEquals(3, svg(IncidentLines.HIDE).all("line").size)
        assertEquals(6, svg(IncidentLines.NORMAL).all("line").size)
        assertEquals(6, svg(IncidentLines.HIDE, selected = null).all("line").size)
        // highlight: the 3 others plus the 3 through it in their own colour and width
        val groups = svg(IncidentLines.HIGHLIGHT).all("g")
        assertEquals(listOf(3, 3), groups.map { it.getElementsByTagName("line").length })
        assertEquals("#E53935", groups[1].getAttribute("stroke").uppercase())
        assertEquals("2.00", groups[1].getAttribute("stroke-width"))
        // no arrangement shown: no lines through the selected point either
        assertEquals(0, parse(pointSetSvg(pts, emptyList(), ExportStyle(selected = 2, incident = IncidentLines.HIGHLIGHT))).all("line").size)
    }

    @Test
    fun ipeUsesTheSettings() {
        val ipe = parse(
            pointSetIpe(pts, emptyList(), ExportStyle(pointRadius = 9f, lines = drawLines, selected = 0, incident = IncidentLines.HIGHLIGHT))
        )
        // radius 9 px = 4.5 pt = 0.6 * size
        assertTrue(ipe.all("use").all { it.getAttribute("size") == "7.500" })
        assertEquals(listOf("lines", "incident", "points", "labels"), ipe.all("layer").map { it.getAttribute("name") }.filter { it != "structures" })
        val incident = ipe.all("path").filter { it.getAttribute("pen") == "1.000" }
        assertEquals(3, incident.size)
        val opacities = ipe.all("opacity").map { it.getAttribute("name") }.toSet()
        for (p in ipe.all("path")) if (p.hasAttribute("opacity")) assertTrue(p.getAttribute("opacity") in opacities)
        // hide: only the 3 lines not through point 0
        val hidden = parse(pointSetIpe(pts, emptyList(), ExportStyle(lines = drawLines, selected = 0)))
        assertEquals(3 + 1, hidden.all("path").size)
    }

    @Test
    fun structuresWithInvalidIndicesAreSkipped() {
        val bad = listOf(Color.Red to listOf(Structure(intArrayOf(0, 1, 7)), Structure(intArrayOf(0, 9), Shape.SEGMENTS)))
        assertEquals(0, parse(pointSetSvg(pts, bad)).all("polygon").size)
        assertEquals(1, parse(pointSetIpe(pts, bad)).all("path").size)
    }

    @Test
    fun exportFollowsTheAppState() = runBlocking {
        clearInMemoryPrefs()
        val s = AppState(CoroutineScope(SupervisorJob() + Dispatchers.Default))
        s.loadPoints("t", pts)
        s.awaitIdle()
        s.pointRadius = 10f
        s.showLabels = false
        s.select(1)
        s.incidentLines = IncidentLines.HIDE
        val svg = parse(exportSvg(s))
        assertTrue(svg.all("circle").all { it.getAttribute("r") == "10.00" })
        assertEquals(0, svg.all("text").size)
        assertEquals(3, svg.all("line").size)
        s.showLines = false
        assertEquals(0, parse(exportSvg(s)).all("line").size)
        val ipe = parse(exportIpe(s))
        assertTrue(ipe.all("use").all { it.getAttribute("size") == fmt(10 * 0.5 / 0.6, 3) })
    }
}
