package org.bytefred.pocoview.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import org.bytefred.pocoview.GeneratedBuildInfo
import org.bytefred.pocoview.GeneratedLicenses
import org.bytefred.pocoview.core.ALL_PROPERTIES
import org.bytefred.pocoview.core.COORD_LIMIT
import org.bytefred.pocoview.core.CrossingProperty
import org.bytefred.pocoview.core.EXAMPLES
import org.bytefred.pocoview.core.Example
import org.bytefred.pocoview.core.ExportFormat
import org.bytefred.pocoview.core.FAMILIES
import org.bytefred.pocoview.core.MonochromaticGeneralHoleProperty
import org.bytefred.pocoview.core.MonochromaticProperty
import org.bytefred.pocoview.core.OrderTypeFile
import org.bytefred.pocoview.core.PointSetFamily
import org.bytefred.pocoview.core.PolygonProperty
import org.bytefred.pocoview.core.PropertyDef
import org.bytefred.pocoview.core.Structure
import org.bytefred.pocoview.core.TriangulationProperty
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/** Which dialog is open (the menu bar and the properties widget open them); HELP shows [AppState.helpPage]. */
enum class DialogKind { NONE, PROPERTIES, ABOUT, IMPORT, EXPORT, SAVE, RANDOM, EXAMPLES, WELCOME, HELP }

/**
 * A menu title that opens a [DropdownMenu] (ktoy's). Plain clickable text rather than a
 * TextButton, so the bar stays narrow. The popup is not focusable, so a click outside it
 * still reaches what is underneath: clicking another menu title closes this menu and opens
 * that one in one click. A click on the open menu's own title closes it: the popup's
 * outside-click dismissal fires first (on press), so the title click right after it must not
 * reopen the menu. The open menu is [AppState.openMenu], one for all menus.
 */
@Composable
private fun MenuButton(state: AppState, label: String, items: @Composable () -> Unit) {
    var dismissedAt by remember { mutableStateOf<TimeMark?>(null) }
    Box {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .testTag("menu-$label")
                .clip(RoundedCornerShape(4.dp))
                .clickable {
                    val justClosed = dismissedAt?.let { it.elapsedNow() < MENU_CLICK_GUARD } == true
                    state.openMenu = menuAfterTitleClick(state.openMenu, label, justClosed)
                }
                .padding(horizontal = 8.dp, vertical = 10.dp),
        )
        DropdownMenu(
            expanded = state.openMenu == label,
            // only close if this menu is still the open one: the outside click
            // may have just opened another menu
            onDismissRequest = {
                if (state.openMenu == label) {
                    dismissedAt = TimeSource.Monotonic.markNow()
                    state.closeMenuByOutsideClick()
                }
            },
            properties = PopupProperties(focusable = false),
        ) {
            items()
        }
    }
}

/**
 * The open menu after a click on the title [label] while [open] is open: a click on the open
 * menu's title closes it, as does one [justClosed] (the same click already closed it by its
 * outside-click dismissal); any other click opens [label], switching from another open menu.
 */
fun menuAfterTitleClick(open: String?, label: String, justClosed: Boolean): String? =
    if (open == label || justClosed) null else label

// a title or canvas click this soon after a menu's outside-click dismissal belongs to
// the same click (press dismissed, release clicked)
internal val MENU_CLICK_GUARD = 500.milliseconds

/** A menu entry: closes the menu, then runs [onClick]. */
@Composable
private fun Item(state: AppState, text: String, enabled: Boolean = true, onClick: () -> Unit) =
    DropdownMenuItem(text = { Text(text) }, enabled = enabled, onClick = { state.openMenu = null; onClick() })

/** A menu toggle with a checkmark; like ktoy's, a click keeps the menu open (to toggle several). */
@Composable
private fun CheckItem(text: String, checked: Boolean, onToggle: () -> Unit) =
    DropdownMenuItem(text = { Text((if (checked) "✓ " else "   ") + text) }, onClick = onToggle)

