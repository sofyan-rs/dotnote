package com.dotnote.core

import com.dotnote.core.Cursor.Position
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CursorTest {
    @Test
    fun `line col starts at one`() {
        assertEquals(Position(1, 1), Cursor.lineCol("", 0))
        assertEquals(Position(1, 1), Cursor.lineCol("abc", 0))
        assertEquals(Position(1, 4), Cursor.lineCol("abc", 3))
    }

    @Test
    fun `line col counts lines`() {
        val text = "one\ntwo\nthree"
        assertEquals(Position(2, 1), Cursor.lineCol(text, 4))
        assertEquals(Position(2, 3), Cursor.lineCol(text, 6))
        assertEquals(Position(3, 6), Cursor.lineCol(text, text.length))
    }

    @Test
    fun `line col handles crlf`() {
        assertEquals(Position(2, 1), Cursor.lineCol("a\r\nb", 3))
    }

    @Test
    fun `columns count characters not bytes`() {
        val text = "日本語"
        assertEquals(Position(1, 4), Cursor.lineCol(text, text.length))
        assertEquals(Position(1, 2), Cursor.lineCol(text, 1))
        assertEquals(Position(1, 4), Cursor.lineCol(text, 999))
        assertEquals(Position(1, 1), Cursor.lineCol(text, -5))
    }

    @Test
    fun `columns count an emoji code point once and clamp inside a surrogate pair`() {
        val text = "a😀b"
        // "😀" is two UTF-16 units: index 1 and 2.
        assertEquals(Position(1, 2), Cursor.lineCol(text, 1))
        assertEquals(Position(1, 2), Cursor.lineCol(text, 2)) // inside the emoji: clamped back
        assertEquals(Position(1, 3), Cursor.lineCol(text, 3))
        assertEquals(Position(1, 4), Cursor.lineCol(text, text.length))

        val multiline = "😀\n日😀x"
        assertEquals(Position(2, 3), Cursor.lineCol(multiline, multiline.length - 1))
    }

    @Test
    fun `char count uses graphemes`() {
        assertEquals(0, Cursor.charCount(""))
        assertEquals(3, Cursor.charCount("abc"))
        assertEquals(4, Cursor.charCount("café"))
        assertEquals(4, Cursor.charCount("cafe\u0301")) // e + combining accent
        assertEquals(2, Cursor.charCount("👩‍💻🇮🇩"))
    }

    @Test
    fun `emoji sequences count as one character`() {
        assertEquals(1, Cursor.charCount("👨‍👩‍👧‍👦")) // family: ZWJ sequence
        assertEquals(1, Cursor.charCount("🇮🇩")) // flag: regional indicator pair
        assertEquals(1, Cursor.charCount("👍🏽")) // skin tone
        assertEquals(1, Cursor.charCount("❤️")) // variation selector
        assertEquals(1, Cursor.charCount("1️⃣")) // keycap
        assertEquals(1, Cursor.charCount("🏴󠁧󠁢󠁥󠁮󠁧󠁿")) // England: tag sequence
        assertEquals(1, Cursor.charCount("🏳️‍🌈")) // rainbow flag: VS + ZWJ
    }

    @Test
    fun `regional indicators pair up`() {
        assertEquals(2, Cursor.charCount("🇮🇩🇯🇵"))
        assertEquals(2, Cursor.charCount("🇮🇩🇯")) // a lone indicator is its own character
        assertEquals(3, Cursor.charCount("a🇮🇩b"))
    }

    @Test
    fun `mixed text counts each visible character`() {
        assertEquals(7, Cursor.charCount("hi 👩‍💻 日本"))
        assertEquals(2, Cursor.charCount("a\u200Db")) // ZWJ between letters doesn't join them
        assertEquals(1, Cursor.charCount("각")) // Hangul syllable
        assertEquals(3, Cursor.charCount("a\r\nb"), "CRLF is one character")
    }
}
