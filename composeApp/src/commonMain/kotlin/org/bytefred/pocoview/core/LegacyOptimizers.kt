package org.bytefred.pocoview.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/*
 * Literal ports of the "legacy" coordinate optimizers of pyotlib2
 * (pyotlib2/algorithms/coord_optimizer.py, symmetry.py, pattern_search.py), which are
 * themselves literal ports of old pyotlib PointSetOptimizer.py, Symmetry.py, PatternSearch.py
 * and the script pyotlib.MinimizePatternSearch.py. Same algorithms, constants, defaults and
 * control flow, quirks included (see the "Quirk (original)" comments).
 *
 * Differences forced by the platform (documented at the functions):
 *  - Python's `random` is a [Random] parameter (same distributions, different sequences).
 *  - Python integers are unbounded; here coordinates are Long. Intermediate coordinates may
 *    grow up to [WIDE_LIMIT] = 2^61 (orientation signs are computed exactly with 128-bit
 *    products, see [orientWide]); a candidate beyond that limit counts as invalid. Results
 *    handed back to the app must lie within [COORD_LIMIT], otherwise the input is returned.
 *  - Off-by-default branches are not ported: PROVIDE_ALL / has_cocircular_points (PROVIDE_ALL
 *    = False) and DEBUG output. `ensure_property` (minimize w.r.t. a property) is ported with
 *    the original's `assert(not ensure_property)` dropped, see [minimizeIntegerCoordinates].
 *  - Python asserts become [check] (IllegalStateException), as in Python they raise.
 */

/** Largest |coordinate| the legacy optimizers use internally (orientation is exact up to it). */
const val WIDE_LIMIT: Long = 1L shl 61

private const val M31 = (1L shl 31) - 1

/** Compares a*b with c*d exactly, for 0 <= a, b, c, d <= 2^62. */
private fun compareProducts(a: Long, b: Long, c: Long, d: Long): Int {
    if (a <= M31 && b <= M31 && c <= M31 && d <= M31) return (a * b).compareTo(c * d)
    // base-2^31 digits: x = x1 * 2^31 + x0 with x1 <= 2^31, x0 < 2^31
    val a1 = a ushr 31; val a0 = a and M31
    val b1 = b ushr 31; val b0 = b and M31
    val c1 = c ushr 31; val c0 = c and M31
    val d1 = d ushr 31; val d0 = d and M31
    val lo1 = a0 * b0
    val mid1 = a1 * b0 + a0 * b1 + (lo1 ushr 31)
    val hi1 = a1 * b1 + (mid1 ushr 31)
    val lo2 = c0 * d0
    val mid2 = c1 * d0 + c0 * d1 + (lo2 ushr 31)
    val hi2 = c1 * d1 + (mid2 ushr 31)
    if (hi1 != hi2) return hi1.compareTo(hi2)
    if ((mid1 and M31) != (mid2 and M31)) return (mid1 and M31).compareTo(mid2 and M31)
    return (lo1 and M31).compareTo(lo2 and M31)
}

/** sign(a*b - c*d) exactly, for |a|, |b|, |c|, |d| <= 2^62. */
private fun signOfProductDifference(a: Long, b: Long, c: Long, d: Long): Int {
    val s1 = a.sign * b.sign
    val s2 = c.sign * d.sign
    if (s1 != s2) return if (s1 > s2) 1 else -1
    if (s1 == 0) return 0
    return s1 * compareProducts(abs(a), abs(b), abs(c), abs(d))
}

/** Exact orientation sign of (a, b, c), as [orient], for |coordinates| <= [WIDE_LIMIT]. */
fun orientWide(ax: Long, ay: Long, bx: Long, by: Long, cx: Long, cy: Long): Int =
    signOfProductDifference(bx - ax, cy - ay, by - ay, cx - ax)

fun orientWide(a: Pt, b: Pt, c: Pt): Int = orientWide(a.x, a.y, b.x, b.y, c.x, c.y)

/** pyotlib2 `ceil_log2` (equivalent closed form, no overflow): ceil(log2(x)), 0 for x <= 1. */
private fun ceilLog2(x: Long): Int = if (x <= 1) 0 else 64 - (x - 1).countLeadingZeroBits()

/** A mutable point list (pyotlib2 `PointSet.points`), coordinates as Long. */
private class Pts(val x: LongArray, val y: LongArray) {
    val n get() = x.size

    fun copy() = Pts(x.copyOf(), y.copyOf())

    fun normalized(): Pts {
        val mx = x.min()
        val my = y.min()
        return Pts(LongArray(n) { x[it] - mx }, LongArray(n) { y[it] - my })
    }

    /** pyotlib2 `PointSet.diameter`: max of the x and y extents. */
    fun diameter(): Long = max(x.max() - x.min(), y.max() - y.min())

    /** pyotlib2 `PointSet.bits`: ceil_log2(1 + diameter). */
    fun bits(): Int = ceilLog2(1 + diameter())

    fun within(limit: Long) = x.all { abs(it) <= limit } && y.all { abs(it) <= limit }

    fun pointWithin(i: Int, limit: Long) = abs(x[i]) <= limit && abs(y[i]) <= limit

    fun orientation(i: Int, j: Int, k: Int) = orientWide(x[i], y[i], x[j], y[j], x[k], y[k])

    fun toPoints(colors: List<String?>) = List(n) { Pt(x[it], y[it], colors[it]) }

    fun toPoints() = List(n) { Pt(x[it], y[it]) }

    companion object {
        fun of(p: List<Pt>) = Pts(LongArray(p.size) { p[it].x }, LongArray(p.size) { p[it].y })
    }
}

/**
 * Small lambda matrix (l[i][j] = #points strictly left of i->j), exact up to [WIDE_LIMIT];
 * null beyond it. Equality of these matrices is pyotlib2's `to_small_lambda().to_string()`
 * comparison (colors, which the string also contains, are kept by index here).
 */
private fun lambdaOf(p: Pts): Array<IntArray>? {
    if (!p.within(WIDE_LIMIT)) return null
    val n = p.n
    return Array(n) { i ->
        IntArray(n) { j ->
            var c = 0
            if (i != j) for (k in 0 until n) if (k != i && k != j && p.orientation(i, j, k) > 0) c++
            c
        }
    }
}

