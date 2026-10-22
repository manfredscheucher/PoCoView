package org.bytefred.pocoview.ui

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.bytefred.pocoview.core.FAMILIES
import org.bytefred.pocoview.core.Pt
import org.bytefred.pocoview.core.loadExamples
import kotlin.test.Test

/**
 * Renders the whole app offscreen (landscape and portrait) with every example, every family and
 * every dialog, so drawing crashes (like text constraints of off-canvas labels) show up as test
 * failures.
 */
class RenderTest {
    private fun state() = AppState(CoroutineScope(SupervisorJob() + Dispatchers.Default))

    private fun render(state: AppState, dialog: DialogKind = DialogKind.NONE, w: Int = 1200, h: Int = 800) {
        val scene = ImageComposeScene(w, h, Density(1f)) { App(state, dialog) }
        try {
            // several frames: first layout, then after the fit request and the property results
            for (t in 0L until 5L) scene.render(t * 50_000_000L).close()
            runBlocking { state.awaitIdle() }
            scene.render(400_000_000L).close()
        } finally {
            scene.close()
        }
    }

    @Test
    fun everyExampleAndFamilyRenders() {
        val sets = runBlocking { loadExamples() }.categories.flatMap { it.examples }.map { it.name to it.points } +
            FAMILIES.map { it.id to it.generate(minOf(16, it.maxN)) }
        for ((name, pts) in sets) {
            val s = state()
            s.changeHighlight(true)
            s.loadPoints(name, pts)
            render(s)
            render(s, w = 420, h = 860)
        }
    }

    @Test
    fun farAwayPointsAndLabelsDoNotCrash() {
        val s = state()
        // a set far off the initial view: labels would be drawn outside the canvas before the fit
        s.loadPoints("far", listOf(Pt(500_000_000, -500_000_000), Pt(500_000_100, -500_000_000), Pt(500_000_000, -499_999_900)))
        render(s)
        render(s, w = 300, h = 200)
    }

    @Test
    fun everyDialogOpens() {
        for (d in DialogKind.entries) {
            render(state(), d)
            render(state(), d, w = 420, h = 860)
        }
    }

    @Test
    fun everyWidgetRendersLandscapeAndPortrait() {
        val s = state()
        s.changeMaxColumns(Widget.entries.size)
        for (w in Widget.entries) if (w !in s.openWidgets) s.toggleWidget(w)
        render(s, w = 1800, h = 900)
        render(s, w = 420, h = 860)
    }
}
