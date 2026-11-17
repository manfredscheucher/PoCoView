package org.bytefred.pocoview.ui

import androidx.compose.foundation.focusable
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.key
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp

/** Landscape (side panel) needs at least this width in AUTO layout mode (ktoy-style, width based). */
private val WIDE_MIN = 760.dp

/** Below MIN_CANVAS + MIN_COLUMN per widget, the side by side widget columns scroll horizontally. */
private val MIN_COLUMN = 260.dp
private val MIN_CANVAS = 320.dp

/** The canvas's share next to widget columns of share 1 each, until a divider is dragged. */
private const val WIDE_CANVAS_WEIGHT = 2f

/** Any press inside a widget column makes it the most recently clicked one (not consumed). */
private fun Modifier.touches(state: AppState, w: Widget) = pointerInput(w) {
    awaitPointerEventScope {
        while (true) {
            val e = awaitPointerEvent(PointerEventPass.Initial)
            if (e.type == PointerEventType.Press) state.touchWidget(w)
        }
    }
}

@Composable
fun App(initialState: AppState? = null, initialDialog: DialogKind? = null) {
    PoCoViewTheme { ProvideMonoStyle {
        val state = remember { initialState ?: AppState() }
        val view = remember { ViewTransform() }
        val focus = remember { FocusRequester() }
        // scroll positions survive closing and reopening a widget
        val scrolls = remember { Widget.entries.associateWith { ScrollState(0) } }
        val columnsScroll = rememberScrollState()

        // shortcuts on the root: key events of the focused canvas, chip or button bubble up here;
        // they are ignored while a text field has focus or a menu is open
        Surface(Modifier.fillMaxSize().onKeyEvent { state.shortcutsActive && handleKey(it, state, view) }) {
            var dialog by remember {
                mutableStateOf(initialDialog ?: if (loadPref(WELCOME_PREF) == "off") DialogKind.NONE else DialogKind.WELCOME)
            }
            Dialogs(state, dialog, open = { dialog = it }) { dialog = DialogKind.NONE }
            Column(Modifier.fillMaxSize()) {
                MenuBar(state, view) { dialog = it }
                ToolBar(state)
                HorizontalDivider()
                BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                    val wide = when (state.layoutMode) {
                        LayoutMode.LANDSCAPE -> true
                        LayoutMode.PORTRAIT -> false
                        LayoutMode.AUTO -> maxWidth >= WIDE_MIN
                    }
                    val canvas = @Composable { m: Modifier ->
                        Box(m.focusRequester(focus).focusable()) {
                            // the canvas is composed inside BoxWithConstraints (subcomposition),
                            // so request the initial focus from here, once it is attached
                            // (again after a widget was opened or closed: keeps the shortcuts working)
                            LaunchedEffect(state.openWidgets.toList()) { if (state.textFieldsFocused == 0) focus.requestFocus() }
                            PointCanvas(state, view, onInteract = { focus.requestFocus() })
                        }
                    }
                    val selectProperties = { dialog = DialogKind.PROPERTIES }
                    val column = @Composable { w: Widget, m: Modifier ->
                        Column(m.touches(state, w)) {
                            WidgetHeader(state, w)
                            ScrollColumn(Modifier.weight(1f), scrolls.getValue(w)) {
                                WidgetContent(state, w, selectProperties)
                            }
                        }
                    }
                    val open = state.openWidgets.toList()
                    // the canvas (key null) and the widgets in opening order, with draggable dividers
                    fun panels(canvasWeight: Float): List<SplitPanel<Widget?>> =
                        listOf(SplitPanel<Widget?>(null, canvasWeight, canvas)) + open.map { w -> SplitPanel<Widget?>(w) { m -> column(w, m) } }
                    if (wide && maxWidth < MIN_CANVAS + MIN_COLUMN * open.size) {
                        // too narrow to split: the canvas keeps MIN_CANVAS, the MIN_COLUMN wide
                        // columns right of it scroll horizontally
                        Row(Modifier.fillMaxSize()) {
                            canvas(Modifier.width(MIN_CANVAS).fillMaxHeight())
                            ScrollbarBox(null, Modifier.weight(1f).fillMaxHeight(), columnsScroll) {
                                Row(Modifier.fillMaxHeight().horizontalScroll(columnsScroll)) {
                                    for (w in open) key(w) {
                                        VerticalDivider()
                                        column(w, Modifier.width(MIN_COLUMN).fillMaxHeight())
                                    }
                                }
                            }
                        }
                    } else if (wide) {
                        // the widgets as columns right of the canvas
                        SplitPane(panels(WIDE_CANVAS_WEIGHT), state.wideWeights, { state.wideWeights = it }, horizontal = true, Modifier.fillMaxSize())
                    } else {
                        // portrait: the widgets stacked below the canvas, each with its own scrolling
                        SplitPane(panels(1f), state.stackedWeights, { state.stackedWeights = it }, horizontal = false, Modifier.fillMaxSize())
                    }
                }
                StatusBar(state)
            }
        }
    } }
}

private fun handleKey(e: KeyEvent, state: AppState, view: ViewTransform): Boolean {
    if (e.type != KeyEventType.KeyDown) return false
    val cmd = e.isCtrlPressed || e.isMetaPressed
    when {
        cmd && e.key == Key.Z && e.isShiftPressed -> state.redo()
        cmd && e.key == Key.Z -> state.undo()
        cmd && e.key == Key.Y -> state.redo()
        cmd -> return false
        e.key == Key.Escape -> state.select(null)
        e.key == Key.Delete || e.key == Key.Backspace -> state.deleteSelected()
        e.key == Key.Plus || e.key == Key.Equals || e.key == Key.NumPadAdd -> view.zoomCenter(1.2)
        e.key == Key.Minus || e.key == Key.NumPadSubtract -> view.zoomCenter(1 / 1.2)
        e.key == Key.MoveHome || e.key == Key.Zero -> state.requestFit()
        e.key == Key.Enter || e.key == Key.NumPadEnter -> state.cellHop(state.optimizeProperty, state.cellHopParams)
        e.key == Key.V -> state.tool = Tool.MOVE
        e.key == Key.A -> state.tool = Tool.ADD
        e.key == Key.D -> state.tool = Tool.DELETE
        e.key == Key.F -> state.fixOrderType = !state.fixOrderType
        e.key == Key.L -> state.showLines = !state.showLines
        e.key == Key.M -> state.minimize()
        e.key == Key.B -> state.beautify()
        e.key == Key.H -> state.changeHighlight(!state.highlight)
        e.key == Key.I -> state.changeIncidentOnly(!state.incidentOnly)
        e.key in WIDGET_KEYS -> state.toggleWidget(Widget.entries[WIDGET_KEYS.indexOf(e.key)])
        e.key == Key.PageUp || e.key == Key.Comma -> state.showSet(state.setIndex - 1)
        e.key == Key.PageDown || e.key == Key.Period -> state.showSet(state.setIndex + 1)
        else -> return false
    }
    return true
}

private val WIDGET_KEYS = listOf(Key.One, Key.Two, Key.Three, Key.Four)
