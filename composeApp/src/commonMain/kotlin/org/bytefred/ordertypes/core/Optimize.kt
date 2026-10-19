package org.bytefred.ordertypes.core

import kotlin.random.Random

/** Translates the set so that the smallest x and y coordinates are 0 (keeps the order type). */
fun normalizeOrigin(p: List<Pt>): List<Pt> {
    if (p.isEmpty()) return p
    val mx = p.minOf { it.x }
    val my = p.minOf { it.y }
    return p.map { it.copy(x = it.x - mx, y = it.y - my) }
}

/** max(width, height) of the bounding box: the size measure the minimizer reduces. */
fun extent(p: List<Pt>): Long =
    if (p.isEmpty()) 0 else maxOf(p.maxOf { it.x } - p.minOf { it.x }, p.maxOf { it.y } - p.minOf { it.y })

private fun moved(p: List<Pt>, i: Int, q: Pt): List<Pt> = p.toMutableList().also { it[i] = q }

/** The coordinate range of the bounding box along one axis. */
fun axisBounds(p: List<Pt>, xAxis: Boolean): LongRange =
    if (xAxis) p.minOf { it.x }..p.maxOf { it.x } else p.minOf { it.y }..p.maxOf { it.y }

/**
 * The valid values of one coordinate of point [i] (other coordinate fixed) form an open
 * interval: the cell of the line arrangement intersected with an axis-parallel line.
 * Returns the integer range [lo, hi] containing the current value, clipped to [bounds]
 * (default: the bounding box, so unbounded cells of hull points do not blow the set up).
 * Exponential search, then binary search; every probe checks only the triples
 * containing i, O(n^2).
 */
fun axisRange(p: List<Pt>, i: Int, xAxis: Boolean, bounds: LongRange = axisBounds(p, xAxis)): LongRange {
    val cur = if (xAxis) p[i].x else p[i].y
    fun at(v: Long) = if (xAxis) p[i].copy(x = v) else p[i].copy(y = v)
    fun ok(v: Long) = v in bounds && v in -COORD_LIMIT..COORD_LIMIT && sameOrderTypeAfterMove(p, i, at(v))
    fun reach(dir: Long): Long {
        var good = 0L
        var step = 1L
        while (ok(cur + dir * (good + step))) { good += step; step *= 2 }
        // now good is valid, good + step is not: binary search in between
        var bad = good + step
        while (bad - good > 1) {
            val mid = good + (bad - good) / 2
            if (ok(cur + dir * mid)) good = mid else bad = mid
        }
        return good
    }
    return (cur - reach(-1))..(cur + reach(1))
}

/** Moves point [i] to the middle of its valid range, first in x, then in y. */
private fun centerPoint(p: List<Pt>, i: Int): List<Pt> {
    var cur = p
    for (xAxis in listOf(true, false)) {
        val r = axisRange(cur, i, xAxis)
        val mid = r.first + (r.last - r.first) / 2
        cur = moved(cur, i, if (xAxis) cur[i].copy(x = mid) else cur[i].copy(y = mid))
    }
    return cur
}

/**
 * One beautify pass: every point is moved, coordinate by coordinate, to the middle of
 * its valid range within the bounding box (pyotlib2 `beautify_coords`). Keeps the
 * labeled order type and never enlarges the bounding box.
 */
suspend fun beautifyStep(p: List<Pt>, ticker: Ticker = Ticker()): List<Pt> {
    if (p.size < 3) return p
    var cur = p
    for (i in cur.indices) {
        cur = centerPoint(cur, i)
        ticker.tick(cur.size.toLong() * cur.size * 20)
    }
    return cur
}

/**
 * One coordinate-minimization step ("move to even, then halve"): every point is moved
 * to even coordinates where that keeps the order type (centering a point in its cell
 * first if it is stuck); if all coordinates are even, the set is halved exactly.
 * Returns null if no further halving was possible.
 */
suspend fun minimizeStep(p: List<Pt>, ticker: Ticker = Ticker()): List<Pt>? {
    if (p.size < 3) return null
    var cur = normalizeOrigin(p)
    if (extent(cur) <= 1) return null
    for (i in cur.indices) {
        ticker.tick(cur.size.toLong() * cur.size * 4)
        val even = toEven(cur, i) ?: run {
            // stuck near a cell wall: center the point in its cell and retry
            cur = centerPoint(cur, i)
            toEven(cur, i)
        } ?: return null
        cur = moved(cur, i, even)
    }
    return normalizeOrigin(cur.map { it.copy(x = it.x / 2, y = it.y / 2) })
}

private fun toEven(p: List<Pt>, i: Int): Pt? {
    val q = p[i]
    fun options(v: Long) = if (v % 2 == 0L) listOf(v) else listOf(v - 1, v + 1)
    for (x in options(q.x)) for (y in options(q.y)) {
        val c = q.copy(x = x, y = y)
        if (c == q || sameOrderTypeAfterMove(p, i, c)) return c
    }
    return null
}

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
