package org.bytefred.pocoview.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/** The Help menu's pages, in menu order (About has its own dialog). */
enum class HelpPage(val title: String) {
    GENERAL("General"),
    PROPERTIES("Properties"),
    OPTIMIZE("Optimize"),
    SHORTCUTS("Keyboard shortcuts"),
}

/** One Help page in a [BigDialog]: plain paragraphs; the shortcuts page lists the keys in bold. */
@Composable
fun HelpDialog(page: HelpPage, onDismiss: () -> Unit) {
    BigDialog(page.title, onDismiss, buttons = { TextButton(onClick = onDismiss) { Text("OK") } }) {
        val vScroll = rememberScrollState()
        ScrollbarBox(vScroll, Modifier.weight(1f).fillMaxWidth()) {
            Column(
                Modifier.verticalScroll(vScroll).padding(end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(if (page == HelpPage.SHORTCUTS) 4.dp else 12.dp),
            ) {
                if (page == HelpPage.SHORTCUTS) {
                    for ((key, action) in SHORTCUTS) KeyLine(key, action)
                    Paragraph(SHORTCUTS_NOTE)
                } else {
                    for (text in helpText(page)) Paragraph(text)
                }
            }
        }
    }
}

/** The paragraphs of [page] as plain text (the shortcuts page: "key: action" per key). */
internal fun helpText(page: HelpPage): List<String> = when (page) {
    HelpPage.GENERAL -> GENERAL_TEXT
    HelpPage.PROPERTIES -> PROPERTIES_TEXT
    HelpPage.OPTIMIZE -> OPTIMIZE_TEXT
    HelpPage.SHORTCUTS -> SHORTCUTS.map { (key, action) -> "$key: $action" } + SHORTCUTS_NOTE
}

@Composable
private fun Paragraph(text: String) = Text(text, style = MaterialTheme.typography.bodyLarge)

/** A shortcut: the key in bold, then what it does. */
@Composable
private fun KeyLine(key: String, action: String) {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(key) }
            append(": $action")
        },
        style = MaterialTheme.typography.bodyLarge,
    )
}

private val GENERAL_TEXT = listOf(
    "PoCoView edits planar point sets and shows properties of their order type (for every three points, " +
        "whether they turn left or right).",
    "Points have integer coordinates, the y axis points up. Three points on a line get a red ring: the set " +
        "is not in general position, and the properties and optimizers wait until that is fixed.",
    "The mode in the toolbar sets what a click does: Move drags a point or selects it, Add adds a point " +
        "where you click, Delete removes the point you click. Drag empty space to pan, zoom with the wheel " +
        "or by pinching. Right-click a point to select or delete it. Undo and Redo are in the Edit menu.",
    "Lock order type keeps a dragged point inside its cell of the line arrangement, so the order type " +
        "stays the same.",
    "The line arrangement is the line through every pair of points. Its cells are the regions in which a " +
        "point can move without changing the order type. The lines through the selected point are hidden " +
        "by default, so you see its cell (Display widget, Lines through it).",
    "The widgets are Properties, Optimize, Data and Display. The toolbar, the View menu and the keys 1 to " +
        "4 show and hide them. By default at most two are open (Display widget, Max. widgets); opening " +
        "another one closes the one you clicked least recently. All dividers can be dragged.",
    "The Data widget has the coordinates as text, one x y or x y colour per line: edit them and press " +
        "Apply, or Revert. It also shows the order type hash and, up to 30 points, the λ matrix.",
    "The Display widget sets how points and lines are drawn, the number of widgets and the layout. These " +
        "settings, the open widgets, the divider positions and the shown properties are remembered for " +
        "the next start.",
    "In the Point Set menu, Examples… has generated families and interesting sets from the literature, " +
        "Import… reads pasted text, Random… makes a random set and Export… gives the text to copy. Open " +
        "file… and Save file… read and write files, Export SVG and Export Ipe save a picture.",
    "If the points have changed since they were loaded or saved, PoCoView asks whether to save them before " +
        "it replaces them. On the desktop, closing the app asks too. An unapplied edit in the Data widget " +
        "is never saved, only discarded.",
    "Files with several sets show one set at a time; switch with < and > in the toolbar or PageUp and " +
        "PageDown.",
)