private fun sameLambda(p: Pts, target: Array<IntArray>) = lambdaOf(p)?.contentDeepEquals(target) == true

private fun wideOrientations(p: Pts) = Orientations(p.toPoints(), ::orientWide)

// ---------------------------------------------------------------------------
// Symmetry.computeSymmetricPS (pyotlib2/algorithms/symmetry.py)
// ---------------------------------------------------------------------------

/** pyotlib2 `_round_py2`: Python-2 round(), halves away from zero. */
private fun roundPy2(v: Double): Long = if (v >= 0) floor(v + 0.5).toLong() else -floor(-v + 0.5).toLong()

private fun computeSymmetricPs(p0: Pts, k: Int): Pts {
    val p = p0.copy()
    val n = p.n
    check(n % k in listOf(0, 1))
    if (n % k == 1) { p.x[n - 1] = 0; p.y[n - 1] = 0 }
    for (i in 0 until n step k) for (j in 1 until k) {
        if (i + j < n) {
            val alpha = 2 * j * PI / k
            val px = p.x[i].toDouble()
            val py = p.y[i].toDouble()
            p.x[i + j] = roundPy2(cos(alpha) * px - sin(alpha) * py)
            p.y[i + j] = roundPy2(sin(alpha) * px + cos(alpha) * py)
        }
    }
    return p
}

/**
 * pyotlib2 `compute_symmetric_ps` (old Symmetry.computeSymmetricPS): make the list k-fold
 * rotationally symmetric around the origin. Points are processed in blocks of k: point i
 * (i = 0, k, 2k, ...) is kept, points i+1 .. i+k-1 become p_i rotated by 2*j*pi/k, rounded
 * half away from zero; if n % k == 1 the last point becomes (0, 0). Requires n % k in {0, 1}.
 * Colors are kept by index.
 */
fun computeSymmetricPs(points: List<Pt>, k: Int): List<Pt> =
    computeSymmetricPs(Pts.of(points), k).toPoints(points.map { it.color })

// ---------------------------------------------------------------------------
// minimizeIntegerCoordinates (PointSetOptimizer.py:32-304)
// ---------------------------------------------------------------------------

private fun balancePoints(p: Pts, blowUpConstant: Long = 1): Pts {
    // balance X and Y coords (scale smaller coords)
    val xMax = p.x.max()
    val yMax = p.y.max()
    val mx = max(blowUpConstant, (blowUpConstant * yMax).floorDiv(xMax))
    val my = max(blowUpConstant, (blowUpConstant * xMax).floorDiv(yMax))
    // both dimensions should have same size ...
    return Pts(LongArray(p.n) { p.x[it] * mx }, LongArray(p.n) { p.y[it] * my })
}

/** pyotlib2 `_balance_points`: scale x by max(B, B*ymax // xmax) and y by max(B, B*xmax // ymax). */
fun balancePoints(points: List<Pt>, blowUpConstant: Long = 1): List<Pt> =
    balancePoints(Pts.of(points), blowUpConstant).toPoints(points.map { it.color })

/** State shared by the minimizer helpers (pyotlib2: BL, SLstr and the module global COMBS). */
private class MinimizerState(
    val bl: Orientations,
    val target: Array<IntArray>,
    val rnd: Random,
    val ticker: Ticker,
    /** ensure_property: counting function (lower = better), or null = keep the order type. */
    val ensureProperty: (suspend (List<Pt>, Ticker) -> Long)? = null,
    /** counting_function(BL): the count of the start configuration. */
    val propertyThreshold: Long = 0,
) {
    /** COMBS[i]: pairs (a, b), a < b, such that {a, b, i} is an exit triple. */
    var combs: Array<List<IntArray>> = emptyArray()
}

/** pyotlib2 `same_ot_as_bl_`: the exit triples through point i keep their orientation. */
private fun sameOtAsBl(st: MinimizerState, ps: Pts, i: Int): Boolean {
    for ((a, b) in st.combs[i]) if (st.bl[a, b, i] != ps.orientation(a, b, i)) return false
    return true
}

/**
 * pyotlib2 `_is_valid_point_set` (PROVIDE_ALL = False). Deviation: a moved point beyond
 * [WIDE_LIMIT] is invalid (Python's unbounded integers would test it).
 *
 * ensure_property: if an exit triple through i flipped, the move is still valid when the set
 * has no collinear points and the count is <= the count of the start configuration (the
 * original's `assert(not ensure_property)` in front of this is dropped). Deviation: the count
 * is taken on the set translated to its bounding box center; a set that does not fit into
 * ±[COORD_LIMIT] then counts as invalid (the properties compute exactly only up to it).
 */
private suspend fun isValidPointSet(st: MinimizerState, ps: Pts, i: Int): Boolean {
    st.ticker.tick(st.combs[i].size + 1L)
    if (!ps.pointWithin(i, WIDE_LIMIT)) return false
    if (sameOtAsBl(st, ps, i)) return true
    val ensure = st.ensureProperty ?: return false
    if (hasCollinearPointsWide(ps, st.ticker)) return false
    val centered = centeredWithin(ps, COORD_LIMIT) ?: return false
    return ensure(centered, st.ticker) <= st.propertyThreshold
}

private suspend fun hasCollinearPointsWide(ps: Pts, ticker: Ticker): Boolean {
    for (a in 0 until ps.n) for (b in a + 1 until ps.n) {
        ticker.tick(ps.n.toLong())
        for (c in b + 1 until ps.n) if (ps.orientation(a, b, c) == 0) return true
    }
    return false
}

/** The points translated so that their bounding box is centered at 0, or null if they then exceed ±[limit]. */
/** [points] translated to their bounding box centre (colours kept), or null if they then exceed ±[COORD_LIMIT]. */
fun centeredPoints(points: List<Pt>): List<Pt>? =
    centeredWithin(Pts.of(points), COORD_LIMIT)?.mapIndexed { i, q -> Pt(q.x, q.y, points[i].color) }

