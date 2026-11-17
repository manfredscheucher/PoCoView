package org.bytefred.pocoview.ui

import androidx.compose.ui.input.pointer.PointerIcon
import java.awt.Cursor

actual fun resizePointerIcon(horizontal: Boolean): PointerIcon =
    PointerIcon(Cursor(if (horizontal) Cursor.E_RESIZE_CURSOR else Cursor.N_RESIZE_CURSOR))
