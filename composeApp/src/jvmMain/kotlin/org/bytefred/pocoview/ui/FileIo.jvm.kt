package org.bytefred.pocoview.ui

import androidx.compose.runtime.Composable
import org.bytefred.pocoview.core.LOAD_EXTENSIONS
import java.io.File
import java.util.prefs.Preferences
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

private var lastDir: File? = null

@Composable
actual fun rememberFileOpener(onOpened: (name: String, bytes: ByteArray) -> Unit): () -> Unit = {
    val chooser = JFileChooser(lastDir).apply {
        dialogTitle = "Open point set"
        // JSON is the default; the other point set formats are one filter away
        addChoosableFileFilter(FileNameExtensionFilter("JSON point set (*.json)", "json"))
        addChoosableFileFilter(FileNameExtensionFilter("All point sets (*.json, *.txt, *.asc, *.psz, *.b08, *.b16, *.b32)", *LOAD_EXTENSIONS.toTypedArray()))
        fileFilter = choosableFileFilters.first { it.description.startsWith("JSON") }
    }
    if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
        val f = chooser.selectedFile
        lastDir = f.parentFile
        onOpened(f.name, f.readBytes())
    }
}

@Composable
actual fun rememberFileSaver(onSaved: (name: String?) -> Unit): (fileName: String, bytes: ByteArray) -> Unit = { fileName, bytes ->
    val chooser = JFileChooser(lastDir).apply {
        dialogTitle = "Save"
        selectedFile = File(lastDir, fileName)
    }
    if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
        var f = chooser.selectedFile
        val ext = fileName.substringAfterLast('.', "")
        if (ext.isNotEmpty() && !f.name.contains('.')) f = File(f.path + "." + ext)
        f.writeBytes(bytes)
        lastDir = f.parentFile
        onSaved(f.name)
    } else {
        onSaved(null)
    }
}

private val prefs: Preferences? = try { Preferences.userRoot().node("org/bytefred/pocoview") } catch (e: Exception) { null }

actual fun loadPref(key: String): String? = try { prefs?.get(key, null) } catch (e: Exception) { null }

actual fun savePref(key: String, value: String) {
    try { prefs?.put(key, value); prefs?.flush() } catch (e: Exception) { }
}