private fun centeredWithin(ps: Pts, limit: Long): List<Pt>? {
    val cx = ps.x.min() + (ps.x.max() - ps.x.min()) / 2
    val cy = ps.y.min() + (ps.y.max() - ps.y.min()) / 2
    val c = Pts(LongArray(ps.n) { ps.x[it] - cx }, LongArray(ps.n) { ps.y[it] - cy })
    return if (c.within(limit)) c.toPoints() else null
}

private suspend fun movePointToEven(st: MinimizerState, ps: Pts, i: Int): Boolean {
    val x0 = ps.x[i]
    val y0 = ps.y[i]
    var x = x0
    var y = y0
    if (x.mod(2L) != 0L) x += 1
    if (y.mod(2L) != 0L) y += 1
    if (x == x0 && y == y0) return true
    // Quirk (original): the rounded-up point (x, y) itself is not tried, only these three.
    for ((px, py) in listOf(x - 2 to y - 2, x - 2 to y, x to y - 2)) {
        ps.x[i] = px; ps.y[i] = py
        if (isValidPointSet(st, ps, i)) return true
    }
    ps.x[i] = x0; ps.y[i] = y0
    return false
}

/** Python `randint(a, b)`: uniform in [a, b], both inclusive. */
private fun Random.randint(a: Long, b: Long) = nextLong(a, b + 1)

private suspend fun randomizePoint(st: MinimizerState, ps: Pts, i: Int, bits: Int) {
    val x = ps.x[i]
    val y = ps.y[i]
    for (e in bits downTo 1) {
        val m = 1L shl e
        repeat(10) {
            val nx = x + st.rnd.randint(-m, m)
            val ny = y + st.rnd.randint(-m, m)
            ps.x[i] = nx; ps.y[i] = ny
            if (isValidPointSet(st, ps, i)) return
        }
    }
    ps.x[i] = x; ps.y[i] = y
}

/**
 * pyotlib2 `_minimize_integer_coordinates_priv` (old _minimizeIntegerCoordinatesPriv): one
 * even / halve / randomize / blow-up run. Deviation: a run whose working coordinates leave
 * ±[WIDE_LIMIT] (only possible after a blow-up) ends early, keeping its best result.
 */
private suspend fun minimizeIntegerCoordinatesPriv(
    quality: (Pts) -> Long, ps: Pts, st: MinimizerState,
    maxTrials: Int, randomize: Int, tolerance: Long?, symmetric: Int,
): Pts {
    val n = ps.n
    val startBits = quality(ps)
    var tmp = ps.copy()
    if (symmetric == 0) tmp = tmp.normalized()
    tmp = balancePoints(tmp)
    if (!tmp.within(WIDE_LIMIT)) return ps
    check(tmp.bits() == ps.bits())

    var bestBits = startBits
    var bestPts = ps.copy()
    var iter = 0
    while (iter < maxTrials) {
        st.ticker.tick(n.toLong())
        if (!tmp.within(WIDE_LIMIT)) break
        // phase 1: Minimize ("for it in range(1)")
        var ok = true
        for (i in 0 until n) if (!movePointToEven(st, tmp, i)) ok = false

        if (ok) {
            // phase 2A: Half
            val backup = tmp.copy()
            val src = if (symmetric == 0) tmp.normalized() else tmp
            tmp = Pts(LongArray(n) { src.x[it].floorDiv(2L) }, LongArray(n) { src.y[it].floorDiv(2L) })

            if (symmetric != 0) {
                tmp = computeSymmetricPs(tmp, symmetric)
                if (!sameLambda(tmp, st.target)) {
                    tmp = backup // backup old solution
                    ok = false
                }
            }

            if (ok) {
                val bits = quality(tmp)
                if (bits < bestBits) {
                    bestBits = bits
                    bestPts = tmp.copy()
                    iter = 0
                }
                if (tolerance != null && bits <= tolerance) break
            }
        }

        if (!ok) {
            if (randomize != 0) {
                // phase 2B: Randomize
                repeat(randomize) {
                    for (i in 0 until n) randomizePoint(st, tmp, i, st.rnd.randint(0, tmp.bits().toLong()).toInt())
                }
            }
            iter += 1
            if (iter == maxTrials / 2) {
                // phase 2C: Blow Up
                tmp = balancePoints(if (symmetric == 0) tmp.normalized() else tmp, blowUpConstant = 3)
            }
        }
    }

    var psNew = bestPts
    if (symmetric == 0) psNew = psNew.normalized()
    // with ensure_property the order type may change, so the original's assert only applies without it
    check(st.ensureProperty != null || sameLambda(psNew, st.target)) { "optimization failed?!" }
    return psNew
}

/** pyotlib2 `minimize_integer_coordinates_private`: repeat the inner minimizer while it improves. */
private suspend fun minimizeIntegerCoordinatesPrivate(
    quality: (Pts) -> Long, ps0: Pts, st: MinimizerState,
    maxTrials: Int, randomize: Int, tolerance: Long?, symmetric: Int,
): Pts {
    var ps = ps0
    if (symmetric == 0) ps = ps.normalized()
    val startBits = quality(ps)
    if (tolerance != null && startBits <= tolerance) return ps

    // exit triples of SL = BL.to_small_lambda(); COMBS
    val n = st.bl.n
    val combSets = Array(n) { LinkedHashMap<Int, IntArray>() }
    for ((e, ws) in exitEdgeWitnesses(st.bl)) for (c in ws) {
        val (a, b) = e
        fun add(i: Int, u: Int, v: Int) {
            val lo = minOf(u, v)
            val hi = maxOf(u, v)
            combSets[i].getOrPut(lo * n + hi) { intArrayOf(lo, hi) }
        }
        add(a, b, c)
        add(b, a, c)
        add(c, a, b)
    }
    st.combs = Array(n) { combSets[it].values.toList() }

    while (true) {
        val curBits = quality(ps)
        if (tolerance != null && curBits <= tolerance) break
        val ps2 = minimizeIntegerCoordinatesPriv(quality, ps, st, maxTrials, randomize, tolerance, symmetric)
        val newBits = quality(ps2)
        if (newBits >= curBits) break
        ps = ps2
    }
    return ps
}

