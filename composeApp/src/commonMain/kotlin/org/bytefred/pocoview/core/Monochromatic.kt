package org.bytefred.pocoview.core

/*
 * Monochromatic holes and islands of coloured point sets, ported from the old pyotlib
 * (old/pyotlib/pyotlib/PolygonCount.py, GeneralHoles.py):
 *
 * - convex k-hole (countChromaticHoles): convex k-gon, all vertices of the same colour,
 *   no point of S (of any colour) inside.
 * - k-island (countChromaticIslands): subset I, |I| = k, with conv(I) ∩ S = I; monochromatic
 *   if all points of I (hull vertices and inner points) have the same colour.
 * - general k-hole, k = 4, 5 (countChromaticGeneralHoles): simple, not necessarily convex
 *   polygon with k vertices of the same colour and no point of S inside; counted as polygons
 *   (a non-convex 4-point set can give up to three of them).
 *
 * Colours are compared as tokens (Pt.color); every point must have one.
 */

/** Why the monochromatic properties cannot be evaluated on [p], or null. */
fun monochromaticSkipReason(p: List<Pt>): String? =
    if (p.any { it.color == null }) "needs a coloured set" else null

private fun requireColoured(p: List<Pt>) {
    monochromaticSkipReason(p)?.let { throw IllegalArgumentException(it) }
}

/**
 * Per-anchor fan as in [countConvexPolygons]: the anchor is the lowest vertex, the other
 * points above it sorted counter-clockwise. Triangle (anchor, cand[u], cand[v]), u < v,
 * contains exactly the cand[w], u < w < v, on the anchor's side of u->v.
 */
private class MonoFan(p: List<Pt>, val o: Orientations, val anchor: Int, selected: Int?) {
    val cand: IntArray
    /** indices into [cand] of the points with the anchor's colour */
    val same: IntArray
    /** inner[u * m + v]: number of points inside triangle (anchor, cand[u], cand[v]) */
    val inner: IntArray
    /** mono[u * m + v]: all those points have the anchor's colour */
    val mono: BooleanArray
    /** selIn[u * m + v]: the selected point lies inside that triangle */
    val selIn: BooleanArray

    init {
        val a = p[anchor]
        cand = p.indices.filter { i ->
            i != anchor && (p[i].y > a.y || (p[i].y == a.y && p[i].x > a.x))
        }.sortedWith { i, j -> -o[anchor, i, j] }.toIntArray()
        val m = cand.size
        same = (0 until m).filter { p[cand[it]].color == a.color }.toIntArray()
        inner = IntArray(m * m)
        mono = BooleanArray(m * m)
        selIn = BooleanArray(m * m)
        for (u in 0 until m) for (v in u + 1 until m) {
            var all = 0
            var other = false
            var s = false
            for (w in u + 1 until v) {
                if (o[cand[u], cand[v], cand[w]] > 0) {
                    all++
                    if (p[cand[w]].color != a.color) other = true
                    if (cand[w] == selected) s = true
                }
            }
            inner[u * m + v] = all
            mono[u * m + v] = !other
            selIn[u * m + v] = s
        }
    }

    fun left(u: Int, v: Int, w: Int) = o[cand[u], cand[v], cand[w]] > 0
}

/**
 * Counts monochromatic k-islands, or monochromatic convex k-holes if [holesOnly], k >= 3, by
 * dynamic programming over the last hull edge and the number of island points so far:
 * O(k n^4). [selected]: also counts those containing that point (as a vertex, or for
 * islands also as an inner point).
 */
suspend fun countMonochromatic(
    p: List<Pt>, k: Int, holesOnly: Boolean, selected: Int?, ticker: Ticker = Ticker(),
): Count {
    require(k >= 3)
    requireColoured(p)
    var total = 0L
    var incident = 0L
    val o = Orientations(p)
    for (anchor in p.indices) {
        val f = MonoFan(p, o, anchor, selected)
        val m = f.cand.size
        ticker.tick(m.toLong() * m * m / 6)
        if (f.same.size < 2) continue
        val sel = if (selected == null) -1 else f.cand.indexOf(selected)
        val anchorSel = selected == anchor
        fun allowed(uv: Int) = if (holesOnly) f.inner[uv] == 0 else f.mono[uv]
        // tot[t][u * m + v]: chains anchor, ..., cand[u], cand[v] whose fan triangles cover t points
        val tot = Array(k + 1) { LongArray(if (it >= 3) m * m else 0) }
        val inc = Array(k + 1) { LongArray(if (it >= 3) m * m else 0) }
        for (u in f.same) for (v in f.same) {
            if (v <= u) continue
            val uv = u * m + v
            if (!allowed(uv)) continue
            val t = 3 + f.inner[uv]
            if (t > k) continue
            tot[t][uv] += 1
            if (anchorSel || u == sel || v == sel || f.selIn[uv]) inc[t][uv] += 1
        }
        for (t in 3 until k) {
            val tt = tot[t]
            val ti = inc[t]
            for (u in f.same) for (v in f.same) {
                if (v <= u) continue
                val c = tt[u * m + v]
                if (c == 0L) continue
                val i = ti[u * m + v]
                for (w in f.same) {
                    if (w <= v) continue
                    val vw = v * m + w
                    if (!allowed(vw) || !f.left(u, v, w)) continue
                    val t2 = t + 1 + f.inner[vw]
                    if (t2 > k) continue
                    tot[t2][vw] += c
                    inc[t2][vw] += if (w == sel || f.selIn[vw]) c else i
                }
                ticker.tick(m.toLong())
            }
        }
        total += tot[k].sum()
        incident += inc[k].sum()
    }
    return Count(total, if (selected == null) 0 else incident)
}

