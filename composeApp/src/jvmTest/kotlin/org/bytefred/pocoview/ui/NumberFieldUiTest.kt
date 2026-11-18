package org.bytefred.pocoview.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.bytefred.pocoview.core.FAMILIES
import org.bytefred.pocoview.core.loadExamples
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The compact number fields: size, typing, and the −/+ buttons (real clicks). */
@OptIn(ExperimentalTestApi::class)
class NumberFieldUiTest {
    private fun state() = AppState(CoroutineScope(SupervisorJob() + Dispatchers.Default))

    @BeforeTest
    fun clearPrefs() = clearInMemoryPrefs()

    @Test
    fun randomDialogNStepsAndTypes() = runComposeUiTest {
        val s = state()
        setContent { App(s, DialogKind.RANDOM) }
        val field = onNodeWithTag("number-n")
        val plus = onNodeWithTag("number-n-plus")
        val minus = onNodeWithTag("number-n-minus")
        // as high as a button, far below Material's 280 dp text field width
        val b = field.getUnclippedBoundsInRoot()
        assertEquals(40.dp, b.bottom - b.top)
        assertTrue(b.right - b.left < 60.dp, "3 digits: ${b.right - b.left}")

        assertEquals(12, s.randomN)
        plus.performClick()
        waitForIdle()
        assertEquals(13, s.randomN)
        minus.performClick()
        minus.performClick()
        waitForIdle()
        assertEquals(11, s.randomN)

        field.performTextReplacement("200")
        waitForIdle()
        assertEquals(200, s.randomN)
        plus.assertIsNotEnabled()
        // typing out of range is still taken (the dialog reports it on Generate), − clamps back
        field.performTextReplacement("250")
        waitForIdle()
        assertEquals(250, s.randomN)
        minus.performClick()
        waitForIdle()
        assertEquals(200, s.randomN)
        // clearing to retype keeps the value
        field.performTextReplacement("")
        waitForIdle()
        assertEquals(200, s.randomN)
        field.performTextReplacement("1")
        waitForIdle()
        minus.assertIsNotEnabled()
        plus.assertIsEnabled()
    }

    @Test
    fun gridFieldHasNoStepperAndFitsNineDigits() = runComposeUiTest {
        val s = state()
        setContent { App(s, DialogKind.RANDOM) }
        val b = onNodeWithTag("number-grid").getUnclippedBoundsInRoot()
        assertTrue(b.right - b.left < 100.dp, "9 digits: ${b.right - b.left}")
        assertEquals(0, onAllTags("number-grid-plus"))
        onNodeWithTag("number-grid").performTextReplacement("5000")
        waitForIdle()
        assertEquals(5000L, s.randomGrid)
    }

    @Test
    fun familyNSteps() = runComposeUiTest {
        val s = state()
        val f = FAMILIES.first { it.name == "Convex position" }
        s.examples.expanded.add("Families")
        s.examples.selected = ExampleChoice.Family(f)
        setContent { App(s, DialogKind.EXAMPLES) }
        val before = s.examples.familyN[f.id]
        onNodeWithTag("number-n-plus").performClick()
        waitForIdle()
        assertEquals((before ?: 16) + 1, s.examples.familyN[f.id])
    }

    @Test
    fun orderTypeIsPickedWithTheNumberFieldAndLoadsTheWholeFile() = runComposeUiTest {
        val s = state()
        setContent { App(s, DialogKind.EXAMPLES) }
        onNodeWithText("Order types").performScrollTo().performClick()
        onNodeWithText("n = 7 (135)").performScrollTo().performClick()
        onNodeWithText("Order types of 7 points (Graz order type database)").performScrollTo()
        val field = onNodeWithTag("number-order type")
        val minus = onNodeWithTag("number-order type-minus")
        val plus = onNodeWithTag("number-order type-plus")
        val load = onNodeWithText("Load")
        field.assertTextEquals("1")
        minus.assertIsNotEnabled()
        plus.performClick()
        plus.performClick()
        field.assertTextEquals("3")
        assertEquals(3, s.examples.orderType[7])

        // out of range: "1 to 135", Load off; − clamps back into range
        field.performTextReplacement("136")
        onNodeWithText("1 to 135").assertExists()
        load.assertIsNotEnabled()
        plus.assertIsNotEnabled()
        assertEquals(3, s.examples.orderType[7])
        minus.performClick()
        field.assertTextEquals("135")
        load.assertIsEnabled()
        field.performTextReplacement("")
        load.assertIsNotEnabled()
        field.performTextReplacement("17")
        onNodeWithText("1 to 135").assertDoesNotExist()
        load.assertIsEnabled()

        load.performClick()
        waitForIdle()
        val file = loadExamples().orderTypes.first { it.n == 7 }
        assertEquals(135, s.sets.size)
        assertEquals(16, s.setIndex)
        assertEquals(file.sets[16], s.points)
        assertEquals("otypes07 · set 17/135", s.title)
        // the other order types of the file stay reachable with set i/n
        s.showSet(17)
        waitForIdle()
        assertEquals(file.sets[17], s.points)
        assertEquals("otypes07 · set 18/135", s.title)
    }

    @Test
    fun noRangeMeansNoStepperAndDecimalsAreKept() = runComposeUiTest {
        val s = state()
        var v by mutableStateOf("1.05")
        setContent { NumberField(s, "alpha", v, { v = it }, decimal = true) }
        onNodeWithTag("number-alpha").performTextReplacement("1.2")
        waitForIdle()
        assertEquals("1.2", v)
        assertEquals(0, onAllTags("number-alpha-minus"))
    }

    private fun SemanticsNodeInteractionsProvider.onAllTags(tag: String) =
        onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().size
}
