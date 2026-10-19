package org.bytefred.ordertypes.core

import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.random.Random

/**
 * Parameters of the qtvis minimizer (`GraphWidget::tryToMinimizeMeta`); the defaults are
 * the ones of the qtvis "try to minimize [Enter]" button.
 */
data class CellHopParams(
    val trials: Int = 3,
    val maxFailIterations: Int = 2,
    val maxIterations: Int = 6,
    val eps: Int = 3,
    val alpha: Double = 1.05,
    val onlyLastPoint: Boolean = false,
)

/** Number of directions per half turn (qtvis `DIRECTION_COUNT`). */
private const val DIRECTION_COUNT = 12

/**
 * Port of the qtvis greedy cell-hopping minimizer (pyotlib2/old/qtvis2/qtvis/graphwidget.cpp,
 * `tryToMinimizeMeta`, `tryToMinimize`, `lineSearch`, `shuffle`).
 *
 * qtvis works on a canvas [0, max_val); here the canvas is the bounding box of the start
 * configuration ([box], inclusive). [count] evaluates the property to minimize; it is only
 * called on configurations in general position: a probe or random restart that is not in
 * general position counts as infinitely bad (qtvis would count it anyway, but the counts are
 * undefined there).
 */
class CellHopMinimizer(
    private val count: suspend (List<Pt>) -> Long,
    private val params: CellHopParams = CellHopParams(),
    private val rnd: Random = Random.Default,
    /** called with every accepted configuration (qtvis moves the nodes live) */
    private val onProgress: (List<Pt>) -> Unit = {},
) {
    private lateinit var box: LongArray // minX, maxX, minY, maxY

    private fun inRange(x: Double, y: Double) = x >= box[0] && x <= box[1] && y >= box[2] && y <= box[3]

    private fun toPt(x: Double, y: Double, color: String?) = Pt(floor(x).toLong(), floor(y).toLong(), color)

    private suspend fun countOrWorst(p: List<Pt>) = if (isGeneralPosition(p)) count(p) else Long.MAX_VALUE

    /**
     * `tryToMinimizeMeta`: repeats [tryToMinimize]; returns the first configuration with a
     * smaller count than the start, or null (start restored) when all trials failed.
     */
    suspend fun minimize(start: List<Pt>): List<Pt>? {
        require(start.size >= 3 && isGeneralPosition(start))
        box = longArrayOf(start.minOf { it.x }, start.maxOf { it.x }, start.minOf { it.y }, start.maxOf { it.y })
        val startCount = count(start)
        if (startCount == 0L) return start
        var trials = params.trials
        var i = 0
        var j = 0
        var cur = start
        while (trials > 0) {
            i++; j++
            val (next, success) = tryToMinimize(cur)
            cur = next
            if (success) {
                i = 0
                if (count(cur) < startCount) return cur
            }
            if (i >= params.maxFailIterations || j >= params.maxIterations) {
                // try another position: restart from the start configuration with one point moved randomly
                i = 0; j = 0; trials--
                var maxLoop = 1000
                while (--maxLoop > 0) {
                    cur = shuffle(start, if (params.onlyLastPoint) start.size - 1 else rnd.nextInt(start.size))
                    if (countOrWorst(cur) < startCount * params.alpha + params.eps) break
                }
                if (maxLoop == 0) break // randomisation search failed too often for the given eps and alpha
                onProgress(cur)
            }
        }
        onProgress(start)
        return null
    }

    /**
     * `tryToMinimize` (one iteration): for every point and 24 directions, find how far the
     * point can move inside its cell ([lineSearch]) and probe just beyond the cell wall
     * (distance + 3, 2, 1, 0); keep the probe if the count does not get worse.
     * Returns the configuration and whether the count decreased.
     */
    private suspend fun tryToMinimize(start: List<Pt>): Pair<List<Pt>, Boolean> {
        val p = start.toMutableList()
        var currentCount = count(p)
        var success = false
        val deltaAngle = 3.1415 / DIRECTION_COUNT // literally as in qtvis (not PI)
        for (i in p.indices) {
            val cx = p[i].x.toDouble()
            val cy = p[i].y.toDouble()
            for (d in 0 until 2 * DIRECTION_COUNT) {
                val dx = cos(deltaAngle * d)
                val dy = sin(deltaAngle * d)
                val distance = lineSearch(p, i, cx, cy, dx, dy)
                val others = p.filterIndexed { k, _ -> k != i }
                var q = p[i]
                for (width in 3 downTo 0) {
                    val x = cx + dx * (distance + width)
                    val y = cy + dy * (distance + width)
                    q = toPt(x, y, p[i].color)
                    if (canAdd(others, q) && inRange(x, y)) break
                }
                val probe = p.toMutableList().also { it[i] = q }
                val newCount = countOrWorst(probe)
                if (newCount <= currentCount) {
                    if (newCount < currentCount) success = true
                    currentCount = newCount
                    p[i] = q
                    onProgress(p.toList())
                }
            }
        }
        return p.toList() to success
    }

    /**
     * `lineSearch`: the largest distance found from (cx, cy) in direction (dx, dy) at which
     * point [i] keeps the order type of [p] (no collinearity, inside the canvas).
     */
    private fun lineSearch(p: List<Pt>, i: Int, cx: Double, cy: Double, dx: Double, dy: Double): Double {
        var alpha = 3.0
        var validDistance = 0.0
        var distance = 1.0
        while (true) {
            val x = cx + dx * distance
            val y = cy + dy * distance
            if (inRange(x, y) && sameOrderTypeAfterMove(p, i, toPt(x, y, p[i].color))) {
                validDistance = distance
                alpha *= 1.3
                distance += alpha
            } else if (alpha > 1) {
                alpha /= 3
                distance = validDistance + alpha
            } else {
                break
            }
        }
        return validDistance
    }

    /** `shuffle(point)`: puts one point at a uniformly random canvas position. */
    private fun shuffle(p: List<Pt>, i: Int): List<Pt> = p.toMutableList().also {
        it[i] = Pt(rnd.nextLong(box[0], box[1] + 1), rnd.nextLong(box[2], box[3] + 1), p[i].color)
    }
}
