package org.bytefred.ordertypes.core

/**
 * Convex hull of the points with the given indices, as indices in counter-clockwise
 * order (Andrew's monotone chain, exact). Collinear boundary points are dropped.
 */
fun convexHull(p: List<Pt>, idx: List<Int> = p.indices.toList()): List<Int> {
    val s = idx.sortedWith(compareBy({ p[it].x }, { p[it].y }))
    if (s.size <= 2) return s
    val lower = ArrayList<Int>()
    for (i in s) {
        while (lower.size >= 2 && orient(p[lower[lower.size - 2]], p[lower.last()], p[i]) <= 0)
            lower.removeAt(lower.size - 1)
        lower.add(i)
    }
    val upper = ArrayList<Int>()
    for (i in s.reversed()) {
        while (upper.size >= 2 && orient(p[upper[upper.size - 2]], p[upper.last()], p[i]) <= 0)
            upper.removeAt(upper.size - 1)
        upper.add(i)
    }
    lower.removeAt(lower.size - 1)
    upper.removeAt(upper.size - 1)
    return lower + upper
}

/** Onion layers (convex layers), outermost first; each layer CCW as point indices. */
fun onionLayers(p: List<Pt>): List<List<Int>> {
    val layers = ArrayList<List<Int>>()
    var rest = p.indices.toList()
    while (rest.isNotEmpty()) {
        val layer = convexHull(p, rest)
        layers.add(layer)
        val hs = layer.toSet()
        rest = rest.filter { it !in hs }
    }
    return layers
}
