package org.bytefred.pocoview.core

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.random.Random

/*
 * Literal ports of the old pyotlib "property optimization flows" (Python 2 scripts in
 * pyotlib2/old/pyotlib/scripts): pyotlib.PointWalk.py, pyotlib.PointWalkLocCr.py,
 * pyotlib.PointMove.py and pyotlib.BrownianMinimization.py. Same algorithms, constants,
 * defaults and control flow (quirks included, marked "Quirk (original)").
 *
 * Not ported:
 *  - pyotlib.PointWalkAbstr.py walks on abstract order types (triple flips, no coordinates).
 *
 * Common replacements (all flows):
 *  - The property is evaluated only through `count` (lower = better); output files, prints,
 *    AutoFileCreator, split and stop_if_improved are replaced by [SearchReport]: the GUI
 *    decides when to stop.
 *  - Python's `random` is the `rnd` parameter (same distributions, different sequences).
 *  - Python integers are unbounded; here every configuration that is counted or reported lies
 *    within [COORD_LIMIT]. Probes of the boundary searches use [orientWide] (exact up to
 *    [WIDE_LIMIT]); a probe beyond WIDE_LIMIT ends the direction like the original's
 *    "step > 100*diam" escape, a new configuration beyond COORD_LIMIT (after the original's
 *    normalization) is skipped, and "goodbits" scales to at most [MAX_GOOD_BITS] bits.
 *  - Point colors are kept by index (some originals drop the coloring of the working set).
 *  - List parameters (candidates) are not offered: all points are candidates (the default).
 */

/** Result channel of a search: called for every configuration a flow accepts. */
fun interface SearchReport {
    /** [value] = property value of [points] (lower is better); return false to stop the search. */
    suspend fun report(points: List<Pt>, value: Long): Boolean
}

/** A flow parameter; [default] is an Int, Double or Boolean (the type of the parameter). */
class SearchParam(val name: String, val default: Any, val description: String) {
    init {
        require(default is Int || default is Double || default is Boolean) { "unsupported parameter type: $name" }
    }
}

abstract class PropertySearchFlow(val id: String, val label: String) {
    abstract val params: List<SearchParam>

    /**
     * Runs the search from [start] (expected in general position). Every accepted configuration
     * is passed to [report]; the run ends when report returns false or the flow terminates.
     * @throws IllegalArgumentException for parameters / inputs the original asserts on, or a
     *   start whose (normalized) coordinates exceed [COORD_LIMIT].
     */
    abstract suspend fun run(
        start: List<Pt>,
        count: suspend (List<Pt>) -> Long,
        values: Map<String, Any>,
        rnd: Random,
        report: SearchReport,
    )

    private fun value(values: Map<String, Any>, name: String): Any =
        values[name] ?: params.first { it.name == name }.default

    protected fun Map<String, Any>.int(name: String): Int = (value(this, name) as Number).toInt()

    protected fun Map<String, Any>.double(name: String): Double = (value(this, name) as Number).toDouble()

    protected fun Map<String, Any>.bool(name: String): Boolean = value(this, name) as Boolean
}

val PROPERTY_SEARCH_FLOWS: List<PropertySearchFlow> =
    listOf(PointWalkFlow, PointWalkLocCrFlow, PointMoveFlow, BrownianMinimizationFlow)

/** Largest "goodbits": a set with b bits has diameter <= 2^b - 1, i.e. fits COORD_LIMIT for b <= 29. */
const val MAX_GOOD_BITS = 29

// ---------------------------------------------------------------------------
// pyotlib PointSet helpers
// ---------------------------------------------------------------------------

private data class Dir(val dx: Long, val dy: Long)

/** The direction sets of the scripts: all sign/swap variants of the given (k, l). */
private fun directionSet(base: List<Pair<Long, Long>>): List<Dir> {
    val res = LinkedHashSet<Dir>()
    for ((k, l) in base) res += listOf(
        Dir(k, l), Dir(l, k), Dir(-l, k), Dir(-k, l), Dir(-k, -l), Dir(-l, -k), Dir(l, -k), Dir(k, -l),
    )
    return res.toList()
}

/** pyotlib PointSet.normalized: translate so that min x = min y = 0. */
private fun normalized(p: List<Pt>): List<Pt> {
    val mx = p.minOf { it.x }
    val my = p.minOf { it.y }
    return p.map { Pt(it.x - mx, it.y - my, it.color) }
}

