package org.bytefred.pocoview.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.bytefred.pocoview.core.EXAMPLES
import org.bytefred.pocoview.core.FAMILIES
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Examples dialog: supergroups and groups expand and collapse; collapsing the node that holds the selection clears it, so Load is off. */
@OptIn(ExperimentalTestApi::class)
class ExamplesDialogUiTest {
    private fun state() = AppState(CoroutineScope(SupervisorJob() + Dispatchers.Default))

    @BeforeTest
    fun clearPrefs() = clearInMemoryPrefs()

    @Test
    fun collapsingTheSelectionsGroupDisablesLoad() = runComposeUiTest {
        val s = state()
        setContent { App(s, DialogKind.EXAMPLES) }
        val load = onNodeWithText("Load")
        load.assertIsNotEnabled()
        onNodeWithText("Families").performClick()
        onNodeWithText(FAMILIES.first().name).performClick()
        load.assertIsEnabled()
        // collapsing another group keeps the selection
        onNodeWithText("Links").performClick()
        onNodeWithText("Links").performClick()
        load.assertIsEnabled()
        onNodeWithText("Families").performClick()
        load.assertIsNotEnabled()
        assertNull(s.examples.selected)
    }

    @Test
    fun collapsingAnOuterNodeClearsANestedSelection() {
        val ex = ExamplesState()
        val choice = ExampleChoice.Single(EXAMPLES.supergroups[1].groups.first().examples.first())
        ex.toggle("SG/extremal")
        ex.toggle("G/extremal/holes")
        ex.selected = choice
        ex.toggle("SG/database")
        assertEquals(choice, ex.selected)
        ex.toggle("SG/extremal")
        assertNull(ex.selected)
        // the inner node stays open, so reopening the outer one shows it as it was
        assertEquals(setOf("G/extremal/holes", "SG/database"), ex.expanded.toSet())
    }

    @Test
    fun supergroupsAndGroupsWithClicks() = runComposeUiTest {
        val s = state()
        setContent { App(s, DialogKind.EXAMPLES) }
        val load = onNodeWithText("Load")
        // all collapsed: the supergroups, Families and Links, no groups
        for (t in listOf("Database", "Extremal point sets", "Families", "Links")) onNodeWithText(t).assertExists()
        onNodeWithText("Holes and convex gons").assertDoesNotExist()
        onNodeWithText("Extremal point sets").performClick()
        onNodeWithText("Sets from the literature that are extremal for a problem.").assertExists()
        onNodeWithText("Bicolored sets").performScrollTo().performClick()
        onNodeWithText("n8-c2-no-monochromatic-3-hole").performScrollTo().performClick()
        load.assertIsEnabled()
        // collapsing the group of the selection clears it
        onNodeWithText("Bicolored sets").performScrollTo().performClick()
        load.assertIsNotEnabled()
        onNodeWithText("Bicolored sets").performClick()
        onNodeWithText("n8-c2-no-monochromatic-3-hole").performScrollTo().performClick()
        // the database: description (no link: the order type files have it), one group with the order type files
        onNodeWithText("Database").performScrollTo().performClick()
        onNodeWithText("All order types of 3 to 7 points.").assertExists()
        load.assertIsEnabled()
        onNodeWithText("Order types").performScrollTo().performClick()
        onNodeWithText("Order type database").assertDoesNotExist()
        onNodeWithText("n = 5 (3)").performScrollTo().performClick()
        // the selected file: description, chooser, then its source
        assertAbove(onNodeWithText("Order types of 5 points (Graz order type database)"), onNodeWithTag("number-order type"))
        assertAbove(onNodeWithTag("number-order type"), onNodeWithText("Order type database"))
        load.performClick()
        waitForIdle()
        assertEquals(3, s.sets.size)
        assertEquals(EXAMPLES.orderTypes.first { it.n == 5 }.sets[0], s.points)
    }

    @Test
    fun loadsASingleSet() = runComposeUiTest {
        val s = state()
        setContent { App(s, DialogKind.EXAMPLES) }
        onNodeWithText("Extremal point sets").performClick()
        // the group header, not the Crossing number property of the widget behind the dialog
        onNode(hasText("Crossing number") and hasClickAction()).performScrollTo().performClick()
        onNodeWithText("n12-c1-min-crossing-number-153").performScrollTo().performClick()
        onNodeWithText("Load").performClick()
        waitForIdle()
        assertEquals(EXAMPLES.examples.first { it.name == "n12-c1-min-crossing-number-153" }.points, s.points)
    }

    @Test
    fun aSetShowsItsSourcesLast() = runComposeUiTest {
        setContent { App(state(), DialogKind.EXAMPLES) }
        onNodeWithText("Extremal point sets").performClick()
        onNodeWithText("Holes and convex gons").performScrollTo().performClick()
        // groups show no links
        onNodeWithText("PoSeZo entry").assertDoesNotExist()
        onNodeWithText("n13-c1-min-convex-3-4-5-holes").performScrollTo().performClick()
        val e = EXAMPLES.examples.first { it.name == "n13-c1-min-convex-3-4-5-holes" }
        // description, note, reference, then the links in file order
        val order = listOf(e.description, e.note!!, e.reference!!) + e.links.map { it.title }
        for ((a, b) in order.zipWithNext()) assertAbove(onNodeWithText(a), onNodeWithText(b))
    }

    @Test
    fun aFamilyShowsItsReferencesLast() = runComposeUiTest {
        setContent { App(state(), DialogKind.EXAMPLES) }
        onNodeWithText("Families").performClick()
        val f = FAMILIES.first { it.references.isNotEmpty() }
        onNodeWithText(f.name).performClick()
        assertAbove(onNodeWithText(f.description), onNodeWithTag("number-n"))
        assertAbove(onNodeWithTag("number-n"), onNodeWithText(f.references.first().text, substring = true))
    }

    /** [a] is drawn above [b]. */
    private fun assertAbove(a: SemanticsNodeInteraction, b: SemanticsNodeInteraction) {
        val ta = a.fetchSemanticsNode().boundsInRoot.top
        val tb = b.fetchSemanticsNode().boundsInRoot.top
        assertTrue(ta < tb, "expected above: $ta < $tb")
    }
}
