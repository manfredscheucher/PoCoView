package org.bytefred.ordertypes.core

/** How a structure is drawn: a filled polygon (closed vertex cycle) or line segments (vertex pairs). */
enum class Shape { POLYGON, SEGMENTS }

/** One structure of a property, as point indices. */
class Structure(val vertices: IntArray, val shape: Shape = Shape.POLYGON)

/** Result of evaluating a property. [incident] is set iff a point was selected. */
data class PropertyResult(
    val count: Long,
    val incident: Long? = null,
    val structures: List<Structure>? = null,
    /** true if [structures] was cut off at the drawing limit */
    val truncated: Boolean = false,
)

/**
 * A point-set property. All properties here are order-type invariants, so results can
 * be cached on the order type signature. [maxN]: refuse to compute above this size
 * (exponential algorithms).
 *
 * [evaluate]: with [selected] set, the result also counts the structures containing that
 * point; [onlyIncident] decides whether the returned structures are only those (incident
 * mode "only") or all of them (incident mode "highlight").
 */
abstract class PropertyDef(val id: String, val label: String, val hasStructures: Boolean, val maxN: Int = 1000) {
    abstract suspend fun evaluate(
        p: List<Pt>, selected: Int?, withStructures: Boolean, ticker: Ticker, onlyIncident: Boolean = true,
    ): PropertyResult
}

/** Max number of structures drawn per property. */
const val STRUCTURE_LIMIT = 3000

class HullProperty : PropertyDef("hull", "Convex hull (size)", true) {
    override suspend fun evaluate(
        p: List<Pt>, selected: Int?, withStructures: Boolean, ticker: Ticker, onlyIncident: Boolean,
    ): PropertyResult {
        val h = convexHull(p)
        return PropertyResult(
            count = h.size.toLong(),
            incident = selected?.let { if (it in h) 1L else 0L },
            structures = listOf(Structure(h.toIntArray())),
        )
    }
}

class OnionProperty : PropertyDef("onion", "Onion layers", true) {
    override suspend fun evaluate(
        p: List<Pt>, selected: Int?, withStructures: Boolean, ticker: Ticker, onlyIncident: Boolean,
    ): PropertyResult {
        val layers = onionLayers(p)
        val shown = layers.filter { selected == null || !onlyIncident || selected in it }
        return PropertyResult(
            count = layers.size.toLong(),
            // the (1-based) layer of the selected point
            incident = selected?.let { s -> (layers.indexOfFirst { s in it } + 1).toLong() },
            structures = shown.map { Structure(it.toIntArray()) },
        )
    }
}

/** Convex k-gons, or k-holes (empty convex k-gons) if [emptyOnly]. */
class PolygonProperty(val k: Int, val emptyOnly: Boolean) : PropertyDef(
    id = (if (emptyOnly) "hole" else "gon") + k,
    label = if (emptyOnly) "$k-holes" else "Convex $k-gons",
    hasStructures = true,
) {
    override suspend fun evaluate(
        p: List<Pt>, selected: Int?, withStructures: Boolean, ticker: Ticker, onlyIncident: Boolean,
    ): PropertyResult {
        val c = countConvexPolygons(p, k, emptyOnly, selected, ticker)
        if (!withStructures) return PropertyResult(c.total, selected?.let { c.incident })
        val filter = selected.takeIf { onlyIncident }
        val list = enumerateConvexPolygons(p, k, emptyOnly, filter, STRUCTURE_LIMIT, ticker)
        val expected = if (filter == null) c.total else c.incident
        return PropertyResult(
            c.total, selected?.let { c.incident },
            list.map { Structure(it) }, truncated = list.size < expected,
        )
    }
}

/** Crossing number of the complete geometric graph = number of convex 4-gons; drawn as crossing diagonals. */
class CrossingProperty : PropertyDef("crossings", "Crossing number", true) {
    override suspend fun evaluate(
        p: List<Pt>, selected: Int?, withStructures: Boolean, ticker: Ticker, onlyIncident: Boolean,
    ): PropertyResult {
        val c = countConvexPolygons(p, 4, false, selected, ticker)
        if (!withStructures) return PropertyResult(c.total, selected?.let { c.incident })
        val filter = selected.takeIf { onlyIncident }
        val list = enumerateConvexPolygons(p, 4, false, filter, STRUCTURE_LIMIT, ticker)
        val expected = if (filter == null) c.total else c.incident
        return PropertyResult(
            c.total, selected?.let { c.incident },
            list.map { q -> Structure(intArrayOf(q[0], q[2], q[1], q[3]), Shape.SEGMENTS) },
            truncated = list.size < expected,
        )
    }
}

class TriangulationProperty : PropertyDef("triangulations", "Triangulations", false, maxN = 16) {
    override suspend fun evaluate(
        p: List<Pt>, selected: Int?, withStructures: Boolean, ticker: Ticker, onlyIncident: Boolean,
    ) =
        PropertyResult(countTriangulations(p, ticker))
}

/** All properties offered in the GUI, in display order. */
val ALL_PROPERTIES: List<PropertyDef> = buildList {
    add(HullProperty())
    add(OnionProperty())
    add(CrossingProperty())
    for (k in 3..7) add(PolygonProperty(k, emptyOnly = true))
    for (k in 4..7) add(PolygonProperty(k, emptyOnly = false))
    add(TriangulationProperty())
}
