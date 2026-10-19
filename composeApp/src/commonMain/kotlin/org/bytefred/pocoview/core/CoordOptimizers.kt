package org.bytefred.pocoview.core

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sqrt
import kotlin.random.Random

/*
 * Literal ports of the coordinate optimizers of pyotlib2 (pyotlib2/cli/commands.py):
 * minimize_coords, beautify_coords, beautify2_coords. Python's random module is replaced
 * by kotlin.random (same distributions, different sequences). Coordinates beyond
 * COORD_LIMIT (exact Long arithmetic) end a minimization run early, keeping its best result.
 */

/** pyotlib2 `_pts_get_bits`: the largest bit length of any |coordinate|. */
fun bitsOf(xs: LongArray, ys: LongArray): Int {
    var m = 0L
    for (v in xs) m = maxOf(m, abs(v))
    for (v in ys) m = maxOf(m, abs(v))
    return 64 - m.countLeadingZeroBits()
}

fun bitsOf(p: List<Pt>): Int = bitsOf(LongArray(p.size) { p[it].x }, LongArray(p.size) { p[it].y })

/** pyotlib2 `_pts_normalize`: translate so min x = min y = 0 (in place). */
private fun normalize(xs: LongArray, ys: LongArray) {
    val mx = xs.min()
    val my = ys.min()
    for (i in xs.indices) { xs[i] -= mx; ys[i] -= my }
}

private fun toPoints(xs: LongArray, ys: LongArray, colors: List<String?>) = List(xs.size) { Pt(xs[it], ys[it], colors[it]) }

private fun withinLimit(xs: LongArray, ys: LongArray) = xs.all { abs(it) <= COORD_LIMIT } && ys.all { abs(it) <= COORD_LIMIT }

private fun det(xa: Long, ya: Long, xb: Long, yb: Long, xc: Long, yc: Long) =
    xa * (yb - yc) + xb * (yc - ya) + xc * (ya - yb)

/**
 * pyotlib2 `_make_exit_validator`: checks, for a moved point i, only the exit triples
 * containing i (they determine the order type).
 */
private class ExitValidator(o: Orientations) {
    // combs[i] = list of (a, b, c, expected sign)
    private val combs: Array<MutableList<IntArray>> = Array(o.n) { ArrayList() }

    init {
        for ((e, ws) in exitEdgeWitnesses(o)) for (c in ws) {
            val (a, b) = e
            val t = intArrayOf(a, b, c, o[a, b, c])
            for (idx in intArrayOf(a, b, c)) combs[idx].add(t)
        }
    }

    fun isValid(xs: LongArray, ys: LongArray, i: Int): Boolean {
        for (t in combs[i]) {
            val d = det(xs[t[0]], ys[t[0]], xs[t[1]], ys[t[1]], xs[t[2]], ys[t[2]])
            if (t[3] > 0 && d <= 0) return false
            if (t[3] < 0 && d >= 0) return false
        }
        return true
    }
}

/**
 * pyotlib2 `minimize_coords` (from old pyotlib minimizeIntegerCoordinates): move each point
 * to even coordinates and halve; if a point resists, randomize; blow up x3 after
 * trials/2 failed attempts; optionally try sheering transforms first.
 */