/** The grey heading of a radio group in a menu (its items are indented), or of a context menu. */
@Composable
fun MenuHeading(text: String) = Text(
    text,
    style = MaterialTheme.typography.labelMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
)

/** Point Set / Edit / View / Help on the left, the app name (and on the web a subtitle) on the right. */
@Composable
fun MenuBar(state: AppState, view: ViewTransform, openDialog: (DialogKind) -> Unit) {
    val opener = rememberFileOpener(onError = { state.error = it }) { name, bytes -> state.open(name, bytes) }
    val saver = rememberFileSaver(onError = { state.error = it }) { name -> if (name != null) state.showInfo("Saved $name") }
    // the popups are not focusable (see MenuButton), so the bar takes the focus while a menu
    // is open; Escape bubbles up from here to the app's key handler, which closes the menu
    val barFocus = remember { FocusRequester() }
    LaunchedEffect(state.openMenu) { if (state.openMenu != null) barFocus.requestFocus() }
    Row(
        Modifier.fillMaxWidth().background(MENU_BAR_COLOR).padding(start = 4.dp, end = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // scrolls sideways when the menus do not fit
        Row(
            Modifier.horizontalScroll(rememberScrollState())
                .testTag("menu-bar")
                .focusRequester(barFocus)
                .focusable(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MenuButton(state, "Point Set") {
                Item(state, "Examples…") { openDialog(DialogKind.EXAMPLES) }
                Item(state, "Import…") { openDialog(DialogKind.IMPORT) }
                Item(state, "Random…") { openDialog(DialogKind.RANDOM) }
                Item(state, "Export…") { openDialog(DialogKind.EXPORT) }
                HorizontalDivider()
                Item(state, "Open file…") { state.leave { opener() } }
                Item(state, "Save file…") { openDialog(DialogKind.SAVE) }
                HorizontalDivider()
                Item(state, "Export SVG") { saver("${state.fileName}.svg", exportSvg(state).encodeToByteArray()) }
                Item(state, "Export Ipe") { saver("${state.fileName}.ipe", exportIpe(state).encodeToByteArray()) }
            }
            MenuButton(state, "Edit") {
                Item(state, "Undo", enabled = state.canUndo) { state.undo() }
                Item(state, "Redo", enabled = state.canRedo) { state.redo() }
                Item(state, "Delete selected point", enabled = state.selected != null) { state.deleteSelected() }
                HorizontalDivider()
                // unlike the View check items, this one closes the menu (GUI spec)
                CheckItem("Lock order type", state.fixOrderType) {
                    state.openMenu = null
                    state.fixOrderType = !state.fixOrderType
                }
            }
            MenuButton(state, "View") {
                Item(state, "Fit") { state.requestFit() }
                Item(state, "Zoom in") { view.zoomCenter(1.2) }
                Item(state, "Zoom out") { view.zoomCenter(1 / 1.2) }
                HorizontalDivider()
                CheckItem("Line arrangement", state.showLines) { state.showLines = !state.showLines }
                CheckItem("Point labels", state.showLabels) { state.showLabels = !state.showLabels }
                HorizontalDivider()
                for ((i, w) in Widget.entries.withIndex()) CheckItem("${w.label} (${i + 1})", w in state.openWidgets) {
                    state.toggleWidget(w)
                }
                HorizontalDivider()
                MenuHeading("Layout")
                for (m in LayoutMode.entries) CheckItem("  " + m.name.lowercase(), state.layoutMode == m) {
                    state.layoutMode = m
                }
                HorizontalDivider()
                Item(state, "Select properties…") { openDialog(DialogKind.PROPERTIES) }
            }
            MenuButton(state, "Help") {
                for (page in HelpPage.entries) Item(state, page.title) { state.helpPage = page; openDialog(DialogKind.HELP) }
                Item(state, "About PoCoView") { openDialog(DialogKind.ABOUT) }
            }
        }
        // one line + ellipsis, shrinking (weight), so the header cannot push the menus off-screen
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("PoCoView") }
                if (isWeb) append("  Point configuration viewer")
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false).padding(start = 8.dp),
        )
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

/** The Display settings the canvas draws with. */
fun exportStyle(state: AppState) = ExportStyle(
    labels = state.showLabels,
    pointRadius = state.pointRadius,
    structureWidth = state.lineWidth,
    lines = if (state.showLines) LineStyle(state.lineColor, state.lineAlpha, state.lineWidth) else null,
    selected = state.selected,
    incident = state.incidentLines,
    incidentStyle = LineStyle(state.incidentLineColor, 1f, state.incidentLineWidth),
)

fun exportSvg(state: AppState): String {
    val (layers, emphasized) = exportLayers(state)
    return pointSetSvg(state.points, layers, exportStyle(state), emphasized)
}

fun exportIpe(state: AppState): String {
    val (layers, emphasized) = exportLayers(state)
    return pointSetIpe(state.points, layers, exportStyle(state), emphasized)
}

@Composable
fun Dialogs(state: AppState, kind: DialogKind, open: (DialogKind) -> Unit, close: () -> Unit) {
    val saver = rememberFileSaver(onError = { state.error = it }, onSaved = state::fileSaved)
    fun save(format: ExportFormat) = saver(state.saveName(format), state.beginSave(format))
    state.busy?.let { OperationDialog(state, it) }
    state.loadedSetsNotice?.let { count -> if (loadPref(SETS_NOTICE_PREF) != "off") SetsLoadedDialog(state, count) else state.loadedSetsNotice = null }
    when (kind) {
        DialogKind.NONE -> {}
        DialogKind.PROPERTIES -> PropertyPicker(state, close)
        DialogKind.IMPORT -> ImportDialog(state, close)
        DialogKind.EXPORT -> ExportDialog(state, close)
        // "Save changes?" replaces it (no two format dialogs on top of each other)
        DialogKind.SAVE -> if (state.pendingLeave == null) {
            SaveChoiceDialog("Save file", null, ExportFormat.entries, onPick = { save(it); close() }, onDismiss = close) {
                TextButton(onClick = close) { Text("Cancel") }
            }
        }
        DialogKind.RANDOM -> RandomDialog(state, close)
        DialogKind.EXAMPLES -> ExamplesDialog(state, close)
        DialogKind.WELCOME -> WelcomeDialog(close = close, showExamples = { open(DialogKind.EXAMPLES) })
        DialogKind.ABOUT -> AboutDialog(close)
        DialogKind.HELP -> HelpDialog(state.helpPage, close)
    }
    LaunchedEffect(state.pendingLeave != null) { if (state.pendingLeave != null && kind == DialogKind.SAVE) close() }
    // Load / Examples / Import / Random / Open file / another set / closing with unsaved changes
    // (ktoy's); after the other dialogs, so it shows on top of the one that asked. The format
    // buttons save the points; a Data widget draft is never saved implicitly, only discarded.
    if (state.pendingLeave != null) SaveChoiceDialog(
        "Save changes?",
        unsavedText(state),
        if (state.pointsUnsaved) ExportFormat.entries else emptyList(),
        onPick = ::save,
        onDismiss = state::cancelLeave,
    ) {
        TextButton(onClick = state::discardAndLeave) { Text("Discard") }
        TextButton(onClick = state::cancelLeave) { Text("Cancel") }
    }
    // last, so it shows on top of the dialog the error came from
    state.error?.let { ErrorDialog(it) { state.error = null } }
}

/** A load or parse error the user must see (the toolbar status is for info): one OK button. */
@Composable
private fun ErrorDialog(message: String, onDismiss: () -> Unit) = AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Error") },
    text = { Text(message, style = MaterialTheme.typography.bodyMedium) },
    confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
)

