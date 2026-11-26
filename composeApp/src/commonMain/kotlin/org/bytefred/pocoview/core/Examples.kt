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

/**
 * A top-level node of the examples tree (composeApp/examples/index.json, "supergroups"): e.g. "Database" or
 * "Extremal point sets". [id] is unique among the supergroups. No links: they belong to the sets.
 */
data class ExampleSupergroup(
    val id: String,
    val title: String,
    val description: String?,
    val groups: List<ExampleGroup>,
)

/**
 * A group inside a supergroup: its bundled single sets ([examples], the .json files) and order type files
 * ([orderTypes], the other files). [id] is unique within its supergroup. No links: they belong to the sets.
 */
data class ExampleGroup(
    val id: String,
    val title: String,
    val description: String? = null,
    val examples: List<Example> = emptyList(),
    val orderTypes: List<OrderTypeFile> = emptyList(),
)

/** A link shown with the examples: a source of a set, or a general link (collections). */
data class ExampleLink(val title: String, val url: String, val description: String?)

/**
 * One file of the Graz order type database: all order types of [n] points, one set each. [links] are its
 * sources (from its entry in index.json, since the file itself is binary).
 */
data class OrderTypeFile(val n: Int, val file: String, val sets: List<List<Pt>>, val links: List<ExampleLink> = emptyList())

/** Everything bundled: the supergroups with their groups, and the general links. */
data class ExampleCatalog(val supergroups: List<ExampleSupergroup>, val links: List<ExampleLink>) {
    val groups: List<ExampleGroup> get() = supergroups.flatMap { it.groups }
    /** All bundled single sets, in index order. */
    val examples: List<Example> get() = groups.flatMap { it.examples }
    /** All bundled order type files, in index order. */
    val orderTypes: List<OrderTypeFile> get() = groups.flatMap { it.orderTypes }
}

/** All bundled examples, parsed at first use. */
val EXAMPLES: ExampleCatalog by lazy { loadExamples() }

/** Parses all bundled examples ([GeneratedExamples]), in the order of the index. */
fun loadExamples(): ExampleCatalog = parseIndex(GeneratedExamples.INDEX, GeneratedExamples.FILES, GeneratedExamples.BINARY_FILES)

/**
 * Parses index.json ([index]); [files] are the texts of the listed .json files, [binaryFiles] the other
 * listed files (order type files) as hex strings, both by their path in the index. A file entry is its
 * path, or for an order type file {"file": path, "links": [...]} (its sources). Links on supergroups and
 * groups are rejected: sources belong to the sets.
 */
fun parseIndex(index: String, files: Map<String, String>, binaryFiles: Map<String, String>): ExampleCatalog {
    val o = parseJson(index).asObject("index.json")
    val supergroups = o.list("supergroups", "index.json").map { s ->
        val sg = s.asObject("index.json supergroup").noLinks("index.json supergroup")
        val groups = sg.list("groups", "index.json supergroup").map { g ->
            val gr = g.asObject("index.json group").noLinks("index.json group")
            // path to the links of its entry (only order type files have any)
            val entries = gr.list("files", "index.json group").map { f ->
                when (f) {
                    is String -> f to emptyList()
                    is Map<*, *> -> {
                        val fo = f.asObject("index.json file")
                        val file = fo.string("file", "index.json file")
                        if (file.endsWith(".json") && "links" in fo) throw FormatException("index.json: the links of $file belong in the file")
                        file to links(fo, "index.json $file")
                    }
                    else -> throw FormatException("index.json: a file entry must be a path or {\"file\": path, \"links\": [...]}")
                }
            }
            ExampleGroup(
                id = gr.string("id", "index.json group"),
                title = gr.string("title", "index.json group"),
                description = gr.optString("description", "index.json group"),
                examples = entries.filter { it.first.endsWith(".json") }.map { (file, _) ->
                    parseExample(files[file] ?: throw FormatException("index.json: $file is not bundled"), file)
                },
                orderTypes = entries.filterNot { it.first.endsWith(".json") }.map { (file, links) ->
                    val hex = binaryFiles[file] ?: throw FormatException("index.json: $file is not bundled")
                    val name = file.substringAfterLast('/')
                    val sets = readPointSets(name, ByteArray(hex.length / 2) { hex.substring(2 * it, 2 * it + 2).toInt(16).toByte() })
                    OrderTypeFile(sets.first().size, name, sets, links)
                },
            )
        }
        val id = sg.string("id", "index.json supergroup")
        groups.groupBy { it.id }.forEach { (gid, l) -> if (l.size > 1) throw FormatException("index.json: group id '$gid' twice in '$id'") }
        ExampleSupergroup(id, sg.string("title", "index.json supergroup"), sg.optString("description", "index.json supergroup"), groups)
    }
    supergroups.groupBy { it.id }.forEach { (id, l) -> if (l.size > 1) throw FormatException("index.json: supergroup id '$id' twice") }
    return ExampleCatalog(supergroups, links(o, "index.json"))
}

/** [this], if it has no "links" (supergroups and groups: sources belong to the sets). */
private fun Map<String, Any?>.noLinks(file: String): Map<String, Any?> =
    if ("links" in this) throw FormatException("$file: no \"links\" here, they belong to the sets") else this

/** The optional "links" array of [o]. */
private fun links(o: Map<String, Any?>, file: String): List<ExampleLink> =
    (o["links"] as? List<*> ?: emptyList<Any?>()).map { l ->
        val lo = l.asObject("$file link")
        ExampleLink(lo.string("title", "$file link"), lo.string("url", "$file link"), lo.optString("description", "$file link"))
    }

/** Parses one example file: the JSON point format plus the metadata keys. */
fun parseExample(text: String, file: String): Example {
    val o = parseJson(text).asObject(file)
    return Example(
        name = o.string("name", file),
        description = o.string("description", file),
        group = o.string("group", file),
        reference = o.optString("reference", file),
        links = links(o, file),
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
