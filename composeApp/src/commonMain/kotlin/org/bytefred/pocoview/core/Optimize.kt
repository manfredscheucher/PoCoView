package org.bytefred.pocoview.core

import kotlin.random.Random

/** max(width, height) of the bounding box: the size measure the minimizer reduces. */
fun extent(p: List<Pt>): Long =
    if (p.isEmpty()) 0 else maxOf(p.maxOf { it.x } - p.minOf { it.x }, p.maxOf { it.y } - p.minOf { it.y })

private fun moved(p: List<Pt>, i: Int, q: Pt): List<Pt> = p.toMutableList().also { it[i] = q }

/**
 * Random point set in general position with coordinates in [0, size). Throws
 * IllegalStateException if the grid is too small (bounded number of attempts).
 */
fun randomPointSet(n: Int, size: Long, rnd: Random = Random.Default): List<Pt> {
    val res = ArrayList<Pt>()
    var tries = 0
    while (res.size < n) {
        check(++tries <= 100 * n + 1000) { "Could not place $n points in general position on a $size×$size grid" }
        val q = Pt(rnd.nextLong(size), rnd.nextLong(size))
        if (canAdd(res, q)) res.add(q)
    }
    return res
}

/** True iff adding [q] keeps the set in general position (no duplicate, no collinear triple). */
fun canAdd(p: List<Pt>, q: Pt): Boolean {
    for (j in p.indices) {
        if (p[j].x == q.x && p[j].y == q.y) return false
        for (k in j + 1 until p.size) if (orient(p[j], p[k], q) == 0) return false
    }
    return true
}

/**
 * Random move of one point for the property walk: a uniformly chosen point is moved by
 * up to [radius] in each coordinate, keeping general position. Null if no valid move
 * was found within a few tries.
 */
fun randomMove(p: List<Pt>, radius: Long, rnd: Random = Random.Default): List<Pt>? {
    if (p.isEmpty()) return null
    repeat(50) {
        val i = rnd.nextInt(p.size)
        val q = p[i].copy(
            x = p[i].x + rnd.nextLong(-radius, radius + 1),
            y = p[i].y + rnd.nextLong(-radius, radius + 1),
        )
        if (q != p[i] && q.inRange() && canAdd(p.filterIndexed { j, _ -> j != i }, q))
            return moved(p, i, q)
    }
    return null
}