suspend fun minimizeCoords(
    start: List<Pt>,
    trials: Int = 10,
    randomize: Int = 10,
    tolerance: Int = 0,
    sheering: Boolean = false,
    rnd: Random = Random.Default,
    ticker: Ticker = Ticker(),
): List<Pt> {
    val n = start.size
    val colors = start.map { it.color }
    val validator = ExitValidator(Orientations(start))
    fun randint(a: Long, b: Long) = rnd.nextLong(a, b + 1)

    fun moveToEven(xs: LongArray, ys: LongArray, i: Int): Boolean {
        val x = xs[i]
        val y = ys[i]
        if (x.mod(2L) == 0L && y.mod(2L) == 0L) return true
        val mx = x.mod(2L)
        val my = y.mod(2L)
        for ((nx, ny) in listOf(x + mx to y + my, x - mx to y + my, x + mx to y - my, x - mx to y - my)) {
            xs[i] = nx; ys[i] = ny
            if (validator.isValid(xs, ys, i)) return true
        }
        xs[i] = x; ys[i] = y
        return false
    }

    fun randomizePoint(xs: LongArray, ys: LongArray, i: Int, bits: Int) {
        val x = xs[i]
        val y = ys[i]
        for (e in bits downTo 1) {
            val m = 1L shl e
            repeat(10) {
                xs[i] = x + randint(-m, m); ys[i] = y + randint(-m, m)
                if (validator.isValid(xs, ys, i)) return
            }
        }
        xs[i] = x; ys[i] = y
    }

    /** Scale axes so both have the same range (improves convergence). */
    fun balance(xs: LongArray, ys: LongArray) {
        val xr = (xs.max() - xs.min()).let { if (it == 0L) 1L else it }
        val yr = (ys.max() - ys.min()).let { if (it == 0L) 1L else it }
        val mx = maxOf(1L, yr / xr)
        val my = maxOf(1L, xr / yr)
        for (i in xs.indices) { xs[i] *= mx; ys[i] *= my }
    }

    suspend fun runMinimize(xs: LongArray, ys: LongArray): Pair<List<Pt>, Int> {
        normalize(xs, ys)
        balance(xs, ys)
        var best = toPoints(xs, ys, colors)
        var bestBits = bitsOf(xs, ys)
        var it = 0
        while (it < trials) {
            ticker.tick(n.toLong() * n * 4)
            if (!withinLimit(xs, ys)) break
            val ok = (0 until n).all { moveToEven(xs, ys, it) }
            if (ok) {
                normalize(xs, ys)
                for (i in 0 until n) { xs[i] = xs[i].floorDiv(2L); ys[i] = ys[i].floorDiv(2L) }
                val bits = bitsOf(xs, ys)
                if (bits < bestBits) {
                    bestBits = bits
                    best = toPoints(xs, ys, colors)
                    it = 0
                    if (tolerance != 0 && bestBits <= tolerance) break
                }
            } else {
                val bits = bitsOf(xs, ys)
                repeat(randomize) {
                    for (i in 0 until n) randomizePoint(xs, ys, i, randint(0, maxOf(1, bits).toLong()).toInt())
                }
                it += 1
                if (it == trials / 2) {
                    // blow-up: scale x3 to escape local minimum
                    normalize(xs, ys)
                    for (i in 0 until n) { xs[i] *= 3; ys[i] *= 3 }
                    balance(xs, ys)
                }
            }
        }
        return best to bestBits
    }

    val xs0 = LongArray(n) { start[it].x }
    val ys0 = LongArray(n) { start[it].y }
    val candidates = mutableListOf(xs0 to ys0)
    if (sheering) {
        candidates += LongArray(n) { xs0[it] + ys0[it] } to ys0.copyOf()
        candidates += xs0.copyOf() to LongArray(n) { ys0[it] + xs0[it] }
        candidates += LongArray(n) { xs0[it] - ys0[it] } to ys0.copyOf()
        candidates += xs0.copyOf() to LongArray(n) { ys0[it] - xs0[it] }
    }
    var bestPts = start
    var bestBits = bitsOf(start)
    for ((cx, cy) in candidates) {
        val (p, b) = runMinimize(cx.copyOf(), cy.copyOf())
        if (b < bestBits) { bestBits = b; bestPts = p }
        if (tolerance != 0 && bestBits <= tolerance) break
    }
    val bx = LongArray(n) { bestPts[it].x }
    val by = LongArray(n) { bestPts[it].y }
    normalize(bx, by)
    return toPoints(bx, by, colors)
}

/**
 * trunc(g * 10 / (10 + it) * 2^-h * (10/9)^e) exactly when it fits into Long (Python's
 * `int(factor * grad_f[k])` with Fractions), otherwise in floating point.
 */
private fun scaledStep(g: Long, it: Int, h: Int, e: Int): Long {
    val num = g * 10
    if (e <= 18) {
        val p10 = 10.0.pow(e).toLong()
        val p9 = 9.0.pow(e).toLong()
        val den = (10L + it) shl h
        if (abs(num) <= Long.MAX_VALUE / p10 && den <= Long.MAX_VALUE / p9) return (num * p10) / (den * p9)
    }
    return (num.toDouble() / (10 + it) / 2.0.pow(h) * (10.0 / 9.0).pow(e)).toLong()
}

/**
 * pyotlib2 `beautify_coords` (from old pyotlib beautifyPointSet): for each coordinate,
 * binary-search the largest safe range [zl, zu], step towards the midpoints with a
 * convergence factor, backtracking and extrapolation.
 */
