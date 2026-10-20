package org.bytefred.pocoview.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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

/** Second row below the menu bar: editing tools, order type lock, navigation in multi-set files. */
@Composable
fun ToolBar(state: AppState) {
    FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for (t in Tool.entries) FilterChip(
            selected = state.tool == t, onClick = { state.tool = t },
            label = { Text(t.name.lowercase().replaceFirstChar { it.uppercase() }) },
        )
        // qtvis "fix current OT": points cannot be dragged out of their cell
        FilterChip(
            selected = state.fixOrderType, onClick = { state.fixOrderType = !state.fixOrderType },
            label = { Text("Lock order type") },
        )
        if (state.sets.size > 1) {
            TextButton(onClick = { state.showSet(state.setIndex - 1) }, enabled = state.setIndex > 0) { Text("<") }
            Text("${state.setIndex + 1} / ${state.sets.size}", Modifier.align(Alignment.CenterVertically))
            TextButton(onClick = { state.showSet(state.setIndex + 1) }, enabled = state.setIndex < state.sets.size - 1) { Text(">") }
        }
    }
}

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

private enum class Tab(val label: String) { PROPERTIES("Properties"), POINTS("Points"), OPTIMIZE("Optimize"), DATA("Data") }

@Composable
fun ControlPanel(state: AppState, onSelectProperties: () -> Unit, modifier: Modifier = Modifier) {
    var tab by remember { mutableStateOf(Tab.PROPERTIES) }
    Column(modifier) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (t in Tab.entries) FilterChip(selected = tab == t, onClick = { tab = t }, label = { Text(t.label) })
        }
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 4.dp)) {
            when (tab) {
                Tab.PROPERTIES -> PropertiesTab(state, onSelectProperties)
                Tab.POINTS -> PointsTab(state)
                Tab.OPTIMIZE -> OptimizeTab(state)
                Tab.DATA -> DataTab(state)
            }
        }
    }
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
    if (!state.highlight) Text(
        "Highlight is off: nothing is drawn; the switches on the right take effect when it is on.",
        style = MaterialTheme.typography.bodySmall,
    )
    if (state.incidentOnly && state.selected == null) Text(
        "Select a point: only structures containing it are counted and highlighted.",
        style = MaterialTheme.typography.bodySmall,
    )
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
            text = v.filter { it.isDigit() || (decimal && it == '.') }.take(10)
            if (text.toDoubleOrNull() != null) onValue(text)
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

@Composable
private fun PointsTab(state: AppState) {
    OutlinedButton(onClick = { state.deleteSelected() }, enabled = state.selected != null) {
        Text("Delete selected point")
    }
    Spacer(Modifier.height(12.dp))
    Text("Random point set (general position)", style = MaterialTheme.typography.titleSmall)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        NumberField(state, "n", state.randomN.toString(), { state.randomN = it.toInt() }, Modifier.width(90.dp))
        NumberField(state, "grid size", state.randomGrid.toString(), { state.randomGrid = it.toLong() }, Modifier.width(130.dp))
        OutlinedButton(onClick = { state.randomSet(state.randomN, state.randomGrid) }) { Text("Generate") }
    }
    Spacer(Modifier.height(12.dp))
    Text(
        "Mouse: drag a point to move it, drag empty space to pan, wheel to zoom. Tools: Move (click selects), " +
            "Add (click empty space), Delete (click a point). Keys: Info → Keyboard shortcuts.",
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun <T> Dropdown(current: String, items: List<T>, label: (T) -> String, onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }) { Text("$current …") }
        DropdownMenu(open, onDismissRequest = { open = false }) {
            for (it in items) DropdownMenuItem(text = { Text(label(it)) }, onClick = { onSelect(it); open = false })
        }
    }
}

