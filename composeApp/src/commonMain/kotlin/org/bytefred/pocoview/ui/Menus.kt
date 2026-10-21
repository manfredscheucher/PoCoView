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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.material3.OutlinedButton
import org.bytefred.pocoview.core.Example
import org.bytefred.pocoview.core.ExampleGroup
import org.bytefred.pocoview.core.ExportFormat
import org.bytefred.pocoview.core.FAMILIES
import org.bytefred.pocoview.core.PointSetFamily
import org.bytefred.pocoview.core.Structure
import org.bytefred.pocoview.core.importPointSet
import org.bytefred.pocoview.core.loadExamples
import org.bytefred.pocoview.core.ALL_PROPERTIES
import org.bytefred.pocoview.core.CrossingProperty
import org.bytefred.pocoview.core.PolygonProperty
import org.bytefred.pocoview.core.PropertyDef
import org.bytefred.pocoview.core.TriangulationProperty
import kotlin.math.roundToInt

/** Which dialog is open (the menu bar and the properties widget open them). */
enum class DialogKind { NONE, PROPERTIES, ABOUT, SHORTCUTS, IMPORT, EXPORT, SAVE, RANDOM, EXAMPLES, WELCOME }

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

/** Point Set / Edit / View / Info. */
@Composable
fun MenuBar(state: AppState, view: ViewTransform, openDialog: (DialogKind) -> Unit) {
    val opener = rememberFileOpener { name, bytes -> state.open(name, bytes) }
    val saver = rememberFileSaver { name -> if (name != null) state.message = "Saved $name" }
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("PoCoView", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 8.dp))
        Menu("Point Set") { close ->
            Item("Import…", close) { openDialog(DialogKind.IMPORT) }
            Item("Random…", close) { openDialog(DialogKind.RANDOM) }
            Item("Export…", close) { openDialog(DialogKind.EXPORT) }
            Item("Examples…", close) { openDialog(DialogKind.EXAMPLES) }
            HorizontalDivider()
            Item("Open file…", close) { state.confirmReplace { opener() } }
            Item("Save file…", close) { openDialog(DialogKind.SAVE) }
            HorizontalDivider()
            Item("Export SVG", close) { saver("${state.fileName}.svg", exportSvg(state).encodeToByteArray()) }
            Item("Export Ipe", close) { saver("${state.fileName}.ipe", exportIpe(state).encodeToByteArray()) }
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
            for ((i, w) in Widget.entries.withIndex()) CheckItem("${w.label} (${i + 1})", w in state.openWidgets, close) {
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

/** The highlighted structures as drawn on the canvas (colour, structures), and which of them are emphasized. */
private fun exportLayers(state: AppState): Pair<List<Pair<Color, List<Structure>>>, Set<Int>> {
    if (!state.highlight) return emptyList<Pair<Color, List<Structure>>>() to emptySet()
    val layers = ArrayList<Pair<Color, List<Structure>>>()
    val emphasized = HashSet<Int>()
    for (p in ALL_PROPERTIES) {
        val t = state.toggles[p.id] ?: continue
        val r = (state.results[p.id] as? PropState.Done)?.result ?: continue
        if (!t.count || !t.draw || r.structures == null) continue
        if (p.emphasized) emphasized += layers.size
        layers += state.colorOf(p.id) to r.structures
    }
    return layers to emphasized
}

private fun lineStyle(state: AppState) = if (state.showLines) LineStyle(state.lineColor, state.lineAlpha, state.lineWidth) else null

fun exportSvg(state: AppState): String {
    val (layers, emphasized) = exportLayers(state)
    return pointSetSvg(state.points, layers, state.showLabels, lineStyle(state), emphasized)
}

fun exportIpe(state: AppState): String {
    val (layers, emphasized) = exportLayers(state)
    return pointSetIpe(state.points, layers, state.showLabels, lineStyle(state), emphasized)
}

@Composable
fun Dialogs(state: AppState, kind: DialogKind, open: (DialogKind) -> Unit, close: () -> Unit) {
    state.busy?.let { OperationDialog(state, it) }
    state.pendingReplace?.let { action -> ConfirmReplaceDialog(state, action) }
    when (kind) {
        DialogKind.NONE -> {}
        DialogKind.PROPERTIES -> PropertyPicker(state, close)
        DialogKind.IMPORT -> ImportDialog(state, close)
        DialogKind.EXPORT -> ExportDialog(state, close)
        DialogKind.SAVE -> SaveDialog(state, close)
        DialogKind.RANDOM -> RandomDialog(state, close)
        DialogKind.EXAMPLES -> ExamplesDialog(state, close)
        DialogKind.WELCOME -> WelcomeDialog(close = close, showExamples = { open(DialogKind.EXAMPLES) })
        DialogKind.ABOUT -> TextDialog("About PoCoView", ABOUT, close)
        DialogKind.SHORTCUTS -> TextDialog("Keyboard shortcuts", SHORTCUTS, close)
    }
}

/** Asked before anything replaces the current point set. */
@Composable
private fun ConfirmReplaceDialog(state: AppState, action: () -> Unit) = AlertDialog(
    onDismissRequest = { state.pendingReplace = null },
    confirmButton = { TextButton(onClick = { state.pendingReplace = null; action() }) { Text("Yes") } },
    dismissButton = { TextButton(onClick = { state.pendingReplace = null }) { Text("Abort") } },
    title = { Text("Replace point set?") },
    text = { Text("The current point set will be lost. This cannot be undone. Continue?", style = MaterialTheme.typography.bodyMedium) },
)

@Composable
private fun FormatSelect(format: ExportFormat, countLine: Boolean, onFormat: (ExportFormat) -> Unit, onCountLine: (Boolean) -> Unit) {
    SelectField("Format", format.extension, ExportFormat.entries, { it.extension }, Modifier.width(160.dp), onFormat)
    if (format == ExportFormat.TXT) SwitchRow("First line: number of points", countLine, onCountLine)
}

/** Save the current set as a file (json or txt). */
@Composable
private fun SaveDialog(state: AppState, close: () -> Unit) {
    val saver = rememberFileSaver { name -> if (name != null) state.message = "Saved $name" }
    var format by remember { mutableStateOf(ExportFormat.JSON) }
    var countLine by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = close,
        confirmButton = {
            TextButton(onClick = {
                saver(state.saveName(format), state.exportText(format, countLine).encodeToByteArray())
                close()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = close) { Text("Cancel") } },
        title = { Text("Save file") },
        text = {
            Column(Modifier.widthIn(min = 300.dp)) {
                FormatSelect(format, countLine, { format = it }, { countLine = it })
                Text(state.saveName(format), style = MaterialTheme.typography.bodySmall)
            }
        },
    )
}

/** The current set as text, to copy. */
@Composable
private fun ExportDialog(state: AppState, close: () -> Unit) {
    var format by remember { mutableStateOf(ExportFormat.JSON) }
    var countLine by remember { mutableStateOf(true) }
    val clipboard = LocalClipboardManager.current
    val text = state.exportText(format, countLine)
    AlertDialog(
        onDismissRequest = close,
        confirmButton = { TextButton(onClick = close) { Text("Close") } },
        dismissButton = {
            TextButton(onClick = { clipboard.setText(AnnotatedString(text)); state.message = "Copied" }) { Text("Copy") }
        },
        title = { Text("Export") },
        text = {
            Column(Modifier.widthIn(min = 320.dp, max = 560.dp)) {
                FormatSelect(format, countLine, { format = it }, { countLine = it })
                OutlinedTextField(
                    text, onValueChange = {}, readOnly = true,
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth().height(260.dp).padding(top = 8.dp).textFocus(state),
                )
            }
        },
    )
}

/** Paste a point set; the format is detected. */
@Composable
private fun ImportDialog(state: AppState, close: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = close,
        confirmButton = {
            TextButton(onClick = {
                try {
                    val p = importPointSet(text)
                    state.confirmReplace { state.loadPoints("imported", p); close() }
                } catch (e: Exception) {
                    error = e.message ?: "Cannot read the content"
                }
            }) { Text("Import") }
        },
        dismissButton = { TextButton(onClick = close) { Text("Cancel") } },
        title = { Text("Import") },
        text = {
            Column(Modifier.widthIn(min = 320.dp, max = 560.dp)) {
                Text(IMPORT_FORMATS, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                OutlinedTextField(
                    text, onValueChange = { text = it; error = null },
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth().height(220.dp).padding(top = 8.dp).textFocus(state),
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
    )
}

private const val IMPORT_FORMATS = """{"points": [[x, y], [x, y, "color"], ...]}
n, then n lines: x y [color]
lines: x y [color]"""

@Composable
private fun RandomDialog(state: AppState, close: () -> Unit) {
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = close,
        confirmButton = {
            TextButton(onClick = {
                error = state.randomSetError(state.randomN, state.randomGrid)
                if (error == null) state.confirmReplace { state.randomSet(state.randomN, state.randomGrid); close() }
            }) { Text("Generate") }
        },
        dismissButton = { TextButton(onClick = close) { Text("Cancel") } },
        title = { Text("Random point set") },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(state, "n", state.randomN.toString(), { state.randomN = it.toInt(); error = null }, Modifier.width(90.dp))
                    NumberField(state, "grid", state.randomGrid.toString(), { state.randomGrid = it.toLong(); error = null }, Modifier.width(130.dp))
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
    )
}

/** Bundled example sets (groups) and the generated families. */
@Composable
private fun ExamplesDialog(state: AppState, close: () -> Unit) {
    var groups by remember { mutableStateOf<List<ExampleGroup>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        try { groups = loadExamples() } catch (e: Exception) { error = e.message ?: "Cannot load the examples" }
    }
    AlertDialog(
        onDismissRequest = close,
        confirmButton = { TextButton(onClick = close) { Text("Close") } },
        title = { Text("Examples") },
        text = {
            Column(Modifier.widthIn(min = 320.dp, max = 640.dp).verticalScroll(rememberScrollState())) {
                Text("Know a set that belongs here? Please send it to $EMAIL", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                Text("Families", style = MaterialTheme.typography.titleSmall)
                for (f in FAMILIES) FamilyRow(state, f, close)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                if (groups == null && error == null) Text("Loading…", style = MaterialTheme.typography.bodySmall)
                for (g in groups.orEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(g.title, style = MaterialTheme.typography.titleSmall)
                    g.note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    for (e in g.examples) ExampleRow(state, e, close)
                }
            }
        },
    )
}

@Composable
private fun ExampleRow(state: AppState, e: Example, close: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clickable { state.confirmReplace { state.loadPoints(e.name, e.points); close() } }
            .padding(vertical = 4.dp),
    ) {
        Text("${e.name} (n = ${e.points.size})", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        Text(e.description, style = MaterialTheme.typography.bodySmall)
        e.reference?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
        e.url?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
    }
}

@Composable
private fun FamilyRow(state: AppState, f: PointSetFamily, close: () -> Unit) {
    var n by remember { mutableStateOf(16) }
    var error by remember { mutableStateOf<String?>(null) }
    Column(Modifier.padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(f.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            NumberField(state, "n", n.toString(), { n = it.toInt(); error = null }, Modifier.width(90.dp))
            OutlinedButton(onClick = {
                if (n !in 1..f.maxN) error = "n must be 1..${f.maxN}"
                else {
                    val size = f.size(n)
                    state.confirmReplace { state.loadPoints("${f.id}_n$size", f.generate(n)); close() }
                }
            }) { Text("Generate") }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        Text(f.description, style = MaterialTheme.typography.bodySmall)
        for (r in f.references) Text(r.text + (r.url?.let { " $it" } ?: ""), style = MaterialTheme.typography.labelSmall)
    }
}

/** Shown at start until "Don't show again" is ticked. */
@Composable
private fun WelcomeDialog(close: () -> Unit, showExamples: () -> Unit) {
    var dontShow by remember { mutableStateOf(false) }
    fun done() { if (dontShow) savePref(WELCOME_PREF, "off"); close() }
    AlertDialog(
        onDismissRequest = ::done,
        confirmButton = { TextButton(onClick = ::done) { Text("Start") } },
        dismissButton = { TextButton(onClick = { if (dontShow) savePref(WELCOME_PREF, "off"); showExamples() }) { Text("Examples") } },
        title = { Text("Welcome to PoCoView") },
        text = {
            Column {
                SelectionContainer {
                    Text(
                        "Explore planar point sets and their order types.\n\n" +
                            "Interesting point configurations or ideas? Please send them to $EMAIL " +
                            "to extend the collection (also noted in Point Set > Examples).",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(dontShow, { dontShow = it })
                    Text("Don't show again", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
    )
}

const val WELCOME_PREF = "welcome"
private const val EMAIL = "manfred.scheucher@proton.me"

/** Shown while an optimization runs: progress and Abort. */
@Composable
private fun OperationDialog(state: AppState, title: String) = AlertDialog(
    onDismissRequest = {},
    confirmButton = { TextButton(onClick = state::abortOperation) { Text("Abort") } },
    title = { Text(state.busyTitle) },
    text = {
        Column {
            Text(title.removeSuffix("…") + if (state.loop) " (repeat)" else "", style = MaterialTheme.typography.bodyMedium)
            state.progress?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
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
M: minimize;  B: beautify (Optimize widget)
L: line arrangement
H: highlight on/off
I: incident only on/off
Enter: qtvis minimizer (property and parameters of the Optimize widget)
1 .. 4: show / hide the widgets Properties, Optimize, Data, Display
PageUp / PageDown (, / .): previous / next set of the file

Mouse: drag a point to move it, drag empty space to pan, wheel or pinch to zoom."""
