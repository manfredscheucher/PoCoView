package org.bytefred.pocoview.ui

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.fromKeyword

@OptIn(ExperimentalComposeUiApi::class)
actual fun resizePointerIcon(horizontal: Boolean): PointerIcon =
    PointerIcon.fromKeyword(if (horizontal) "col-resize" else "row-resize")
