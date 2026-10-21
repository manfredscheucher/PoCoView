package org.bytefred.pocoview.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedCard
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.bytefred.pocoview.core.ALL_PROPERTIES
import org.bytefred.pocoview.core.CellHopParams
import org.bytefred.pocoview.core.HullProperty
import org.bytefred.pocoview.core.OnionProperty
import org.bytefred.pocoview.core.PROPERTY_SEARCH_FLOWS
import org.bytefred.pocoview.core.PropertyDef
import org.bytefred.pocoview.core.PropertyResult
import org.bytefred.pocoview.core.bitsOf
import org.bytefred.pocoview.core.extent
import org.bytefred.pocoview.core.smallLambda
import kotlin.math.roundToInt

/** Second row below the menu bar: editing tools, order type lock, navigation in multi-set files. */
@Composable
fun ToolBar(state: AppState) {
    FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ToolbarLabel("Mode")
        for (t in Tool.entries) FilterChip(
            selected = state.tool == t, onClick = { state.tool = t },
            label = { Text(t.name.lowercase().replaceFirstChar { it.uppercase() }) },
        )
        // qtvis "fix current OT": points cannot be dragged out of their cell
        FilterChip(
            selected = state.fixOrderType, onClick = { state.fixOrderType = !state.fixOrderType },
            label = { Text("Lock order type") },
        )
        // widget columns: one toggle per widget (keys 1..4)
        VerticalDivider(Modifier.height(32.dp).align(Alignment.CenterVertically).padding(horizontal = 4.dp))
        ToolbarLabel("Widgets")
        for (w in Widget.entries) FilterChip(
            selected = w in state.openWidgets, onClick = { state.toggleWidget(w) },
            label = { Text(w.label) },
        )
        if (state.sets.size > 1) {
            TextButton(onClick = { state.showSet(state.setIndex - 1) }, enabled = state.setIndex > 0) { Text("<") }
            Text("${state.setIndex + 1} / ${state.sets.size}", Modifier.align(Alignment.CenterVertically))
            TextButton(onClick = { state.showSet(state.setIndex + 1) }, enabled = state.setIndex < state.sets.size - 1) { Text(">") }
        }
    }
}

@Composable
private fun FlowRowScope.ToolbarLabel(text: String) =
    Text(text, Modifier.align(Alignment.CenterVertically).padding(horizontal = 4.dp), style = MaterialTheme.typography.labelSmall)

@Composable
fun StatusBar(state: AppState) {
    val text = state.busy ?: state.message ?: ""
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "n = ${state.points.size}" + (if (state.collinear.isNotEmpty()) "  ·  NOT in general position" else "") +
                (if (text.isNotEmpty()) "  ·  $text" else ""),
            style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Title row of a widget: its name and a close button. */
@Composable
fun WidgetHeader(state: AppState, w: Widget) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(start = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(w.label, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
        TextButton(onClick = { state.toggleWidget(w) }) { Text("x") }
    }
}

/** The content of a widget (without scrolling; the caller scrolls). */
@Composable
fun WidgetContent(state: AppState, w: Widget, onSelectProperties: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp)) {
        when (w) {
            Widget.PROPERTIES -> PropertiesTab(state, onSelectProperties)
            Widget.OPTIMIZE -> OptimizeTab(state)
            Widget.DATA -> DataTab(state)
            Widget.DISPLAY -> DisplayWidget(state)
        }
    }
}

