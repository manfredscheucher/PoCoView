package org.bytefred.pocoview.ui

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.bytefred.pocoview.GeneratedLicenses
import org.bytefred.pocoview.core.EXAMPLES
import org.bytefred.pocoview.core.FAMILIES
import org.bytefred.pocoview.core.Pt
import org.bytefred.pocoview.core.loadExamples
import org.bytefred.pocoview.resources.Res
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Renders the whole app offscreen (landscape and portrait) with every example, every family and
 * every dialog, so drawing crashes (like text constraints of off-canvas labels) show up as test
 * failures.
 */
class RenderTest {
    private fun state() = AppState(CoroutineScope(SupervisorJob() + Dispatchers.Default))

    // App stores changed display settings in the shared in-memory pref store: each test starts empty
    @BeforeTest
    fun clearPrefs() = clearInMemoryPrefs()

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
        val sets = loadExamples().categories.flatMap { it.examples }.map { it.name to it.points } +
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
        for (page in HelpPage.entries) render(state().apply { helpPage = page }, DialogKind.HELP, w = 420, h = 860)
    }

    @Test
    fun saveChangesRendersOverADialog() {
        val s = state()
        s.deletePoint(0)
        s.coordinatesDraft = "1 2"
        s.leave {}
        render(s, DialogKind.EXAMPLES)
        render(s, DialogKind.SAVE, w = 420, h = 860)
    }

    @Test
    fun everyWidgetRendersLandscapeAndPortrait() {
        val s = state()
        s.changeMaxColumns(Widget.entries.size)
        for (w in Widget.entries) if (w !in s.openWidgets) s.toggleWidget(w)
        render(s, w = 1800, h = 900)
        render(s, w = 420, h = 860)
    }

    @Test
    fun narrowLandscapeColumnsScrollAndOpenMenuRenders() {
        val s = state()
        s.layoutMode = LayoutMode.LANDSCAPE
        s.changeMaxColumns(Widget.entries.size)
        for (w in Widget.entries) if (w !in s.openWidgets) s.toggleWidget(w)
        render(s, w = 900, h = 700)
        s.openMenu = "View"
        render(s)
        render(s, w = 420, h = 860)
    }

    @Test
    fun examplesDialogKeepsItsStateWhenReopened() {
        val s = state()
        val ex = s.examples
        ex.expanded += listOf("Families", "Singletons", "Links")
        val family = ExampleChoice.Family(FAMILIES.first())
        ex.selected = family
        ex.familyN[family.family.id] = 7
        render(s, DialogKind.EXAMPLES)
        for ((i, g) in EXAMPLES.categories.withIndex()) if (i % 2 == 0) ex.expanded += "S/" + g.title
        render(s, DialogKind.EXAMPLES)
        val target = ex.scroll.maxValue / 2
        assertTrue(target > 0, "the expanded tree must scroll")
        runBlocking { ex.scroll.scrollTo(target) }
        render(s)
        render(s, DialogKind.EXAMPLES)
        assertEquals(target, ex.scroll.value, "scroll position")
        assertEquals(family, ex.selected)
        assertEquals(7, ex.familyN[family.family.id])
        assertTrue("Singletons" in ex.expanded)
    }

    @Test
    fun everyHelpPageHasText() {
        for (page in HelpPage.entries) assertTrue(helpText(page).isNotEmpty() && helpText(page).all { it.isNotBlank() }, page.title)
    }

    @Test
    fun errorDialogRendersOverADialog() {
        val s = state()
        s.parseImport("not a point set")
        assertNotNull(s.error)
        render(s, DialogKind.IMPORT)
        render(s, w = 420, h = 860)
    }

    @Test
    fun licenseTextsAreGenerated() {
        val notices = GeneratedLicenses.NOTICES
        assertEquals(listOf("PoCoView (MIT)", "Font: DejaVu Sans Mono (Bitstream Vera license)"), notices.map { it.title })
        assertTrue(notices[0].text.startsWith("MIT License"))
        assertTrue("Bitstream Vera" in notices[1].text)
    }

    @Test
    fun monoFontIsBundled() {
        // MonoStyle uses it; without it the web build falls back to a proportional font
        assertTrue(runBlocking { Res.readBytes("font/dejavu_sans_mono.ttf") }.isNotEmpty())
    }
}
