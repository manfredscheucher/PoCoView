package org.bytefred.pocoview.ui

import androidx.compose.runtime.Composable
import org.bytefred.pocoview.core.LOAD_EXTENSIONS
import java.io.File
import java.io.IOException
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

private var lastDir: File? = null

@Composable
actual fun rememberFileOpener(
    onError: (message: String) -> Unit,
    onOpened: (name: String, bytes: ByteArray) -> Unit,
): () -> Unit = {
    val chooser = JFileChooser(lastDir).apply {
        dialogTitle = "Open point set"
        // JSON is the default; the other point set formats are one filter away
        addChoosableFileFilter(FileNameExtensionFilter("JSON point set (*.json)", "json"))
        addChoosableFileFilter(FileNameExtensionFilter(
            "All point sets (${LOAD_EXTENSIONS.joinToString(", ") { "*.$it" }})", *LOAD_EXTENSIONS.toTypedArray(),
        ))
        fileFilter = choosableFileFilters.first { it.description.startsWith("JSON") }
    }
    if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
        val f = chooser.selectedFile
        lastDir = f.parentFile
        readFile(f, onError, onOpened)
    }
}

@Composable
actual fun rememberFileSaver(
    onError: (message: String) -> Unit,
    onSaved: (name: String?) -> Unit,
): (fileName: String, bytes: ByteArray) -> Unit = { fileName, bytes ->
    val chooser = JFileChooser(lastDir).apply {
        dialogTitle = "Save"
        selectedFile = File(lastDir, fileName)
    }
    if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
        var f = chooser.selectedFile
        val ext = fileName.substringAfterLast('.', "")
        if (ext.isNotEmpty() && !f.name.contains('.')) f = File(f.path + "." + ext)
        lastDir = f.parentFile
        writeFile(f, bytes, onError, onSaved)
    } else {
        onSaved(null)
    }
}

/** Reads [f] for [onOpened]; a read error goes to [onError]. */
internal fun readFile(f: File, onError: (String) -> Unit, onOpened: (name: String, bytes: ByteArray) -> Unit) {
    val bytes = try {
        f.readBytes()
    } catch (e: IOException) {
        onError("Cannot open ${f.name}: ${e.message ?: "read error"}")
        return
    }
    onOpened(f.name, bytes)
}

/** Writes [bytes] to [f] and reports its name, or a write error to [onError] and null (not saved). */
internal fun writeFile(f: File, bytes: ByteArray, onError: (String) -> Unit, onSaved: (name: String?) -> Unit) {
    try {
        f.writeBytes(bytes)
    } catch (e: IOException) {
        onError("Cannot save ${f.name}: ${e.message ?: "write error"}")
        onSaved(null)
        return
    }
    onSaved(f.name)
}