private val PROPERTIES_TEXT = listOf(
    "The Properties widget lists the properties picked in View → Select properties…. Most depend only on " +
        "the order type (the monochromatic ones also on the colours), and all need general position. " +
        "Triangulations is off by default (it is slow).",
    "Highlight draws the structures of every property whose switch is on, for example the 5-holes as " +
        "filled polygons. It is off by default. At most 3000 structures per property are drawn, " +
        "and the colour of each property is set in Select properties….",
    "Incident only counts and draws only the structures that contain the selected point.",
    "Convex hull: the number of points on the convex hull.",
    "Onion layers: how often the convex hull can be peeled off until no point is left.",
    "Crossing number: the crossings of the complete straight-line graph, which equals the number of " +
        "convex 4-gons.",
    "Exit edges: the edges whose witness point can cross them alone; their orientations determine the " +
        "order type. With Incident only: the edges that have the selected point as witness.",
    "Crossing family: the largest set of pairwise crossing segments, drawn red and bold. Up to 50 points.",
    "k-holes: empty convex k-gons (no point inside), k = 3 to 7.",
    "Convex k-gons: k points in convex position, k = 4 to 7.",
    "Triangulations: the number of triangulations. Exponential, so only up to 16 points.",
    "Monochromatic properties need a colour for every point. Convex k-holes (k = 3 to 5) are empty convex " +
        "k-gons with vertices of one colour, general k-holes (k = 4, 5) the same for simple polygons that " +
        "need not be convex. A k-island (k = 4 to 6) is k points of one colour with no other point in " +
        "their convex hull.",
)

private val OPTIMIZE_TEXT = listOf(
    "Each box of the Optimize widget has its own parameters (Reset restores them), a run button and " +
        "Repeat, which runs again and again until you press Abort. A run is one undo step.",
    "Minimize (M) makes the coordinates smaller and keeps the order type. Even + halve moves the points " +
        "to even coordinates and halves them, perturb moves them towards their centre.",
    "Minimize w.r.t. property makes the coordinates smaller while the order type may change, as long as " +
        "the chosen property does not get larger. A worse result is undone.",
    "Beautify (B) keeps the order type and avoids thin triangles and very different distances. Minimize " +
        "after shrinks the coordinates afterwards.",
    "Search looks for a smaller value of the chosen property; the order type may change. Equal values are " +
        "taken over, larger ones not, and it stops at the first improvement.",
    "The qtvis minimizer (Enter) moves points from cell to cell until the chosen property gets smaller. " +
        "If it finds nothing, the start configuration comes back.",
)

/** The keyboard shortcuts page: key and action (doc/USER_GUIDE.md has the same list). */
private val SHORTCUTS = listOf(
    "Esc" to "deselect, or close the open menu",
    "Del or Backspace" to "delete the selected point",
    "Ctrl+Z" to "undo",
    "Ctrl+Shift+Z or Ctrl+Y" to "redo",
    "+ / -" to "zoom in / out",
    "Home or 0" to "fit",
    "V / A / D" to "mode Move / Add / Delete",
    "F" to "lock order type",
    "L" to "line arrangement",
    "H" to "highlight",
    "I" to "incident only",
    "1 to 4" to "show or hide Properties, Optimize, Data, Display",
    "M" to "minimize",
    "B" to "beautify",
    "Enter" to "qtvis minimizer",
    "PageUp or ," to "previous set of the file",
    "PageDown or ." to "next set of the file",
    "Mouse" to "drag a point to move it, drag empty space to pan, wheel or pinch to zoom, right-click a " +
        "point for its menu",
)

private const val SHORTCUTS_NOTE = "On a Mac, Cmd works like Ctrl and Ctrl+click like a right-click. The keys " +
    "do nothing while you type in a field or a menu is open (except Esc)."
