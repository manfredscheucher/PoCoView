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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextDecoration
import org.bytefred.pocoview.core.ExampleCatalog
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.material3.OutlinedButton
import org.bytefred.pocoview.core.Example
import org.bytefred.pocoview.core.ExportFormat
import org.bytefred.pocoview.core.FAMILIES
import org.bytefred.pocoview.core.PointSetFamily
import org.bytefred.pocoview.core.Structure
import org.bytefred.pocoview.core.importPointSets
import org.bytefred.pocoview.core.loadExamples
import org.bytefred.pocoview.core.ALL_PROPERTIES
import org.bytefred.pocoview.core.CrossingProperty
import org.bytefred.pocoview.core.MonochromaticGeneralHoleProperty
import org.bytefred.pocoview.core.MonochromaticProperty
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
    state.loadedSetsNotice?.let { count -> if (loadPref(SETS_NOTICE_PREF) != "off") SetsLoadedDialog(state, count) else state.loadedSetsNotice = null }
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
private fun FormatSelect(format: ExportFormat, onFormat: (ExportFormat) -> Unit) {
    SelectField("Format", format.extension, ExportFormat.entries, { it.extension }, Modifier.width(160.dp), onFormat)
}

/** Save the current set as a file (json or txt). */
@Composable
private fun SaveDialog(state: AppState, close: () -> Unit) {
    val saver = rememberFileSaver { name -> if (name != null) state.message = "Saved $name" }
    var format by remember { mutableStateOf(ExportFormat.JSON) }
    AlertDialog(
        onDismissRequest = close,
        confirmButton = {
            TextButton(onClick = {
                saver(state.saveName(format), state.exportText(format).encodeToByteArray())
                close()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = close) { Text("Cancel") } },
        title = { Text("Save file") },
        text = {
            Column(Modifier.widthIn(min = 300.dp)) {
                FormatSelect(format) { format = it }
                Text(state.saveName(format), style = MaterialTheme.typography.bodySmall)
            }
        },
    )
}

/** The current set as text, to copy. */
@Composable
private fun ExportDialog(state: AppState, close: () -> Unit) {
    var format by remember { mutableStateOf(ExportFormat.JSON) }
    val clipboard = LocalClipboardManager.current
    val text = state.exportText(format)
    AlertDialog(
        onDismissRequest = close,
        confirmButton = { TextButton(onClick = close) { Text("Close") } },
        dismissButton = {
            TextButton(onClick = { clipboard.setText(AnnotatedString(text)); state.message = "Copied" }) { Text("Copy") }
        },
        title = { Text("Export") },
        text = {
            Column(Modifier.widthIn(min = 320.dp, max = 560.dp)) {
                FormatSelect(format) { format = it }
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
                    val sets = importPointSets(text)
                    state.confirmReplace { state.loadSets("imported", sets); close() }
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

private const val IMPORT_FORMATS = """{"points": [[x, y], [x, y, "color"], ...]}  (one set per line)
n, then n lines: x y [color]  (sets one after another)
lines: x y [color]  (one set)"""

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

/** Families (generated), the bundled single sets in categories, and links; all collapsible. */
@Composable
private fun ExamplesDialog(state: AppState, close: () -> Unit) {
    var catalog by remember { mutableStateOf<ExampleCatalog?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        try { catalog = loadExamples() } catch (e: Exception) { error = e.message ?: "Cannot load the examples" }
    }
    val open = remember { mutableStateListOf<String>() }
    fun toggle(key: String) { if (key in open) open.remove(key) else open.add(key) }
    val uri = LocalUriHandler.current
    AlertDialog(
        onDismissRequest = close,
        confirmButton = { TextButton(onClick = close) { Text("Close") } },
        title = { Text("Examples") },
        text = {
            ScrollColumn(Modifier.widthIn(min = 320.dp, max = 640.dp).heightIn(max = 560.dp)) {
                FeedbackBox("Know a set that belongs here, or have general feedback?")
                Spacer(Modifier.height(8.dp))
                SectionHeader("Families", "Families" in open, level = 0) { toggle("Families") }
                if ("Families" in open) for (f in FAMILIES) FamilyRow(state, f, close)
                SectionHeader("Singletons", "Singletons" in open, level = 0) { toggle("Singletons") }
                if ("Singletons" in open) {
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    if (catalog == null && error == null) Text("Loading…", style = MaterialTheme.typography.bodySmall)
                    for (g in catalog?.categories.orEmpty()) {
                        val key = "S/" + g.title
                        SectionHeader("${g.title} (${g.examples.size})", key in open, level = 1) { toggle(key) }
                        if (key in open) for (e in g.examples) ExampleRow(state, e, close)
                    }
                }
                SectionHeader("Links", "Links" in open, level = 0) { toggle("Links") }
                if ("Links" in open) for (l in catalog?.links.orEmpty()) Column(
                    Modifier.fillMaxWidth().clickable { uri.openUri(l.url) }.padding(start = 16.dp, top = 4.dp, bottom = 4.dp),
                ) {
                    Text(l.title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    l.description?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    Text(l.url, style = MaterialTheme.typography.labelSmall)
                }
            }
        },
    )
}

/** A collapsible header row: title and an arrow, click toggles. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SectionHeader(title: String, expanded: Boolean, level: Int, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = (16 * level).dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title, Modifier.weight(1f),
            style = if (level == 0) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
        )
        ExposedDropdownMenuDefaults.TrailingIcon(expanded)
    }
}

@Composable
private fun ExampleRow(state: AppState, e: Example, close: () -> Unit) {
    val uri = LocalUriHandler.current
    Column(
        Modifier.fillMaxWidth().clickable { state.confirmReplace { state.loadPoints(e.name, e.points); close() } }
            .padding(start = 32.dp, top = 4.dp, bottom = 4.dp),
    ) {
        Text(e.name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        Text(e.description, style = MaterialTheme.typography.bodySmall)
        e.note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        e.reference?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
        if (e.links.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            for (l in e.links) Text(
                l.title, style = MaterialTheme.typography.labelSmall.copy(textDecoration = TextDecoration.Underline),
                color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable { uri.openUri(l.url) },
            )
        }
    }
}

/** Yellow box asking for interesting sets and feedback, with a clickable e-mail address. */
@Composable
fun FeedbackBox(question: String) {
    val uri = LocalUriHandler.current
    Column(
        Modifier.fillMaxWidth().background(Color(0xFFFFF3B0), RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFFE0C200), RoundedCornerShape(8.dp)).padding(10.dp),
    ) {
        Text(question, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF3A3000))
        Text(
            EMAIL,
            style = MaterialTheme.typography.bodyMedium.copy(textDecoration = TextDecoration.Underline),
            color = Color(0xFF1A4FB0),
            modifier = Modifier.clickable { uri.openUri(feedbackMailto()) }.padding(top = 2.dp),
        )
    }
}

private fun feedbackMailto(): String {
    fun enc(s: String) = buildString {
        for (b in s.encodeToByteArray()) {
            val c = (b.toInt() and 0xFF).toChar()
            if (c.isLetterOrDigit() && c.code < 128 || c in "-_.~") append(c)
            else append("%" + (b.toInt() and 0xFF).toString(16).uppercase().padStart(2, '0'))
        }
    }
    val subject = "PoCoView: point set / feedback"
    val body = "Hi Manfred,\n\n(an interesting point set, an idea, or other feedback)\n\n" +
        "Point set (Point Set > Export, JSON or text):\n\n"
    return "mailto:$EMAIL?subject=${enc(subject)}&body=${enc(body)}"
}

@Composable
private fun FamilyRow(state: AppState, f: PointSetFamily, close: () -> Unit) {
    var n by remember { mutableStateOf(16) }
    var error by remember { mutableStateOf<String?>(null) }
    Column(Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp)) {
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
                Text("Explore planar point sets and their order types.", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(10.dp))
                FeedbackBox("Interesting point configurations, ideas or general feedback? Please send them, they extend the collection in Point Set > Examples.")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(dontShow, { dontShow = it })
                    Text("Don't show again", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
    )
}

const val WELCOME_PREF = "welcome"
private const val SETS_NOTICE_PREF = "setsNotice"

/** After loading content with several sets. */
@Composable
private fun SetsLoadedDialog(state: AppState, count: Int) {
    var dontShow by remember { mutableStateOf(false) }
    fun done() { if (dontShow) savePref(SETS_NOTICE_PREF, "off"); state.loadedSetsNotice = null }
    AlertDialog(
        onDismissRequest = ::done,
        confirmButton = { TextButton(onClick = ::done) { Text("OK") } },
        title = { Text("$count point sets loaded") },
        text = {
            Column {
                Text(
                    "Showing the first of $count. Switch between them with the arrows < > at the top (or PageUp / PageDown).",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(dontShow, { dontShow = it })
                    Text("Don't show again", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
    )
}
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
    text = { ScrollColumn(Modifier.heightIn(max = 520.dp)) { Text(text, style = MaterialTheme.typography.bodyMedium) } },
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
    val mono = { p: PropertyDef -> p is MonochromaticProperty || p is MonochromaticGeneralHoleProperty }
    val groups = listOf(
        "General" to ALL_PROPERTIES.filter { it !is PolygonProperty && !mono(it) },
        "k-holes" to ALL_PROPERTIES.filter { it is PolygonProperty && it.emptyOnly },
        "Convex k-gons" to ALL_PROPERTIES.filter { it is PolygonProperty && !it.emptyOnly },
        "Monochromatic (bicolored sets)" to ALL_PROPERTIES.filter(mono),
    )
    AlertDialog(
        onDismissRequest = close,
        confirmButton = { TextButton(onClick = close) { Text("Done") } },
        title = { Text("Select properties") },
        text = {
            ScrollColumn(Modifier.heightIn(max = 560.dp)) { FlowRow(
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
            } }
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
