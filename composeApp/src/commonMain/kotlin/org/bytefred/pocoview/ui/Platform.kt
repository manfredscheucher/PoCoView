package org.bytefred.pocoview.ui

import androidx.compose.runtime.staticCompositionLocalOf

/** True in the browser build: the header then shows the web-only subtitle. */
expect val isWeb: Boolean

/**
 * How the canvas context menu opens: a right-click and/or a long press. Right-click on the
 * desktop, both on the web (a touch screen has no right-click).
 */
data class MenuGestures(val rightClick: Boolean, val longPress: Boolean)

expect val platformMenuGestures: MenuGestures

/** The gestures in use; tests can switch on the web's ones. */
val LocalMenuGestures = staticCompositionLocalOf { platformMenuGestures }
