package com.kunotes.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LineEndingTest {
    @Test
    fun `detects style from first line break`() {
        assertEquals(LineEnding.LF, LineEnding.detect("a\nb"))
        assertEquals(LineEnding.CRLF, LineEnding.detect("a\r\nb"))
        assertEquals(LineEnding.LF, LineEnding.detect("no break"))
        assertEquals(LineEnding.LF, LineEnding.detect("\nstarts with break"))
        assertEquals(LineEnding.LF, LineEnding.detect(""))
    }

    @Test
    fun `round trip keeps crlf files identical`() {
        val original = "# Title\r\n\r\nBody\r\n"
        val style = LineEnding.detect(original)
        val inEditor = LineEnding.normalize(original)
        assertEquals("# Title\n\nBody\n", inEditor)
        assertEquals(original, style.apply(inEditor))
    }

    @Test
    fun `lf files are unchanged`() {
        val original = "a\nb\n"
        assertEquals(original, LineEnding.detect(original).apply(LineEnding.normalize(original)))
    }

    @Test
    fun `round trip keeps emoji and cjk text`() {
        val original = "日本語 👩‍💻\r\n🇮🇩\r\n"
        val style = LineEnding.detect(original)
        assertEquals(LineEnding.CRLF, style)
        assertEquals(original, style.apply(LineEnding.normalize(original)))
    }
}