/** How points and lines are drawn, the selected point, and the widget columns. */
@Composable
private fun DisplayWidget(state: AppState) {
    Text("Point size: ${state.pointRadius.roundToInt()} px", style = MaterialTheme.typography.bodyMedium)
    Slider(state.pointRadius, { state.pointRadius = it }, valueRange = 2f..20f)
    Text("Line width: ${(state.lineWidth * 10).roundToInt() / 10.0} px", style = MaterialTheme.typography.bodyMedium)
    Slider(state.lineWidth, { state.lineWidth = it }, valueRange = 0.5f..5f)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Line colour", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        ColorChooser(state, state.lineColor) { state.lineColor = it }
    }
    Text("Line opacity: ${(state.lineAlpha * 100).roundToInt()} %", style = MaterialTheme.typography.bodyMedium)
    Slider(state.lineAlpha, { state.lineAlpha = it }, valueRange = 0.02f..1f)
    SwitchRow("Line arrangement", state.showLines) { state.showLines = it }
    SwitchRow("Point labels", state.showLabels) { state.showLabels = it }

    Spacer(Modifier.height(12.dp))
    Text("Selected point", style = MaterialTheme.typography.titleSmall)
    Text("Size: ${state.selectedScalePercent} % of the point size", style = MaterialTheme.typography.bodyMedium)
    Slider(state.selectedScalePercent.toFloat(), { state.selectedScalePercent = it.roundToInt() }, valueRange = 100f..400f)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Colour", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        ColorChooser(state, state.selectedColor) { state.selectedColor = it }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Lines through it", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        SelectField(null, state.incidentLines.label, IncidentLines.entries, { it.label }, Modifier.width(150.dp)) { state.incidentLines = it }
    }
    if (state.incidentLines == IncidentLines.HIGHLIGHT) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Their colour", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            ColorChooser(state, state.incidentLineColor) { state.incidentLineColor = it }
        }
        Text("Their width: ${(state.incidentLineWidth * 10).roundToInt() / 10.0} px", style = MaterialTheme.typography.bodyMedium)
        Slider(state.incidentLineWidth, { state.incidentLineWidth = it }, valueRange = 0.5f..8f)
    }

    Spacer(Modifier.height(12.dp))
    Text("Widgets", style = MaterialTheme.typography.titleSmall)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Max. widgets: ${state.maxColumns}", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = { state.changeMaxColumns(state.maxColumns - 1) }, enabled = state.maxColumns > 1) { Text("-") }
        TextButton(onClick = { state.changeMaxColumns(state.maxColumns + 1) }, enabled = state.maxColumns < Widget.entries.size) { Text("+") }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Layout", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        SelectField(null, state.layoutMode.name.lowercase(), LayoutMode.entries, { it.name.lowercase() }, Modifier.width(150.dp)) { state.layoutMode = it }
    }
    Spacer(Modifier.height(8.dp))
    OutlinedButton(onClick = state::resetDisplay) { Text("Reset to defaults") }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().height(44.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked, onChange)
    }
}

@Composable
private fun PropertiesTab(state: AppState, onSelectProperties: () -> Unit) {
    SwitchRow("Highlight", state.highlight, state::changeHighlight)
    SwitchRow("Incident only", state.incidentOnly, state::changeIncidentOnly)
    Spacer(Modifier.height(4.dp))
    for (prop in ALL_PROPERTIES) {
        val t = state.toggles[prop.id] ?: continue
        if (!t.count) continue
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(44.dp)) {
            // colour dot only for properties that can be highlighted
            if (prop.hasStructures) Box(Modifier.size(10.dp).background(state.colorOf(prop.id), CircleShape))
            else Spacer(Modifier.size(10.dp))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(prop.label, style = MaterialTheme.typography.bodyMedium)
                Text(
                    resultText(state, prop, state.results[prop.id]),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary,
                )
            }
            // visualize toggle, only for visualizable properties; only effective with "highlight"
            if (prop.hasStructures) Switch(
                t.draw, onCheckedChange = { state.setToggle(prop.id, t.copy(draw = it)) }, enabled = state.highlight,
            )
        }
    }
    Spacer(Modifier.height(8.dp))
    OutlinedButton(onClick = onSelectProperties) { Text("Select properties…") }
}

private fun resultText(state: AppState, prop: PropertyDef, s: PropState?): String = when (s) {
    null, PropState.Computing -> "computing…"
    is PropState.Skipped -> s.reason
    is PropState.Failed -> "error: ${s.message}"
    is PropState.Done -> doneText(state, prop, s.result)
}

