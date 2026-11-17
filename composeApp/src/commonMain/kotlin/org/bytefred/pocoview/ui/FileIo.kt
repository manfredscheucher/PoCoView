package org.bytefred.pocoview.ui

import androidx.compose.runtime.Composable

/**
 * Cross-platform file open / save (ported from ktoy's FileIo, extended to bytes because
 * the Graz database formats are binary). Each returns a launch lambda; a file that cannot be
 * read or written goes to [onError] ("Cannot open X: …") instead of crashing the app.
 */

/** Opens a file picker; on success calls [onOpened] with the file name (with extension) and its bytes. */
@Composable
expect fun rememberFileOpener(
    onError: (message: String) -> Unit,
    onOpened: (name: String, bytes: ByteArray) -> Unit,
): () -> Unit

/**
 * Saves bytes under a suggested file name: a Save dialog on desktop, a download in the
 * browser. [onSaved] gets the file name, or null if cancelled or failed (after [onError]);
 * the browser cannot report whether the user kept the download.
 */
@Composable
expect fun rememberFileSaver(
    onError: (message: String) -> Unit,
    onSaved: (name: String?) -> Unit,
): (fileName: String, bytes: ByteArray) -> Unit
