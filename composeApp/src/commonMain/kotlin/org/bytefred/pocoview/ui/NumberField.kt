package org.bytefred.pocoview.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp

/** Inner padding left and right of the number in a [NumberField]. */
private val FIELD_PADDING = 6.dp

/**
 * A compact number input (ktoy's steps/sec field): "label:", then a low field with a 1 dp grey
 * border, as high as the buttons and as wide as the longest allowed value (the digits of
 * [range]'s maximum, else 9 digits). Material's OutlinedTextField is at least 280 dp wide and 56 dp
 * high, far bigger than a number. Keeps what the user types; only parseable values are passed to
 * [onValue], so clearing the field to retype does not turn the value into 0. The border turns red
 * when the text is not a number, is outside [range], or [isError]. With [stepper] (integers only),
 * "−"/"+" buttons change the value by 1 within [range]. [onText] gets every edit, also text that
 * is not a number (e.g. to disable a button meanwhile).
 *
 * Test tags: "number-<label>" on the field, "number-<label>-minus"/"-plus" on the buttons.
 */
@Composable
fun NumberField(
    state: AppState, label: String, value: String, onValue: (String) -> Unit, modifier: Modifier = Modifier,
    decimal: Boolean = false, range: LongRange? = null, stepper: Boolean = false, isError: Boolean = false,
    onText: (String) -> Unit = {},
) {
    var text by remember(value) { mutableStateOf(value) }
    // at most 9 digits: every integer field value fits into an Int
    val maxLength = if (decimal) 10 else 9
    fun edit(v: String) {
        text = v.filter { it.isDigit() || (decimal && it == '.') }.take(maxLength)
        onText(text)
        if (if (decimal) text.toDoubleOrNull() != null else text.toIntOrNull() != null) onValue(text)
    }
    val current = text.toLongOrNull()
    val invalid = isError ||
        (if (decimal) text.toDoubleOrNull() == null else current == null) ||
        (range != null && current != null && current !in range)
    fun step(by: Long) {
        val v = (current ?: value.toLongOrNull() ?: return) + by
        edit((if (range != null) v.coerceIn(range) else v).toString())
    }

    val style = MonoStyle
    val measurer = rememberTextMeasurer()
    val digits = range?.last?.toString()?.length ?: maxLength
    val textPx = remember(digits, style) { measurer.measure("0".repeat(digits), style).size.width }
    val textWidth = with(LocalDensity.current) { textPx.toDp() }
    val border = if (invalid) MaterialTheme.colorScheme.error else Color(0xFF888888)

    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text("$label:", Modifier.padding(end = 4.dp), style = MaterialTheme.typography.bodyMedium)
        if (stepper) OutlinedButton(
            onClick = { step(-1) },
            enabled = current == null || range == null || current > range.first,
            modifier = Modifier.testTag("number-$label-minus"),
        ) { Text("−") }
        BasicTextField(
            value = text,
            onValueChange = ::edit,
            singleLine = true,
            textStyle = style,
            keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
            // + 2 dp for the cursor after the last digit
            modifier = Modifier.width(textWidth + FIELD_PADDING * 2 + 2.dp).height(ButtonDefaults.MinHeight)
                .testTag("number-$label").textFocus(state),
            // the text centred vertically instead of fixed padding, so a larger font scale does not clip it
            decorationBox = { inner ->
                Box(
                    Modifier.fillMaxSize().border(1.dp, border, RoundedCornerShape(4.dp)).padding(horizontal = FIELD_PADDING),
                    contentAlignment = Alignment.CenterStart,
                ) { inner() }
            },
        )
        if (stepper) OutlinedButton(
            onClick = { step(1) },
            enabled = current == null || range == null || current < range.last,
            modifier = Modifier.testTag("number-$label-plus"),
        ) { Text("+") }
    }
}