private fun doneText(state: AppState, prop: PropertyDef, r: PropertyResult): String {
    val base = r.count.toString()
    val inc = r.incident ?: return base + truncNote(r)
    return when (prop) {
        is OnionProperty -> "$base  ·  point ${state.selected} on layer $inc"
        is HullProperty -> "$base  ·  point ${state.selected} " + if (inc > 0) "on the hull" else "not on the hull"
        else -> "$base  ·  incident: $inc" + truncNote(r)
    }
}

private fun truncNote(r: PropertyResult) =
    if (r.truncated) "  ·  drawing first ${r.structures?.size}" else ""

/**
 * A number input that keeps what the user types; only parseable values are passed to
 * [onValue], so clearing the field to retype does not turn the value into 0.
 */
@Composable
fun NumberField(
    state: AppState, label: String, value: String, onValue: (String) -> Unit, modifier: Modifier = Modifier,
    decimal: Boolean = false,
) {
    var text by remember(value) { mutableStateOf(value) }
    OutlinedTextField(
        text,
        onValueChange = { v ->
            // at most 9 digits: every integer field value fits into an Int
            text = v.filter { it.isDigit() || (decimal && it == '.') }.take(if (decimal) 10 else 9)
            if (if (decimal) text.toDoubleOrNull() != null else text.toIntOrNull() != null) onValue(text)
        },
        label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        modifier = modifier.textFocus(state),
    )
}

/** Counts focused text fields in [AppState.textFieldsFocused], so shortcuts do not fire while typing. */
fun Modifier.textFocus(state: AppState): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { if (focused) state.textFieldsFocused-- } }
    onFocusChanged {
        if (it.isFocused != focused) {
            focused = it.isFocused
            state.textFieldsFocused += if (focused) 1 else -1
        }
    }
}

/** A real dropdown: read-only text field with an arrow; click opens the list. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> SelectField(
    label: String?, current: String, items: List<T>, itemLabel: (T) -> String, modifier: Modifier = Modifier,
    onSelect: (T) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(open, onExpandedChange = { open = it }, modifier = modifier) {
        OutlinedTextField(
            current, onValueChange = {}, readOnly = true, singleLine = true,
            label = label?.let { { Text(it) } },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) },
            textStyle = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(open, onDismissRequest = { open = false }) {
            for (it in items) DropdownMenuItem(text = { Text(itemLabel(it)) }, onClick = { onSelect(it); open = false })
        }
    }
}

/** One optimizer box: title with a small Reset, content, and the run buttons. */
@Composable
private fun OptBox(title: String, onReset: (() -> Unit)?, content: @Composable () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.padding(start = 10.dp, end = 10.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                if (onReset != null) TextButton(onClick = onReset) { Text("Reset", style = MaterialTheme.typography.labelSmall) }
            }
            content()
        }
    }
}

@Composable
private fun RunButtons(run: String, idle: Boolean, onRun: () -> Unit, onRepeat: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
        OutlinedButton(onClick = onRun, enabled = idle) { Text(run) }
        OutlinedButton(onClick = onRepeat, enabled = idle) { Text("Repeat") }
    }
}

@Composable
private fun Hint(text: String) = Text(text, style = MaterialTheme.typography.bodySmall)

@Composable
private fun PropertySelect(state: AppState) =
    SelectField("Property", ALL_PROPERTIES.first { it.id == state.optimizeProperty }.label, ALL_PROPERTIES, { it.label }) {
        state.optimizeProperty = it.id
    }