/** pyotlib mymath.ceil_log2: smallest b with x <= 2^b. */
private fun ceilLog2(x: Long): Int = if (x <= 1) 0 else 64 - (x - 1).countLeadingZeroBits()

/** pyotlib PointSet.getBits: ceil_log2(1 + diameter), diameter = [extent]. */
private fun bitsOld(p: List<Pt>): Int = ceilLog2(1 + extent(p))

/** The scripts' `diam`: max |coordinate|. */
private fun maxAbs(p: List<Pt>): Long = p.maxOf { max(abs(it.x), abs(it.y)) }

private fun within(p: List<Pt>, limit: Long) = p.all { abs(it.x) <= limit && abs(it.y) <= limit }

private fun withinWide(q: Pt) = abs(q.x) <= WIDE_LIMIT && abs(q.y) <= WIDE_LIMIT

/**
 * "dbits = goodbits - getBits(); if dbits > 0: scale by 2**dbits", with goodbits capped at
 * [MAX_GOOD_BITS] so that the result fits COORD_LIMIT after normalization.
 */
private fun scaleToGoodBits(p: List<Pt>, goodbits: Int): List<Pt> {
    val dbits = minOf(goodbits, MAX_GOOD_BITS) - bitsOld(p)
    if (dbits <= 0) return p
    return p.map { Pt(it.x shl dbits, it.y shl dbits, it.color) }
}

/** pyotlib PointSetOptimizer.sameOT_: orientation(a, b, i) equal in both sets for all pairs a, b != i. */
private fun sameOtAt(p1: List<Pt>, p2: List<Pt>, i: Int): Boolean {
    for (a in p1.indices) for (b in a + 1 until p1.size) {
        if (a == i || b == i) continue
        if (orientWide(p1[a], p1[b], p1[i]) != orientWide(p2[a], p2[b], p2[i])) return false
    }
    return true
}

/** pyotlib PointSet.hasCollinearPointsAtPoint (duplicates of p[i] count as collinear). */
private fun hasCollinearPointsAtPoint(p: List<Pt>, i: Int): Boolean {
    for (a in p.indices) for (b in a + 1 until p.size) {
        if (a == i || b == i) continue
        if (orientWide(p[a], p[b], p[i]) == 0) return true
    }
    return false
}

/** `len(set(PS.points)) == PS.n`. */
private fun distinct(p: List<Pt>) = p.map { it.x to it.y }.toSet().size == p.size

private fun requireStart(p: List<Pt>) =
    require(within(p, COORD_LIMIT)) { "coordinates (after normalization) exceed COORD_LIMIT" }

/**
 * Key of `md5(SL.toString())`: the small lambda matrix and the colors (the string pyotlib
 * hashes), condensed to 128 bits (two 64-bit hashes; md5 there also only saves memory).
 */
private data class OtKey(val h1: Long, val h2: Long)

private fun otKey(p: List<Pt>): OtKey {
    var h1 = -0x340d631b7bdddcdbL // FNV-1a 64 offset basis
    var h2 = 0x2545f4914f6cdd1dL
    fun add(v: Int) {
        h1 = (h1 xor v.toLong()) * 0x100000001b3L
        h2 = (h2 + v + 1) * -0x61c8864680b583ebL
        h2 = h2 xor (h2 ushr 31)
    }
    for ((i, row) in smallLambda(p).withIndex()) {
        for (v in row) add(v)
        add(p[i].color?.hashCode() ?: 0)
    }
    return OtKey(h1, h2)
}

/** SmallLambda.toString of the lambda matrix [l] relabeled by [lab] (transposed if [mirrored]). */
private fun lambdaString(l: Array<IntArray>, colors: List<String?>, lab: IntArray, mirrored: Boolean): String {
    val n = l.size
    val sb = StringBuilder()
    for (i in 0 until n) {
        for (j in 0 until n) sb.append(if (mirrored) l[lab[j]][lab[i]] else l[lab[i]][lab[j]]).append(' ')
        sb.append(colors[lab[i]] ?: "").append('\n')
    }
    return sb.toString()
}

/** SmallLambda.toString of the labeled order type. */
private fun labeledString(p: List<Pt>) = lambdaString(smallLambda(p), p.map { it.color }, IntArray(p.size) { it }, false)

