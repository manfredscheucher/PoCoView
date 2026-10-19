package org.bytefred.ordertypes.ui

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
import androidx.compose.material3.Checkbox
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
import org.bytefred.ordertypes.core.ALL_PROPERTIES
import org.bytefred.ordertypes.core.HullProperty
import org.bytefred.ordertypes.core.OnionProperty
import org.bytefred.ordertypes.core.PointFormat
import org.bytefred.ordertypes.core.PropertyDef
import org.bytefred.ordertypes.core.PropertyResult
import org.bytefred.ordertypes.core.extent
import org.bytefred.ordertypes.core.smallLambda

@Composable
fun TopBar(state: AppState, view: ViewTransform) {
    val opener = rememberFileOpener { name, bytes -> state.open(name, bytes) }
    val saver = rememberFileSaver { name -> if (name != null) state.message = "Saved / downloaded $name" }
    var saveMenu by remember { mutableStateOf(false) }

    FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Order Types", style = MaterialTheme.typography.titleMedium, modifier = Modifier.align(Alignment.CenterVertically).padding(end = 8.dp))
        TextButton(onClick = opener) { Text("Open") }
        Box {
            TextButton(onClick = { saveMenu = true }) { Text("Save…") }
            DropdownMenu(saveMenu, onDismissRequest = { saveMenu = false }) {
                for (fmt in PointFormat.entries) {
                    DropdownMenuItem(text = { Text("Save as .${fmt.extension}") }, onClick = {
                        saveMenu = false
                        state.encode(fmt, allSets = false)?.let { saver(state.saveName(fmt), it) }
                    })
                    if (state.sets.size > 1) DropdownMenuItem(text = { Text("Save all ${state.sets.size} sets as .${fmt.extension}") }, onClick = {
                        saveMenu = false
                        state.encode(fmt, allSets = true)?.let { saver(state.saveName(fmt), it) }
                    })
                }
                DropdownMenuItem(text = { Text("Export SVG") }, onClick = {
                    saveMenu = false
                    saver("${state.fileName}.svg", exportSvg(state).encodeToByteArray())
                })
            }
        }
        if (state.sets.size > 1) {
            TextButton(onClick = { state.showSet(state.setIndex - 1) }, enabled = state.setIndex > 0) { Text("<") }
            Text("${state.setIndex + 1} / ${state.sets.size}", Modifier.align(Alignment.CenterVertically))
            TextButton(onClick = { state.showSet(state.setIndex + 1) }, enabled = state.setIndex < state.sets.size - 1) { Text(">") }
        }
        TextButton(onClick = state::undo, enabled = state.canUndo) { Text("Undo") }
        TextButton(onClick = state::redo, enabled = state.canRedo) { Text("Redo") }
        for (t in Tool.entries) {
            FilterChip(
                selected = state.tool == t, onClick = { state.tool = t },
                label = { Text(t.name.lowercase().replaceFirstChar { it.uppercase() }) },
            )
        }
        TextButton(onClick = state::requestFit) { Text("Fit") }
        TextButton(onClick = { view.zoomCenter(1.2) }) { Text("+") }
        TextButton(onClick = { view.zoomCenter(1 / 1.2) }) { Text("−") }
        TextButton(onClick = {
            state.layoutMode = LayoutMode.entries[(state.layoutMode.ordinal + 1) % LayoutMode.entries.size]
        }) { Text("Layout: ${state.layoutMode.name.lowercase()}") }
    }
}

private fun exportSvg(state: AppState): String {
    val layers = if (state.mode == Mode.COUNT) emptyList() else ALL_PROPERTIES.mapIndexedNotNull { idx, p ->
        val t = state.toggles[p.id] ?: return@mapIndexedNotNull null
        val r = (state.results[p.id] as? PropState.Done)?.result ?: return@mapIndexedNotNull null
        if (t.count && t.draw && r.structures != null) propertyColor(idx) to r.structures else null
    }
    return pointSetSvg(state.points, layers, state.showLabels)
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
fun ControlPanel(state: AppState, modifier: Modifier = Modifier) {
    var tab by remember { mutableStateOf(Tab.PROPERTIES) }
    Column(modifier) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (t in Tab.entries) FilterChip(selected = tab == t, onClick = { tab = t }, label = { Text(t.label) })
        }
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 4.dp)) {
            when (tab) {
                Tab.PROPERTIES -> PropertiesTab(state)
                Tab.EDIT -> EditTab(state)
                Tab.OPTIMIZE -> OptimizeTab(state)
                Tab.INFO -> InfoTab(state)
            }
        }
    }
}

