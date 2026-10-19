package org.bytefred.pocoview.core

/** Number of structures, and how many of them contain the selected point. */
data class Count(val total: Long, val incident: Long)

/**
 * Per-anchor setup for convex polygons: the anchor is the lowest vertex (by y, then x);
 * the other vertices are the points above it, sorted counter-clockwise around it.
 * In this order every chain anchor, v1, ..., v_{k-1} with left turns at all inner
 * vertices is a convex k-gon, and each convex k-gon is found exactly once.
 */
private class Fan(p: List<Pt>, val o: Orientations, val anchor: Int, withEmpty: Boolean) {
    val cand: IntArray
    /** empty[a * m + b] (a < b): triangle (anchor, cand[a], cand[b]) contains no point. */
    val empty: BooleanArray

    init {
        val a = p[anchor]
        cand = p.indices.filter { i ->
            i != anchor && (p[i].y > a.y || (p[i].y == a.y && p[i].x > a.x))
        }.sortedWith { i, j -> -o[anchor, i, j] }.toIntArray()
        val m = cand.size
        empty = BooleanArray(if (withEmpty) m * m else 0)
        if (withEmpty) for (u in 0 until m) for (v in u + 1 until m) {
            // only points angularly between u and v can lie inside the fan triangle;
            // they are inside iff they lie on the anchor's side of u->v
            var e = true
            for (w in u + 1 until v) {
                if (o[cand[u], cand[v], cand[w]] > 0) { e = false; break }
            }
            empty[u * m + v] = e
        }
    }

    fun left(u: Int, v: Int, w: Int) = o[cand[u], cand[v], cand[w]] > 0
}

/**
 * Counts convex k-gons (or k-holes if [emptyOnly]) of a point set in general position,
 * k >= 3, by dynamic programming over the last edge of the chain: O(k n^4) overall.
 * [selected]: if not null, also counts the polygons having that point as a vertex.
 */
suspend fun countConvexPolygons(
    p: List<Pt>, k: Int, emptyOnly: Boolean, selected: Int?, ticker: Ticker = Ticker(),
): Count {
    require(k >= 3)
    var total = 0L
    var incident = 0L
    val o = Orientations(p)
    for (anchor in p.indices) {
        val f = Fan(p, o, anchor, emptyOnly)
        val m = f.cand.size
        if (m < k - 1) continue
        ticker.tick(m.toLong() * m * m / 2)
        val sel = if (selected == null) -1 else f.cand.indexOf(selected)
        val anchorSel = selected == anchor
        // chains anchor, ..., cand[u], cand[v] with `level` vertices besides the anchor
        var tot = LongArray(m * m)
        var inc = LongArray(m * m)
        for (u in 0 until m) for (v in u + 1 until m) {
            if (emptyOnly && !f.empty[u * m + v]) continue
            tot[u * m + v] = 1
            if (anchorSel || u == sel || v == sel) inc[u * m + v] = 1
        }
        for (level in 3..k - 1) {
            val nt = LongArray(m * m)
            val ni = LongArray(m * m)
            for (u in 0 until m) for (v in u + 1 until m) {
                val t = tot[u * m + v]
                if (t == 0L) continue
                val i = inc[u * m + v]
                for (w in v + 1 until m) {
                    if (emptyOnly && !f.empty[v * m + w]) continue
                    if (!f.left(u, v, w)) continue
                    nt[v * m + w] += t
                    ni[v * m + w] += if (w == sel) t else i
                }
                ticker.tick(m.toLong())
            }
            tot = nt
            inc = ni
        }
        total += tot.sum()
        incident += inc.sum()
    }
    return Count(total, if (selected == null) 0 else incident)
}

/**
 * Lists convex k-gons (k-holes if [emptyOnly]) as vertex index arrays in CCW order,
 * at most [limit] of them. With [selected] set, only those having it as a vertex.
 */
suspend fun enumerateConvexPolygons(
    p: List<Pt>, k: Int, emptyOnly: Boolean, selected: Int?, limit: Int,
    ticker: Ticker = Ticker(),
): List<IntArray> {
    require(k >= 3)
    val res = ArrayList<IntArray>()
    val o = Orientations(p)
    for (anchor in p.indices) {
        if (res.size >= limit) break
        val f = Fan(p, o, anchor, emptyOnly)
        val m = f.cand.size
        if (m < k - 1) continue
        val sel = if (selected == null) -1 else f.cand.indexOf(selected)
        if (selected != null && selected != anchor && sel < 0) continue
        val chain = IntArray(k - 1)

        suspend fun dfs(len: Int, hasSel: Boolean) {
            if (res.size >= limit) return
            ticker.tick()
            if (len == k - 1) {
                if (selected == null || hasSel)
                    res.add(IntArray(k) { if (it == 0) anchor else f.cand[chain[it - 1]] })
                return
            }
            val last = chain[len - 1]
            for (w in last + 1 until m) {
                // not enough candidates left to complete the chain
                if (m - w < k - 1 - len) break
                // the selected point must still fit into the rest of the chain
                if (selected != null && !hasSel && sel < w) break
                if (emptyOnly && !f.empty[last * m + w]) continue
                if (len >= 2 && !f.left(chain[len - 2], last, w)) continue
                chain[len] = w
                dfs(len + 1, hasSel || w == sel)
            }
        }

        for (u in 0 until m) {
            if (selected != null && selected != anchor && sel < u) break
            chain[0] = u
            dfs(1, selected == anchor || u == sel)
        }
    }
    return res
}
