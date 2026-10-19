package org.bytefred.ordertypes.ui

import androidx.compose.runtime.Composable
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

private var lastDir: File? = null

@Composable
actual fun rememberFileOpener(onOpened: (name: String, bytes: ByteArray) -> Unit): () -> Unit = {
    val chooser = JFileChooser(lastDir).apply {
        dialogTitle = "Open point set"
        fileFilter = FileNameExtensionFilter(
            "Point sets (*.asc, *.psz, *.txt, *.json, *.b08, *.b16, *.b32)",
            "asc", "psz", "txt", "json", "b08", "b16", "b32", "pb08", "pb16", "pb32",
        )
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
