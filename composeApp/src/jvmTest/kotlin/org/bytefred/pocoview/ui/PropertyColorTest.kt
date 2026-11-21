package org.bytefred.pocoview.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.bytefred.pocoview.core.ALL_PROPERTIES
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Every property gets its own default colour; the colour chooser lists no colour twice. */
class PropertyColorTest {
    @BeforeTest
    fun clearPrefs() = clearInMemoryPrefs()

    @Test
    fun defaultColoursAreDistinct() {
        val s = AppState(CoroutineScope(SupervisorJob() + Dispatchers.Default))
        val colours = ALL_PROPERTIES.filter { it.hasStructures }.groupBy { s.colorOf(it.id) }
        assertEquals(emptyList(), colours.values.filter { it.size > 1 }.map { g -> g.map { it.label } })
    }

    @Test
    fun chooserColoursAreDistinct() = assertEquals(CHOOSER_COLORS.size, CHOOSER_COLORS.toSet().size)
}
