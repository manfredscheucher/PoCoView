package org.bytefred.pocoview.core

import kotlin.math.abs

/**
 * Nelder-Mead simplex minimization, a port of scipy's `optimize.fmin`
 * (`_minimize_neldermead`, non-adaptive: rho = 1, chi = 2, psi = 0.5, sigma = 0.5; initial
 * simplex: 5% per coordinate, 0.00025 for zero coordinates). [f] gets a copy of the point,
 * as in scipy. Stops after [maxIter] iterations, [maxFun] function evaluations, or when
 * both the simplex (xtol) and its values (ftol) have converged.
 */
suspend fun nelderMead(
    f: (DoubleArray) -> Double, x0: DoubleArray,
    xtol: Double = 1e-4, ftol: Double = 1e-4,
    maxIter: Int = 200 * x0.size, maxFun: Int = 200 * x0.size,
    ticker: Ticker = Ticker(),
): DoubleArray {
    val rho = 1.0
    val chi = 2.0
    val psi = 0.5
    val sigma = 0.5
    val nonzdelt = 0.05
    val zdelt = 0.00025
    val n = x0.size
    var fcalls = 0
    fun func(x: DoubleArray): Double {
        fcalls++
        return f(x.copyOf())
    }

    var sim = Array(n + 1) { k ->
        val y = x0.copyOf()
        if (k > 0) {
            if (y[k - 1] != 0.0) y[k - 1] = (1 + nonzdelt) * y[k - 1] else y[k - 1] = zdelt
        }
        y
    }
    var fsim = DoubleArray(n + 1) { func(sim[it]) }
    fun sortSimplex() {
        val ind = (0..n).sortedWith { a, b -> fsim[a].compareTo(fsim[b]) }
        sim = Array(n + 1) { sim[ind[it]] }
        fsim = DoubleArray(n + 1) { fsim[ind[it]] }
    }
    sortSimplex()

    var iterations = 1
    while (fcalls < maxFun && iterations < maxIter) {
        ticker.tick(n.toLong() * 50)
        var xdiff = 0.0
        var fdiff = 0.0
        for (k in 1..n) {
            for (d in 0 until n) xdiff = maxOf(xdiff, abs(sim[k][d] - sim[0][d]))
            fdiff = maxOf(fdiff, abs(fsim[0] - fsim[k]))
        }
        // maxOf propagates NaN (e.g. inf - inf), which never counts as converged, as in numpy
        if (xdiff <= xtol && fdiff <= ftol) break

        val xbar = DoubleArray(n) { d -> (0 until n).sumOf { sim[it][d] } / n }
        val worst = sim[n]
        fun point(a: Double, b: Double) = DoubleArray(n) { d -> a * xbar[d] + b * worst[d] }
        val xr = point(1 + rho, -rho)
        val fxr = func(xr)
        var doShrink = false
        if (fxr < fsim[0]) {
            val xe = point(1 + rho * chi, -rho * chi)
            val fxe = func(xe)
            if (fxe < fxr) { sim[n] = xe; fsim[n] = fxe } else { sim[n] = xr; fsim[n] = fxr }
        } else if (fxr < fsim[n - 1]) {
            sim[n] = xr; fsim[n] = fxr
        } else {
            if (fxr < fsim[n]) {
                val xc = point(1 + psi * rho, -psi * rho)
                val fxc = func(xc)
                if (fxc <= fxr) { sim[n] = xc; fsim[n] = fxc } else doShrink = true
            } else {
                val xcc = point(1 - psi, psi)
                val fxcc = func(xcc)
                if (fxcc < fsim[n]) { sim[n] = xcc; fsim[n] = fxcc } else doShrink = true
            }
            if (doShrink) for (j in 1..n) {
                sim[j] = DoubleArray(n) { d -> sim[0][d] + sigma * (sim[j][d] - sim[0][d]) }
                fsim[j] = func(sim[j])
            }
        }
        iterations++
        sortSimplex()
    }
    return sim[0]
}
