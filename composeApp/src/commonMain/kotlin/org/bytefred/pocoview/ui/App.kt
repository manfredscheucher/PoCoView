package org.bytefred.pocoview.ui

import androidx.compose.foundation.focusable
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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

/** Widget column width; columns get narrower (down to [MIN_COLUMN]) to leave the canvas [MIN_CANVAS]. */
private val COLUMN_WIDTH = 360.dp
private val MIN_COLUMN = 260.dp
private val MIN_CANVAS = 320.dp

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
fun App() {
    MaterialTheme {
        val state = remember { AppState() }
        val view = remember { ViewTransform() }
        val focus = remember { FocusRequester() }
        // scroll positions survive closing and reopening a widget
        val scrolls = remember { Widget.entries.associateWith { ScrollState(0) } }
        val columnsScroll = rememberScrollState()

        // shortcuts on the root: key events of the focused canvas, chip or button bubble up here;
        // they are ignored while a text field has focus
        Surface(Modifier.fillMaxSize().onKeyEvent { state.textFieldsFocused == 0 && handleKey(it, state, view) }) {
            var dialog by remember { mutableStateOf(if (loadPref(WELCOME_PREF) == "off") DialogKind.NONE else DialogKind.WELCOME) }
            Dialogs(state, dialog, open = { dialog = it }) { dialog = DialogKind.NONE }
            Column(Modifier.fillMaxSize()) {
                MenuBar(state, view) { dialog = it }
                ToolBar(state)
                HorizontalDivider()
                BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                    // portrait: more room below the canvas when several widgets share it
                    val panelHeight = maxHeight * if (state.openWidgets.size > 1) 0.55f else 0.45f
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
                    val open = state.openWidgets.toList()
                    if (wide) {
                        // the widgets as columns right of the canvas, in opening order
                        // (if even MIN_COLUMN wide columns do not fit, the columns scroll horizontally)
                        val columnWidth = if (open.isEmpty()) COLUMN_WIDTH
                        else ((maxWidth - MIN_CANVAS) / open.size).coerceIn(MIN_COLUMN, COLUMN_WIDTH)
                        val area = (maxWidth - MIN_CANVAS).coerceAtLeast(MIN_COLUMN)
                        val scrollColumns = columnWidth * open.size > area
                        Row(Modifier.fillMaxSize()) {
                            canvas(Modifier.weight(1f).fillMaxHeight())
                            Row(
                                if (scrollColumns) Modifier.width(area).fillMaxHeight().horizontalScroll(columnsScroll)
                                else Modifier.fillMaxHeight(),
                            ) {
                                for (w in open) key(w) {
                                    VerticalDivider()
                                    Column(Modifier.width(columnWidth).fillMaxHeight().touches(state, w)) {
                                        WidgetHeader(state, w)
                                        Box(Modifier.weight(1f).verticalScroll(scrolls.getValue(w))) {
                                            WidgetContent(state, w, selectProperties)
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // portrait: the widgets stacked below the canvas, each with its own share
                        // of the height and its own scrolling
                        Column(Modifier.fillMaxSize()) {
                            canvas(Modifier.weight(1f).fillMaxWidth())
                            if (open.isNotEmpty()) {
                                HorizontalDivider()
                                Column(Modifier.fillMaxWidth().height(panelHeight)) {
                                    for ((i, w) in open.withIndex()) key(w) {
                                        if (i > 0) HorizontalDivider()
                                        Column(Modifier.fillMaxWidth().weight(1f).touches(state, w)) {
                                            WidgetHeader(state, w)
                                            Box(Modifier.weight(1f).verticalScroll(scrolls.getValue(w))) {
                                                WidgetContent(state, w, selectProperties)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                StatusBar(state)
            }
        }
    }
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
