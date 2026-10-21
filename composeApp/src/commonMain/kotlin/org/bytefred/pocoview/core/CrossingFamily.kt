package org.bytefred.pocoview.core

/**
 * Crossing families (Aichholzer, Kynčl, Scheucher, Vogtenhuber, Valtr, On crossing-families in
 * planar point sets, Comput. Geom. 107 (2022) 101899, arXiv:2109.10705): a k-crossing family is a
 * set of k segments spanned by points of S that pairwise cross in their interior.
 *
 * Computed exactly as a maximum clique of the crossing graph of all n(n-1)/2 segments, with the
 * bitset branch and bound of San Segundo et al. (BBMC): greedy colouring (with Re-NUMBER
 * recolouring) gives the upper bound, vertices that cannot beat the incumbent are not branched on.
 * Extra bounds from the geometry: the segments of a family have distinct endpoints (at most n/2
 * segments, and at most half the endpoints of the candidates). A greedy clique from the
 * highest-core segments gives the initial lower bound.
 */

/** Segments as point index pairs. [incident] is set iff a point was selected. */
class CrossingFamilies(val maximum: List<Pair<Int, Int>>, val incident: List<Pair<Int, Int>>?)

/**
 * A maximum crossing family of [p] (general position), and with [selected] set also a maximum
 * family among those containing a segment incident to that point.
 */
suspend fun crossingFamilies(p: List<Pt>, selected: Int?, ticker: Ticker = Ticker()): CrossingFamilies {
    val g = crossingGraph(p, ticker)
    val max = CliqueSearch(g, ticker).also { it.run(null) }.best.map { g.segment(it) }
    val inc = when {
        selected == null -> null
        max.any { it.first == selected || it.second == selected } -> max
        else -> CliqueSearch(g, ticker).also { it.run(g.incidentTo(selected)) }.best.map { g.segment(it) }
    }
    return CrossingFamilies(max, inc)
}

/**
 * Crossing graph of the segments; vertex v is segment (a[v], b[v]), vertices are numbered
 * high-core first (reversed degeneracy order), which makes the greedy colouring bound tight.
 * [adj] holds the neighbours of v as a bitset of [w] words at v * w.
 */
private class CrossingGraph(val n: Int, val m: Int, val w: Int, val adj: IntArray, val a: IntArray, val b: IntArray) {
    fun segment(v: Int) = Pair(a[v], b[v])

    fun incidentTo(point: Int): List<Int> = (0 until m).filter { a[it] == point || b[it] == point }
}

private suspend fun crossingGraph(p: List<Pt>, ticker: Ticker): CrossingGraph {
    val n = p.size
    val o = Orientations(p)
    val sa = ArrayList<Int>()
    val sb = ArrayList<Int>()
    for (i in 0 until n) for (j in i + 1 until n) { sa.add(i); sb.add(j) }
    val m = sa.size
    val nb = Array(m) { ArrayList<Int>() }
    for (s in 0 until m) {
        val i = sa[s]
        val j = sb[s]
        for (t in s + 1 until m) {
            val k = sa[t]
            val l = sb[t]
            if (k == i || k == j || l == i || l == j) continue
            if (o[i, j, k] * o[i, j, l] < 0 && o[k, l, i] * o[k, l, j] < 0) {
                nb[s].add(t)
                nb[t].add(s)
            }
        }
        ticker.tick(m.toLong())
    }
    // degeneracy order: repeatedly remove a vertex of minimum degree; it goes to the end
    val deg = IntArray(m) { nb[it].size }
    val removed = BooleanArray(m)
    val order = IntArray(m)
    for (pos in m - 1 downTo 0) {
        var v = -1
        for (u in 0 until m) if (!removed[u] && (v < 0 || deg[u] < deg[v])) v = u
        removed[v] = true
        order[pos] = v
        for (u in nb[v]) deg[u]--
        ticker.tick(m.toLong())
    }
    val index = IntArray(m)
    for (pos in 0 until m) index[order[pos]] = pos
    val w = (m + 31) / 32
    val adj = IntArray(m * w)
    for (s in 0 until m) {
        val v = index[s]
        for (t in nb[s]) {
            val u = index[t]
            adj[v * w + (u ushr 5)] = adj[v * w + (u ushr 5)] or (1 shl (u and 31))
        }
    }
    return CrossingGraph(n, m, w, adj, IntArray(m) { sa[order[it]] }, IntArray(m) { sb[order[it]] })
}

private class CliqueSearch(val g: CrossingGraph, val ticker: Ticker) {
    var best = IntArray(0)
    private val cur = IntArray(g.m + 1)
    private val w = g.w
    private val mark = IntArray(g.n)
    private var stamp = 0

    /**
     * The segments of a crossing family have pairwise distinct endpoints: a clique within [p]
     * has at most (number of endpoints of the segments in p) / 2 vertices.
     */
    private fun pointBound(p: IntArray): Int {
        stamp++
        var pts = 0
        for (wi in 0 until w) {
            var x = p[wi]
            while (x != 0) {
                val v = (wi shl 5) or x.countTrailingZeroBits()
                x = x and (x - 1)
                if (mark[g.a[v]] != stamp) { mark[g.a[v]] = stamp; pts++ }
                if (mark[g.b[v]] != stamp) { mark[g.b[v]] = stamp; pts++ }
            }
        }
        return pts / 2
    }

