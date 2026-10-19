package org.bytefred.pocoview.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.bytefred.pocoview.core.ALL_PROPERTIES
import org.bytefred.pocoview.core.CrossingProperty
import org.bytefred.pocoview.core.PointFormat
import org.bytefred.pocoview.core.PolygonProperty
import org.bytefred.pocoview.core.PropertyDef
import org.bytefred.pocoview.core.TriangulationProperty
import kotlin.math.roundToInt

/** Which dialog is open (the menu bar and the properties tab open them). */
enum class DialogKind { NONE, PROPERTIES, SETTINGS, ABOUT, SHORTCUTS }

@Composable
private fun Menu(title: String, content: @Composable (close: () -> Unit) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) { Text(title) }
        DropdownMenu(open, onDismissRequest = { open = false }) { content { open = false } }
    }
}

@Composable
private fun Item(text: String, close: () -> Unit, enabled: Boolean = true, onClick: () -> Unit) =
    DropdownMenuItem(text = { Text(text) }, enabled = enabled, onClick = { close(); onClick() })

@Composable
private fun CheckItem(text: String, checked: Boolean, close: () -> Unit, onToggle: () -> Unit) =
    DropdownMenuItem(text = { Text((if (checked) "✓ " else "   ") + text) }, onClick = { close(); onToggle() })

/** File / Edit / View / Info. */
@Composable
fun MenuBar(state: AppState, view: ViewTransform, openDialog: (DialogKind) -> Unit) {
    val opener = rememberFileOpener { name, bytes -> state.open(name, bytes) }
    val saver = rememberFileSaver { name -> if (name != null) state.message = "Saved / downloaded $name" }
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("PoCoView", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 8.dp))
        Menu("File") { close ->
            Item("Open…", close) { opener() }
            HorizontalDivider()
            for (fmt in PointFormat.entries) Item("Save as .${fmt.extension}", close) {
                state.encode(fmt, allSets = false)?.let { saver(state.saveName(fmt), it) }
            }
            if (state.sets.size > 1) {
                HorizontalDivider()
                for (fmt in PointFormat.entries) Item("Save all ${state.sets.size} sets as .${fmt.extension}", close) {
                    state.encode(fmt, allSets = true)?.let { saver(state.saveName(fmt), it) }
                }
            }
            HorizontalDivider()
            Item("Export SVG", close) { saver("${state.fileName}.svg", exportSvg(state).encodeToByteArray()) }
        }
        Menu("Edit") { close ->
            Item("Undo", close, enabled = state.canUndo) { state.undo() }
            Item("Redo", close, enabled = state.canRedo) { state.redo() }
            Item("Delete selected point", close, enabled = state.selected != null) { state.deleteSelected() }
            HorizontalDivider()
            CheckItem("Lock order type", state.fixOrderType, close) { state.fixOrderType = !state.fixOrderType }
        }
        Menu("View") { close ->
            Item("Fit", close) { state.requestFit() }
            Item("Zoom in", close) { view.zoomCenter(1.2) }
            Item("Zoom out", close) { view.zoomCenter(1 / 1.2) }
            HorizontalDivider()
            CheckItem("Line arrangement", state.showLines, close) { state.showLines = !state.showLines }
            CheckItem("Hide lines through the selected point", state.hideSelectedLines, close) {
                state.hideSelectedLines = !state.hideSelectedLines
            }
            CheckItem("Point labels", state.showLabels, close) { state.showLabels = !state.showLabels }
            HorizontalDivider()
            for (m in LayoutMode.entries) CheckItem("Layout: ${m.name.lowercase()}", state.layoutMode == m, close) {
                state.layoutMode = m
            }
            HorizontalDivider()
            Item("Settings…", close) { openDialog(DialogKind.SETTINGS) }
        }
        Menu("Info") { close ->
            Item("About PoCoView", close) { openDialog(DialogKind.ABOUT) }
            Item("Keyboard shortcuts", close) { openDialog(DialogKind.SHORTCUTS) }
        }
    }
}

fun exportSvg(state: AppState): String {
    val layers = if (!state.highlight) emptyList() else ALL_PROPERTIES.mapIndexedNotNull { idx, p ->
        val t = state.toggles[p.id] ?: return@mapIndexedNotNull null
        val r = (state.results[p.id] as? PropState.Done)?.result ?: return@mapIndexedNotNull null
        if (t.count && t.draw && r.structures != null) propertyColor(idx) to r.structures else null
    }
    return pointSetSvg(state.points, layers, state.showLabels)
}

