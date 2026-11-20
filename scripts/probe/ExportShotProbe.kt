// Probe (not a test): copy into composeApp/src/jvmTest/kotlin/org/bytefred/pocoview/ui/, run with
// --tests '*ExportShotProbe', then delete. Writes the app screenshot and SVG/Ipe exports with
// non-default Display settings to $EXPORT_SHOT_DIR for a visual comparison.
package org.bytefred.pocoview.ui

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.bytefred.pocoview.core.Pt
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test

class ExportShotProbe {
    @Test
    fun shots() {
        clearInMemoryPrefs()
        val out = File(System.getenv("EXPORT_SHOT_DIR") ?: "build/export-shots").apply { mkdirs() }
        val s = AppState(CoroutineScope(SupervisorJob() + Dispatchers.Default))
        s.loadPoints("probe", listOf(Pt(0, 0), Pt(100, 0, "R"), Pt(30, 80), Pt(60, 20), Pt(90, 70, "B"), Pt(10, 50)))
        s.setToggle("hole4", PropToggle(count = true, draw = true))
        s.changeHighlight(true)
        s.pointRadius = 9f
        s.lineWidth = 2f
        s.lineColor = Color(0xFF1565C0)
        s.lineAlpha = 0.4f
        s.select(3)
        s.selectedScalePercent = 150
        s.incidentLines = IncidentLines.HIGHLIGHT
        val scene = ImageComposeScene(1200, 800, Density(1f)) { App(s, DialogKind.NONE) }
        try {
            for (t in 0L until 5L) scene.render(t * 50_000_000L).close()
            runBlocking { s.awaitIdle() }
            val img = scene.render(400_000_000L)
            File(out, "app.png").writeBytes(img.encodeToData(EncodedImageFormat.PNG)!!.bytes)
        } finally {
            scene.close()
        }
        File(out, "export.svg").writeText(exportSvg(s))
        File(out, "export.ipe").writeText(exportIpe(s))
    }
}