/**
 * pyotlib2 `minimize_integer_coordinates` (old minimizeIntegerCoordinates): shrink the
 * coordinates keeping the labeled order type. Each run moves every point to even
 * coordinates (only the exit triples through the moved point are checked) and halves; if a
 * point resists, all points are randomized [randomize] times and a trial is spent; after
 * maxTrials / 2 failed trials the set is blown up (x3, balanced). Runs repeat while they
 * improve.
 *
 * @param maxTrials failed trials per run (default 1, as in the original: then no blow-up).
 * @param tolerance stop as soon as the quality is <= tolerance (0 = off).
 * @param sheering also try the shears (x, y - x), (x, y + x), (x - y, y), (x + y, y).
 *   Quirk (original): each shear is applied to the current best set, the comparison is
 *   always against the *start* quality, and the tolerance `break` leaves only the inner loop.
 * @param minimizeDiameter quality = diameter instead of bits = ceil(log2(1 + diameter)).
 * @param symmetric k > 0: keep the set k-fold rotationally symmetric around the origin
 *   ([computeSymmetricPs] after each halving; the set is not normalized). Needs n % k in {0, 1}
 *   and an input that is already symmetric in that labeling.
 * @param ensureProperty minimize w.r.t. a property (old `ensure_property`): a counting
 *   function (lower = better); a move that flips an exit triple is then accepted if the count
 *   does not exceed the start count (see [isValidPointSet]). Only the exit triples through
 *   the moved point are tested, so the result may still be worse or contain collinear points;
 *   callers check it. null = keep the order type.
 * @return normalized (unless [symmetric]) points, colors kept by index.
 * @throws IllegalStateException where the Python asserts (e.g. order type changed by the
 *   symmetrization of a non-symmetric input) or on division by zero in the balancing.
 */
suspend fun minimizeIntegerCoordinates(
    points: List<Pt>,
    maxTrials: Int = 1,
    randomize: Int = 10,
    tolerance: Int = 0,
    sheering: Boolean = false,
    minimizeDiameter: Boolean = false,
    symmetric: Int = 0,
    rnd: Random = Random.Default,
    ticker: Ticker = Ticker(),
    ensureProperty: (suspend (List<Pt>, Ticker) -> Long)? = null,
): List<Pt> {
    val colors = points.map { it.color }
    return minimizeIntegerCoordinates(
        Pts.of(points), maxTrials, randomize, tolerance, sheering, minimizeDiameter, symmetric, rnd, ticker, ensureProperty,
    ).toPoints(colors)
}

private suspend fun minimizeIntegerCoordinates(
    ps0: Pts, maxTrials: Int, randomize: Int, tolerance0: Int, sheering: Boolean,
    minimizeDiameter: Boolean, symmetric: Int, rnd: Random, ticker: Ticker,
    ensureProperty: (suspend (List<Pt>, Ticker) -> Long)?,
): Pts {
    val tolerance: Long? = if (tolerance0 == 0) null else tolerance0.toLong()
    val quality: (Pts) -> Long = if (minimizeDiameter) { p -> p.diameter() } else { p -> p.bits().toLong() }

    var ps = ps0
    if (symmetric == 0) ps = ps.normalized()
    require(ps.within(WIDE_LIMIT)) { "coordinates exceed 2^61" }
    val bestBits = quality(ps)
    val startBits = bestBits
    var psBest = ps
    if (tolerance != null && startBits <= tolerance) return ps

    val bl = wideOrientations(ps)
    val threshold = ensureProperty?.let { f ->
        f(requireNotNull(centeredWithin(ps, COORD_LIMIT)) { "coordinates exceed 2^29" }, ticker)
    } ?: 0L
    val st = MinimizerState(bl, lambdaOf(ps)!!, rnd, ticker, ensureProperty, threshold)

    val dxs = mutableListOf(0L)
    val dys = mutableListOf(0L)
    if (sheering) {
        dxs += listOf(-1L, 1L)
        dys += listOf(-1L, 1L)
    }
    for (dx in dxs) {
        for (dy in dys) {
            if (dx * dy != 0L) continue
            val ps1 = Pts(LongArray(ps.n) { psBest.x[it] + psBest.y[it] * dx }, LongArray(ps.n) { psBest.y[it] + psBest.x[it] * dy })
            val ps2 = minimizeIntegerCoordinatesPrivate(quality, ps1, st, maxTrials, randomize, tolerance, symmetric)
            val endBits = quality(ps2)
            // Quirk (original): best_bits is never updated, so every result better than the
            // start replaces the best one.
            if (endBits < bestBits) psBest = ps2
            if (tolerance != null && endBits <= tolerance) break
        }
    }
    return psBest
}

// ---------------------------------------------------------------------------
// beautifyPointSet (PointSetOptimizer.py:308)
// ---------------------------------------------------------------------------

/** Exact base^e if <= 2^62, else null. */
private fun powExact(base: Long, e: Int): Long? {
    var r = 1L
    repeat(e) {
        if (r > (1L shl 62) / base) return null
        r *= base
    }
    return r
}

/** a * b if <= 2^62 (a, b >= 0), else null. */
private fun mulExact(a: Long, b: Long): Long? = if (b != 0L && a > (1L shl 62) / b) null else a * b

/** Steps above this make a coordinate leave ±[BEAUTIFY_LIMIT] anyway. */
private const val STEP_LIMIT = (1L shl 40).toDouble()

/** Coordinates beautifyPointSet accepts as valid (covers all of its probes for inputs within COORD_LIMIT). */
private const val BEAUTIFY_LIMIT = 1L shl 31

/**
 * Python `int(factor * grad[i])` with grad[i] = g * Fraction(10, 10 + it) and
 * factor = 2^-h * (10/9)^e (Fractions): truncation towards zero. Exact (via 128-bit
 * comparisons) while 10^|e| and (10 + it) * 2^h * 9^|e| fit into 2^62, otherwise from the
 * floating point value (documented deviation). null: larger than [STEP_LIMIT].
 */