/**
 * SmallLambda.getLexMin().toString(): the lexicographically smallest lambda matrix over the
 * natural labelings (from each extremal point) and their mirrored reverses.
 * Quirk (original): the comparison starts with the identity labeling, which is kept if it
 * is smaller than every natural labeling.
 */
private fun lexMinString(p: List<Pt>): String {
    val n = p.size
    val l = smallLambda(p)
    // getExtremalPoints
    val extremal = LinkedHashSet<Int>()
    for (i in 0 until n) for (j in 0 until i) if (l[i][j] == 0 || l[i][j] == n - 2) { extremal += i; extremal += j }
    check(extremal.isNotEmpty()) // there must be extremal points!

    fun compare(m1: Boolean, lab1: IntArray, m2: Boolean, lab2: IntArray): Int {
        for (i in 0 until n) for (j in 0 until n) {
            val a = if (!m1) l[lab1[i]][lab1[j]] else l[lab1[j]][lab1[i]]
            val b = if (!m2) l[lab2[i]][lab2[j]] else l[lab2[j]][lab2[i]]
            if (a < b) return -1
            if (a > b) return +1
        }
        return 0
    }

    var minLab = IntArray(n) { it }
    var minMirrored = false
    for (p0 in extremal) {
        // getNaturalLabeling(p0)
        val cur = IntArray(n) { it }
        cur[0] = p0
        for (q in 0 until n) if (q != p0) cur[1 + l[p0][q]] = q
        if (compare(false, cur, minMirrored, minLab) < 0) { minLab = cur; minMirrored = false }
        val rcur = IntArray(n) { cur[(n - it) % n] }
        if (compare(true, rcur, minMirrored, minLab) < 0) { minLab = rcur; minMirrored = true }
    }
    return lambdaString(l, p.map { it.color }, minLab, minMirrored)
}

// ---------------------------------------------------------------------------
// pyotlib.PointWalk.py / pyotlib.PointWalkLocCr.py
// ---------------------------------------------------------------------------

/**
 * pyotlib.PointWalk.py: depth-first search over order types reached by moving one point.
 * Each stack entry (scaled to goodbits, normalized) tries its points in random order and, per
 * point, the 40 directions (k, l) in {(1,0),(1,1),(1,2),(1,3),(1,4),(1,6)} with all sign/swap
 * variants in random order: a doubling/halving search moves the point to the boundary of its
 * order-type cell (giving up beyond 100 * max|coordinate|), then up to 10 unit steps past it
 * until the point is distinct and not collinear with two others. The neighbor is accepted if
 * its value is <= the value of the entry it came from (equal values accepted); with siman
 * a worse one is accepted with probability exp(-(c - c0) / (c0 * T)), T *= dT on every
 * worse neighbor. Accepted neighbors whose labeled order type was not seen yet are pushed
 * and reported; the bottom entry is dropped when the stack exceeds trace. Ends when the
 * stack is empty.
 */
object PointWalkFlow : PropertySearchFlow("pointwalk", "PointWalk (DFS over point moves)") {
    override val params = listOf(
        SearchParam("trace", 10000, "length of trace when performing depth search (stack size limit)"),
        SearchParam("beautify", 0, "number of iterations when beautifying a configuration before searching it (beautifyPointSet)"),
        SearchParam("minimize", 0, "number of trials when minimizing a configuration after an improvement (minimizeIntegerCoordinates)"),
        SearchParam("goodbits", 30, "number of bits to use during calculations (configurations are scaled up to it; at most $MAX_GOOD_BITS here)"),
        SearchParam("acceleration", 0.0, "acceleration when searching (0..1): the boundary search starts with step 2^randint(0, acceleration*bits)"),
        SearchParam("siman", false, "simulated annealing: also accept a worse neighbor with probability exp(-(c-c0)/(c0*T))"),
        SearchParam("T0", 0.1, "initial temperature (siman)"),
        SearchParam("dT", 0.999, "temperature factor per worse neighbor (siman)"),
        SearchParam("symmetric", 0, "k > 0: keep the set k-fold rotationally symmetric around the origin (n % k must be 0 or 1; sets goodbits = 0)"),
    )

