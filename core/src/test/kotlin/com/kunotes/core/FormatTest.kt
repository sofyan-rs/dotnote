package com.kunotes.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FormatTest {
    /** Applies [edit] and returns the new text plus the newly selected text. */
    private fun run(
        text: String,
        edit: Edit,
    ): Pair<String, String> {
        val result = edit.apply(text)
        return result to result.substring(edit.newSelection)
    }

    private fun range(
        start: Int,
        end: Int,
    ) = TextRange(start, end)

    // Ported desktop tests (format.rs)

    @Test
    fun `strikethrough and task list`() {
        assertEquals("a ~~b~~", Format.strikethrough("a b", range(2, 3)).apply("a b"))
        assertEquals("- [ ] one\n- [ ] two", Format.taskList("one\ntwo", range(0, 7)).apply("one\ntwo"))
    }

    @Test
    fun `table goes on its own lines with the first header selected`() {
        val text = "intro"
        val edit = Format.table(text, range(5, 5))
        val result = edit.apply(text)
        assertTrue(result.startsWith("intro\n\n| Column 1 | Column 2 |\n| --- | --- |"))
        assertEquals("Column 1", result.substring(edit.newSelection))
    }

    @Test
    fun `images are inserted one per line`() {
        val images =
            listOf(
                Format.Image("cat", ".img/cat.png"),
                Format.Image("日本", ".img/日本.jpg"),
            )
        val edit = Format.images("x ", range(2, 2), images)
        assertEquals("x ![cat](.img/cat.png)\n![日本](.img/日本.jpg)", edit.apply("x "))
        assertEquals(edit.apply("x ").length, edit.newSelection.start)
    }

    @Test
    fun `bold wraps selection and keeps it selected`() {
        val text = "make this bold"
        val (result, selected) = run(text, Format.bold(text, range(10, 14)))
        assertEquals("make this **bold**", result)
        assertEquals("bold", selected)
    }

    @Test
    fun `wrap without selection puts cursor between markers`() {
        val edit = Format.italic("ab", range(1, 1))
        assertEquals("a**b", edit.apply("ab"))
        assertEquals(range(2, 2), edit.newSelection)
    }

    @Test
    fun `inline code works at start and end of text`() {
        assertEquals("`x`", Format.inlineCode("x", range(0, 1)).apply("x"))
        assertEquals("``", Format.inlineCode("", range(0, 0)).apply(""))
    }

    @Test
    fun `wrap handles multibyte text`() {
        val text = "hi 👩‍💻 日本"
        val start = text.indexOf('日')
        val (result, selected) = run(text, Format.bold(text, range(start, text.length)))
        assertEquals("hi 👩‍💻 **日本**", result)
        assertEquals("日本", selected)
    }

    @Test
    fun `out of range or mid char selection never throws`() {
        assertEquals("**日本**", Format.bold("日本", range(0, 100)).apply("日本"))
        // Index 1 is inside the first emoji's surrogate pair: it moves back to the emoji's start.
        assertEquals("**😀😀**", Format.bold("😀😀", range(1, 100)).apply("😀😀"))
        assertEquals("x****", Format.bold("x", range(50, 60)).apply("x"))
    }

    @Test
    fun `heading applies to current line from anywhere in it`() {
        val text = "first\nsecond line\nthird"
        val (result, _) = run(text, Format.heading(text, range(9, 9), 1))
        assertEquals("first\n# second line\nthird", result)
    }

    @Test
    fun `heading replaces existing marker`() {
        assertEquals("# Title", Format.heading("## Title", range(0, 0), 1).apply("## Title"))
        assertEquals("## #tag", Format.heading("#tag", range(0, 0), 2).apply("#tag"))
    }

    @Test
    fun `line prefix covers every selected line`() {
        val text = "a\nb\nc"
        val (result, selected) = run(text, Format.quote(text, range(0, 3)))
        assertEquals("> a\n> b\nc", result)
        assertEquals("a\n> b", selected)
    }

    @Test
    fun `selection ending at line start excludes that line`() {
        // "a\n" selected: the cursor sits at the start of "b".
        assertEquals("- a\nb\nc", Format.bulletList("a\nb\nc", range(0, 2)).apply("a\nb\nc"))
    }

    @Test
    fun `numbered list counts up`() {
        val text = "x\ny\nz"
        assertEquals("1. x\n2. y\n3. z", Format.numberedList(text, range(0, text.length)).apply(text))
    }

    @Test
    fun `line prefix on empty text`() {
        val edit = Format.bulletList("", range(0, 0))
        assertEquals("- ", edit.apply(""))
        assertEquals(range(2, 2), edit.newSelection)
    }

    @Test
    fun `link selects url placeholder`() {
        val text = "see docs"
        val (result, selected) = run(text, Format.link(text, range(4, 8)))
        assertEquals("see [docs](url)", result)
        assertEquals("url", selected)
    }

    @Test
    fun `link without selection puts cursor in brackets`() {
        val edit = Format.link("", range(0, 0))
        assertEquals("[](url)", edit.apply(""))
        assertEquals(range(1, 1), edit.newSelection)
    }

    @Test
    fun `code block goes on its own lines`() {
        val text = "before code after"
        val (result, selected) = run(text, Format.codeBlock(text, range(7, 11)))
        assertEquals("before \n```\ncode\n```\n after", result)
        assertEquals("code", selected)

        assertEquals("```\ncode\n```", Format.codeBlock("code", range(0, 4)).apply("code"))
    }

    @Test
    fun `horizontal rule adds blank lines only when needed`() {
        assertEquals("---\n\n", Format.horizontalRule("", range(0, 0)).apply(""))
        assertEquals("text\n\n---\n\n", Format.horizontalRule("text", range(4, 4)).apply("text"))
        assertEquals("a\n\n---\n\nb", Format.horizontalRule("a\n\nb", range(3, 3)).apply("a\n\nb"))

        assertEquals(range(11, 11), Format.horizontalRule("text", range(4, 4)).newSelection)
    }

    // Emoji and CJK (UTF-16 offsets)

    @Test
    fun `wrap selection right before an emoji`() {
        val text = "ab😀"
        val (result, selected) = run(text, Format.bold(text, range(0, 2)))
        assertEquals("**ab**😀", result)
        assertEquals("ab", selected)
    }

    @Test
    fun `wrap selection right after an emoji`() {
        val text = "😀ab"
        val (result, selected) = run(text, Format.italic(text, range(2, 4)))
        assertEquals("😀*ab*", result)
        assertEquals("ab", selected)
    }

    @Test
    fun `wrap selection around an emoji keeps it whole`() {
        val text = "x👩‍💻y"
        val emojiEnd = text.length - 1
        val (result, selected) = run(text, Format.strikethrough(text, range(1, emojiEnd)))
        assertEquals("x~~👩‍💻~~y", result)
        assertEquals("👩‍💻", selected)
    }

    @Test
    fun `half selected emoji grows to the whole emoji`() {
        val text = "a😀b"
        // End index 2 is between the two halves of the emoji: the end moves forward.
        val (result, selected) = run(text, Format.bold(text, range(0, 2)))
        assertEquals("**a😀**b", result)
        assertEquals("a😀", selected)
    }

    @Test
    fun `empty selection next to an emoji puts cursor between markers`() {
        val text = "😀😀"
        val edit = Format.inlineCode(text, range(2, 2))
        assertEquals("😀``😀", edit.apply(text))
        assertEquals(range(3, 3), edit.newSelection)
    }

    @Test
    fun `wrap at start and end of document with cjk`() {
        val text = "日本語"
        assertEquals("**日**本語", Format.bold(text, range(0, 1)).apply(text))
        val (result, selected) = run(text, Format.bold(text, range(2, 3)))
        assertEquals("日本**語**", result)
        assertEquals("語", selected)
    }

    @Test
    fun `link around emoji selects url`() {
        val text = "go 🚀"
        val (result, selected) = run(text, Format.link(text, range(3, text.length)))
        assertEquals("go [🚀](url)", result)
        assertEquals("url", selected)
    }

    @Test
    fun `line prefix keeps selection on emoji lines`() {
        val text = "😀 one\n日本 two"
        val (result, selected) = run(text, Format.bulletList(text, range(0, text.length)))
        assertEquals("- 😀 one\n- 日本 two", result)
        assertEquals("😀 one\n- 日本 two", selected)
    }

    @Test
    fun `heading on last line with emoji and cursor at end of document`() {
        val text = "intro\n## 🎉 Party"
        val edit = Format.heading(text, range(text.length, text.length), 3)
        assertEquals("intro\n### 🎉 Party", edit.apply(text))
        assertEquals(range(edit.apply(text).length, edit.apply(text).length), edit.newSelection)
    }

    @Test
    fun `numbered list at start of document with empty selection`() {
        val text = "日本\nnext"
        val edit = Format.numberedList(text, range(0, 0))
        assertEquals("1. 日本\nnext", edit.apply(text))
        assertEquals(range(3, 3), edit.newSelection)
    }

    @Test
    fun `heading level is clamped`() {
        assertEquals("# a", Format.heading("a", range(0, 0), 0).apply("a"))
        assertEquals("###### a", Format.heading("a", range(0, 0), 9).apply("a"))
    }

    @Test
    fun `code block around emoji and cjk`() {
        val text = "前😀後"
        val (result, selected) = run(text, Format.codeBlock(text, range(1, 3)))
        assertEquals("前\n```\n😀\n```\n後", result)
        assertEquals("😀", selected)
    }

    @Test
    fun `table and rule after emoji text`() {
        val text = "🎉"
        val edit = Format.table(text, range(2, 2))
        assertEquals("Column 1", edit.apply(text).substring(edit.newSelection))

        val rule = Format.horizontalRule(text, range(2, 2))
        assertEquals("🎉\n\n---\n\n", rule.apply(text))
        assertEquals(range(9, 9), rule.newSelection)
    }

    @Test
    fun `images replace selection at start of document`() {
        val text = "old😀"
        val edit = Format.images(text, range(0, 3), listOf(Format.Image("🐱", ".img/🐱.png")))
        assertEquals("![🐱](.img/🐱.png)😀", edit.apply(text))
        assertEquals("![🐱](.img/🐱.png)".length, edit.newSelection.start)
    }
}
