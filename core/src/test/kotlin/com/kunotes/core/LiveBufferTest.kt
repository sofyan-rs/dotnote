package com.kunotes.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LiveBufferTest {
    private fun buffer(
        text: String,
        cursor: Int,
    ): LiveBuffer = LiveBuffer(text).also { it.moveTo(cursor, extend = false) }

    private fun LiveBuffer.selectedText(): String = text.substring(selection)

    @Test
    fun `typing inserts at the cursor and replaces a selection`() {
        val b = buffer("ac", 1)
        b.insert("b")
        assertEquals("abc" to 2, b.text to b.cursor)
        b.select(TextRange(0, 3))
        b.insert("x")
        assertEquals("x", b.text)
    }

    @Test
    fun `backspace and delete step over whole characters`() {
        var b = buffer("a👩‍💻b", "a👩‍💻".length)
        b.backspace()
        assertEquals("ab", b.text)
        b = buffer("é!", 0) // "é" written as e + combining accent
        b.deleteForward()
        assertEquals("!", b.text)
        b = buffer("", 0)
        b.backspace() // nothing to delete, no crash
        assertEquals("", b.text)
    }

    @Test
    fun `backspace removes flags skin tones and crlf whole`() {
        var b = buffer("x🇮🇩🇯🇵", "x🇮🇩🇯🇵".length)
        b.backspace()
        assertEquals("x🇮🇩", b.text, "one flag at a time")
        b = buffer("👍🏽", 4)
        b.backspace()
        assertEquals("", b.text)
        b = buffer("a\r\nb", 3)
        b.backspace()
        assertEquals("ab", b.text)
        b = buffer("한국", 2)
        b.backspace()
        assertEquals("한", b.text)
    }

    @Test
    fun `arrows move and extend`() {
        val b = buffer("ab日😀", 0)
        b.moveRight(extend = false)
        b.moveRight(extend = true)
        assertEquals(TextRange(1, 2), b.selection)
        b.moveRight(extend = true)
        assertEquals(TextRange(1, 3), b.selection, "the CJK character is one step")
        b.moveRight(extend = true)
        assertEquals(TextRange(1, 5), b.selection, "the emoji (two UTF-16 chars) is one step")
        b.moveLeft(extend = false)
        assertEquals(TextRange(1, 1), b.selection, "left collapses to the start")
    }

    @Test
    fun `shift left extends backwards from the anchor`() {
        val b = buffer("ab😀", 4)
        b.moveLeft(extend = true)
        assertEquals(TextRange(2, 4), b.selection)
        assertEquals(2, b.cursor)
        b.moveLeft(extend = true)
        assertEquals(TextRange(1, 4), b.selection)
    }

    @Test
    fun `home and end stay on the line`() {
        val b = buffer("one\ntwo\nthree", 5)
        b.moveToLineStart(extend = false)
        assertEquals(4, b.cursor)
        b.moveToLineEnd(extend = true)
        assertEquals(TextRange(4, 7), b.selection)
    }

    @Test
    fun `enter continues lists and ends them on an empty item`() {
        var b = buffer("- item", 6)
        b.newLine()
        assertEquals("- item\n- ", b.text)
        b.newLine() // empty item: end the list
        assertEquals("- item\n", b.text)

        b = buffer("  9. nine", 9)
        b.newLine()
        assertEquals("  9. nine\n  10. ", b.text)

        b = buffer("- [x] done", 10)
        b.newLine()
        assertEquals("- [x] done\n- [ ] ", b.text, "a new task starts unticked")

        b = buffer("plain", 5)
        b.newLine()
        assertEquals("plain\n", b.text)

        b = buffer("1) 日本 😀", 8)
        b.newLine()
        assertEquals("1) 日本 😀\n2) ", b.text)
    }

    @Test
    fun `task boxes toggle without moving the cursor`() {
        var b = buffer("- [ ] a\n* [x] b\nplain", 0)
        assertTrue(b.toggleTask(3))
        assertTrue(b.toggleTask(10))
        assertFalse(b.toggleTask(17))
        assertEquals("- [x] a\n* [ ] b\nplain", b.text)
        assertEquals(0, b.cursor)
        b = buffer("1. [ ] numbered", 0)
        assertTrue(b.toggleTask(0))
        assertEquals("1. [x] numbered", b.text)
        assertNull(LiveLists.taskToggle("[ ] no marker", 0))
        assertEquals(TaskToggle(TextRange(5, 8), "[x]"), LiveLists.taskToggle("日本\n- [ ] x", 4))
        assertEquals(TaskToggle(TextRange(6, 9), "[ ]"), LiveLists.taskToggle("😀\n\t- [X] y", 9))
        assertNull(LiveLists.taskToggle("😀\n- [ ] x", 1), "inside a surrogate pair")
    }

    @Test
    fun `undo groups typing and redo restores`() {
        val b = buffer("", 0)
        for (c in listOf("h", "i")) b.insert(c)
        b.insert("\n")
        b.insert("x")
        assertEquals("hi\nx", b.text)
        assertTrue(b.undo())
        assertEquals("hi\n", b.text)
        assertTrue(b.undo())
        assertEquals("hi", b.text)
        assertTrue(b.undo())
        assertEquals("", b.text, "typed characters in a row undo together")
        assertFalse(b.undo())
        assertTrue(b.redo())
        assertEquals("hi", b.text)
        b.backspace()
        assertFalse(b.redo(), "a new change clears redo")
    }

    @Test
    fun `typed emoji and cjk merge into one undo step`() {
        val b = buffer("", 0)
        for (c in listOf("日", "😀", "本", "👍")) b.insert(c)
        assertEquals("日😀本👍", b.text)
        assertEquals(6, b.cursor)
        assertTrue(b.undo())
        assertEquals("", b.text)
        assertEquals(TextRange(0, 0), b.selection)
        assertTrue(b.redo())
        assertEquals("日😀本👍", b.text)
        assertEquals(TextRange(6, 6), b.selection)
        b.insert("👩‍💻") // several code points: a paste, not typing
        b.insert("x")
        assertTrue(b.undo())
        assertEquals("日😀本👍👩‍💻", b.text)
    }

    @Test
    fun `moving the cursor ends a typing run`() {
        val b = buffer("", 0)
        b.insert("a")
        b.moveTo(0, extend = false)
        b.insert("b")
        b.undo()
        assertEquals("a", b.text, "the second insert undoes separately")
    }

    @Test
    fun `word movement skips spaces and punctuation`() {
        val b = buffer("one, two  three", 15)
        b.moveWordLeft(extend = false)
        assertEquals(10, b.cursor)
        b.moveWordLeft(extend = false)
        assertEquals(5, b.cursor)
        b.moveWordRight(extend = false)
        assertEquals(8, b.cursor)
        b.deleteWordBack()
        assertEquals("one,   three", b.text)
    }

    @Test
    fun `word movement over emoji and cjk`() {
        val text = "😀 héllo, 日本 👩‍💻 end"
        val b = buffer(text, text.length)
        b.moveWordLeft(extend = false)
        assertEquals(text.indexOf("end"), b.cursor)
        b.moveWordLeft(extend = false)
        assertEquals(text.indexOf("本"), b.cursor, "each ideograph is its own word, emoji are skipped")
        b.moveWordLeft(extend = false)
        assertEquals(text.indexOf("日"), b.cursor)
        b.moveWordLeft(extend = false)
        assertEquals(text.indexOf("héllo"), b.cursor)
        b.moveWordLeft(extend = false)
        assertEquals(0, b.cursor, "no word before: the start")
        b.moveWordRight(extend = false)
        assertEquals(text.indexOf(","), b.cursor)
        b.moveTo(text.indexOf("👩"), extend = false)
        b.moveWordRight(extend = false)
        assertEquals(text.length, b.cursor)
    }

    @Test
    fun `double and triple click select word and line`() {
        val b = buffer("hello wörld\nnext", 0)
        b.selectWord(8)
        assertEquals("wörld", b.selectedText())
        b.selectLine(2)
        assertEquals("hello wörld\n", b.selectedText())
    }

    @Test
    fun `double click selects emoji and words like don't`() {
        val b = buffer("I don't 👩‍💻 3.14 snake_case", 0)
        b.selectWord(3)
        assertEquals("don't", b.selectedText())
        b.selectWord(9) // inside the emoji
        assertEquals("👩‍💻", b.selectedText())
        b.selectWord(16)
        assertEquals("3.14", b.selectedText())
        b.selectWord(20)
        assertEquals("snake_case", b.selectedText())
        b.selectWord(b.text.length)
        assertEquals("", b.selectedText(), "past the last word: just a cursor")
    }

    @Test
    fun `delete to line start joins lines at the start`() {
        val b = buffer("ab\ncd", 5)
        b.deleteToLineStart()
        assertEquals("ab\n", b.text)
        b.deleteToLineStart()
        assertEquals("ab", b.text)
    }

    @Test
    fun `offsets are clamped to character boundaries`() {
        val b = buffer("😀😀", 0)
        b.moveTo(1, extend = false) // inside the first emoji
        assertEquals(0, b.cursor)
        b.moveTo(3, extend = false)
        assertEquals(2, b.cursor)
        b.moveTo(100, extend = false)
        assertEquals(4, b.cursor)
        b.moveTo(-5, extend = false)
        assertEquals(0, b.cursor)
        b.select(TextRange(1, 3))
        assertEquals(TextRange(0, 2), b.selection)
    }

    @Test
    fun `select all and replace keep a valid cursor`() {
        val b = buffer("日本😀", 0)
        b.selectAll()
        assertEquals(TextRange(0, 4), b.selection)
        b.replace(TextRange(1, 3), "x", mergeWithTyping = false) // 3 is inside 😀: clamped to 2
        assertEquals("日x😀", b.text)
        assertEquals(2, b.cursor)
    }
}
