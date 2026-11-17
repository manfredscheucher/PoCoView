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

/** One Help page in a [BigDialog]: plain paragraphs, each starting with its bold lead-in words. */
@Composable
fun HelpDialog(page: HelpPage, onDismiss: () -> Unit) {
    BigDialog(page.title, onDismiss, buttons = { TextButton(onClick = onDismiss) { Text("OK") } }) {
        val vScroll = rememberScrollState()
        ScrollbarBox(vScroll, Modifier.weight(1f).fillMaxWidth()) {
            Column(
                Modifier.verticalScroll(vScroll).padding(end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(if (page == HelpPage.SHORTCUTS) 4.dp else 12.dp),
            ) {
                for ((bold, text) in helpText(page)) Paragraph(text, bold)
            }
        }
    }
}

/** The paragraphs of [page]: bold lead-in words and the text that follows them. */
internal fun helpText(page: HelpPage): List<Pair<String, String>> = when (page) {
    HelpPage.GENERAL -> GENERAL_TEXT
    HelpPage.PROPERTIES -> PROPERTIES_TEXT
    HelpPage.OPTIMIZE -> OPTIMIZE_TEXT
    HelpPage.SHORTCUTS -> SHORTCUTS_TEXT
}

/** A paragraph; [bold] is its first words, set in bold. */
@Composable
private fun Paragraph(text: String, bold: String) {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(bold) }
            append(text)
        },
        style = MaterialTheme.typography.bodyLarge,
    )
}

private val GENERAL_TEXT = listOf(
    "PoCoView" to " edits planar point sets and shows properties of their order type: for every three " +
        "points, whether they turn left or right.",
    "Points" to " have integer coordinates, the y axis points up. Three points on a line get a red ring: " +
        "the set is not in general position, and the properties and optimizers wait until that is fixed.",
    "Mode" to " (toolbar): Move drags a point or selects it with a click, Add adds a point where you click, " +
        "Delete removes the point you click. Drag empty space to pan, zoom with the wheel or by pinching. " +
        "Undo and Redo are in the Edit menu.",
    "Lock order type" to " keeps a dragged point inside its cell of the line arrangement, so the order type " +
        "stays the same.",
    "Line arrangement" to ": the line through every pair of points. Its cells are the regions in which a " +
        "point can move without changing the order type. The lines through the selected point are hidden by " +
        "default, so you see its cell (Display widget, Lines through it).",
    "Widgets" to ": Properties, Optimize, Data and Display. The toolbar, the View menu and the keys 1 to 4 " +
        "show and hide them. At most two are open (Display widget, Max. widgets); opening another one closes " +
        "the one you clicked least recently. The dividers next to the canvas can be dragged.",
    "Data" to " has the coordinates as text, one x y or x y colour per line: edit them and press Apply, or " +
        "Revert. It also shows the order type hash and, up to 30 points, the λ matrix.",
    "Display" to " sets how points and lines are drawn, the number of widgets and the layout.",
    "Point Set" to " menu: Examples… has generated families and interesting sets from the literature. " +
        "Random… makes a random set. Import… and Export… take and give text to paste or copy, Open file… and " +
        "Save file… read and write files, Export SVG and Export Ipe save a picture. Before the current set is " +
        "replaced, PoCoView asks.",
    "Files with several sets" to " show one set at a time; switch with < and > in the toolbar or PageUp " +
        "and PageDown.",
)

private val PROPERTIES_TEXT = listOf(
    "The Properties widget" to " lists the properties picked in View → Select properties…. They depend only " +
        "on the order type and need general position. Triangulations is off at start; an example can switch " +
        "on the properties it is about.",
    "Highlight" to " draws the structures of every property whose switch is on, for example the 5-holes as " +
        "filled polygons. It is off at start. At most 3000 structures per property are drawn, and the colour " +
        "of each property is set in Select properties….",
    "Incident only" to " counts and draws only the structures that contain the selected point.",
    "Convex hull:" to " the number of points on the convex hull.",
    "Onion layers:" to " how often the convex hull can be peeled off until no point is left.",
    "Crossing number:" to " the crossings of the complete straight-line graph, which equals the number of " +
        "convex 4-gons.",
    "Exit edges:" to " the edges whose witness point can cross them alone; their orientations determine the " +
        "order type. With Incident only: the edges that have the selected point as witness.",
    "Crossing family:" to " the largest set of pairwise crossing segments, drawn red and bold. Up to 50 points.",
    "k-holes:" to " empty convex k-gons (no point inside), k = 3 to 7.",
    "Convex k-gons:" to " k points in convex position, k = 4 to 7.",
    "Triangulations:" to " the number of triangulations. Exponential, so only up to 16 points.",
    "Monochromatic properties" to " need a colour for every point. Convex k-holes (k = 3 to 5) are empty " +
        "convex k-gons with vertices of one colour, general k-holes (k = 4, 5) the same for simple polygons " +
        "that need not be convex. A k-island (k = 4 to 6) is k points of one colour with no other point in " +
        "their convex hull.",
)

private val OPTIMIZE_TEXT = listOf(
    "Each box" to " of the Optimize widget has its own parameters (Reset restores them), a run button and " +
        "Repeat, which runs again and again until you press Abort. A run is one undo step.",
    "Minimize" to " (M) makes the coordinates smaller and keeps the order type. Even + halve moves the points " +
        "to even coordinates and halves them, perturb moves them towards their centre.",
    "Minimize w.r.t. property" to " makes the coordinates smaller while the order type may change, as long " +
        "as the chosen property does not get larger. A worse result is undone.",
    "Beautify" to " (B) keeps the order type and avoids thin triangles and very different distances. " +
        "Minimize after shrinks the coordinates afterwards.",
    "Search" to " looks for a smaller value of the chosen property; the order type may change. Equal values " +
        "are taken over, larger ones not, and it stops at the first improvement.",
    "qtvis minimizer" to " (Enter) moves points from cell to cell until the chosen property gets smaller. " +
        "If it finds nothing, the start configuration comes back.",
)

private val SHORTCUTS_TEXT = listOf(
    "Esc:" to " deselect",
    "Del / Backspace:" to " delete the selected point",
    "Ctrl+Z:" to " undo",
    "Ctrl+Shift+Z or Ctrl+Y:" to " redo",
    "+ / -:" to " zoom in / out",
    "Home or 0:" to " fit",
    "V / A / D:" to " mode Move / Add / Delete",
    "F:" to " lock order type",
    "L:" to " line arrangement",
    "H:" to " highlight",
    "I:" to " incident only",
    "1 to 4:" to " show or hide Properties, Optimize, Data, Display",
    "M:" to " minimize",
    "B:" to " beautify",
    "Enter:" to " qtvis minimizer",
    "PageUp / PageDown (, / .):" to " previous / next set of the file",
    "Mouse:" to " drag a point to move it, drag empty space to pan, wheel or pinch to zoom.",
    "On a Mac" to " Cmd works like Ctrl. The keys do nothing while you type in a field or a menu is open.",
)
