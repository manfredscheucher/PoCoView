package org.bytefred.pocoview.ui

import org.bytefred.pocoview.core.LOAD_EXTENSIONS
import androidx.compose.runtime.Composable
import kotlinx.browser.document
import kotlinx.browser.window
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array
import org.w3c.dom.HTMLAnchorElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.url.URL
import org.w3c.files.Blob
import org.w3c.files.BlobPropertyBag
import org.w3c.files.FileReader
import org.w3c.files.get

// Browser: open via a hidden <input type=file> + FileReader, save via a Blob download.

@Composable
actual fun rememberFileOpener(
    onError: (message: String) -> Unit,
    onOpened: (name: String, bytes: ByteArray) -> Unit,
): () -> Unit = {
    val input = document.createElement("input") as HTMLInputElement
    input.type = "file"
    input.accept = LOAD_EXTENSIONS.joinToString(",") { ".$it" }
    input.onchange = {
        val file = input.files?.get(0)
        if (file != null) {
            val reader = FileReader()
            reader.onload = {
                val buf = reader.result as ArrayBuffer
                onOpened(file.name, Int8Array(buf).unsafeCast<ByteArray>())
            }
            reader.onerror = { onError("Cannot open ${file.name}: ${reader.error?.message ?: "read error"}") }
            reader.readAsArrayBuffer(file)
        }
    }
    input.click()
}

@Composable
actual fun rememberFileSaver(
    onError: (message: String) -> Unit,
    onSaved: (name: String?) -> Unit,
): (fileName: String, bytes: ByteArray) -> Unit = { fileName, bytes ->
    val saved = try {
        val blob = Blob(arrayOf(bytes.unsafeCast<Int8Array>()), BlobPropertyBag(type = "application/octet-stream"))
        val url = URL.createObjectURL(blob)
        val a = document.createElement("a") as HTMLAnchorElement
        a.href = url
        a.download = fileName
        a.click()
        // revoking right away can abort the download in some browsers
        window.setTimeout({ URL.revokeObjectURL(url) }, 10_000)
        true
    } catch (e: Throwable) {
        onError("Cannot save $fileName: ${e.message ?: "download failed"}")
        false
    }
    onSaved(if (saved) fileName else null)
}