/**
 * Lists monochromatic k-islands (convex k-holes if [holesOnly]) as their hull vertices in CCW
 * order, at most [limit] of them; with [selected] only those containing it.
 */
suspend fun enumerateMonochromatic(
    p: List<Pt>, k: Int, holesOnly: Boolean, selected: Int?, limit: Int, ticker: Ticker = Ticker(),
): List<IntArray> {
    require(k >= 3)
    requireColoured(p)
    val res = ArrayList<IntArray>()
    val o = Orientations(p)
    for (anchor in p.indices) {
        if (res.size >= limit) break
        if (selected != null && selected != anchor && p[selected].y < p[anchor].y) continue
        val f = MonoFan(p, o, anchor, selected)
        val m = f.cand.size
        if (f.same.size < 2) continue
        val sel = if (selected == null) -1 else f.cand.indexOf(selected)
        if (selected != null && selected != anchor && sel < 0) continue
        fun allowed(uv: Int) = if (holesOnly) f.inner[uv] == 0 else f.mono[uv]
        val chain = IntArray(k - 1)

        suspend fun dfs(len: Int, t: Int, hasSel: Boolean) {
            if (res.size >= limit) return
            ticker.tick()
            if (t == k) {
                if (selected == null || hasSel)
                    res.add(IntArray(len + 1) { if (it == 0) anchor else f.cand[chain[it - 1]] })
                return
            }
            val last = chain[len - 1]
            for (w in f.same) {
                if (w <= last) continue
                val lw = last * m + w
                if (!allowed(lw)) continue
                if (len >= 2 && !f.left(chain[len - 2], last, w)) continue
                val t2 = t + 1 + f.inner[lw]
                if (t2 > k) continue
                chain[len] = w
                dfs(len + 1, t2, hasSel || w == sel || f.selIn[lw])
            }
        }

        for (u in f.same) {
            chain[0] = u
            // anchor and cand[u]: no fan triangle yet
            dfs(1, 2, selected == anchor || u == sel)
        }
    }
    return res
}

/** Rotates [poly] so that its smallest index comes first (pyotlib `_poly_lexmin`) and encodes it. */
private fun lexminKey(poly: IntArray, n: Int): Long {
    var start = 0
    for (i in poly.indices) if (poly[i] < poly[start]) start = i
    var key = 0L
    for (i in poly.indices) key = key * n + poly[(start + i) % poly.size]
    return key
}

private fun lexmin(poly: IntArray): IntArray {
    var start = 0
    for (i in poly.indices) if (poly[i] < poly[start]) start = i
    return IntArray(poly.size) { poly[(start + it) % poly.size] }
}

/**
 * Monochromatic general (simple, not necessarily convex) k-holes, k = 4 or 5, as CCW vertex
 * cycles, literally as pyotlib GeneralHoles.empty_quads / empty_pents with monochrom=True:
 * 4-holes are two empty monochromatic triangles sharing an edge; 5-holes a 4-hole plus an
 * empty monochromatic triangle on one of its edges (with pyotlib's two reflex-vertex checks).
 */
suspend fun monochromaticGeneralHoles(p: List<Pt>, k: Int, ticker: Ticker = Ticker()): List<IntArray> {
    require(k == 4 || k == 5)
    requireColoured(p)
    val n = p.size
    val o = Orientations(p)
    // empty monochromatic CCW triangles, from the fan of their lowest vertex
    val tris = ArrayList<IntArray>()
    for (anchor in p.indices) {
        val f = MonoFan(p, o, anchor, null)
        val m = f.cand.size
        ticker.tick(m.toLong() * m * m / 6)
        for (u in f.same) for (v in f.same) {
            if (v > u && f.inner[u * m + v] == 0) tris.add(intArrayOf(anchor, f.cand[u], f.cand[v]))
        }
    }
    // directed edge a->b  ->  triangles having it
    val byEdge = HashMap<Int, MutableList<IntArray>>()
    for (t in tris) for (i in 0 until 3) byEdge.getOrPut(t[i] * n + t[(i + 1) % 3]) { ArrayList() }.add(t)
    fun third(t: IntArray, a: Int, b: Int) = t.first { it != a && it != b }

    val quadKeys = HashSet<Long>()
    val quads = ArrayList<IntArray>()
    for (t1 in tris) for (i in 0 until 3) {
        val b = t1[i]
        val d = t1[(i + 1) % 3]
        val a = third(t1, b, d)
        for (t2 in byEdge[d * n + b] ?: continue) {
            val quad = lexmin(intArrayOf(a, b, third(t2, b, d), d))
            if (quadKeys.add(lexminKey(quad, n))) quads.add(quad)
        }
        ticker.tick()
    }
    if (k == 4) return quads

    val pentKeys = HashSet<Long>()
    val pents = ArrayList<IntArray>()
    for (q in quads) for (i in 0 until 4) {
        // quad edge a->b, triangle edge b->a
        val a = q[i]
        val bi = (i + 1) % 4
        val b = q[bi]
        val c = q[(bi + 1) % 4]
        val d = q[(bi + 2) % 4]
        for (t in byEdge[b * n + a] ?: continue) {
            val x = third(t, a, b)
            if (x == c || x == d) continue
            if (o[a, b, c] < 0 && o[b, c, x] >= 0) continue
            if (o[d, a, b] < 0 && o[x, d, a] >= 0) continue
            val pent = lexmin(intArrayOf(b, c, d, a, x))
            if (pentKeys.add(lexminKey(pent, n))) pents.add(pent)
        }
        ticker.tick()
    }
    return pents
}