    /** [roots] = null: all vertices; otherwise the clique must contain one of them. */
    suspend fun run(roots: List<Int>?) {
        // segments sharing an endpoint never cross, so a family contains at most one root
        val starts = roots ?: (0 until minOf(g.m, 2 * g.n)).toList()
        for (r in starts) greedy(r)
        if (roots == null) {
            val all = IntArray(w)
            for (v in 0 until g.m) all[v ushr 5] = all[v ushr 5] or (1 shl (v and 31))
            if (g.m > 0) expand(all, 0)
            return
        }
        for (r in roots) {
            cur[0] = r
            val p = IntArray(w) { g.adj[r * w + it] }
            if (!isEmpty(p)) expand(p, 1)
        }
    }

    /** Initial lower bound: a clique from [start], always adding the candidate with most candidate neighbours. */
    private suspend fun greedy(start: Int) {
        val clique = arrayListOf(start)
        val p = IntArray(w) { g.adj[start * w + it] }
        while (true) {
            var bestV = -1
            var bestDeg = -1
            for (wi in 0 until w) {
                var x = p[wi]
                while (x != 0) {
                    val v = (wi shl 5) or x.countTrailingZeroBits()
                    x = x and (x - 1)
                    var d = 0
                    for (y in 0 until w) d += (p[y] and g.adj[v * w + y]).countOneBits()
                    if (d > bestDeg) { bestDeg = d; bestV = v }
                }
            }
            ticker.tick(w.toLong() * 32 * w)
            if (bestV < 0) break
            clique.add(bestV)
            for (y in 0 until w) p[y] = p[y] and g.adj[bestV * w + y]
        }
        if (clique.size > best.size) best = clique.toIntArray()
    }

    private fun isEmpty(p: IntArray): Boolean {
        for (x in p) if (x != 0) return false
        return true
    }

    /** No crossing family has more than n/2 segments (distinct endpoints). */
    private val sizeLimit = g.n / 2

    private suspend fun expand(p: IntArray, depth: Int) {
        if (depth + pointBound(p) <= best.size) return
        // greedy colouring of p; only vertices with colour >= kMin can extend to a larger clique
        val kMin = best.size - depth + 1
        val low = maxOf(kMin - 1, 0)
        // the colour classes 1 .. kMin-1, for re-colouring
        val cls = IntArray(low * w)
        val u = p.copyOf()
        val q = IntArray(w)
        val verts = IntArray(Int.SIZE_BITS * w)
        val cols = IntArray(Int.SIZE_BITS * w)
        var cnt = 0
        var work = 0L
        var k = 0
        var left = 0
        for (x in u) left += x.countOneBits()
        while (left > 0) {
            k++
            u.copyInto(q)
            for (wi in 0 until w) {
                while (q[wi] != 0) {
                    val bit = q[wi].countTrailingZeroBits()
                    val v = (wi shl 5) or bit
                    val mask = (1 shl bit).inv()
                    q[wi] = q[wi] and mask
                    u[wi] = u[wi] and mask
                    left--
                    if (k >= kMin && recolour(v, cls, low)) { work += low * w; continue }
                    val base = v * w
                    for (x in wi until w) q[x] = q[x] and g.adj[base + x].inv()
                    work += w - wi
                    if (k >= kMin) {
                        verts[cnt] = v
                        cols[cnt] = k
                        cnt++
                    } else cls[(k - 1) * w + wi] = cls[(k - 1) * w + wi] or (1 shl bit)
                }
            }
        }
        ticker.tick(work + 1)
        val np = IntArray(w)
        for (idx in cnt - 1 downTo 0) {
            if (depth + cols[idx] <= best.size || best.size >= sizeLimit) return
            val v = verts[idx]
            cur[depth] = v
            val base = v * w
            var empty = true
            for (x in 0 until w) {
                np[x] = p[x] and g.adj[base + x]
                if (np[x] != 0) empty = false
            }
            if (empty) {
                if (depth + 1 > best.size) best = cur.copyOf(depth + 1)
            } else expand(np.copyOf(), depth + 1)
            p[v ushr 5] = p[v ushr 5] and (1 shl (v and 31)).inv()
        }
    }

    /**
     * Re-NUMBER (Tomita et al., MCS): put [v] into one of the [low] classes of [cls] instead of a
     * new colour, directly or by moving its only neighbour u in class c1 into a class c2 without
     * neighbours of u. Returns false if neither is possible.
     */
    private fun recolour(v: Int, cls: IntArray, low: Int): Boolean {
        val vb = v * w
        for (c1 in 0 until low) {
            var count = 0
            var nbr = -1
            for (x in 0 until w) {
                val c = cls[c1 * w + x] and g.adj[vb + x]
                if (c != 0) {
                    count += c.countOneBits()
                    if (count > 1) break
                    nbr = (x shl 5) or c.countTrailingZeroBits()
                }
            }
            if (count == 0) {
                set(cls, c1, v)
                return true
            }
            if (count > 1) continue
            val ub = nbr * w
            for (c2 in c1 + 1 until low) {
                var free = true
                for (x in 0 until w) if (cls[c2 * w + x] and g.adj[ub + x] != 0) { free = false; break }
                if (free) {
                    cls[c1 * w + (nbr ushr 5)] = cls[c1 * w + (nbr ushr 5)] and (1 shl (nbr and 31)).inv()
                    set(cls, c2, nbr)
                    set(cls, c1, v)
                    return true
                }
            }
        }
        return false
    }

    private fun set(cls: IntArray, c: Int, v: Int) {
        cls[c * w + (v ushr 5)] = cls[c * w + (v ushr 5)] or (1 shl (v and 31))
    }
}

/**
 * Largest n for the crossing family property (exponential search). Measured on the JVM: random
 * sets n = 48 take < 0.1 s, n = 60 up to 0.6 s, n = 70 10-100 s.
 */
const val CROSSING_FAMILY_MAX_N = 50
