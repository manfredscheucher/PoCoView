package org.bytefred.pocoview

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document
import kotlinx.browser.window
import org.bytefred.pocoview.ui.App
import org.w3c.dom.events.KeyboardEvent

/** Web (JS/browser) entry point; the bundle is loaded by src/jsMain/resources/index.html. */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    // Ctrl/Cmd+O and Ctrl/Cmd+S are the app's Open file / Save file: the browser must not open its
    // own dialogs. Compose prevents that only when no text field has focus, so do it here, after
    // Compose has seen the key (bubble phase: in the capture phase a text field drops Cmd+O/S).
    window.addEventListener("keydown", { e ->
        e as KeyboardEvent
        if ((e.ctrlKey || e.metaKey) && !e.shiftKey && (e.code == "KeyO" || e.code == "KeyS")) e.preventDefault()
    }, false)
    ComposeViewport(document.body!!) {
        App()
    }
}