/** What "Save changes?" says is unsaved, or null if nothing (the dialog shows no empty text). */
private fun unsavedText(state: AppState): String? = buildList {
    if (state.pointsUnsaved) add("Your changes to “${state.fileName}” are not saved.")
    if (state.unappliedDraft) add("The coordinates edited in the Data widget are not applied.")
}.joinToString(" ").takeIf { it.isNotEmpty() }

@Composable
private fun FormatSelect(format: ExportFormat, onFormat: (ExportFormat) -> Unit) {
    SelectField("Format", format.extension, ExportFormat.entries, { it.extension }, Modifier.width(160.dp), onFormat)
}

/**
 * Which format to save all sets as (ktoy's): [message], one full-width button per format in
 * [formats], and [buttons] below them (Cancel, or Discard / Cancel in "Save changes?").
 */
@Composable
private fun SaveChoiceDialog(
    title: String,
    message: String?,
    formats: List<ExportFormat>,
    onPick: (ExportFormat) -> Unit,
    onDismiss: () -> Unit,
    buttons: @Composable () -> Unit,
) = AlertDialog(
    onDismissRequest = onDismiss,
    confirmButton = { Row { buttons() } },
    title = { Text(title) },
    text = {
        Column(Modifier.widthIn(min = 300.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            message?.let { Text(it, Modifier.padding(bottom = 4.dp), style = MaterialTheme.typography.bodyMedium) }
            for (format in formats) FilledTonalButton(onClick = { onPick(format) }, Modifier.fillMaxWidth()) {
                Text(saveLabel(format))
            }
        }
    },
)

private fun saveLabel(format: ExportFormat) = when (format) {
    ExportFormat.JSON -> "Save JSON"
    ExportFormat.TXT -> "Save text"
}

/** The current set as text, to copy. */
@Composable
private fun ExportDialog(state: AppState, close: () -> Unit) {
    var format by remember { mutableStateOf(ExportFormat.JSON) }
    val clipboard = LocalClipboardManager.current
    val text = state.exportText(format)
    BigDialog(
        "Export",
        onDismiss = close,
        buttons = {
            TextButton(onClick = { clipboard.setText(AnnotatedString(text)); state.showInfo("Copied") }) { Text("Copy") }
            TextButton(onClick = close) { Text("Close") }
        },
    ) {
        FormatSelect(format) { format = it }
        OutlinedTextField(
            text, onValueChange = {}, readOnly = true,
            textStyle = MonoStyle,
            modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 8.dp).textFocus(state),
        )
    }
}