private fun legacyStep(g: Long, it: Int, h: Int, e: Int): Long? {
    if (g == 0L) return 0L
    val ag = abs(g)
    val ae = abs(e)
    val pa = if (e >= 0) 10L else 9L
    val pb = if (e >= 0) 9L else 10L
    val est = ag * 10.0 / (10.0 + it) * 2.0.pow(-h) * (pa.toDouble() / pb).pow(ae)
    if (!(est < STEP_LIMIT)) return null
    var q = est.toLong()
    val a = powExact(pa, ae)
    val b = powExact(pb, ae)
    val num1 = ag * 10
    val den = if (a != null && b != null && h < 62) mulExact(10L + it, 1L shl h)?.let { mulExact(it, b) } else null
    if (a != null && den != null && num1 <= (1L shl 62)) {
        // largest q with q * den <= num1 * a
        while (q > 0 && compareProducts(q, den, num1, a) > 0) q--
        while (compareProducts(q + 1, den, num1, a) <= 0) q++
    }
    return if (g < 0) -q else q
}

/** factor = 2^-h * (10/9)^e > 1 ? Exact while the powers fit into 2^62, else via logarithms. */
private fun factorGreaterThanOne(h: Int, e: Int): Boolean {
    if (e <= 0) return false
    val p10 = powExact(10, e)
    val rhs = if (h < 62) powExact(9, e)?.let { mulExact(it, 1L shl h) } else null
    if (p10 != null && rhs != null) return p10 > rhs
    return e * ln(10.0 / 9.0) > h * ln(2.0)
}

/**
 * pyotlib2 `beautify_point_set` (old beautifyPointSet): for each coordinate, binary-search
 * the range [zl, zu] it can move in (all other coordinates fixed), step towards the
 * midpoints with convergence factor 10 / (10 + it), backtracking (factor / 2), extrapolation
 * (factor * 10/9) and capping at 1.
 *
 * Quirks (original): a probed coordinate is not reset after its probes (later coordinates
 * are probed with it at its last probed value); `retries` of the original is unused and not
 * ported. Without a cap, the extrapolation loop does not terminate when Z0 + factor * grad
 * stays valid for every factor (e.g. grad == 0, as for n < 3): such a run only ends by
 * cancellation through [ticker].
 *
 * Deviations: Python Fractions are exact in Long where they fit (see [legacyStep]); a
 * configuration with a coordinate beyond ±2^31 counts as invalid (Python would test it; all
 * probes of an input within COORD_LIMIT stay inside); a result outside [COORD_LIMIT] returns
 * the input.
 *
 * @param goodBits scale up to this many bits first (null = current bits).
 * @return the normalized result if the labeled order type is unchanged, else [points].
 */
suspend fun beautifyPointSet(points: List<Pt>, maxIter: Int = 100, goodBits: Int? = null, ticker: Ticker = Ticker()): List<Pt> {
    val n = points.size
    val colors = points.map { it.color }
    val ps0 = Pts.of(points)
    require(ps0.normalized().within(COORD_LIMIT)) { "coordinates exceed COORD_LIMIT" }
    val bl = wideOrientations(ps0)
    val sl = lambdaOf(ps0)!!

    val orientations = ArrayList<IntArray>()
    for (a in 0 until n) for (b in 0 until n) for (c in 0 until n)
        if (a < b && a < c && bl[a, b, c] > 0) orientations.add(intArrayOf(a, b, c))
    val orientations2 = Array(n) { a0 -> orientations.filter { a0 in it } }

    fun isValid(z: LongArray, i: Int? = null): Boolean {
        for (t in (if (i != null) orientations2[i % n] else orientations)) {
            val (a, b, c) = t
            if (abs(z[a]) > BEAUTIFY_LIMIT || abs(z[a + n]) > BEAUTIFY_LIMIT ||
                abs(z[b]) > BEAUTIFY_LIMIT || abs(z[b + n]) > BEAUTIFY_LIMIT ||
                abs(z[c]) > BEAUTIFY_LIMIT || abs(z[c + n]) > BEAUTIFY_LIMIT
            ) return false
            // o3(a, b, c) < 1
            if (orientWide(z[a], z[a + n], z[b], z[b + n], z[c], z[c + n]) <= 0) return false
        }
        return true
    }

    val norm0 = ps0.normalized()
    var curBits = norm0.bits()
    val gb = goodBits ?: curBits
    var z = LongArray(2 * n) { if (it < n) norm0.x[it] else norm0.y[it - n] }
    if (curBits < gb) {
        val s = 1L shl (gb - curBits)
        z = LongArray(2 * n) { z[it] * s }
        curBits = gb
        require(z.all { abs(it) <= COORD_LIMIT }) { "goodBits scale beyond COORD_LIMIT" }
    }

    // min_diff = 2*n*2**goodbits; diffs are Fractions S * (10 + it) / 10: compare S*m with minS*minM
    var minS = 2L * n * (1L shl gb) * 10
    var minM = 1L
    var stopTimer = 5
    val tickUnits = orientations.size.toLong() + 1

    for (it in 1..maxIter) {
        val grad = LongArray(2 * n)
        val z0 = z.copyOf()

        for (i in 0 until 2 * n) {
            ticker.tick(tickUnits * curBits)
            val zz0 = z[i]
            var zl = zz0
            var zu = zz0
            for (bit in curBits - 1 downTo 0) {
                z[i] = zl - (1L shl bit)
                if (isValid(z, i)) zl = z[i]
                z[i] = zu + (1L shl bit)
                if (isValid(z, i)) zu = z[i]
            }
            // Quirk (original): z[i] is not restored to zz0 here.
            val z1 = (zl + zu).floorDiv(2L)
            grad[i] = z1 - zz0
        }

        // factor = 2^-h * (10/9)^e, exactly
        var h = 0
        var e = 0
        fun apply(): Boolean {
            for (i in 0 until 2 * n) {
                val st = legacyStep(grad[i], it, h, e) ?: return false
                z[i] = z0[i] + st
            }
            return true
        }
        suspend fun valid(): Boolean {
            ticker.tick(tickUnits)
            return apply() && isValid(z)
        }

        check(isValid(z0))

        while (true) {
            if (valid()) break
            h++
        }
        // Quirk (original): no iteration cap (see KDoc).
        while (true) {
            e++
            if (!valid()) {
                e--
                break
            }
        }
        if (factorGreaterThanOne(h, e)) { h = 0; e = 0 }
        while (true) {
            if (valid()) break else e--
        }

        var s = 0L
        for (i in 0 until 2 * n) s += abs(z[i] - z0[i])
        val m = 10L + it
        if (compareProducts(s, m, minS, minM) < 0) {
            minS = s
            minM = m
        } else {
            stopTimer = 8
        }
        stopTimer -= 1
        if (s == 0L || stopTimer < 0) break
    }

    val res = Pts(LongArray(n) { z[it] }, LongArray(n) { z[it + n] }).normalized()
    return if (res.within(COORD_LIMIT) && sameLambda(res, sl)) res.toPoints(colors) else points
}

