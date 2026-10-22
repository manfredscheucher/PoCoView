package org.bytefred.pocoview.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** A visible vertical scrollbar for [state] (desktop and browser). */
@Composable
expect fun VerticalScrollbarFor(state: ScrollState, modifier: Modifier = Modifier)

/** A vertically scrolling column with a visible scrollbar on the right. */
@Composable
fun ScrollColumn(
    modifier: Modifier = Modifier,
    state: ScrollState = rememberScrollState(),
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier) {
        Column(Modifier.verticalScroll(state).padding(end = 10.dp), content = content)
        VerticalScrollbarFor(state, Modifier.align(Alignment.CenterEnd).fillMaxHeight())
    }
}
