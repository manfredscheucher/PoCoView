package org.bytefred.pocoview.ui

import androidx.compose.runtime.Composable
import org.bytefred.pocoview.core.LOAD_EXTENSIONS
import java.io.File
import java.io.IOException
import javax.swing.JFileChooser
import javax.swing.JOptionPane
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
    val f = chooseSaveTarget(
        fileName,
        choose = { if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null },
        confirmReplace = { existing ->
            chooser.selectedFile = existing
            JOptionPane.showConfirmDialog(null, replaceQuestion(existing), "Save", JOptionPane.YES_NO_OPTION) ==
                JOptionPane.YES_OPTION
        },
    )
    if (f != null) {
        lastDir = f.parentFile
        writeFile(f, bytes, onError, onSaved)
    } else {
        onSaved(null)
    }
}

/** The question before replacing [f]. */
internal fun replaceQuestion(f: File) = "${f.name} already exists. Replace it?"

/**
 * The file to save to: [choose] shows the chooser (null: cancelled). The suggested [fileName]'s
 * extension is added to a name without one. An existing file is taken only when [confirmReplace]
 * says yes; no shows the chooser again.
 */
internal fun chooseSaveTarget(fileName: String, choose: () -> File?, confirmReplace: (File) -> Boolean): File? {
    val ext = fileName.substringAfterLast('.', "")
    while (true) {
        var f = choose() ?: return null
        if (ext.isNotEmpty() && !f.name.contains('.')) f = File(f.path + "." + ext)
        if (!f.exists() || confirmReplace(f)) return f
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