@Composable
fun Dialogs(state: AppState, kind: DialogKind, close: () -> Unit) {
    when (kind) {
        DialogKind.NONE -> {}
        DialogKind.PROPERTIES -> PropertyPicker(state, close)
        DialogKind.SETTINGS -> SettingsDialog(state, close)
        DialogKind.ABOUT -> TextDialog("About PoCoView", ABOUT, close)
        DialogKind.SHORTCUTS -> TextDialog("Keyboard shortcuts", SHORTCUTS, close)
    }
}

@Composable
private fun TextDialog(title: String, text: String, close: () -> Unit) = AlertDialog(
    onDismissRequest = close,
    confirmButton = { TextButton(onClick = close) { Text("Close") } },
    title = { Text(title) },
    text = { Text(text, Modifier.verticalScroll(rememberScrollState()), style = MaterialTheme.typography.bodyMedium) },
)

/** Choose which properties are shown (and computed) in the Properties tab; grouped in columns. */
@Composable
private fun PropertyPicker(state: AppState, close: () -> Unit) {
    val groups = listOf(
        "General" to ALL_PROPERTIES.filter { it !is PolygonProperty },
        "k-holes" to ALL_PROPERTIES.filter { it is PolygonProperty && it.emptyOnly },
        "Convex k-gons" to ALL_PROPERTIES.filter { it is PolygonProperty && !it.emptyOnly },
    )
    AlertDialog(
        onDismissRequest = close,
        confirmButton = { TextButton(onClick = close) { Text("Done") } },
        title = { Text("Select properties") },
        text = {
            FlowRow(
                Modifier.verticalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                for ((title, props) in groups) Column(Modifier.widthIn(min = 180.dp)) {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    for (p in props) PickerRow(state, p)
                    Row {
                        TextButton(onClick = { props.forEach { state.setShown(it.id, true) } }) { Text("all") }
                        TextButton(onClick = { props.forEach { state.setShown(it.id, false) } }) { Text("none") }
                    }
                }
            }
        },
    )
}

@Composable
private fun PickerRow(state: AppState, p: PropertyDef) {
    val shown = state.toggles[p.id]?.count == true
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(shown, onCheckedChange = { state.setShown(p.id, it) })
        Column {
            Text(p.label, style = MaterialTheme.typography.bodyMedium)
            val note = when (p) {
                is TriangulationProperty -> "exponential, n ≤ ${p.maxN}"
                is CrossingProperty -> "= convex 4-gons"
                else -> if (p.hasStructures) "" else "count only"
            }
            if (note.isNotEmpty()) Text(note, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SettingsDialog(state: AppState, close: () -> Unit) = AlertDialog(
    onDismissRequest = close,
    confirmButton = { TextButton(onClick = close) { Text("Close") } },
    dismissButton = {
        TextButton(onClick = { state.pointRadius = 6f; state.selectedScalePercent = 100; state.lineWidth = 1f }) { Text("Defaults") }
    },
    title = { Text("Settings") },
    text = {
        Column(Modifier.widthIn(max = 420.dp)) {
            Text("Point size: ${state.pointRadius.roundToInt()} px")
            Slider(state.pointRadius, { state.pointRadius = it }, valueRange = 2f..20f)
            Text("Selected point: ${state.selectedScalePercent} % of the point size")
            Slider(state.selectedScalePercent.toFloat(), { state.selectedScalePercent = it.roundToInt() }, valueRange = 100f..400f)
            Text("Line width: ${(state.lineWidth * 10).roundToInt() / 10.0} px")
            Slider(state.lineWidth, { state.lineWidth = it }, valueRange = 0.5f..5f)
            Spacer(Modifier.height(4.dp))
            Text("Line width applies to the line arrangement; structure outlines are 1.5× as wide.", style = MaterialTheme.typography.bodySmall)
        }
    },
)

private const val ABOUT = """PoCoView - Point Configuration Viewer

An interactive editor and viewer for point configurations and their order types: load and save point sets (also the order type database formats), move, add and delete points, and count or visualize convex hull, onion layers, crossings, k-holes, k-gons and triangulations, also restricted to a selected point. Coordinates can be minimized and beautified while keeping the order type.

Successor of qtvis (C++/Qt) and the pyotlib2 editor; the optimizers are ports of pyotlib / pyotlib2.

Kotlin Multiplatform + Compose; runs in the browser and on the desktop (JVM)."""

private const val SHORTCUTS = """Esc - deselect
Del / Backspace - delete the selected point
Ctrl+Z / Ctrl+Shift+Z (Ctrl+Y) - undo / redo
+ / - - zoom, Home or 0 - fit
V / A / D - tools move / add / delete
F - lock order type
L - line arrangement
H - highlight on/off
I - incident only on/off
Enter - qtvis minimizer (property of the Optimize tab)
PageUp / PageDown (, / .) - previous / next set of the file

Mouse: drag a point to move it, drag empty space to pan, wheel or pinch to zoom."""
