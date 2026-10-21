package org.bytefred.pocoview.core

import org.bytefred.pocoview.resources.Res
import org.jetbrains.compose.resources.ExperimentalResourceApi

/** One bundled example point set (a file under composeResources/files/examples/). */
data class Example(
    val name: String,
    val description: String,
    val group: String,
    val reference: String?,
    val url: String?,
    val points: List<Pt>,
)

/** A group of examples as listed in files/examples/index.json. */
data class ExampleGroup(val title: String, val note: String?, val examples: List<Example>)

private const val EXAMPLES_DIR = "files/examples/"

/** Loads all bundled examples, in the group and file order of the index. */
suspend fun loadExamples(): List<ExampleGroup> {
    val index = parseJson(readExampleFile("index.json")).asObject("index.json")
    return index.list("groups", "index.json").map { g ->
        val group = g.asObject("index.json group")
        ExampleGroup(
            title = group.string("title", "index.json group"),
            note = group.optString("note", "index.json group"),
            examples = group.list("files", "index.json group").map { f ->
                val file = f as? String ?: throw FormatException("index.json: file names must be strings")
                parseExample(readExampleFile(file), file)
            },
        )
    }
}

/** Parses one example file: the JSON point format plus the metadata keys. */
fun parseExample(text: String, file: String): Example {
    val o = parseJson(text).asObject(file)
    return Example(
        name = o.string("name", file),
        description = o.string("description", file),
        group = o.string("group", file),
        reference = o.optString("reference", file),
        url = o.optString("url", file),
        points = o.list("points", file).map { point(it, file) },
    )
}

@OptIn(ExperimentalResourceApi::class)
private suspend fun readExampleFile(file: String): String = Res.readBytes(EXAMPLES_DIR + file).decodeToString()

private fun point(v: Any?, file: String): Pt {
    val c = v as? List<*>
    val ok = c != null && (c.size == 2 || (c.size == 3 && c[2] is String)) && c[0] is Long && c[1] is Long
    if (!ok) throw FormatException("$file: expected a point [x, y] or [x, y, \"color\"], got $v")
    return Pt(c[0] as Long, c[1] as Long, c.getOrNull(2) as String?)
}

private fun Any?.asObject(file: String): Map<String, Any?> {
    if (this !is Map<*, *>) throw FormatException("$file: expected a JSON object")
    @Suppress("UNCHECKED_CAST")
    return this as Map<String, Any?>
}

private fun Map<String, Any?>.string(key: String, file: String): String =
    this[key] as? String ?: throw FormatException("$file: \"$key\" must be a string")

private fun Map<String, Any?>.optString(key: String, file: String): String? = when (val v = this[key]) {
    null -> null
    is String -> v
    else -> throw FormatException("$file: \"$key\" must be a string or null")
}

private fun Map<String, Any?>.list(key: String, file: String): List<Any?> =
    this[key] as? List<Any?> ?: throw FormatException("$file: \"$key\" must be an array")
