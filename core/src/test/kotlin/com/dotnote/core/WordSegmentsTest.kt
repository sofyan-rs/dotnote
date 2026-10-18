package com.dotnote.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WordSegmentsTest {
    private fun words(text: String): List<String> = WordSegments.split(text).map { text.substring(it) }

    @Test
    fun `splits like unicode word boundaries`() {
        assertEquals(listOf("one", ",", " ", "two", "  ", "three"), words("one, two  three"))
        assertEquals(listOf("don't", " ", "3.14", " ", "a_b", " ", "1,000"), words("don't 3.14 a_b 1,000"))
        assertEquals(listOf("hi", ".", ".", ".", "\r\n", "next"), words("hi...\r\nnext"))
        assertEquals(emptyList<String>(), words(""))
    }

    @Test
    fun `cjk emoji and flags`() {
        assertEquals(listOf("日", "本", "語", "で", "す", " ", "カタカナ"), words("日本語です カタカナ"))
        assertEquals(
            listOf("👩‍💻", "👍🏽", "🇮🇩", "🇯🇵", " ", "wörld"),
            words("👩‍💻👍🏽🇮🇩🇯🇵 wörld"),
        )
        assertEquals(listOf("café", "!"), words("café!"), "accents stay in the word")
    }

    @Test
    fun `alphanumeric check matches rust`() {
        assertTrue(WordSegments.hasAlphanumeric("日"))
        assertTrue(WordSegments.hasAlphanumeric("²"))
        assertFalse(WordSegments.hasAlphanumeric("👩‍💻"))
        assertFalse(WordSegments.hasAlphanumeric(", "))
    }
}