@Composable
private fun PropertiesTab(state: AppState) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (m in Mode.entries) FilterChip(
            selected = state.mode == m, onClick = { state.changeMode(m) },
            label = { Text(m.name.lowercase()) },
        )
    }
    Text(
        when (state.mode) {
            Mode.COUNT -> "Numbers only."
            Mode.COLOR -> "Structures of the 'draw' properties are drawn."
            Mode.INCIDENT -> if (state.selected == null) "Select a point: only structures containing it are counted and drawn."
            else "Counting/drawing only structures containing point ${state.selected}."
        },
        style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 4.dp),
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("count", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(48.dp))
        Text("property", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
        Text("draw", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(48.dp))
    }
    ALL_PROPERTIES.forEachIndexed { idx, prop ->
        val t = state.toggles[prop.id] ?: return@forEachIndexed
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(40.dp)) {
            Checkbox(t.count, onCheckedChange = { state.setToggle(prop.id, t.copy(count = it)) }, modifier = Modifier.width(48.dp))
            Box(Modifier.size(10.dp).background(propertyColor(idx), CircleShape))
            Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f)) {
                Text(prop.label, style = MaterialTheme.typography.bodyMedium)
                if (t.count) Text(
                    resultText(state, prop, state.results[prop.id]),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary,
                )
            }
            if (prop.hasStructures) Checkbox(
                t.draw, onCheckedChange = { state.setToggle(prop.id, t.copy(draw = it)) },
                enabled = t.count && state.mode != Mode.COUNT, modifier = Modifier.width(48.dp),
            ) else Spacer(Modifier.width(48.dp))
        }
    }
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
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().height(44.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked, onChange)
    }
}

@Composable
private fun NumberField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value, onValueChange = { v -> onChange(v.filter { it.isDigit() }.take(10)) }, label = { Text(label) },
        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = modifier,
    )
}

@Composable
private fun EditTab(state: AppState) {
    SwitchRow("Fix order type (reject moves that change it)", state.fixOrderType) { state.fixOrderType = it }
    SwitchRow("Show line arrangement", state.showLines) { state.showLines = it }
    SwitchRow("Hide lines through the selected point", state.hideSelectedLines) { state.hideSelectedLines = it }
    SwitchRow("Point labels", state.showLabels) { state.showLabels = it }
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
            "Add (click empty space), Delete (click a point). Keys: Esc deselect, Del delete, Ctrl+Z / Ctrl+Shift+Z undo/redo, " +
            "+/− zoom, Home fit, 1/2/3 count/color/incident, V/A/D tools, F fix order type, L lines, B beautify, " +
            "M minimize step, PageUp/PageDown previous/next set.",
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun OptimizeTab(state: AppState) {
    val idle = state.busy == null
    Text("Extent (max of width, height): ${extent(state.points)}", style = MaterialTheme.typography.bodyMedium)
    Spacer(Modifier.height(8.dp))
    Text("Beautify: move every point to the middle of its cell (keeps the order type)", style = MaterialTheme.typography.bodySmall)
    OutlinedButton(onClick = state::beautify, enabled = idle) { Text("Beautify (B)") }
    Spacer(Modifier.height(8.dp))
    Text("Minimize coordinates: move points to even coordinates and halve (keeps the order type)", style = MaterialTheme.typography.bodySmall)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { state.minimize(auto = false) }, enabled = idle) { Text("1 step (M)") }
        OutlinedButton(onClick = { state.minimize(auto = true) }, enabled = idle) { Text("Auto") }
    }
    Spacer(Modifier.height(12.dp))
    Text("Property walk: random local moves, kept if the property does not get worse", style = MaterialTheme.typography.bodySmall)
    var propId by remember { mutableStateOf("hole5") }
    var maximize by remember { mutableStateOf(false) }
    var steps by remember { mutableStateOf("500") }
    var menu by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { menu = true }) { Text(ALL_PROPERTIES.first { it.id == propId }.label + " …") }
        DropdownMenu(menu, onDismissRequest = { menu = false }) {
            for (p in ALL_PROPERTIES) DropdownMenuItem(text = { Text(p.label) }, onClick = { propId = p.id; menu = false })
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        FilterChip(selected = !maximize, onClick = { maximize = false }, label = { Text("min") })
        FilterChip(selected = maximize, onClick = { maximize = true }, label = { Text("max") })
        NumberField("steps", steps, { steps = it }, Modifier.width(110.dp))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { state.walk(propId, maximize, steps.toIntOrNull() ?: 0) }, enabled = idle) { Text("Run") }
        OutlinedButton(onClick = state::cancelOperation, enabled = !idle) { Text("Stop") }
    }
}

@Composable
private fun InfoTab(state: AppState) {
    val p = state.points
    Text("n = ${p.size}, extent ${extent(p)}", style = MaterialTheme.typography.bodyMedium)
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
