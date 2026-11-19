package org.bytefred.pocoview.core

import kotlin.math.floor

/** Crash guard of [snapWithRefinement]; in practice [COORD_LIMIT] (2^29) stops much earlier. */
const val MAX_DOUBLINGS = 64

/** Outcome of [snapWithRefinement]. */
sealed interface Snap {
    /** The new point list (all coordinates multiplied by 2^[doublings]) with the placed point. */
    data class Placed(val points: List<Pt>, val doublings: Int) : Snap

    /** Lock order type is on and the position lies outside the point's cell: nothing to do. */
    data object OutsideLockedCell : Snap

    /** No lattice point found before [COORD_LIMIT] or [MAX_DOUBLINGS]. */
    data object NoRoom : Snap
}

/**
 * Puts point [i] (or a new point if [i] is null) on the grid near the real position ([x], [y]),
 * in the same cell of the line arrangement of the other points as ([x], [y]).
 *
 * Tries the 4 corners of the unit square around the position, nearest first. If none lies in
 * that cell (or one is a duplicate), all coordinates and the position are doubled and the 4
 * corners are tried again. The position only gives the target cell (its signs are computed in
 * Double); every candidate is checked with exact Long orientations. Where the position lies
 * exactly on a line, either side is accepted.
 *
 * With [lock] (Lock order type, only for a moved point), the target cell is the point's current
 * cell; a position outside it gives [Snap.OutsideLockedCell].
 */
fun snapWithRefinement(points: List<Pt>, i: Int?, x: Double, y: Double, lock: Boolean = false): Snap {
    val others = if (i == null) points else points.filterIndexed { j, _ -> j != i }
    val m = others.size
    // target sign per pair (j, k) of the other points, j < k
    val target = IntArray(m * m)
    for (j in 0 until m) for (k in j + 1 until m) {
        val a = others[j]
        val b = others[k]
        val d = (b.x - a.x).toDouble() * (y - a.y) - (b.y - a.y).toDouble() * (x - a.x)
        var s = if (d > 0) 1 else if (d < 0) -1 else 0
        if (lock && i != null) {
            val old = orient(a, b, points[i])
            if (s != 0 && s != old) return Snap.OutsideLockedCell
            s = old
        }
        target[j * m + k] = s
    }
    var scaled = others
    var f = 1.0
    for (doublings in 0..MAX_DOUBLINGS) {
        if (doublings > 0) {
            scaled = scaled.map { Pt(2 * it.x, 2 * it.y, it.color) }
            if (scaled.any { !it.inRange() }) return Snap.NoRoom
            f *= 2
        }
        val px = x * f
        val py = y * f
        val fx = floor(px).toLong()
        val fy = floor(py).toLong()
        val corners = listOf(Pt(fx, fy), Pt(fx + 1, fy), Pt(fx, fy + 1), Pt(fx + 1, fy + 1))
            .sortedBy { (it.x - px) * (it.x - px) + (it.y - py) * (it.y - py) }
        for (c in corners) {
            if (!c.inRange() || !inTargetCell(scaled, c, target)) continue
            val q = Pt(c.x, c.y, i?.let { points[it].color })
            val res = if (i == null) scaled + q
            else scaled.toMutableList().also { it.add(i, q) }
            return Snap.Placed(res, doublings)
        }
    }
    return Snap.NoRoom
}

/** [c] is no duplicate and has the [target] sign (nonzero) with every pair of [p]. */
private fun inTargetCell(p: List<Pt>, c: Pt, target: IntArray): Boolean {
    val m = p.size
    for (j in 0 until m) {
        if (p[j].x == c.x && p[j].y == c.y) return false
        for (k in j + 1 until m) {
            val o = orient(p[j], p[k], c)
            val t = target[j * m + k]
            if (o == 0 || (t != 0 && o != t)) return false
        }
    }
    return true
}
