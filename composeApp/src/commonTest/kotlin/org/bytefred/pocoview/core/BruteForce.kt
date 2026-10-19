package org.bytefred.pocoview.core

/** Reference implementations by exhaustive search, for cross-checking the fast algorithms. */
object BruteForce {
    fun subsets(n: Int, k: Int): Sequence<IntArray> = sequence {
        val c = IntArray(k) { it }
        if (k > n) return@sequence
        while (true) {
            yield(c.copyOf())
            var i = k - 1
            while (i >= 0 && c[i] == n - k + i) i--
            if (i < 0) break
            c[i]++
            for (j in i + 1 until k) c[j] = c[j - 1] + 1
        }
    }

    fun isConvexPosition(p: List<Pt>, s: IntArray) = convexHull(p, s.toList()).size == s.size

    fun isEmpty(p: List<Pt>, s: IntArray): Boolean {
        val h = convexHull(p, s.toList())
        return p.indices.none { q ->
            q !in s && h.indices.all { i -> orient(p[h[i]], p[h[(i + 1) % h.size]], p[q]) > 0 }
        }
    }

    fun countPolygons(p: List<Pt>, k: Int, emptyOnly: Boolean, selected: Int? = null): Long =
        subsets(p.size, k).count { s ->
            (selected == null || selected in s) && isConvexPosition(p, s) && (!emptyOnly || isEmpty(p, s))
        }.toLong()
}
