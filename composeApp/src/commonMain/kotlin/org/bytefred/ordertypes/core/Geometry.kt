package org.bytefred.ordertypes.core

import kotlin.math.abs

/** A point with exact integer coordinates (y axis pointing up, as in the data files). */
data class Pt(val x: Long, val y: Long, val color: String? = null)

/**
 * Coordinates are limited to |c| <= [COORD_LIMIT] so the orientation determinant
 * fits into a Long: differences < 2^30, products < 2^60, their difference < 2^61.
 */
const val COORD_LIMIT: Long = 1L shl 29

fun Pt.inRange(): Boolean = abs(x) <= COORD_LIMIT && abs(y) <= COORD_LIMIT

/** Exact orientation of (a, b, c): +1 = counter-clockwise (c left of a->b), -1 = clockwise, 0 = collinear. */
fun orient(a: Pt, b: Pt, c: Pt): Int {
    val d = (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x)
    return if (d > 0) 1 else if (d < 0) -1 else 0
}

/** All index triples i<j<k that are collinear (or duplicate points). */
fun collinearTriples(p: List<Pt>): List<Triple<Int, Int, Int>> {
    val res = ArrayList<Triple<Int, Int, Int>>()
    for (i in p.indices) for (j in i + 1 until p.size) for (k in j + 1 until p.size)
        if (orient(p[i], p[j], p[k]) == 0) res.add(Triple(i, j, k))
    return res
}

/** Indices of points that are part of some collinear triple. */
fun collinearPoints(p: List<Pt>): Set<Int> =
    collinearTriples(p).flatMap { listOf(it.first, it.second, it.third) }.toSet()

fun isGeneralPosition(p: List<Pt>): Boolean {
    for (i in p.indices) for (j in i + 1 until p.size) for (k in j + 1 until p.size)
        if (orient(p[i], p[j], p[k]) == 0) return false
    return true
}

/**
 * True iff moving point [i] to [q] keeps the sign of every triple containing i
 * (so the labeled order type is unchanged) and creates no collinearity. O(n^2).
 */
fun sameOrderTypeAfterMove(p: List<Pt>, i: Int, q: Pt): Boolean {
    val old = p[i]
    for (j in p.indices) {
        if (j == i) continue
        for (k in j + 1 until p.size) {
            if (k == i) continue
            val o = orient(old, p[j], p[k])
            if (o == 0 || orient(q, p[j], p[k]) != o) return false
        }
    }
    return true
}

/**
 * Small lambda matrix: l[i][j] = number of points strictly left of the directed line i->j.
 * O(n^3); used for the info panel.
 */
fun smallLambda(p: List<Pt>): Array<IntArray> {
    val n = p.size
    val l = Array(n) { IntArray(n) }
    for (i in 0 until n) for (j in 0 until n) {
        if (i == j) continue
        var c = 0
        for (k in 0 until n) if (k != i && k != j && orient(p[i], p[j], p[k]) > 0) c++
        l[i][j] = c
    }
    return l
}

/**
 * Signature of the labeled order type: all triple signs packed into a string.
 * Two point lists with equal signatures have the same labeled order type, so all
 * order-type properties can be cached on it.
 */
fun orderTypeSignature(p: List<Pt>): String {
    val sb = StringBuilder(p.size * p.size * p.size / 6 + 4)
    sb.append(p.size).append(':')
    for (i in p.indices) for (j in i + 1 until p.size) for (k in j + 1 until p.size)
        sb.append(
            when (orient(p[i], p[j], p[k])) {
                1 -> '+'
                -1 -> '-'
                else -> '0'
            }
        )
    return sb.toString()
}

/**
 * Orientation of every index triple, computed once (O(n^3) bytes). The counting
 * algorithms look orientations up here instead of recomputing them; on Kotlin/JS,
 * Long arithmetic is emulated and comparatively slow.
 */
class Orientations(p: List<Pt>) {
    val n = p.size
    private val t = ByteArray(n * n * n)

    init {
        for (i in 0 until n) for (j in 0 until n) for (k in 0 until n)
            if (i != j && j != k && i != k) t[(i * n + j) * n + k] = orient(p[i], p[j], p[k]).toByte()
    }

    operator fun get(i: Int, j: Int, k: Int): Int = t[(i * n + j) * n + k].toInt()
}

/** Point strictly inside triangle (a, b, c) (any orientation). */
fun inTriangle(a: Pt, b: Pt, c: Pt, q: Pt): Boolean {
    val o = orient(a, b, c)
    if (o == 0) return false
    return orient(a, b, q) == o && orient(b, c, q) == o && orient(c, a, q) == o
}

/** Proper crossing of segments ab and cd (shared endpoints do not count). */
fun segmentsCross(a: Pt, b: Pt, c: Pt, d: Pt): Boolean {
    val o1 = orient(a, b, c)
    val o2 = orient(a, b, d)
    val o3 = orient(c, d, a)
    val o4 = orient(c, d, b)
    return o1 * o2 < 0 && o3 * o4 < 0
}