@Composable
private fun OptimizeTab(state: AppState) {
    val idle = state.busy == null
    val p = state.points
    Text("Range ${rangeText(p)} · ${bitsOf(p)} bits", style = MaterialTheme.typography.bodyMedium)

    val m = state.minimizeSettings
    fun setM(c: MinimizeSettings) { state.minimizeSettings = c }
    OptBox("Minimize", { state.minimizeSettings = MinimizeSettings() }) {
        Hint("Keeps the order type.")
        SelectField("Method", state.minimizeMethod.label, MinimizeMethod.entries, { it.label }) { state.minimizeMethod = it }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when (state.minimizeMethod) {
                MinimizeMethod.EVEN_HALVE -> {
                    NumberField(state, "trials", m.trials.toString(), { setM(m.copy(trials = it.toInt())) }, Modifier.width(90.dp))
                    NumberField(state, "randomize", m.randomize.toString(), { setM(m.copy(randomize = it.toInt())) }, Modifier.width(110.dp))
                }
                MinimizeMethod.PERTURB -> {
                    NumberField(state, "max trials", m.psMaxTrials.toString(), { setM(m.copy(psMaxTrials = it.toInt())) }, Modifier.width(100.dp))
                    NumberField(state, "scale", m.psScale.toString(), { setM(m.copy(psScale = it.toInt())) }, Modifier.width(90.dp))
                    NumberField(state, "blowup", m.psBlowup.toString(), { setM(m.copy(psBlowup = it.toInt())) }, Modifier.width(90.dp))
                }
            }
        }
        if (state.minimizeMethod == MinimizeMethod.EVEN_HALVE) SwitchRow("Shear", m.sheering) { setM(m.copy(sheering = it)) }
        RunButtons("Minimize", idle, { state.minimize() }, { state.minimize(repeat = true) })
    }

    val w = state.wrtSettings
    fun setW(c: WrtSettings) { state.wrtSettings = c }
    OptBox("Minimize w.r.t. property", { state.wrtSettings = WrtSettings() }) {
        Hint("Order type may change; the property never gets worse.")
        PropertySelect(state)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(state, "max trials", w.maxTrials.toString(), { setW(w.copy(maxTrials = it.toInt())) }, Modifier.width(100.dp))
            NumberField(state, "randomize", w.randomize.toString(), { setW(w.copy(randomize = it.toInt())) }, Modifier.width(110.dp))
            NumberField(state, "symmetric k", w.symmetric.toString(), { setW(w.copy(symmetric = it.toInt())) }, Modifier.width(110.dp))
        }
        SwitchRow("Shear", w.sheering) { setW(w.copy(sheering = it)) }
        SwitchRow("Minimize diameter", w.minimizeDiameter) { setW(w.copy(minimizeDiameter = it)) }
        RunButtons("Minimize", idle, { state.minimizeWrtProperty() }, { state.minimizeWrtProperty(repeat = true) })
    }

    val b = state.beautifySettings
    fun setB(c: BeautifySettings) { state.beautifySettings = c }
    OptBox("Beautify", { state.beautifySettings = BeautifySettings() }) {
        Hint("Keeps the order type.")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(state, "iter1", b.iter1.toString(), { setB(b.copy(iter1 = it.toInt())) }, Modifier.width(90.dp))
            NumberField(state, "iter2", b.iter2.toString(), { setB(b.copy(iter2 = it.toInt())) }, Modifier.width(100.dp))
        }
        SwitchRow("Minimize after", b.minimizeAfter) { setB(b.copy(minimizeAfter = it)) }
        RunButtons("Beautify", idle, { state.beautify() }, { state.beautify(repeat = true) })
    }

    // Search and the qtvis minimizer: kept as they were (Manfred decides their place later)
    val flow = PROPERTY_SEARCH_FLOWS.first { it.id == state.searchFlowId }
    OptBox("Search", { flow.params.forEach { state.searchValues.remove("${flow.id}.${it.name}") } }) {
        Hint("Order type may change; equal values are taken over.")
        PropertySelect(state)
        SelectField("Flow", flow.label, PROPERTY_SEARCH_FLOWS, { it.label }) { state.searchFlowId = it.id }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (param in flow.params) {
                val key = "${flow.id}.${param.name}"
                when (val v = state.searchValue(flow, param)) {
                    is Int -> NumberField(state, param.name, v.toString(), { state.searchValues[key] = it.toDouble().toInt() }, Modifier.width(110.dp))
                    is Double -> NumberField(state, param.name, v.toString(), { state.searchValues[key] = it.toDouble() }, Modifier.width(110.dp), decimal = true)
                    else -> {}
                }
            }
        }
        for (param in flow.params.filter { it.default is Boolean }) {
            val key = "${flow.id}.${param.name}"
            SwitchRow(param.name, state.searchValue(flow, param) as Boolean) { state.searchValues[key] = it }
        }
        RunButtons("Search", idle, { state.searchProperty() }, { state.searchProperty(repeat = true) })
    }

    val c = state.cellHopParams
    OptBox("qtvis minimizer", { state.cellHopParams = CellHopParams() }) {
        Hint("Uses the property above (Enter).")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(state, "trials", c.trials.toString(), { state.cellHopParams = c.copy(trials = it.toInt()) }, Modifier.width(90.dp))
            NumberField(state, "max iter", c.maxIterations.toString(), { state.cellHopParams = c.copy(maxIterations = it.toInt()) }, Modifier.width(100.dp))
            NumberField(state, "max fail", c.maxFailIterations.toString(), { state.cellHopParams = c.copy(maxFailIterations = it.toInt()) }, Modifier.width(100.dp))
            NumberField(state, "eps", c.eps.toString(), { state.cellHopParams = c.copy(eps = it.toInt()) }, Modifier.width(80.dp))
            NumberField(state, "alpha", c.alpha.toString(), { state.cellHopParams = c.copy(alpha = it.toDouble()) }, Modifier.width(90.dp), decimal = true)
        }
        SwitchRow("Last point only", c.onlyLastPoint) { state.cellHopParams = c.copy(onlyLastPoint = it) }
        RunButtons(
            "Minimize", idle,
            { state.cellHop(state.optimizeProperty, state.cellHopParams) },
            { state.cellHop(state.optimizeProperty, state.cellHopParams, repeat = true) },
        )
    }
}

