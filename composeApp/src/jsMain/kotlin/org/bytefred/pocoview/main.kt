package org.bytefred.pocoview

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document
import org.bytefred.pocoview.ui.App

/** Web (JS/browser) entry point; the bundle is loaded by src/jsMain/resources/index.html. */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport(document.body!!) {
        App()
    }
}