@Composable
private fun OptimizeTab(state: AppState) {
    val idle = state.busy == null
    val p = state.points
    Text("Extent ${extent(p)} (max of width, height), ${bitsOf(p)} bits per coordinate", style = MaterialTheme.typography.bodyMedium)
    Spacer(Modifier.height(8.dp))

    val cfg = state.coordSettings
    fun set(c: CoordSettings) { state.coordSettings = c }
    SwitchRow("Loop: continue until aborted (otherwise stop at the first improvement / after one run)", state.loop) {
        state.loop = it
    }

    Text("Minimize coordinates (M), keeps the order type", style = MaterialTheme.typography.titleSmall)
    Dropdown(state.minimizeMethod.label, MinimizeMethod.entries, { it.label }) { state.minimizeMethod = it }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        when (state.minimizeMethod) {
            MinimizeMethod.MINIMIZE_COORDS -> {
                NumberField(state, "trials", cfg.trials.toString(), { set(cfg.copy(trials = it.toInt())) }, Modifier.width(90.dp))
                NumberField(state, "randomize", cfg.randomize.toString(), { set(cfg.copy(randomize = it.toInt())) }, Modifier.width(110.dp))
            }
            MinimizeMethod.MINIMIZE_INTEGER_COORDINATES -> {
                NumberField(state, "max trials", cfg.oldMaxTrials.toString(), { set(cfg.copy(oldMaxTrials = it.toInt())) }, Modifier.width(100.dp))
                NumberField(state, "randomize", cfg.randomize.toString(), { set(cfg.copy(randomize = it.toInt())) }, Modifier.width(110.dp))
                NumberField(state, "symmetric (k)", cfg.symmetric.toString(), { set(cfg.copy(symmetric = it.toInt())) }, Modifier.width(120.dp))
            }
            MinimizeMethod.PATTERN_SEARCH -> {
                NumberField(state, "max trials", cfg.psMaxTrials.toString(), { set(cfg.copy(psMaxTrials = it.toInt())) }, Modifier.width(100.dp))
                NumberField(state, "scale", cfg.psScale.toString(), { set(cfg.copy(psScale = it.toInt())) }, Modifier.width(90.dp))
                NumberField(state, "blowup", cfg.psBlowup.toString(), { set(cfg.copy(psBlowup = it.toInt())) }, Modifier.width(90.dp))
            }
        }
    }
    if (state.minimizeMethod != MinimizeMethod.PATTERN_SEARCH)
        SwitchRow("Also try sheering transforms", cfg.sheering) { set(cfg.copy(sheering = it)) }
    if (state.minimizeMethod == MinimizeMethod.MINIMIZE_INTEGER_COORDINATES)
        SwitchRow("Minimize the diameter instead of the bits", cfg.minimizeDiameter) { set(cfg.copy(minimizeDiameter = it)) }
    OutlinedButton(onClick = state::minimize, enabled = idle) { Text("Minimize") }

    Spacer(Modifier.height(12.dp))
    Text("Beautify (B), keeps the order type", style = MaterialTheme.typography.titleSmall)
    Dropdown(state.beautifyMethod.label, BeautifyMethod.entries, { it.label }) { state.beautifyMethod = it }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        when (state.beautifyMethod) {
            BeautifyMethod.BEAUTIFY_COORDS, BeautifyMethod.BEAUTIFY_POINT_SET ->
                NumberField(state, "max iter.", cfg.maxIter.toString(), { set(cfg.copy(maxIter = it.toInt())) }, Modifier.width(110.dp))
            BeautifyMethod.BEAUTIFY2_COORDS -> {
                NumberField(state, "iter1", cfg.iter1.toString(), { set(cfg.copy(iter1 = it.toInt())) }, Modifier.width(90.dp))
                NumberField(state, "iter2", cfg.iter2.toString(), { set(cfg.copy(iter2 = it.toInt())) }, Modifier.width(100.dp))
            }
            BeautifyMethod.BEAUTIFY2 -> {
                NumberField(state, "iter1", cfg.oldIter1.toString(), { set(cfg.copy(oldIter1 = it.toInt())) }, Modifier.width(90.dp))
                NumberField(state, "iter2", cfg.iter2.toString(), { set(cfg.copy(iter2 = it.toInt())) }, Modifier.width(100.dp))
                NumberField(state, "good bits", cfg.oldGoodBits.toString(), { set(cfg.copy(oldGoodBits = it.toInt())) }, Modifier.width(100.dp))
            }
        }
    }
    if (state.beautifyMethod == BeautifyMethod.BEAUTIFY2_COORDS || state.beautifyMethod == BeautifyMethod.BEAUTIFY2)
        SwitchRow("Minimize after each run", cfg.minimizeAfter) { set(cfg.copy(minimizeAfter = it)) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = state::beautify, enabled = idle) { Text("Beautify") }
        OutlinedButton(onClick = { set(CoordSettings()) }) { Text("Default parameters") }
    }
    Spacer(Modifier.height(16.dp))
    Text("Property (may change the order type)", style = MaterialTheme.typography.titleSmall)
    Dropdown(ALL_PROPERTIES.first { it.id == state.optimizeProperty }.label, ALL_PROPERTIES, { it.label }) {
        state.optimizeProperty = it.id
    }
    Spacer(Modifier.height(4.dp))
    Text(
        "Minimize w.r.t. property: like minimizeIntegerCoordinates (its parameters above), but the order type may " +
            "change as long as the property does not get worse; a worse result is undone",
        style = MaterialTheme.typography.bodySmall,
    )
    OutlinedButton(onClick = state::minimizeWrtProperty, enabled = idle) { Text("Minimize w.r.t. property") }
    Spacer(Modifier.height(8.dp))
    Text(
        "Search (old pyotlib flows): moves points to find a configuration with a smaller value; equal values are " +
            "taken over, worse ones not; stops at the first improvement unless Loop is on",
        style = MaterialTheme.typography.bodySmall,
    )
    val flow = PROPERTY_SEARCH_FLOWS.first { it.id == state.searchFlowId }
    Dropdown(flow.label, PROPERTY_SEARCH_FLOWS, { it.label }) { state.searchFlowId = it.id }
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
        SwitchRow("${param.name}: ${param.description}", state.searchValue(flow, param) as Boolean) { state.searchValues[key] = it }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = state::searchProperty, enabled = idle) { Text("Search") }
        OutlinedButton(onClick = { flow.params.forEach { state.searchValues.remove("${flow.id}.${it.name}") } }) { Text("Default parameters") }
    }
    Spacer(Modifier.height(8.dp))
    Text(
        "qtvis minimizer (Enter): pushes points just past their cell walls, accepts moves that do not increase " +
            "the property, restarts with a random point; stops at the first smaller value",
        style = MaterialTheme.typography.bodySmall,
    )
    val c = state.cellHopParams
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NumberField(state, "trials", c.trials.toString(), { state.cellHopParams = c.copy(trials = it.toInt()) }, Modifier.width(90.dp))
        NumberField(state, "max iter.", c.maxIterations.toString(), { state.cellHopParams = c.copy(maxIterations = it.toInt()) }, Modifier.width(100.dp))
        NumberField(state, "max fail iter.", c.maxFailIterations.toString(), { state.cellHopParams = c.copy(maxFailIterations = it.toInt()) }, Modifier.width(120.dp))
        NumberField(state, "epsilon", c.eps.toString(), { state.cellHopParams = c.copy(eps = it.toInt()) }, Modifier.width(90.dp))
        NumberField(state, "alpha", c.alpha.toString(), { state.cellHopParams = c.copy(alpha = it.toDouble()) }, Modifier.width(90.dp), decimal = true)
    }
    SwitchRow("Only move the last point on restarts", c.onlyLastPoint) { state.cellHopParams = c.copy(onlyLastPoint = it) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { state.cellHop(state.optimizeProperty, state.cellHopParams) }, enabled = idle) { Text("Try to minimize") }
        OutlinedButton(onClick = { state.cellHopParams = CellHopParams() }) { Text("Default parameters") }
    }
}

@Composable
private fun DataTab(state: AppState) {
    val p = state.points
    Text("n = ${p.size}, extent ${extent(p)}, ${bitsOf(p)} bits", style = MaterialTheme.typography.bodyMedium)
    Text(
        if (state.collinear.isEmpty()) "General position: yes" else "General position: NO (points ${state.collinear.sorted().joinToString()})",
        style = MaterialTheme.typography.bodyMedium,
    )
    Text("Order type hash: ${state.orderTypeHash}", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
    Spacer(Modifier.height(8.dp))
    Text("Coordinates (edit and apply; one 'x y' per line)", style = MaterialTheme.typography.titleSmall)
    val current = p.joinToString("\n") { "${it.x} ${it.y}" + (it.color?.let { c -> " $c" } ?: "") }
    // the draft lives in the state, so switching tabs does not lose an unapplied edit
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
        Text("λ matrix (λ[i][j] = #points left of i→j)", style = MaterialTheme.typography.titleSmall)
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
