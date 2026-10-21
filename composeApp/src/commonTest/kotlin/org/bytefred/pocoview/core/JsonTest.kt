package org.bytefred.pocoview.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class JsonTest {
    @Test
    fun parsesValues() {
        assertEquals(mapOf("a" to listOf(1L, -2L, 1.5, "x\u00e9", true, null)), parseJson("""{"a": [1, -2, 1.5, "x\u00e9", true, null]}"""))
    }

    @Test
    fun rejectsBadInput() {
        for (bad in listOf("007", "\"\\u+041\"", "\"\\u12\"", "{\"a\":1,\"a\":2}", "[1,]", "[1] 2", "[" .repeat(100) + "]".repeat(100)))
            assertFailsWith<FormatException>(bad) { parseJson(bad) }
        assertEquals(0L, parseJson("0"))
    }

    @Test
    fun deepNestingIsAnErrorNotACrash() {
        assertFailsWith<FormatException> { importPointSet("{\"points\": " + "[".repeat(5000) + "]".repeat(5000) + "}") }
    }

    @Test
    fun colourMustBeOneWord() {
        assertFailsWith<FormatException> { importPointSet("""{"points": [[0, 0, "dark red"], [1, 0], [0, 1]]}""") }
        assertFailsWith<FormatException> { importPointSet("""{"points": [[0, 0, ""], [1, 0], [0, 1]]}""") }
        assertEquals("R", importPointSet("""{"points": [[0, 0, "R"], [1, 0], [0, 1]]}""")[0].color)
    }
}