@Composable
private fun DataTab(state: AppState) {
    val p = state.points
    Text("n = ${p.size} · range ${rangeText(p)} · ${bitsOf(p)} bits", style = MaterialTheme.typography.bodyMedium)
    Text(
        if (state.collinear.isEmpty()) "General position: yes" else "General position: NO (points ${state.collinear.sorted().joinToString()})",
        style = MaterialTheme.typography.bodyMedium,
    )
    Text("Hash: ${state.orderTypeHash}", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
    Spacer(Modifier.height(8.dp))
    Text("Coordinates", style = MaterialTheme.typography.titleSmall)
    val current = p.joinToString("\n") { "${it.x} ${it.y}" + (it.color?.let { c -> " $c" } ?: "") }
    // the draft lives in the state, so closing the widget does not lose an unapplied edit
    val draft = state.coordinatesDraft
    OutlinedTextField(
        draft ?: current, onValueChange = { state.coordinatesDraft = it },
        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        modifier = Modifier.fillMaxWidth().height(200.dp).textFocus(state),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { state.applyCoordinatesText(draft ?: current) }, enabled = draft != null) { Text("Apply") }
        OutlinedButton(onClick = { state.coordinatesDraft = null }, enabled = draft != null) { Text("Revert") }
    }
    if (p.size in 1..30) {
        Spacer(Modifier.height(8.dp))
        Text("λ matrix", style = MaterialTheme.typography.titleSmall)
        val l = smallLambda(p)
        val w = (p.size - 2).toString().length + 1
        val sel = state.selected
        Text(
            l.mapIndexed { i, row ->
                row.joinToString("") { it.toString().padStart(w) } + if (i == sel) "  *" else ""
            }.joinToString("\n"),
            fontFamily = FontFamily.Monospace, fontSize = 11.sp,
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        )
    }
}