/** Paste a point set; the format is detected. */
@Composable
private fun ImportDialog(state: AppState, close: () -> Unit) {
    var text by remember { mutableStateOf("") }
    BigDialog(
        "Import",
        onDismiss = close,
        buttons = {
            TextButton(onClick = close) { Text("Cancel") }
            TextButton(onClick = {
                state.parseImport(text)?.let { sets -> state.leave { state.loadSets("imported", sets); close() } }
            }) { Text("Import") }
        },
    ) {
        Text(IMPORT_FORMATS, style = MonoStyle)
        OutlinedTextField(
            text, onValueChange = { text = it },
            textStyle = MonoStyle,
            modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 8.dp).textFocus(state),
        )
    }
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
                if (error == null) state.leave { state.randomSet(state.randomN, state.randomGrid); close() }
            }) { Text("Generate") }
        },
        dismissButton = { TextButton(onClick = close) { Text("Cancel") } },
        title = { Text("Random point set") },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    NumberField(state, "n", state.randomN.toString(), { state.randomN = it.toInt(); error = null }, range = 1L..200, stepper = true)
                    NumberField(state, "grid", state.randomGrid.toString(), { state.randomGrid = it.toLong(); error = null }, range = 2L..COORD_LIMIT)
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
    )
}

/** What is selected in the Examples dialog: a family (generated with n) or a bundled set. */
sealed interface ExampleChoice {
    data class Family(val family: PointSetFamily) : ExampleChoice
    data class Single(val example: Example) : ExampleChoice
    data class OrderTypes(val file: OrderTypeFile) : ExampleChoice
}