    override suspend fun run(start: List<Pt>, count: suspend (List<Pt>) -> Long, values: Map<String, Any>, rnd: Random, report: SearchReport) {
        val symmetric = values.int("symmetric")
        PointWalkSearch(
            locCr = false,
            n = start.size,
            trace = values.int("trace"),
            beautify = values.int("beautify"),
            minimize = values.int("minimize"),
            goodbits = if (symmetric != 0) 0 else values.int("goodbits"),
            acceleration = values.double("acceleration"),
            siman = values.bool("siman"),
            t0 = values.double("T0"),
            dT = values.double("dT"),
            symmetric = symmetric,
            directions = directionSet(listOf(1L to 0L, 1L to 1L, 1L to 2L, 1L to 3L, 1L to 4L, 1L to 6L)),
            count = count, rnd = rnd, report = report,
        ).run(start)
    }
}

/**
 * pyotlib.PointWalkLocCr.py: the PointWalk DFS written for the local crossing number (here:
 * the property given by `count`). Differences to [PointWalkFlow] (as in the original): a
 * neighbor is accepted only if its value is <= the best value found so far (not the value of
 * its parent), the 16 directions of (1,0),(1,1),(1,2), no simulated annealing / symmetry /
 * minimization, and the steps past the boundary only test collinearity at the moved point.
 * Its parameters k / empty / to_count only name the output file and are not offered.
 */
object PointWalkLocCrFlow : PropertySearchFlow("pointwalk-loccr", "PointWalkLocCr (DFS, accept <= best so far)") {
    override val params = listOf(
        SearchParam("trace", 10000, "length of trace when performing depth search (stack size limit)"),
        SearchParam("beautify", 0, "number of iterations when beautifying a configuration before searching it (beautifyPointSet)"),
        SearchParam("goodbits", 30, "number of bits to use during calculations (configurations are scaled up to it; at most $MAX_GOOD_BITS here)"),
        SearchParam("acceleration", 0.0, "acceleration when searching (0..1): the boundary search starts with step 2^randint(0, acceleration*bits)"),
    )

    override suspend fun run(start: List<Pt>, count: suspend (List<Pt>) -> Long, values: Map<String, Any>, rnd: Random, report: SearchReport) {
        PointWalkSearch(
            locCr = true,
            n = start.size,
            trace = values.int("trace"),
            beautify = values.int("beautify"),
            minimize = 0,
            goodbits = values.int("goodbits"),
            acceleration = values.double("acceleration"),
            siman = false,
            t0 = 0.0,
            dT = 1.0,
            symmetric = 0,
            directions = directionSet(listOf(1L to 0L, 1L to 1L, 1L to 2L)),
            count = count, rnd = rnd, report = report,
        ).run(start)
    }
}

private class WalkFrame(val points: List<Pt>, val key: OtKey, val depth: Int, val diam: Long, val todo: MutableList<Int>, val count: Long) {
    var i = 0
    var directions: MutableList<Dir> = mutableListOf()
}

