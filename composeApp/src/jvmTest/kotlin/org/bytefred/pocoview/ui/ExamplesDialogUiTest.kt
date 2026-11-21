package org.bytefred.pocoview.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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

/** Examples dialog: collapsing the node that holds the selection clears it, so Load is off. */
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
        val group = EXAMPLES.categories.first()
        val choice = ExampleChoice.Single(group.examples.first())
        ex.toggle("Singletons")
        ex.toggle("S/" + group.title)
        ex.selected = choice
        ex.toggle("Order types")
        assertEquals(choice, ex.selected)
        ex.toggle("Singletons")
        assertNull(ex.selected)
        // the inner node stays open, so reopening the outer one shows it as it was
        assertEquals(setOf("S/" + group.title, "Order types"), ex.expanded.toSet())
    }
}
