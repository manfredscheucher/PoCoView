package org.bytefred.pocoview.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class TermsPrivacyTest {
    @Test
    fun eachLinkLabelOccursOnceInItsParagraph() {
        assertEquals(1, PRIVACY_PARAGRAPHS.sumOf { Regex(PRIVACY_LINK_LABEL).findAll(it).count() })
        assertEquals(1, Regex(FEEDBACK_LINK_LABEL).findAll(TERMS_PARAGRAPH).count())
    }

    @Test
    fun theDraftsSiteLabelIsNotShown() {
        assertFalse(PRIVACY_PARAGRAPHS.any { "Compose sites only" in it })
    }
}
