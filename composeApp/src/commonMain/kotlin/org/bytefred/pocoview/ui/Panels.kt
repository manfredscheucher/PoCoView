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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.bytefred.pocoview.core.ALL_PROPERTIES
import org.bytefred.pocoview.core.CellHopParams
import org.bytefred.pocoview.core.HullProperty
import org.bytefred.pocoview.core.OnionProperty
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
            label = { Text(if (state.fixOrderType) "OT locked" else "Lock OT") },
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
        if (state.busy != null) TextButton(onClick = state::cancelOperation) { Text("Stop") }
    }
}

private enum class Tab(val label: String) { PROPERTIES("Properties"), EDIT("Edit"), OPTIMIZE("Optimize"), INFO("Info") }

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
                Tab.EDIT -> EditTab(state)
                Tab.OPTIMIZE -> OptimizeTab(state)
                Tab.INFO -> InfoTab(state)
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
    if (state.incidentOnly && state.selected == null) Text(
        "Select a point: only structures containing it are counted and highlighted.",
        style = MaterialTheme.typography.bodySmall,
    )
    Spacer(Modifier.height(4.dp))
    for (prop in ALL_PROPERTIES) {
        val t = state.toggles[prop.id] ?: continue
        if (!t.count) continue
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(44.dp)) {
            Box(Modifier.size(10.dp).background(state.colorOf(prop.id), CircleShape))
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

@Composable
private fun NumberField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value, onValueChange = { v -> onChange(v.filter { it.isDigit() }.take(10)) }, label = { Text(label) },
        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = modifier,
    )
}

