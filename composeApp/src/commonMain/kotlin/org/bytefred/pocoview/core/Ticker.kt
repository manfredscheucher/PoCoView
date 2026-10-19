package org.bytefred.pocoview.core

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.yield

/**
 * Cooperative progress for long computations. The browser is single-threaded, so
 * heavy loops call [tick] regularly; every [every] units of work it checks for
 * cancellation and yields to the event loop so the UI stays responsive.
 */
class Ticker(private val every: Long = 200_000) {
    private var work = 0L

    suspend fun tick(units: Long = 1) {
        work += units
        if (work >= every) {
            work = 0
            currentCoroutineContext().ensureActive()
            yield()
        }
    }
}
