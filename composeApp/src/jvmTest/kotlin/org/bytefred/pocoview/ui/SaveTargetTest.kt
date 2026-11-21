package org.bytefred.pocoview.ui

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Desktop save: an existing file is replaced only after a yes; no goes back to the chooser. */
class SaveTargetTest {
    private val dir = createTempDirectory("pocoview-save").toFile().apply { deleteOnExit() }
    private val existing = File(dir, "set.json").apply { writeText("{}"); deleteOnExit() }

    /** Runs [chooseSaveTarget] with the chooser answers [picks] (null: cancel) and the replace answers [replies]. */
    private fun choose(picks: List<File?>, replies: List<Boolean>, asked: MutableList<File>): File? {
        val p = picks.iterator()
        val r = replies.iterator()
        return chooseSaveTarget("set.json", { p.next() }, { asked += it; r.next() })
    }

    @Test
    fun newFileIsTakenWithoutAsking() {
        val asked = mutableListOf<File>()
        assertEquals(File(dir, "new.json"), choose(listOf(File(dir, "new.json")), emptyList(), asked))
        assertEquals(emptyList(), asked)
    }

    @Test
    fun existingFileNeedsYes() {
        val asked = mutableListOf<File>()
        assertEquals(existing, choose(listOf(existing), listOf(true), asked))
        assertEquals(listOf(existing), asked)
        assertEquals("set.json already exists. Replace it?", replaceQuestion(existing))
    }

    @Test
    fun noGoesBackToTheChooser() {
        val asked = mutableListOf<File>()
        assertEquals(File(dir, "other.json"), choose(listOf(existing, File(dir, "other.json")), listOf(false), asked))
        assertNull(choose(listOf(existing, null), listOf(false), asked))
    }

    @Test
    fun theAddedExtensionCountsForExisting() {
        // "set" becomes set.json, which exists
        val asked = mutableListOf<File>()
        assertEquals(existing, choose(listOf(File(dir, "set")), listOf(true), asked))
        assertEquals(listOf(existing), asked)
    }
}