// ---------------------------------------------------------------------------
// beautify2 (PointSetOptimizer.py:440)
// ---------------------------------------------------------------------------

/**
 * pyotlib2 `_to_integer` (old ScipyRealizationTester._toInteger): converts each value to its
 * exact binary fraction and repeatedly multiplies all values by the first denominator > 1
 * until all are integers (in total: by 2^K, K the largest denominator exponent). null if a
 * result exceeds [WIDE_LIMIT] (Python's integers are unbounded).
 */
internal fun toIntegerExact(values: DoubleArray): LongArray? {
    val m = LongArray(values.size)
    val e2 = IntArray(values.size)
    for ((idx, v) in values.withIndex()) {
        require(v.isFinite()) { "cannot convert $v to an integer" }
        if (v == 0.0) continue
        val bits = v.toRawBits()
        val expBits = ((bits ushr 52) and 0x7FF).toInt()
        val frac = bits and ((1L shl 52) - 1)
        var mant = if (expBits == 0) frac else frac or (1L shl 52)
        var ex = if (expBits == 0) -1074 else expBits - 1075
        val tz = mant.countTrailingZeroBits()
        mant = mant ushr tz
        ex += tz
        m[idx] = if (v < 0) -mant else mant
        e2[idx] = ex
    }
    var k = 0
    for (idx in values.indices) if (m[idx] != 0L) k = max(k, -e2[idx])
    val res = LongArray(values.size)
    for (idx in values.indices) {
        if (m[idx] == 0L) continue
        val shift = e2[idx] + k
        val len = 64 - abs(m[idx]).countLeadingZeroBits()
        if (len + shift > 61) return null
        res[idx] = m[idx] shl shift
    }
    return res
}

/**
 * pyotlib2 `beautify2` (old beautify2): Nelder-Mead ([nelderMead], port of scipy fmin with
 * xtol = ftol = [epsilon], maxiter = [iter2], maxfun = 50 * iter2) on the quality
 * error1 * error2 * error3 (exit-triple "thickness" relative to the perimeter, spread of the
 * squared distances, |log max |z||), with the convex hull points fixed; the result is made
 * integral exactly ([toIntegerExact], i.e. scaled by a power of two, typically to 50+ bits),
 * normalized and, if [minimize], shrunk by [minimizeIntegerCoordinates] (defaults). [iter1]
 * rounds. The original's prints are not ported.
 *
 * @param diamBound if not null/0: the quality is infinite for larger extents and the result
 *   must have diameter <= diamBound.
 * @return the normalized result if the labeled order type is unchanged (and the diameter
 *   bound holds), else [points]. Deviations: the order type is compared without colors (the
 *   original compares strings containing the coloring and its result has none, so a colored
 *   input always fell back); colors are kept by index. If an intermediate result exceeds
 *   ±2^61 or the result exceeds [COORD_LIMIT] (always the case without [minimize], see
 *   above), [points] is returned.
 */
suspend fun beautify2(
    points: List<Pt>,
    iter1: Int = 1,
    iter2: Int = 1000,
    goodBits: Int = 0,
    minimize: Boolean = false,
    diamBound: Long? = null,
    norm: Int = 1,
    epsilon: Double = 0.0001,
    rnd: Random = Random.Default,
    ticker: Ticker = Ticker(),
): List<Pt> {
    val res = beautify2Wide(points, iter1, iter2, goodBits, minimize, diamBound, norm, epsilon, rnd, ticker) ?: return points
    return if (res.all { it.inRange() }) res else points
}

/** [beautify2] without the final [COORD_LIMIT] check (coordinates up to 2^61); null = fallback. */
internal suspend fun beautify2Wide(
    points: List<Pt>, iter1: Int, iter2: Int, goodBits: Int, minimize: Boolean, diamBound: Long?,
    norm: Int, epsilon: Double, rnd: Random, ticker: Ticker,
): List<Pt>? {
    val n = points.size
    val colors = points.map { it.color }
    val ot = Pts.of(points)
    val bl = wideOrientations(ot)
    val otLambda = lambdaOf(ot) ?: return null
    val bound = if (diamBound == 0L) null else diamBound

    // Quirk (original): the positive triples are computed and immediately replaced by the exit triples.
    val orientations = exitTriples(bl)

    var z0 = DoubleArray(2 * n)
    // CH0 = OT.get_extremal_points(): i with l[i][j] == 0 for some j != i
    val ch0 = (0 until n).filter { i -> (0 until n).any { j -> j != i && otLambda[i][j] == 0 } }
    val nd = norm.toDouble()

    fun quality(z: DoubleArray): Double {
        for (i in ch0) { z[i] = z0[i]; z[i + n] = z0[i + n] } // fix convex hull
        fun x(i: Int) = z[i]
        fun y(i: Int) = z[i + n]
        if (bound != null) {
            var minX = x(0); var maxX = x(0); var minY = y(0); var maxY = y(0)
            for (i in 1 until n) {
                minX = minOf(minX, x(i)); maxX = maxOf(maxX, x(i))
                minY = minOf(minY, y(i)); maxY = maxOf(maxY, y(i))
            }
            if (max(maxX - minX, maxY - minY) > bound) return Double.POSITIVE_INFINITY
        }
        val zmax = z.maxOf { abs(it) }

        fun o2(a: Int, b: Int) = x(a) * y(b) - y(a) * x(b)
        fun o3(a: Int, b: Int, c: Int) = o2(a, b) + o2(b, c) + o2(c, a)
        fun d2(a: Int, b: Int) = (x(a) - x(b)).pow(2) + (y(a) - y(b)).pow(2)
        fun d1(a: Int, b: Int) = sqrt(d2(a, b))

        val dd2 = ArrayList<Double>(n * (n - 1) / 2)
        for (a in 0 until n) for (b in a + 1 until n) dd2.add(d2(a, b))
        val f3 = orientations.map { (a, b, c) -> o3(a, b, c) * bl[a, b, c] / (d1(a, b) + d1(b, c) + d1(c, a)) }

        if (f3.min() <= 0) return Double.POSITIVE_INFINITY

        val error1 = f3.sumOf { 1.0 / it.pow(nd) }.pow(1.0 / nd)
        val error2 = (dd2.sumOf { it.pow(nd) } * dd2.sumOf { 1.0 / it.pow(nd) }).pow(1.0 / nd)
        val error3 = abs(ln(zmax))
        return error1 * error2 * error3
    }

    var ps = ot.normalized()
    if (minimize) ps = minimizeIntegerCoordinates(ps, 1, 10, 0, false, false, 0, rnd, ticker, null)

    repeat(iter1) {
        val dbits = goodBits - ps.bits()
        if (dbits > 0) {
            // "scale point set!"
            val s = 1L shl dbits
            ps = Pts(LongArray(n) { ps.x[it] * s }, LongArray(n) { ps.y[it] * s })
            if (!ps.within(WIDE_LIMIT)) return null
        }
        val zi0 = LongArray(2 * n) { if (it < n) ps.x[it] else ps.y[it - n] }
        z0 = DoubleArray(2 * n) { zi0[it].toDouble() }

        val sol = nelderMead(::quality, z0, xtol = epsilon, ftol = epsilon, maxIter = iter2, maxFun = 50 * iter2, ticker = ticker)
        for (i in ch0) { sol[i] = z0[i]; sol[i + n] = z0[i + n] } // fix convex hull
        val zi = toIntegerExact(sol) ?: return null
        ps = Pts(LongArray(n) { zi[it] }, LongArray(n) { zi[it + n] }).normalized()
        if (!ps.within(WIDE_LIMIT)) return null
        if (minimize) ps = minimizeIntegerCoordinates(ps, 1, 10, 0, false, false, 0, rnd, ticker, null)
    }

    return if (sameLambda(ps, otLambda) && (bound == null || ps.diameter() <= bound)) ps.toPoints(colors) else points
}

