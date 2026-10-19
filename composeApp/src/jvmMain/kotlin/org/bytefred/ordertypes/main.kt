package org.bytefred.ordertypes

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import org.bytefred.ordertypes.ui.App

/** Desktop (JVM) entry point. */
fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Order Types",
        state = rememberWindowState(width = 1200.dp, height = 800.dp),
    ) {
        App()
    }
}
