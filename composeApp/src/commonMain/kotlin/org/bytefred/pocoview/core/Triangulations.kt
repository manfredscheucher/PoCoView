package org.bytefred.pocoview.core

/**
 * Number of triangulations of a point set in general position (exponential; meant for
 * small n). Recursion on simple polygons: the triangle on the edge (b0, b1) of the
 * current polygon has a third vertex c that is either an interior point (the polygon
 * grows around c) or a polygon vertex (the polygon splits in two). Memoized on the
 * polygon, which determines its interior points.
 */
suspend fun countTriangulations(p: List<Pt>, ticker: Ticker = Ticker(20_000)): Long {
    if (p.size < 3) return if (p.isEmpty()) 0 else 1
    val hull = convexHull(p)
    return Triangulator(p, ticker).count(hull)
}

private class Triangulator(val p: List<Pt>, val ticker: Ticker) {
    private val memo = HashMap<String, Long>()

    private fun inside(poly: List<Int>, q: Pt): Boolean {
        var w = 0
        for (i in poly.indices) {
            val a = p[poly[i]]
            val b = p[poly[(i + 1) % poly.size]]
            if (a.y <= q.y && q.y < b.y && orient(a, b, q) > 0) w++
            else if (b.y <= q.y && q.y < a.y && orient(a, b, q) < 0) w--
        }
        return w != 0
    }

    private fun key(poly: List<Int>): String {
        val r = poly.indexOf(poly.min())
        return (poly.drop(r) + poly.take(r)).joinToString(",")
    }

    suspend fun count(poly: List<Int>): Long {
        if (poly.size < 3) return 1
        val k = key(poly)
        memo[k]?.let { return it }
        ticker.tick()
        val onPoly = poly.toSet()
        val interior = p.indices.filter { it !in onPoly && inside(poly, p[it]) }
        if (poly.size == 3 && interior.isEmpty()) { memo[k] = 1; return 1 }

        val b0 = poly[0]
        val b1 = poly[1]
        var total = 0L
        val candidates = poly.drop(2) + interior
        for (c in candidates) {
            if (orient(p[b0], p[b1], p[c]) <= 0) continue
            if (candidates.any { it != c && inTriangle(p[b0], p[b1], p[c], p[it]) }) continue
            if (crossesBoundary(poly, b1, c) || crossesBoundary(poly, c, b0)) continue
            val j = poly.indexOf(c)
            total += if (j < 0) {
                count(listOf(b0, c) + poly.drop(1))
            } else {
                count(poly.subList(1, j + 1)) * count(poly.subList(j, poly.size) + b0)
            }
        }
        memo[k] = total
        return total
    }

    private fun crossesBoundary(poly: List<Int>, u: Int, v: Int): Boolean {
        for (i in poly.indices) {
            val a = poly[i]
            val b = poly[(i + 1) % poly.size]
            if (segmentsCross(p[u], p[v], p[a], p[b])) return true
        }
        return false
    }
}