// ---------------------------------------------------------------------------
// MinimizePatternSearch (scripts/pyotlib.MinimizePatternSearch.py)
// ---------------------------------------------------------------------------

/** Result of [patternSearchOptimize] / [minimizePatternSearch]: the points and the unused blow-ups. */
data class PatternSearchResult(val points: List<Pt>, val blowupLeft: Int)

private fun psDet(p: Array<LongArray>, i: Int, j: Int, k: Int): Long {
    val (ix, iy) = p[i]
    val (jx, jy) = p[j]
    val (kx, ky) = p[k]
    return ix * (jy - ky) + jx * (ky - iy) + kx * (iy - jy)
}

/** `_ps_same_order_type` over all triples (det1 * det2 <= 0 compared via signs: no overflow). */
private fun psSameOrderType(p1: Array<LongArray>, p2: Array<LongArray>): Boolean {
    val n = p1.size
    for (a in 0 until n) for (b in a + 1 until n) for (c in b + 1 until n)
        if (psDet(p1, a, b, c).sign * psDet(p2, a, b, c).sign <= 0) return false
    return true
}

private fun psNormalized(p: Array<LongArray>): Array<LongArray> {
    val xmin = p.minOf { it[0] }
    val ymin = p.minOf { it[1] }
    return Array(p.size) { longArrayOf(p[it][0] - xmin, p[it][1] - ymin) }
}

/** `_ps_get_diameter2`: sum over points of |n*x - sum X| + |n*y - sum Y|. */
private fun psDiameter2(p: Array<LongArray>): Long {
    val xmid = p.sumOf { it[0] }
    val ymid = p.sumOf { it[1] }
    val n = p.size.toLong()
    return p.sumOf { abs(n * it[0] - xmid) + abs(n * it[1] - ymid) }
}

private fun psBits(p: Array<LongArray>) = Pts(LongArray(p.size) { p[it][0] }, LongArray(p.size) { p[it][1] }).bits()

private fun psWithin(p: Array<LongArray>) = p.all { abs(it[0]) <= COORD_LIMIT && abs(it[1]) <= COORD_LIMIT }

private fun deepcopy(p: Array<LongArray>) = Array(p.size) { p[it].copyOf() }

/**
 * pyotlib2 `pattern_search_optimize` (MinimizePatternSearch.py `optimize`): integer pattern
 * search on the L1 spread around the centroid (`_ps_get_diameter2`), each coordinate tried at
 * ±s; s grows by 1 + s / 2 after an improvement, else shrinks to s / 4 (s > 6) or s / 2;
 * extrapolation by the last move; with no change and s == 0, blow up x2 while [blowup] > 0,
 * else stop.
 *
 * Quirks (original): the order-type check compares with the *previous* iterate (points_old
 * is rebound), not with the input; after an improvement at +s the coordinate keeps the -s
 * probe in points_new. The DEBUG-only `bits` computation and assert are not ported.
 *
 * Deviations: candidates outside [COORD_LIMIT] count as order-type changes (penalty), and a
 * blow-up that would leave it stops the search (as without blow-ups left). Needs n <= 600
 * (10000 * diameter2 must fit into a Long).
 */
