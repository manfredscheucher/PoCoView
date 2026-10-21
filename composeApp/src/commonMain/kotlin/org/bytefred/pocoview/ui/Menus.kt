package org.bytefred.pocoview.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import org.bytefred.pocoview.core.ALL_PROPERTIES
import org.bytefred.pocoview.core.CrossingProperty
import org.bytefred.pocoview.core.PointFormat
import org.bytefred.pocoview.core.PolygonProperty
import org.bytefred.pocoview.core.PropertyDef
import org.bytefred.pocoview.core.TriangulationProperty
import kotlin.math.roundToInt

/** Which dialog is open (the menu bar and the properties widget open them). */
enum class DialogKind { NONE, PROPERTIES, ABOUT, SHORTCUTS, IMPORT, SAVE }

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
    val saver = rememberFileSaver { name -> if (name != null) state.message = "Saved $name" }
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("PoCoView", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 8.dp))
        Menu("File") { close ->
            Item("Open…", close) { opener() }
            Item("Import (paste)…", close) { openDialog(DialogKind.IMPORT) }
            Item("Save…", close) { openDialog(DialogKind.SAVE) }
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
            CheckItem("Point labels", state.showLabels, close) { state.showLabels = !state.showLabels }
            HorizontalDivider()
            for ((i, w) in Widget.entries.withIndex()) CheckItem("Widget: ${w.label} (${i + 1})", w in state.openWidgets, close) {
                state.toggleWidget(w)
            }
            HorizontalDivider()
            for (m in LayoutMode.entries) CheckItem("Layout: ${m.name.lowercase()}", state.layoutMode == m, close) {
                state.layoutMode = m
            }
            HorizontalDivider()
            Item("Select properties…", close) { openDialog(DialogKind.PROPERTIES) }
        }
        Menu("Info") { close ->
            Item("About PoCoView", close) { openDialog(DialogKind.ABOUT) }
            Item("Keyboard shortcuts", close) { openDialog(DialogKind.SHORTCUTS) }
        }
    }
}

fun exportSvg(state: AppState): String {
    val layers = if (!state.highlight) emptyList() else ALL_PROPERTIES.mapNotNull { p ->
        val t = state.toggles[p.id] ?: return@mapNotNull null
        val r = (state.results[p.id] as? PropState.Done)?.result ?: return@mapNotNull null
        if (t.count && t.draw && r.structures != null) state.colorOf(p.id) to r.structures else null
    }
    return pointSetSvg(state.points, layers, state.showLabels)
}

@Composable
fun Dialogs(state: AppState, kind: DialogKind, close: () -> Unit) {
    state.busy?.let { OperationDialog(state, it) }
    when (kind) {
        DialogKind.NONE -> {}
        DialogKind.PROPERTIES -> PropertyPicker(state, close)
        DialogKind.IMPORT -> ImportDialog(state, close)
        DialogKind.SAVE -> SaveDialog(state, close)
        DialogKind.ABOUT -> TextDialog("About PoCoView", ABOUT, close)
        DialogKind.SHORTCUTS -> TextDialog("Keyboard shortcuts", SHORTCUTS, close)
    }
}

/** Save the current set (or all sets of the file) in a chosen format. */
@Composable
private fun SaveDialog(state: AppState, close: () -> Unit) {
    val saver = rememberFileSaver { name -> if (name != null) state.message = "Saved $name" }
    var format by remember { mutableStateOf(PointFormat.ASC) }
    var all by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = close,
        confirmButton = {
            TextButton(onClick = {
                state.encode(format, allSets = all)?.let { saver(state.saveName(format), it) }
                close()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = close) { Text("Cancel") } },
        title = { Text("Save point set") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Format", Modifier.padding(end = 8.dp))
                    Box {
                        TextButton(onClick = { menu = true }) { Text(".${format.extension} …") }
                        DropdownMenu(menu, onDismissRequest = { menu = false }) {
                            for (f in PointFormat.entries) DropdownMenuItem(text = { Text(".${f.extension}") }, onClick = { format = f; menu = false })
                        }
                    }
                }
                Text("File name: ${state.saveName(format)}", style = MaterialTheme.typography.bodySmall)
                if (state.sets.size > 1) SwitchRow("All ${state.sets.size} sets of the file", all) { all = it }
            }
        },
    )
}