suspend fun beautifyCoords(start: List<Pt>, maxIter: Int = 100, goodBits: Int = 0, ticker: Ticker = Ticker()): List<Pt> {
    val n = start.size
    if (n < 3) return start
    val colors = start.map { it.color }
    val xs = LongArray(n) { start[it].x }
    val ys = LongArray(n) { start[it].y }
    normalize(xs, ys)
    var curBits = bitsOf(xs, ys)
    if (goodBits != 0 && curBits < goodBits) {
        val scale = 1L shl (goodBits - curBits)
        for (i in 0 until n) { xs[i] *= scale; ys[i] *= scale }
        curBits = goodBits
    }

    val o = Orientations(start)
    // only positively oriented triples (a < b, a < c)
    val orientations = ArrayList<IntArray>()
    for (a in 0 until n) for (b in 0 until n) for (c in 0 until n)
        if (a < b && a < c && o[a, b, c] > 0) orientations.add(intArrayOf(a, b, c))
    // per-coordinate index: orientations involving coordinate i (x) or i + n (y)
    val oriByCoord = Array(2 * n) { ArrayList<IntArray>() }
    for (t in orientations) for (idx in t) { oriByCoord[idx].add(t); oriByCoord[idx + n].add(t) }

    // Z = [x0, ..., x_{n-1}, y0, ..., y_{n-1}]
    var z = LongArray(2 * n) { if (it < n) xs[it] else ys[it - n] }
    fun o3(z: LongArray, a: Int, b: Int, c: Int) =
        z[a] * z[b + n] - z[a + n] * z[b] + z[b] * z[c + n] - z[b + n] * z[c] + z[c] * z[a + n] - z[c + n] * z[a]
    fun isValidCoord(z: LongArray, coord: Int) = oriByCoord[coord].all { o3(z, it[0], it[1], it[2]) >= 1 }
    fun allValid(z: LongArray) = orientations.all { o3(z, it[0], it[1], it[2]) > 0 }

    var minDiff = 2L * n * (1L shl curBits)
    var stopTimer = 5
    for (it in 1..maxIter) {
        ticker.tick(orientations.size.toLong() * 4 + 2L * n * curBits * n * n)
        val z0 = z.copyOf()
        val grad = LongArray(2 * n)
        for (coord in 0 until 2 * n) {
            val c0 = z[coord]
            var zl = c0
            var zu = c0
            for (bit in curBits downTo 0) {
                z[coord] = zl - (1L shl bit)
                if (isValidCoord(z, coord)) zl = z[coord]
                z[coord] = zu + (1L shl bit)
                if (isValidCoord(z, coord)) zu = z[coord]
            }
            z[coord] = c0
            grad[coord] = (zl + zu).floorDiv(2L) - c0
        }

        // backtrack until valid: factor = 2^-h
        var h = 0
        var found = false
        while (h < 20) {
            for (k in 0 until 2 * n) z[k] = z0[k] + scaledStep(grad[k], it, h, 0)
            if (allValid(z)) { found = true; break }
            h++
        }
        if (!found) { z = z0; break }

        // try to extrapolate further: factor = 2^-h * (10/9)^e (capped at 2^20)
        var e = 0
        while (2.0.pow(-h) * (10.0 / 9.0).pow(e) < 2.0.pow(20)) {
            e++
            for (k in 0 until 2 * n) z[k] = z0[k] + scaledStep(grad[k], it, h, e)
            if (!allValid(z)) { e--; break }
        }
        if (2.0.pow(-h) * (10.0 / 9.0).pow(e) > 1) { h = 0; e = 0 }
        for (k in 0 until 2 * n) z[k] = z0[k] + scaledStep(grad[k], it, h, e)

        var diffs = 0L
        for (k in 0 until 2 * n) diffs += abs(z[k] - z0[k])
        if (diffs < minDiff) minDiff = diffs else stopTimer = 8
        stopTimer -= 1
        if (diffs == 0L || stopTimer < 0) break
    }

    val nx = LongArray(n) { z[it] }
    val ny = LongArray(n) { z[it + n] }
    normalize(nx, ny)
    val res = toPoints(nx, ny, colors)
    return if (withinLimit(nx, ny) && isGeneralPosition(res) && orderTypeSignature(res) == orderTypeSignature(start)) res
    else start
}

/**
 * pyotlib2 `beautify2_coords` (from old pyotlib beautify2): Nelder-Mead on a quality
 * function (exit-triple "thickness", spread of the distances, scale) with the convex hull
 * fixed; rounded to integers (2^20) after each run, optionally followed by [minimizeCoords].
 */