@Composable
private fun EditTab(state: AppState) {
    OutlinedButton(onClick = { state.deleteSelected() }, enabled = state.selected != null) {
        Text("Delete selected point")
    }
    Spacer(Modifier.height(12.dp))
    Text("Random point set (general position)", style = MaterialTheme.typography.titleSmall)
    var n by remember { mutableStateOf("12") }
    var size by remember { mutableStateOf("1000") }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        NumberField("n", n, { n = it }, Modifier.width(90.dp))
        NumberField("grid size", size, { size = it }, Modifier.width(130.dp))
        OutlinedButton(onClick = { state.randomSet(n.toIntOrNull() ?: 0, size.toLongOrNull() ?: 0) }) { Text("Generate") }
    }
    Spacer(Modifier.height(12.dp))
    Text(
        "Mouse: drag a point to move it, drag empty space to pan, wheel to zoom. Tools: Move (click selects), " +
            "Add (click empty space), Delete (click a point). Keyboard shortcuts: Info → Shortcuts.",
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

    Text("Minimize coordinates (M), keeps the order type", style = MaterialTheme.typography.titleSmall)
    Dropdown(state.minimizeMethod.label, MinimizeMethod.entries, { it.label }) { state.minimizeMethod = it }
    when (state.minimizeMethod) {
        MinimizeMethod.MINIMIZE_COORDS -> {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField("trials", cfg.trials.toString(), { set(cfg.copy(trials = it.toIntOrNull() ?: 0)) }, Modifier.width(90.dp))
                NumberField("randomize", cfg.randomize.toString(), { set(cfg.copy(randomize = it.toIntOrNull() ?: 0)) }, Modifier.width(110.dp))
            }
            SwitchRow("Also try sheering transforms", cfg.sheering) { set(cfg.copy(sheering = it)) }
        }
    }
    OutlinedButton(onClick = state::minimize, enabled = idle) { Text("Minimize") }

    Spacer(Modifier.height(12.dp))
    Text("Beautify (B), keeps the order type", style = MaterialTheme.typography.titleSmall)
    Dropdown(state.beautifyMethod.label, BeautifyMethod.entries, { it.label }) { state.beautifyMethod = it }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        when (state.beautifyMethod) {
            BeautifyMethod.BEAUTIFY_COORDS ->
                NumberField("max iter.", cfg.maxIter.toString(), { set(cfg.copy(maxIter = it.toIntOrNull() ?: 0)) }, Modifier.width(110.dp))
            BeautifyMethod.BEAUTIFY2_COORDS -> {
                NumberField("iter1", cfg.iter1.toString(), { set(cfg.copy(iter1 = it.toIntOrNull() ?: 0)) }, Modifier.width(90.dp))
                NumberField("iter2", cfg.iter2.toString(), { set(cfg.copy(iter2 = it.toIntOrNull() ?: 0)) }, Modifier.width(100.dp))
            }
        }
    }
    if (state.beautifyMethod == BeautifyMethod.BEAUTIFY2_COORDS)
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
    Text("Random walk: random local moves, kept if the property does not get worse", style = MaterialTheme.typography.bodySmall)
    var maximize by remember { mutableStateOf(false) }
    var steps by remember { mutableStateOf("500") }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        FilterChip(selected = !maximize, onClick = { maximize = false }, label = { Text("min") })
        FilterChip(selected = maximize, onClick = { maximize = true }, label = { Text("max") })
        NumberField("steps", steps, { steps = it }, Modifier.width(110.dp))
    }
    OutlinedButton(onClick = { state.walk(state.optimizeProperty, maximize, steps.toIntOrNull() ?: 0) }, enabled = idle) { Text("Run walk") }
    Spacer(Modifier.height(8.dp))
    Text(
        "qtvis minimizer (Enter): pushes points just past their cell walls, accepts moves that do not increase " +
            "the property, restarts with a random point; stops at the first smaller value",
        style = MaterialTheme.typography.bodySmall,
    )
    val d = CellHopParams()
    var trials by remember { mutableStateOf(d.trials.toString()) }
    var maxIt by remember { mutableStateOf(d.maxIterations.toString()) }
    var maxFail by remember { mutableStateOf(d.maxFailIterations.toString()) }
    var eps by remember { mutableStateOf(d.eps.toString()) }
    var alpha by remember { mutableStateOf(d.alpha.toString()) }
    var onlyLast by remember { mutableStateOf(d.onlyLastPoint) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NumberField("trials", trials, { trials = it }, Modifier.width(90.dp))
        NumberField("max iter.", maxIt, { maxIt = it }, Modifier.width(100.dp))
        NumberField("max fail iter.", maxFail, { maxFail = it }, Modifier.width(120.dp))
        NumberField("epsilon", eps, { eps = it }, Modifier.width(90.dp))
        OutlinedTextField(
            alpha, onValueChange = { v -> alpha = v.filter { it.isDigit() || it == '.' }.take(8) }, label = { Text("alpha") },
            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.width(90.dp),
        )
    }
    SwitchRow("Only move the last point on restarts", onlyLast) { onlyLast = it }
    OutlinedButton(onClick = {
        state.cellHop(
            state.optimizeProperty,
            CellHopParams(
                trials = trials.toIntOrNull() ?: d.trials,
                maxFailIterations = maxFail.toIntOrNull() ?: d.maxFailIterations,
                maxIterations = maxIt.toIntOrNull() ?: d.maxIterations,
                eps = eps.toIntOrNull() ?: d.eps,
                alpha = alpha.toDoubleOrNull() ?: d.alpha,
                onlyLastPoint = onlyLast,
            ),
        )
    }, enabled = idle) { Text("Try to minimize") }
}

@Composable
private fun InfoTab(state: AppState) {
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
    // a different set or file discards an unapplied edit
    var text by remember(state.setIndex, state.sets) { mutableStateOf(current) }
    var edited by remember(state.setIndex, state.sets) { mutableStateOf(false) }
    LaunchedEffect(current) { if (!edited) text = current }
    OutlinedTextField(
        text, onValueChange = { text = it; edited = true },
        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        modifier = Modifier.fillMaxWidth().height(200.dp),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { state.applyCoordinatesText(text); edited = false }, enabled = edited) { Text("Apply") }
        OutlinedButton(onClick = { text = current; edited = false }, enabled = edited) { Text("Revert") }
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
