package com.kunotes.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GraphemesTest {
    /** The text split into user-perceived characters, walking forward with [Graphemes.nextBoundary]. */
    private fun clusters(text: String): List<String> {
        val result = mutableListOf<String>()
        var i = 0
        while (i < text.length) {
            val next = Graphemes.nextBoundary(text, i)
            result.add(text.substring(i, next))
            i = next
        }
        return result
    }

    /** The same, walking backward with [Graphemes.previousBoundary]. */
    private fun clustersBackward(text: String): List<String> {
        val result = mutableListOf<String>()
        var i = text.length
        while (i > 0) {
            val previous = Graphemes.previousBoundary(text, i)
            result.add(0, text.substring(previous, i))
            i = previous
        }
        return result
    }

    @Test
    fun `emoji sequences flags and accents are one character`() {
        val text = "a👩‍💻b🇮🇩🇯🇵é👍🏽x\r\ny日本한"
        val expected = listOf("a", "👩‍💻", "b", "🇮🇩", "🇯🇵", "é", "👍🏽", "x", "\r\n", "y", "日", "本", "한")
        assertEquals(expected, clusters(text))
        assertEquals(expected, clustersBackward(text))
    }

    @Test
    fun `offsets inside a character snap to its edges`() {
        val text = "a👩‍💻b"
        assertEquals(1, Graphemes.previousBoundary(text, 3))
        assertEquals(6, Graphemes.nextBoundary(text, 3))
        assertEquals(0, Graphemes.previousBoundary(text, -1))
        assertEquals(text.length, Graphemes.nextBoundary(text, 99))
    }
}
