package org.bytefred.pocoview.core

import org.bytefred.pocoview.resources.Res
import org.jetbrains.compose.resources.ExperimentalResourceApi

/** One bundled example point set (a file under composeResources/files/examples/). */
data class Example(
    val name: String,
    val description: String,
    val group: String,
    val reference: String?,
    /** Direct links: paper, thesis, the set's PoSeZo entry, … */
    val links: List<ExampleLink>,
    val points: List<Pt>,
    /** Optional remark, e.g. "same order type as …". */
    val note: String? = null,
)

/** A category of examples as listed in files/examples/index.json. */
data class ExampleGroup(val title: String, val examples: List<Example>)

/** A link shown with the examples (collections, databases). */
data class ExampleLink(val title: String, val url: String, val description: String?, val papers: List<ExampleLink> = emptyList())

/** Everything bundled: the categories of single sets and the links. */
data class ExampleCatalog(val categories: List<ExampleGroup>, val links: List<ExampleLink>)

private const val EXAMPLES_DIR = "files/examples/"

/** Loads all bundled examples, in the category and file order of the index. */
suspend fun loadExamples(): ExampleCatalog {
    val index = parseJson(readExampleFile("index.json")).asObject("index.json")
    val categories = index.list("categories", "index.json").map { g ->
        val cat = g.asObject("index.json category")
        ExampleGroup(
            title = cat.string("title", "index.json category"),
            examples = cat.list("files", "index.json category").map { f ->
                val file = f as? String ?: throw FormatException("index.json: file names must be strings")
                parseExample(readExampleFile(file), file)
            },
        )
    }
    val links = index.list("links", "index.json").map { l ->
        val o = l.asObject("index.json link")
        val papers = (o["papers"] as? List<*> ?: emptyList<Any?>()).map { q ->
            val po = q.asObject("index.json paper")
            ExampleLink(po.string("title", "index.json paper"), po.string("url", "index.json paper"), null)
        }
        ExampleLink(o.string("title", "index.json link"), o.string("url", "index.json link"), o.optString("description", "index.json link"), papers)
    }
    return ExampleCatalog(categories, links)
}

/** Parses one example file: the JSON point format plus the metadata keys. */
fun parseExample(text: String, file: String): Example {
    val o = parseJson(text).asObject(file)
    return Example(
        name = o.string("name", file),
        description = o.string("description", file),
        group = o.string("group", file),
        reference = o.optString("reference", file),
        links = (o["links"] as? List<*> ?: emptyList<Any?>()).map { l ->
            val lo = l.asObject("$file link")
            ExampleLink(lo.string("title", "$file link"), lo.string("url", "$file link"), lo.optString("description", "$file link"))
        },
        points = o.list("points", file).map { point(it, file) },
        note = o.optString("note", file),
    ).also {
        // the metadata must match the points (names follow n<N>-c<colors>-<description>)
        val n = o["n"] as? Long
        if (n != null && n != it.points.size.toLong()) throw FormatException("$file: \"n\" is $n, but there are ${it.points.size} points")
        if (!Regex("n${it.points.size}-c[0-9]+-[a-z0-9-]+").matches(it.name)) throw FormatException("$file: name '${it.name}' does not follow n<N>-c<colors>-<description>")
    }
}

@OptIn(ExperimentalResourceApi::class)
private suspend fun readExampleFile(file: String): String = Res.readBytes(EXAMPLES_DIR + file).decodeToString()

private fun point(v: Any?, file: String): Pt {
    val c = v as? List<*>
    val ok = c != null && (c.size == 2 || (c.size == 3 && c[2] is String)) && c[0] is Long && c[1] is Long
    if (!ok || (c!!.getOrNull(2) as String?)?.let { !isColorToken(it) } == true)
        throw FormatException("$file: expected a point [x, y] or [x, y, \"color\"] (colour one word), got $v")
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