suspend fun patternSearchOptimize(
    points: List<Pt>,
    maxTrials: Int = 2000,
    scale: Long = 1,
    blowup: Int = 0,
    tolerance: Int = 0,
    ticker: Ticker = Ticker(),
): PatternSearchResult {
    val n = points.size
    require(n <= 600) { "pattern search supports at most 600 points" }
    val colors = points.map { it.color }
    val m = scale
    var pointsOld = Array(n) { longArrayOf(m * points[it].x, m * points[it].y) }
    require(psWithin(pointsOld)) { "scaled coordinates exceed COORD_LIMIT" }
    var blowupLeft = blowup

    fun quality(p: Array<LongArray>): Long =
        if (!psWithin(p) || !psSameOrderType(pointsOld, p)) psDiameter2(pointsOld) * 10000 else psDiameter2(p)

    val tickUnits = n.toLong() * n * n / 6 + 1
    var fBst = quality(pointsOld)
    var s = 1L
    var pointsCur = pointsOld // copy
    var pointsBst = deepcopy(pointsCur)

    for (it in 0 until maxTrials) {
        val pointsNew = deepcopy(pointsCur)
        pointsBst = deepcopy(pointsCur)

        // search
        var anythingImproved = false
        for (i in 0 until n) for (j in 0..1) {
            var improved = false
            for (d in longArrayOf(s, -s)) {
                ticker.tick(tickUnits)
                pointsNew[i][j] = pointsCur[i][j] + d
                val fNew = quality(pointsNew)
                if (fNew < fBst) {
                    anythingImproved = true
                    improved = true
                    fBst = fNew
                    pointsBst = deepcopy(pointsNew)
                }
            }
            if (!improved) pointsNew[i][j] = pointsCur[i][j] // backup
        }

        if (anythingImproved) {
            s += 1 + s / 2
            if (tolerance > 0 && psBits(pointsNew) <= tolerance) break
        } else {
            s /= if (s > 6) 4 else 2
        }

        // extrapolate
        for (i in 0 until n) for (j in 0..1) {
            val diff = pointsCur[i][j] - pointsOld[i][j]
            pointsNew[i][j] = pointsBst[i][j] + diff
        }
        val fNew = quality(pointsNew)
        if (fNew < fBst) {
            pointsBst = deepcopy(pointsNew)
            fBst = fNew
        }

        var diff2 = 0L
        for (i in 0 until n) for (j in 0..1) {
            val d = pointsBst[i][j] - pointsCur[i][j]
            diff2 += d * d
        }
        if (diff2 != 0L) {
            pointsOld = deepcopy(pointsCur)
            pointsCur = deepcopy(pointsBst)
        }

        if (diff2 == 0L && s == 0L) {
            val blown = Array(n) { longArrayOf(2 * pointsCur[it][0], 2 * pointsCur[it][1]) }
            if (blowupLeft != 0 && psWithin(blown)) {
                // BLOW UP!
                blowupLeft -= 1
                s = 1
                pointsOld = blown
                pointsCur = blown
                fBst = quality(pointsOld)
            } else {
                // STOP! NO CHANGES!
                break
            }
        }

        if (s == 0L) s = 1
    }
    return PatternSearchResult(List(n) { Pt(pointsBst[it][0], pointsBst[it][1], colors[it]) }, blowupLeft)
}

/**
 * pyotlib2 `minimize_pattern_search` (MinimizePatternSearch.py actionInner, without the
 * writer): if [tolerance] is 0 or the input has more bits, run [patternSearchOptimize] and
 * normalize; otherwise the input is returned unchanged.
 * @throws IllegalStateException if the labeled order type changed (Python assert).
 */
suspend fun minimizePatternSearch(
    points: List<Pt>,
    maxTrials: Int = 2000,
    scale: Long = 1,
    blowup: Int = 0,
    tolerance: Int = 0,
    ticker: Ticker = Ticker(),
): PatternSearchResult {
    val n = points.size
    val colors = points.map { it.color }
    val bits0 = Pts.of(points).bits()
    if (tolerance == 0 || bits0 > tolerance) {
        val sl0 = lambdaOf(Pts.of(points))
        val r = patternSearchOptimize(points, maxTrials, scale, blowup, tolerance, ticker)
        val p = psNormalized(Array(n) { longArrayOf(r.points[it].x, r.points[it].y) })
        val res = List(n) { Pt(p[it][0], p[it][1], colors[it]) }
        check(sl0 != null && sameLambda(Pts.of(res), sl0))
        return PatternSearchResult(res, r.blowupLeft)
    }
    return PatternSearchResult(points, blowup)
}

// ---------------------------------------------------------------------------
// PatternSearch.my_pattern_search (pyotlib2/algorithms/pattern_search.py)
// ---------------------------------------------------------------------------

/** Result of [myPatternSearch] (Python dict {'X0', 'f0', 'iterations'}). */
class MyPatternSearchResult(val x0: DoubleArray, val f0: Double, val iterations: Int)

/** pyotlib2 `my_pattern_search_step`: one coordinate-wise step with step size s0. */
private fun myPatternSearchStep(f: (DoubleArray) -> Double, x0In: DoubleArray, s0In: Double): Pair<DoubleArray, Double> {
    var x0 = x0In
    var s0 = s0In
    val n = x0.size
    var f0 = f(x0)
    var xLast = x0.copyOf()
    var fLast = f0
    for (i in x0.indices) {
        val xNew = xLast.copyOf()
        for (sgn in intArrayOf(1, -1)) {
            xNew[i] = xLast[i] + sgn * s0
            val fNew = f(xNew)
            if (fNew < fLast) {
                xLast = xNew
                fLast = fNew
                break
            }
        }
    }
    fLast = f(xLast)
    if (fLast < f0) {
        val xl = xLast
        val prev = x0
        x0 = DoubleArray(n) { 2 * xl[it] - prev[it] }
        f0 = f(x0)
        if (f0 > fLast) {
            x0 = xLast
            f0 = fLast
        }
        s0 *= 1.1
    } else {
        s0 /= 2.0
    }
    return x0 to s0
}

/**
 * pyotlib2 `my_pattern_search` (old PatternSearch.my_pattern_search, used by the old
 * ScipyRealizationTester, not by the coordinate minimizers): iterate the step until
 * f0 == 0, s0 < [minStep] or [maxIter] iterations (null = no limit, as in the original:
 * then only these conditions or cancellation through [ticker] end it).
 */
suspend fun myPatternSearch(
    f: (DoubleArray) -> Double,
    x0: DoubleArray,
    s0: Double,
    minStep: Double = 1e-10,
    maxIter: Int? = null,
    ticker: Ticker = Ticker(),
): MyPatternSearchResult {
    var x = x0
    var s = s0
    var i = 0
    while (i != maxIter) {
        ticker.tick(2L * x.size + 2)
        i += 1
        val f0 = f(x)
        if (f0 == 0.0) break
        if (s < minStep) break
        check(s > 0)
        val (nx, ns) = myPatternSearchStep(f, x, s)
        x = nx
        s = ns
    }
    return MyPatternSearchResult(x, f(x), i)
}