suspend fun beautify2Coords(
    start: List<Pt>,
    iter1: Int = 5,
    iter2: Int = 2000,
    goodBits: Int = 0,
    minimize: Boolean = true,
    norm: Int = 1,
    epsilon: Double = 1e-4,
    rnd: Random = Random.Default,
    ticker: Ticker = Ticker(),
): List<Pt> {
    val n = start.size
    if (n < 3) return start
    val colors = start.map { it.color }
    val sig = orderTypeSignature(start)
    val o = Orientations(start)
    val exitTrps = exitTriples(o)
    val hull = convexHull(start)

    var xs = LongArray(n) { start[it].x }
    var ys = LongArray(n) { start[it].y }
    normalize(xs, ys)
    val curBits = bitsOf(xs, ys)
    if (goodBits != 0 && curBits < goodBits) {
        val scale = 1L shl (goodBits - curBits)
        for (i in 0 until n) { xs[i] *= scale; ys[i] *= scale }
    }

    var z0 = DoubleArray(2 * n)
    val nd = norm.toDouble()
    fun quality(z: DoubleArray): Double {
        // fix hull points
        for (i in hull) { z[i] = z0[i]; z[i + n] = z0[i + n] }
        val zmax = z.maxOf { abs(it) }.let { if (it == 0.0) 1.0 else it }
        fun x(i: Int) = z[i]
        fun y(i: Int) = z[i + n]
        fun o3(a: Int, b: Int, c: Int) = x(a) * y(b) - y(a) * x(b) + x(b) * y(c) - y(b) * x(c) + x(c) * y(a) - y(c) * x(a)
        fun d2(a: Int, b: Int) = (x(a) - x(b)).pow(2) + (y(a) - y(b)).pow(2)
        fun perim(a: Int, b: Int, c: Int) = sqrt(d2(a, b)) + sqrt(d2(b, c)) + sqrt(d2(c, a))
        val f3 = exitTrps.map { (a, b, c) ->
            val p = perim(a, b, c)
            if (p > 0) o[a, b, c] * o3(a, b, c) / p else 0.0
        }
        if (f3.isEmpty() || f3.min() <= 0) return Double.POSITIVE_INFINITY
        val d2s = ArrayList<Double>(n * (n - 1) / 2)
        for (a in 0 until n) for (b in a + 1 until n) d2s.add(d2(a, b))
        if (d2s.isEmpty() || d2s.min() <= 0) return Double.POSITIVE_INFINITY
        val error1 = f3.sumOf { 1.0 / it.pow(nd) }.pow(1.0 / nd)
        val error2 = (d2s.sumOf { it.pow(nd) } * d2s.sumOf { 1.0 / it.pow(nd) }).pow(1.0 / nd)
        val error3 = abs(ln(zmax))
        return error1 * error2 * (error3 + 1e-9)
    }

    fun toInteger(z: DoubleArray): LongArray {
        val scale = z.maxOf { abs(it) }.let { if (it == 0.0) 1.0 else it }
        val factor = 2.0.pow(20) / scale
        return LongArray(z.size) { round(z[it] * factor).toLong() } // round half to even, as Python
    }

    repeat(iter1) {
        z0 = DoubleArray(2 * n) { if (it < n) xs[it].toDouble() else ys[it - n].toDouble() }
        val sol = nelderMead(::quality, z0, xtol = epsilon, ftol = epsilon, maxIter = iter2, maxFun = 50 * iter2, ticker = ticker)
        // restore hull
        for (i in hull) { sol[i] = z0[i]; sol[i + n] = z0[i + n] }
        val zi = toInteger(sol)
        val nx = LongArray(n) { zi[it] }
        val ny = LongArray(n) { zi[it + n] }
        normalize(nx, ny)
        val cand = toPoints(nx, ny, colors)
        // verify OT preserved
        if (isGeneralPosition(cand) && orderTypeSignature(cand) == sig) {
            var p = cand
            if (minimize) p = minimizeCoords(p, trials = 10, randomize = 10, rnd = rnd, ticker = ticker)
            xs = LongArray(n) { p[it].x }
            ys = LongArray(n) { p[it].y }
        }
    }
    normalize(xs, ys)
    val res = toPoints(xs, ys, colors)
    return if (withinLimit(xs, ys) && isGeneralPosition(res) && orderTypeSignature(res) == sig) res else start
}