/** Paste point sets in one of the file formats; binary formats as a hex dump plus n. */
@Composable
private fun ImportDialog(state: AppState, close: () -> Unit) {
    var format by remember { mutableStateOf(PointFormat.ASC) }
    var text by remember { mutableStateOf("") }
    var n by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = close,
        confirmButton = {
            TextButton(onClick = {
                error = state.import(format, text, n.toIntOrNull())
                if (error == null) close()
            }) { Text("Import") }
        },
        dismissButton = { TextButton(onClick = close) { Text("Cancel") } },
        title = { Text("Import point sets") },
        text = {
            Column(Modifier.widthIn(min = 320.dp, max = 560.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Format", Modifier.padding(end = 8.dp))
                    Box {
                        TextButton(onClick = { menu = true }) { Text(".${format.extension} …") }
                        DropdownMenu(menu, onDismissRequest = { menu = false }) {
                            for (f in PointFormat.entries) DropdownMenuItem(text = { Text(".${f.extension}") }, onClick = { format = f; menu = false })
                        }
                    }
                    if (format.isBinary) OutlinedTextField(
                        n, onValueChange = { v -> n = v.filter { it.isDigit() }.take(3) }, label = { Text("n") },
                        singleLine = true, modifier = Modifier.width(90.dp).padding(start = 8.dp).textFocus(state),
                    )
                }
                Text(
                    when {
                        format.isBinary -> "Hex dump of the file, e.g. 00 00 01 fe ff ff fe 01 (${format.binaryBytes} byte(s) per coordinate, little-endian)"
                        format == PointFormat.JSON -> "[[x,y],...] per set, or [[[x,y],...],...]"
                        else -> "A line with n, then one 'x y [color]' per point (several sets may follow); or just 'x y' lines"
                    },
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 4.dp),
                )
                OutlinedTextField(
                    text, onValueChange = { text = it; error = null },
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth().height(220.dp).textFocus(state),
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
    )
}

/** Shown while an optimization runs: progress, loop switch, Abort. */
@Composable
private fun OperationDialog(state: AppState, title: String) = AlertDialog(
    onDismissRequest = {},
    confirmButton = { TextButton(onClick = state::abortOperation) { Text("Abort") } },
    title = { Text(state.busyTitle) },
    text = {
        Column {
            Text(title.removeSuffix("…"), style = MaterialTheme.typography.bodyMedium)
            state.progress?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            Spacer(Modifier.height(8.dp))
            SwitchRow("Loop (continue until aborted)", state.loop) { state.loop = it }
        }
    },
)

@Composable
private fun TextDialog(title: String, text: String, close: () -> Unit) = AlertDialog(
    onDismissRequest = close,
    confirmButton = { TextButton(onClick = close) { Text("Close") } },
    title = { Text(title) },
    text = { Text(text, Modifier.verticalScroll(rememberScrollState()), style = MaterialTheme.typography.bodyMedium) },
)

/** A colour swatch; click opens a palette with a hex field (#RRGGBB). */
@Composable
fun ColorChooser(state: AppState, color: Color, onChange: (Color) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Box(
            Modifier.size(26.dp).background(color, CircleShape).border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                .clickable { open = true },
        )
        DropdownMenu(open, onDismissRequest = { open = false }) {
            Column(Modifier.padding(8.dp).width(220.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (c in CHOOSER_COLORS) Box(
                        Modifier.size(24.dp).background(c, CircleShape)
                            .border(if (c == color) 3.dp else 1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                            .clickable { onChange(c); open = false },
                    )
                }
                var hex by remember(open) { mutableStateOf(color.toHex()) }
                OutlinedTextField(
                    hex, onValueChange = { v ->
                        hex = v.take(7)
                        parseHexColor(hex)?.let(onChange)
                    },
                    label = { Text("#RRGGBB") }, singleLine = true, modifier = Modifier.padding(top = 8.dp).textFocus(state),
                )
            }
        }
    }
}

fun Color.toHex(): String = "#" + (toArgb() and 0xFFFFFF).toString(16).padStart(6, '0').uppercase()

fun parseHexColor(s: String): Color? {
    val h = s.removePrefix("#")
    if (h.length != 6) return null
    val v = h.toLongOrNull(16) ?: return null
    return Color(0xFF000000 or v)
}

/** Choose which properties are shown (and computed) in the Properties widget; grouped in columns. */
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
        // highlight colour, for properties that can be highlighted
        if (p.hasStructures) ColorChooser(state, state.colorOf(p.id)) { state.propertyColors[p.id] = it }
        else Spacer(Modifier.width(26.dp))
        Spacer(Modifier.width(8.dp))
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

private const val ABOUT = """PoCoView - Point Configuration Viewer

An interactive editor and viewer for point configurations and their order types: load and save point sets (also the order type database formats), move, add and delete points, and count or visualize convex hull, onion layers, crossings, k-holes, k-gons and triangulations, also restricted to a selected point. Coordinates can be minimized and beautified while keeping the order type.

Successor of qtvis (C++/Qt) and the pyotlib2 editor; the optimizers are ports of pyotlib / pyotlib2.

Kotlin Multiplatform + Compose; runs in the browser and on the desktop (JVM)."""

private const val SHORTCUTS = """Esc: deselect
Del / Backspace: delete the selected point
Ctrl+Z / Ctrl+Shift+Z (Ctrl+Y): undo / redo
+ / -: zoom;  Home or 0: fit
V / A / D: tools move / add / delete
F: lock order type
M: minimize coordinates;  B: beautify (implementations: Optimize widget)
L: line arrangement
H: highlight on/off
I: incident only on/off
Enter: qtvis minimizer (property and parameters of the Optimize widget)
1 .. 5: show / hide the widgets Properties, Points, Optimize, Data, Display
PageUp / PageDown (, / .): previous / next set of the file

Mouse: drag a point to move it, drag empty space to pan, wheel or pinch to zoom."""
