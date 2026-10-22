package org.bytefred.pocoview.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
actual fun VerticalScrollbarFor(state: ScrollState, modifier: Modifier) =
    VerticalScrollbar(rememberScrollbarAdapter(state), modifier)