/** State and phases of PointWalkScript (both scripts; [locCr] selects PointWalkLocCr's differences). */
private class PointWalkSearch(
    val locCr: Boolean,
    val n: Int,
    val trace: Int,
    val beautify: Int,
    val minimize: Int,
    val goodbits: Int,
    val acceleration: Double,
    val siman: Boolean,
    t0: Double,
    val dT: Double,
    val symmetric: Int,
    val directions: List<Dir>,
    val count: suspend (List<Pt>) -> Long,
    val rnd: Random,
    val report: SearchReport,
) {
    private val ticker = Ticker()
    private val otSet = HashSet<OtKey>()
    private val stack = ArrayDeque<WalkFrame>()
    private var mincount: Long? = null
    private var startcount: Long? = null
    private var temperature = t0
    private var stopped = false

    init {
        require(acceleration in 0.0..1.0) { "acceleration must be in [0, 1]" }
    }

    // actionInner
    suspend fun run(start: List<Pt>) {
        val sl = if (symmetric != 0) computeSymmetricPs(start, symmetric) else normalized(start)
        requireStart(sl)
        phase1(0, sl, null)
        while (stack.isNotEmpty() && !stopped) phase2()
    }

    // check order type, append to stack if it's a good one
    private suspend fun phase1(depth: Int, sl0: List<Pt>, parent: WalkFrame?) {
        var sl = sl0
        val currentcount = count(sl)
        val mc = mincount

        // step 2: check if worse neighbor
        if (mc != null) {
            if (locCr) {
                if (currentcount > mc) return
            } else if (currentcount > parent!!.count) {
                temperature *= dT
                // Deviation: Python raises ZeroDivisionError for parent.count == 0; here the
                // probability exp(-inf) = 0 rejects the neighbor.
                if (!siman || rnd.nextDouble() > exp(-(currentcount - parent.count).toDouble() / (parent.count * temperature))) return
            }
        }

        // step 3: check if better neighbor
        if (mc == null || currentcount < mc) {
            mincount = currentcount
            if (startcount == null) {
                startcount = currentcount
            } else if (minimize != 0) {
                // IMPROVED (the original writes the result here; it is reported below)
                sl = minimizeIntegerCoordinates(sl, maxTrials = minimize, sheering = true, rnd = rnd, ticker = ticker)
            }
        }

        // step 4. prepare for iterative search
        val key = otKey(sl)
        if (key in otSet) return // loop!

        var ps0 = if (beautify != 0) beautifyPointSet(sl, maxIter = beautify, goodBits = minOf(goodbits, MAX_GOOD_BITS), ticker = ticker)
        else scaleToGoodBits(sl, goodbits)
        if (symmetric == 0) ps0 = normalized(ps0)

        val frame = WalkFrame(ps0, key, depth, maxAbs(ps0), MutableList(n) { it }.apply { shuffle(rnd) }, currentcount)
        stack.addLast(frame)
        otSet.add(key)
        if (stack.size > trace) otSet.remove(stack.removeFirst().key)

        // Reported: the configuration as the original writes it (after minimize, before
        // beautify / scaling; same order type as the frame). The original writes only
        // improvements, before the loop check; an improvement is never a loop (every seen
        // configuration had a value >= the best one at the time), so reporting here covers them.
        if (!report.report(sl, currentcount)) stopped = true
    }

    // check neighbors
    private suspend fun phase2() {
        val f = stack.last()
        val accel = (acceleration * bitsOld(f.points)).toInt()

        // step 1. calculate directions for fixed i
        if (f.directions.isEmpty()) {
            if (f.todo.isEmpty()) {
                stack.removeLast()
                return
            }
            f.i = f.todo.removeAt(f.todo.lastIndex)
            f.directions = directions.toMutableList().apply { shuffle(rnd) }
        }

        // step 2. calculate neighbors in those directions
        val i = f.i
        val (dx, dy) = f.directions.removeAt(f.directions.lastIndex)
        var p = f.points.toMutableList()
        var x1 = p[i].x
        var y1 = p[i].y

        var step = 1L shl rnd.nextInt(0, accel + 1)
        var grow = true
        val bound = 100 * f.diam
        while (step > 0) {
            ticker.tick(n.toLong() * n)
            val q = Pt(x1 + step * dx, y1 + step * dy, p[i].color)
            if (!withinWide(q)) return // deviation: escaped beyond WIDE_LIMIT
            p[i] = q
            if (symmetric != 0) p = computeSymmetricPs(p, symmetric).toMutableList()
            if (sameOtAt(p, f.points, i)) {
                x1 = p[i].x
                y1 = p[i].y
                if (grow) step *= 2
            } else {
                step /= 2
                grow = false
            }
            if (step > bound) break
        }
        if (step > bound) return

        p[i] = Pt(x1, y1, p[i].color) // acceleration q = 0 of the original
        var s = 1
        while (true) {
            p[i] = Pt(p[i].x + dx, p[i].y + dy, p[i].color)
            if (symmetric != 0) p = computeSymmetricPs(p, symmetric).toMutableList()
            if ((locCr || distinct(p)) && !hasCollinearPointsAtPoint(p, i)) break
            s++
            if (s > 10) break
        }
        if (s > 10) return

        // step 3. test neighbor (normalized first: the original normalizes it in phase1, the
        // property does not depend on a translation)
        val neighbor = if (symmetric == 0) normalized(p) else p.toList()
        if (!within(neighbor, COORD_LIMIT)) return // deviation: does not fit exact Long arithmetic
        phase1(f.depth + 1, neighbor, f)
    }
}

// ---------------------------------------------------------------------------
// pyotlib.PointMove.py
// ---------------------------------------------------------------------------