/**
 * What the Examples dialog remembers between openings ([AppState.examples]): the expanded nodes (everything starts collapsed), the selection, the n per family and the
 * scroll position. Reopening shows the tree exactly as it was left.
 */
class ExamplesState {
    val expanded = mutableStateListOf<String>()
    var selected by mutableStateOf<ExampleChoice?>(null)
    val familyN = mutableStateMapOf<String, Int>()
    /** The chosen order type (1-based) per n. */
    val orderType = mutableStateMapOf<Int, Int>()
    val scroll = ScrollState(0)
}

/**
 * Families (generated), the bundled single sets in categories, and links; all collapsible.
 * Families and sets are radio buttons; the selected one shows its details (a family also its n).
 * Load replaces the current set with it (after "Save changes?", [AppState.leave]).
 */
@Composable
private fun ExamplesDialog(state: AppState, close: () -> Unit) {
    val ex = state.examples
    val open = ex.expanded
    fun toggle(key: String) { if (key in open) open.remove(key) else open.add(key) }
    var selected by ex::selected
    val familyN = ex.familyN
    var nError by remember { mutableStateOf<String?>(null) }
    // the order type number as typed; Load is off while it is not in 1..M
    var orderTypeText by remember { mutableStateOf<String?>(null) }
    val orderTypeOk = when (val c = selected) {
        is ExampleChoice.OrderTypes -> orderTypeText.let { it == null || it.toIntOrNull() in 1..c.file.sets.size }
        else -> true
    }
    fun load() {
        when (val choice = selected) {
            null -> {}
            is ExampleChoice.Family -> {
                val f = choice.family
                val n = familyN[f.id] ?: DEFAULT_FAMILY_N
                if (n !in 1..f.maxN) nError = "n must be 1..${f.maxN}"
                else state.leave { state.loadPoints("${f.id}_n${f.size(n)}", f.generate(n)); close() }
            }
            is ExampleChoice.Single -> {
                val e = choice.example
                state.leave { state.loadPoints(e.name, e.points, e.properties); close() }
            }            is ExampleChoice.OrderTypes -> {
                val f = choice.file
                // the whole file, at the chosen set, so "set i/n" steps through the other order types
                val i = ex.orderType[f.n] ?: 1
                if (orderTypeOk) state.leave { state.loadSets(f.file, f.sets, start = i - 1); close() }
            }
        }
    }
    BigDialog(
        "Examples",
        onDismiss = close,
        buttons = {
            TextButton(onClick = close) { Text("Cancel") }
            TextButton(onClick = ::load, enabled = selected != null && orderTypeOk) { Text("Load") }
        },
    ) {
        ScrollColumn(Modifier.fillMaxSize(), ex.scroll) {
            FeedbackBox("Know a set that belongs here, or have general feedback?")
            Spacer(Modifier.height(8.dp))
            TreeHeader("Families", "Families" in open, depth = 0) { toggle("Families") }
            HorizontalDivider()
            if ("Families" in open) for (f in FAMILIES) {
                val choice = ExampleChoice.Family(f)
                TreeLeaf(f.name, depth = 1, selected == choice) { selected = choice; nError = null }
                if (selected == choice) FamilyDetails(state, f, familyN[f.id] ?: DEFAULT_FAMILY_N, nError) {
                    familyN[f.id] = it
                    nError = null
                }
            }
            TreeHeader("Singletons", "Singletons" in open, depth = 0) { toggle("Singletons") }
            HorizontalDivider()
            if ("Singletons" in open) {
                for (g in EXAMPLES.categories) {
                    val key = "S/" + g.title
                    TreeHeader("${g.title} (${g.examples.size})", key in open, depth = 1) { toggle(key) }
                    if (key in open) for (e in g.examples) {
                        val choice = ExampleChoice.Single(e)
                        TreeLeaf(e.name, depth = 2, selected == choice) { selected = choice }
                        if (selected == choice) ExampleDetails(e)
                    }
                }
            }
            TreeHeader("Order types", "Order types" in open, depth = 0) { toggle("Order types") }
            HorizontalDivider()
            if ("Order types" in open) for (f in EXAMPLES.orderTypes) {
                val choice = ExampleChoice.OrderTypes(f)
                TreeLeaf("n = ${f.n} (${f.sets.size})", depth = 1, selected == choice) { selected = choice; orderTypeText = null }
                if (selected == choice) OrderTypeDetails(state, f, ex.orderType[f.n] ?: 1, orderTypeOk, { orderTypeText = it }) {
                    ex.orderType[f.n] = it
                }
            }
            TreeHeader("Links", "Links" in open, depth = 0) { toggle("Links") }
            HorizontalDivider()
            if ("Links" in open) for (l in EXAMPLES.links) Column(Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp)) {
                LinkText(l.title, l.url, MaterialTheme.typography.bodyLarge)
                l.description?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                Text(l.url, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

private const val DEFAULT_FAMILY_N = 16

/** A clickable, expand/collapse tree node (category or subfolder). */
@Composable
private fun TreeHeader(label: String, open: Boolean, depth: Int, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clickable { onToggle() }
            .padding(start = (depth * 16).dp)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(if (open) "▾" else "▸", style = MaterialTheme.typography.bodyLarge)
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

/** An example leaf: a radio button; a click selects it. */
@Composable
private fun TreeLeaf(name: String, depth: Int, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .padding(start = (depth * 16).dp)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 8.dp))
    }
}

/** The details of the selected bundled set: description, note, reference, links. */
@Composable
private fun ExampleDetails(e: Example) {
    Column(Modifier.padding(start = 64.dp, bottom = 8.dp)) {
        Text(e.description, style = MaterialTheme.typography.bodySmall)
        e.note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        e.reference?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
        if (e.links.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            for (l in e.links) LinkText(l.title, l.url, MaterialTheme.typography.labelSmall)
        }
    }
}

/** Yellow box asking for interesting sets and feedback, with a clickable e-mail address. */
@Composable
fun FeedbackBox(question: String) {
    Column(
        Modifier.fillMaxWidth().background(Color(0xFFFFF3B0), RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFFE0C200), RoundedCornerShape(8.dp)).padding(10.dp),
    ) {
        Text(question, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF3A3000))
        LinkText(EMAIL, feedbackMailto(), MaterialTheme.typography.bodyMedium)
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
        "Point set (Point Set → Export, JSON or text):\n\n"
    return "mailto:$EMAIL?subject=${enc(subject)}&body=${enc(body)}"
}

/** The details of the selected family: its n, description and references. */
@Composable
private fun FamilyDetails(state: AppState, f: PointSetFamily, n: Int, error: String?, onN: (Int) -> Unit) {
    Column(Modifier.padding(start = 48.dp, bottom = 8.dp)) {
        NumberField(state, "n", n.toString(), { onN(it.toInt()) }, range = 1L..f.maxN, stepper = true, isError = error != null)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        Text(f.description, style = MaterialTheme.typography.bodySmall)
        for (r in f.references) Text(r.text + (r.url?.let { " $it" } ?: ""), style = MaterialTheme.typography.labelSmall)
    }
}

/** The details of a selected order type file: which order type to load ("order type: − [17] + of 135"). */
@Composable
private fun OrderTypeDetails(
    state: AppState, f: OrderTypeFile, index: Int, ok: Boolean, onText: (String) -> Unit, onIndex: (Int) -> Unit,
) {
    val count = f.sets.size
    Column(Modifier.padding(start = 48.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NumberField(
                state, "order type", index.toString(), { it.toInt().let { i -> if (i in 1..count) onIndex(i) } },
                range = 1L..count, stepper = true, onText = onText,
            )
            Text(" of $count", style = MaterialTheme.typography.bodyMedium)
        }
        if (!ok) Text("1 to $count", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        Text("Order types of ${f.n} points (Graz order type database)", style = MaterialTheme.typography.bodySmall)
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
                Text(WELCOME_TEXT, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(10.dp))
                FeedbackBox("Interesting point configurations, ideas or general feedback?")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(dontShow, { dontShow = it })
                    Text("Don't show again", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
    )
}

const val WELCOME_PREF = "welcome"

/** What PoCoView is for, at the top of the Welcome dialog and the General help page. */
const val WELCOME_TEXT = "PoCoView is a viewer for point sets in the plane. Change a point set by dragging points and " +
    "explore and visualise its combinatorial properties."
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
                    "Showing ${if (state.setIndex == 0) "the first" else "set ${state.setIndex + 1}"} of $count. Switch between them with the arrows < > at the top (or PageUp / PageDown).",
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

/** About: description, feedback box, the projects with their licenses, build identity, license texts. */
@Composable
private fun AboutDialog(close: () -> Unit) = AlertDialog(
    onDismissRequest = close,
    confirmButton = { TextButton(onClick = close) { Text("OK") } },
    title = { Text("PoCoView") },
    text = {
        ScrollColumn(Modifier.heightIn(max = 520.dp)) {
            Text(ABOUT, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            FeedbackBox("PoCoView is in early development. Feedback is very welcome:")
            Spacer(Modifier.height(8.dp))
            ProjectLine("PoCoView", "https://github.com/manfredscheucher/PoCoView", "this app")
            ProjectLine(
                "pyotlib2", "https://github.com/manfredscheucher/pyotlib2",
                "the Python library whose editor PoCoView replaces and whose optimizers it ports",
            )
            Spacer(Modifier.height(8.dp))
            Text(buildLine(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            var showLicenses by remember { mutableStateOf(false) }
            TextButton(onClick = { showLicenses = !showLicenses }) {
                Text(if (showLicenses) "Hide license texts" else "License texts")
            }
            if (showLicenses) {
                for (notice in GeneratedLicenses.NOTICES) {
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Text(notice.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    Text(notice.text, style = MonoStyle.copy(fontSize = MaterialTheme.typography.labelSmall.fontSize))
                }
            }
        }
    },
)

/** One About line "[name] (license): note" (no bracket without a license); only the name is a link. */
@Composable
private fun ProjectLine(name: String, url: String, note: String, license: String? = "MIT") {
    Row {
        LinkText(name, url, MaterialTheme.typography.bodyMedium)
        Text(
            (license?.let { " ($it)" } ?: "") + ": $note",
            Modifier.padding(vertical = 2.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

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
    BigDialog(
        "Select properties",
        onDismiss = close,
        buttons = { TextButton(onClick = close) { Text("Done") } },
    ) {
        ScrollColumn(Modifier.fillMaxSize()) { FlowRow(
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
    }
}

@Composable
private fun PickerRow(state: AppState, p: PropertyDef) {
    val shown = state.toggles[p.id]?.count == true
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(shown, onCheckedChange = { state.setShown(p.id, it) })
        // highlight colour, for properties that can be highlighted
        if (p.hasStructures) ColorChooser(state, state.colorOf(p.id)) { state.setPropertyColor(p.id, it) }
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

private const val ABOUT = "PoCoView (Point Configuration Viewer) is an interactive editor and viewer for point sets and " +
    "their order types. It counts and visualizes convex hulls, onion layers, crossings, k-holes, k-gons and " +
    "triangulations, and can minimize or beautify coordinates while keeping the order type."

/** "sha · committed <date> · built <date>" (GeneratedBuildInfo, written at build time); unknown parts are left out. */
private fun buildLine(): String = buildList {
    val sha = GeneratedBuildInfo.COMMIT_SHA
    if (sha != "unknown") add(if (GeneratedBuildInfo.IS_DIRTY == "dirty") "$sha (modified)" else sha)
    if (GeneratedBuildInfo.COMMIT_DATE != "unknown") add("committed ${GeneratedBuildInfo.COMMIT_DATE}")
    add("built ${GeneratedBuildInfo.BUILD_DATE}")
}.joinToString(" · ")
