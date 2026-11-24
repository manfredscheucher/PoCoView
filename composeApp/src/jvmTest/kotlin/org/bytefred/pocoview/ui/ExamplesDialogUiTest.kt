package org.bytefred.pocoview.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
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
        // the database: description, its link, one group with the order type files
        onNodeWithText("Database").performScrollTo().performClick()
        onNodeWithText("All order types of 3 to 7 points.").assertExists()
        onNodeWithText("Order type database").assertExists()
        load.assertIsEnabled()
        onNodeWithText("Order types").performScrollTo().performClick()
        onNodeWithText("n = 5 (3)").performScrollTo().performClick()
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
}