/**
 * pyotlib.PointMove.py: one pass over the neighbors of the start (no iteration). For each
 * point and each of the 16 directions of (1,0),(1,1),(1,2) (fixed order), the doubling/halving
 * search moves the point to the boundary of its order-type cell (giving up beyond
 * 100 * max|coordinate|), then up to 10 unit steps past it until no collinearity at the
 * point. A neighbor whose labeled and lex-min order type were not reported yet and whose
 * value is <= the start value is reported. Quirk (original): the next direction of the same
 * point starts at the boundary position of the previous one.
 *
 * Deviations: the original relabels the start and every result to its lex-min labeling
 * (possibly mirrored, x and y swapped); here the search runs and reports in the given
 * labeling and frame, the lex-min form is only used for the duplicate test (the reported
 * order types are the same, found in a different point order). Python's set iteration order
 * of the directions is replaced by their generation order.
 */
object PointMoveFlow : PropertySearchFlow("pointmove", "PointMove (similar neighbors of the start)") {
    override val params = emptyList<SearchParam>()

    private val directions = directionSet(listOf(1L to 0L, 1L to 1L, 1L to 2L))

    override suspend fun run(start: List<Pt>, count: suspend (List<Pt>) -> Long, values: Map<String, Any>, rnd: Random, report: SearchReport) {
        val ticker = Ticker()
        val n = start.size
        val ots = HashSet<String>()

        val p0 = normalized(start)
        requireStart(p0)
        val ct0 = count(p0)
        if (!report.report(p0, ct0)) return
        ots.add(lexMinString(p0))

        val diam = maxAbs(p0)
        for (i in 0 until n) {
            val p = p0.toMutableList()
            var x1 = p[i].x
            var y1 = p[i].y
            val color = p[i].color

            for ((dx, dy) in directions) {
                var step = 1L
                var grow = true
                var escaped = false

                // search
                while (step > 0) {
                    ticker.tick(n.toLong() * n)
                    val q = Pt(x1 + step * dx, y1 + step * dy, color)
                    if (!withinWide(q)) { escaped = true; break } // deviation: beyond WIDE_LIMIT
                    p[i] = q
                    if (sameOtAt(p, p0, i)) {
                        x1 = q.x
                        y1 = q.y
                        if (grow) step *= 2
                    } else {
                        step /= 2
                        grow = false
                    }
                    if (step > 100 * diam) break
                }
                if (escaped || step > 100 * diam) continue

                p[i] = Pt(x1, y1, color)
                var s = 1L
                while (true) {
                    p[i] = Pt(x1 + s * dx, y1 + s * dy, color)
                    if (!hasCollinearPointsAtPoint(p, i)) break
                    s++
                    if (s > 10) break
                }
                if (s > 10) continue

                val cand = normalized(p)
                if (!within(cand, COORD_LIMIT)) continue // deviation: does not fit exact Long arithmetic
                val labeled = labeledString(cand)
                if (labeled in ots) continue
                val lexMin = lexMinString(cand)
                if (lexMin in ots) continue
                val ct = count(cand)
                if (ct > ct0) continue

                if (!report.report(cand, ct)) return
                ots.add(lexMin)
                ots.add(labeled)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// pyotlib.BrownianMinimization.py
// ---------------------------------------------------------------------------

/**
 * pyotlib.BrownianMinimization.py: random walk. Each trial moves a random point i by
 * (dx, dy) uniform in [-c, c]^2, c = randint(1, cmax), cmax = 5 + diam0 // 3 (or brownrange).
 * Moves creating a duplicate or a collinearity at i, or a larger value, are undone. A smaller
 * value is an improvement (normalize; stop unless continue). Without mindiam the walk stops
 * at value 0 and undoes moves beyond bounddiam; with mindiam (default) a move is kept only if
 * the diameter does not grow, and the walk ends after maxtrials trials without a smaller
 * diameter (a smaller diameter resets the trial counter). Equal values are kept. Without
 * mindiam the walk only ends by an improvement (continue off), value 0, or the GUI.
 *
 * Quirk (original): after an improvement with continue on, a move beyond bounddiam restores
 * the old point into the already normalized set (the result is a different configuration,
 * whose value is not recounted).
 * Deviations: `mindiam` has no default in the code (int(None) raises); the help text says
 * default 1, used here. `similar` only writes extra files and is not offered (every accepted
 * configuration is reported). A move leaving COORD_LIMIT is undone like an invalid one.
 */
object BrownianMinimizationFlow : PropertySearchFlow("brownian", "BrownianMinimization (random walk)") {
    override val params = listOf(
        SearchParam("mindiam", true, "minimize the diameter while the property does not get worse"),
        SearchParam("continue", false, "continue after an improvement of the property"),
        SearchParam("bounddiam", 0, "maximum diameter without mindiam (0 = none, -1 = start diameter)"),
        SearchParam("brownrange", 0, "maximal move range c (0 = 5 + diameter / 3)"),
        SearchParam("maxtrials", 10000, "with mindiam: stop after this many trials without a smaller diameter"),
        SearchParam("goodbits", 25, "without mindiam and bounddiam: scale up to this many bits first (at most $MAX_GOOD_BITS here)"),
        SearchParam("symmetric", 0, "k > 0: keep the set k-fold rotationally symmetric around the origin (n % k must be 0 or 1)"),
    )

    override suspend fun run(start: List<Pt>, count: suspend (List<Pt>) -> Long, values: Map<String, Any>, rnd: Random, report: SearchReport) {
        val mindiam = values.bool("mindiam")
        val cont = values.bool("continue")
        var bounddiam = values.int("bounddiam").toLong()
        val brownrange = values.int("brownrange").toLong()
        val maxtrials = values.int("maxtrials")
        val goodbits = values.int("goodbits")
        val symmetric = values.int("symmetric")
        val ticker = Ticker()
        val n = start.size
        fun randint(a: Long, b: Long) = rnd.nextLong(a, b + 1)
        fun sym(p: List<Pt>) = computeSymmetricPs(p, symmetric).toMutableList()

        var ps: MutableList<Pt> = start.toMutableList()
        if (symmetric != 0) ps = sym(ps)
        if (!mindiam && bounddiam == 0L) ps = scaleToGoodBits(ps, goodbits).toMutableList()
        if (symmetric == 0) ps = normalized(ps).toMutableList()
        requireStart(ps)
        var ct0 = count(ps.toList())
        var diam0 = extent(ps)
        if (bounddiam == -1L) bounddiam = diam0

        var trials = 0
        while (trials < maxtrials) {
            ticker.tick(n.toLong() * n)
            if (mindiam) trials++
            val i = rnd.nextInt(n)
            val p0 = ps[i]
            val cmax = if (brownrange == 0L) 5 + diam0 / 3 else brownrange
            val c = randint(1, cmax)
            val dx = randint(-c, c)
            val dy = randint(-c, c)
            ps[i] = Pt(p0.x + dx, p0.y + dy, p0.color)
            if (symmetric != 0) ps = sym(ps)

            fun backup() {
                ps[i] = p0
                if (symmetric != 0) ps = sym(ps)
            }

            if (!within(ps, COORD_LIMIT) || !distinct(ps) || hasCollinearPointsAtPoint(ps, i)) {
                backup()
                continue
            }

            val ct = count(ps.toList())
            if (ct > ct0) {
                backup()
                continue
            }

            var reported = false
            if (ct < ct0) {
                // IMPROVED PROPERTY (the original writes the normalized set)
                ct0 = ct
                if (symmetric == 0) ps = normalized(ps).toMutableList()
                if (!report.report(ps.toList(), ct)) return
                reported = true
                diam0 = extent(ps)
                if (!cont) return
            }

            if (!mindiam && ct == 0L) {
                if (!reported) report.report(ps.toList(), ct0)
                return
            }

            if (mindiam) {
                val diam = extent(ps)
                if (diam <= diam0) {
                    if (symmetric == 0) ps = normalized(ps).toMutableList()
                    if (diam < diam0) trials = 0
                    diam0 = diam
                    if (!reported && !report.report(ps.toList(), ct)) return
                } else {
                    backup()
                }
            } else {
                if (bounddiam != 0L && extent(ps) > bounddiam) {
                    backup() // Quirk (original): see the class comment
                } else if (!reported && !report.report(ps.toList(), ct)) {
                    return
                }
            }
        }

        // stop: the original writes the final (normalized) set with its count
        if (symmetric == 0) ps = normalized(ps).toMutableList()
        report.report(ps.toList(), count(ps.toList()))
    }
}
