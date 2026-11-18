package org.bytefred.pocoview.core

/** One bundled example point set (a file under composeApp/examples/, embedded at build time). */
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
    /** Ids of properties switched on when the example is loaded (e.g. "triangulations", off by default). */
    val properties: List<String> = emptyList(),
)

/** A category of examples as listed in composeApp/examples/index.json. */
data class ExampleGroup(val title: String, val examples: List<Example>)

/** A link shown with the examples (collections, databases). */
data class ExampleLink(val title: String, val url: String, val description: String?)

/** One file of the Graz order type database: all order types of [n] points, one set each. */
data class OrderTypeFile(val n: Int, val file: String, val sets: List<List<Pt>>)

/** Everything bundled: the categories of single sets, the order type files and the links. */
data class ExampleCatalog(
    val categories: List<ExampleGroup>,
    val links: List<ExampleLink>,
    val orderTypes: List<OrderTypeFile> = emptyList(),
)

/** All bundled examples, parsed at first use. */
val EXAMPLES: ExampleCatalog by lazy { loadExamples() }

/** Parses all bundled examples ([GeneratedExamples]), in the category and file order of the index. */
fun loadExamples(): ExampleCatalog {
    val index = parseJson(GeneratedExamples.INDEX).asObject("index.json")
    val categories = index.list("categories", "index.json").map { g ->
        val cat = g.asObject("index.json category")
        ExampleGroup(
            title = cat.string("title", "index.json category"),
            examples = cat.list("files", "index.json category").map { f ->
                val file = f as? String ?: throw FormatException("index.json: file names must be strings")
                val text = GeneratedExamples.FILES[file] ?: throw FormatException("index.json: $file is not bundled")
                parseExample(text, file)
            },
        )
    }
    val links = index.list("links", "index.json").map { l ->
        val o = l.asObject("index.json link")
        ExampleLink(o.string("title", "index.json link"), o.string("url", "index.json link"), o.optString("description", "index.json link"))
    }
    val orderTypes = index.list("orderTypes", "index.json").map { f ->
        val path = f as? String ?: throw FormatException("index.json: order type files must be strings")
        val hex = GeneratedExamples.BINARY_FILES[path] ?: throw FormatException("index.json: $path is not bundled")
        val name = path.substringAfterLast('/')
        val sets = readPointSets(name, ByteArray(hex.length / 2) { hex.substring(2 * it, 2 * it + 2).toInt(16).toByte() })
        OrderTypeFile(sets.first().size, name, sets)
    }
    return ExampleCatalog(categories, links, orderTypes)
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
        properties = (o["properties"] as? List<*> ?: emptyList<Any?>()).map { id ->
            id as? String ?: throw FormatException("$file: \"properties\" must be an array of property ids")
        },
    ).also {
        // the metadata must match the points (names follow n<N>-c<colors>-<description>)
        val n = o["n"] as? Long
        if (n != null && n != it.points.size.toLong()) throw FormatException("$file: \"n\" is $n, but there are ${it.points.size} points")
        for (id in it.properties) if (ALL_PROPERTIES.none { p -> p.id == id }) throw FormatException("$file: unknown property '$id'")
        if (!Regex("n${it.points.size}-c[0-9]+-[a-z0-9-]+").matches(it.name)) throw FormatException("$file: name '${it.name}' does not follow n<N>-c<colors>-<description>")
    }
}

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
