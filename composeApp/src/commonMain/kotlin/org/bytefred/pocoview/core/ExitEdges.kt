package org.bytefred.pocoview.core

/**
 * Unsigned rotation system: for each point i, the other points in the order in which a
 * line rotating around i meets them. Literal port of pyotlib2
 * `BigLambda.get_rotation_system(signed=False)` (pyotlib2/core/big_lambda.py).
 */
fun rotationSystemUnsigned(o: Orientations): List<IntArray> {
    val n = o.n
    val words = (n + 63) / 64
    fun set(i: Int, j: Int, positive: Boolean): LongArray {
        val s = LongArray(words)
        for (k in 0 until n) {
            val v = o[i, j, k]
            if (if (positive) v > 0 else v < 0) s[k / 64] = s[k / 64] or (1L shl (k % 64))
        }
        return s
    }
    fun has(s: LongArray, k: Int) = (s[k / 64] shr (k % 64)) and 1L == 1L
    return List(n) { i ->
        val order = ArrayList<Int>()
        if (n > 1) {
            // left[j] = {k : o[i, j, k] > 0}, right[j] = {k : o[i, j, k] < 0}
            val left = Array(n) { j -> if (j == i) LongArray(words) else set(i, j, true) }
            val right = Array(n) { j -> if (j == i) LongArray(words) else set(i, j, false) }
            var j = (i + 1) % n
            while (true) {
                if (j in order) break
                order.add(j)
                val jleft = left[j]
                var jnext = -1
                for (j2 in 0 until n) {
                    if (j2 == j || j2 == i) continue
                    if (has(jleft, j2)) {
                        val u = left[j2].copyOf().also { it[j2 / 64] = it[j2 / 64] or (1L shl (j2 % 64)) }
                        if (u.contentEquals(jleft)) { jnext = j2; break }
                    } else if (right[j2].contentEquals(jleft)) {
                        jnext = j2; break
                    }
                }
                check(jnext >= 0) { "rotation system broken at i=$i, j=$j" }
                j = jnext
            }
            check(order.size == n - 1)
        }
        order.toIntArray()
    }
}

/**
 * Exit edges with their witnesses: (a, b) (a < b) is an exit edge with witness c iff c is
 * the neighbour of b around a and of a around b on the same side, with matching
 * orientation. Literal port of pyotlib2 `filter_exit_edges(ot, return_witnesses=True)`
 * (pyotlib2/algorithms/exit_edges.py). Keys in iteration order of the pairs a < b.
 */
fun exitEdgeWitnesses(o: Orientations): Map<Pair<Int, Int>, Set<Int>> {
    val n = o.n
    val srs = rotationSystemUnsigned(o)
    val witnesses = LinkedHashMap<Pair<Int, Int>, MutableSet<Int>>()
    for (a in 0 until n) for (b in a + 1 until n) {
        val idxA = srs[a].indexOf(b)
        val idxB = srs[b].indexOf(a)
        for (sign in intArrayOf(-1, 1)) {
            val aNext = (idxA + sign).mod(n - 1)
            val bNext = (idxB - sign).mod(n - 1)
            val c = srs[a][aNext]
            if (c == srs[b][bNext] && o[a, b, c] == sign) witnesses.getOrPut(a to b) { LinkedHashSet() }.add(c)
        }
    }
    return witnesses
}

/** The exit triples (a, b, c): exit edge (a, b) with witness c (pyotlib2 `exit_triples`). */
fun exitTriples(o: Orientations): List<IntArray> =
    exitEdgeWitnesses(o).flatMap { (e, ws) -> ws.map { c -> intArrayOf(e.first, e.second, c) } }
