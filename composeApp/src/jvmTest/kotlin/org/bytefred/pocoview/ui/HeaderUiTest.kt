package org.bytefred.pocoview.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.bytefred.pocoview.core.Pt
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** The app name in the menu bar and the file name, n and messages in the toolbar (no status bar). */
@OptIn(ExperimentalTestApi::class)
class HeaderUiTest {
    private fun state() = AppState(CoroutineScope(SupervisorJob() + Dispatchers.Default))

    @BeforeTest
    fun clearPrefs() = clearInMemoryPrefs()

    private fun SemanticsNodeInteraction.text() =
        fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }

    @Test
    fun headerAndToolbarStatus() = runComposeUiTest {
        val s = state()
        setContent { App(s, DialogKind.NONE) }
        // the desktop build shows the app name without the web subtitle
        onNodeWithText("PoCoView").assertIsDisplayed()
        val status = onNodeWithTag("toolbar-status")
        status.assertIsDisplayed()
        assertEquals("n13-c1-min-convex-3-4-5-holes  ·  n = 13", status.text())

        s.showInfo("Saved x.json")
        waitForIdle()
        assertEquals("n13-c1-min-convex-3-4-5-holes  ·  n = 13  ·  Saved x.json", status.text())

        val line = listOf(Pt(0, 0), Pt(1, 1), Pt(2, 2), Pt(0, 5))
        s.loadSets("two", listOf(line, line))
        s.showSet(1)
        waitForIdle()
        assertEquals("two · set 2/2  ·  n = 4  ·  NOT in general position", status.text()?.substringBefore("  ·  Loaded"))
    }
}
