package org.bytefred.pocoview.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The shared open-menu state of the menu bar (menuAfterTitleClick). */
class MenuBarTest {

    @Test
    fun clickOpensAndSwitchesMenus() {
        assertEquals("View", menuAfterTitleClick(null, "View", justClosed = false))
        // one click on another title switches straight to that menu
        assertEquals("Edit", menuAfterTitleClick("View", "Edit", justClosed = false))
    }

    @Test
    fun clickOnTheOpenTitleCloses() {
        assertNull(menuAfterTitleClick("View", "View", justClosed = false))
        // the press already dismissed the menu (outside click): the release must not reopen it
        assertNull(menuAfterTitleClick(null, "View", justClosed = true))
    }
}
