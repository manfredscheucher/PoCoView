package org.bytefred.pocoview

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import org.bytefred.pocoview.ui.App

/** Desktop (JVM) entry point. */
fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "PoCoView",
        state = rememberWindowState(width = 1200.dp, height = 800.dp),
    ) {
        App()
    }
}
