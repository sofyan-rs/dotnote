package com.dotnote.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LiveViewTest {
    private fun view(
        line: String,
        revealed: Boolean,
    ): LineView = LiveView.lineView(line, LiveView.lines(line)[0].spans, revealed)

    @Test
    fun `lines split text and keep relative spans`() {
        val text = "# A\nplain\r\n**b**"
        val all = LiveView.lines(text)
        assertEquals(3, all.size)
        assertEquals("plain", text.substring(all[1].range), "\\r is not part of the line")
        assertEquals(TextRange(2, 3), all[2].spans[1].range, "relative to the line start")
        assertEquals(2, LiveView.lines("a\n").size, "a trailing break gives an empty last line")
        assertEquals(listOf(LiveLine(TextRange(0, 0), emptyList())), LiveView.lines(""))
    }

    @Test
    fun `hidden markers disappear until revealed`() {
        val hidden = view("## Title", false)
        assertEquals("Title", hidden.text)
        assertEquals(LineKind.Heading(2), hidden.kind)
        assertEquals("## Title", view("## Title", true).text)
    }

    @Test
    fun `emphasis and code markers hide`() {
        assertEquals("a b c", view("a **b** `c`", false).text)
        assertEquals("see docs", view("see [docs](https://x.dev)", false).text)
    }

    @Test
    fun `bullets become dots and tasks become checkboxes`() {
        val bullet = view("- item", false)
        assertEquals("• item", bullet.text)
        assertEquals(LineKind.Bullet, bullet.kind)
        val task = view("- [x] done", false)
        assertEquals("done", task.text)
        assertEquals(LineKind.Task(checked = true), task.kind)
        assertEquals(LineKind.Task(checked = false), view("- [ ] todo", false).kind)
        assertEquals("1. step", view("1. step", false).text)
    }

    @Test
    fun `image lines are recognised`() {
        val line = "![cat](.img/cat.png)"
        val all = LiveView.lines(line)
        assertEquals(LineKind.Image, view(line, false).kind)
        assertEquals(".img/cat.png", LiveView.imageLink(line, all[0].spans)?.let { line.substring(it) })
        val inline = "see ![cat](c.png) here"
        assertNull(LiveView.imageLink(inline, LiveView.lines(inline)[0].spans), "not alone on its line")
        val empty = LiveView.emptyView(LineKind.Image, line.length)
        assertEquals(0 to line.length, empty.toDisplay(3) to empty.toSource(0))
    }

    @Test
    fun `rules draw nothing until revealed`() {
        assertEquals("", view("---", false).text)
        assertEquals(LineKind.Rule, view("---", false).kind)
        assertEquals("---", view("---", true).text)
    }

    @Test
    fun `runs cover the drawn text`() {
        val line = view("x **bold** y", true)
        assertEquals(line.text.length, line.runs.sumOf { it.length })
        assertTrue(line.runs.any { it.kind == SpanKind.Bold })
    }

    @Test
    fun `positions map both ways around hidden markers`() {
        val line = view("## Title", false) // drawn: "Title"
        assertEquals(0, line.toDisplay(0), "inside the hidden marker")
        assertEquals(0, line.toDisplay(3), "start of the text")
        assertEquals(5, line.toDisplay(8))
        assertEquals(3, line.toSource(0), "clicking before the text lands after the marker")
        assertEquals(8, line.toSource(5))

        val bold = view("a **b** c", false) // drawn: "a b c"
        assertEquals(4, bold.toSource(2), "the start of 'b'")
        assertEquals(2, bold.toDisplay(4))
        assertEquals(9, bold.toSource(5))
    }

    @Test
    fun `revealed lines map one to one`() {
        val line = view("**b** c", true)
        for (i in 0..7) {
            assertEquals(i, line.toDisplay(i))
            assertEquals(i, line.toSource(i))
        }
    }

    @Test
    fun `multibyte text maps on character boundaries`() {
        val line = view("# 日本", false) // drawn: "日本"
        assertEquals("日本", line.text)
        assertEquals(3, line.toSource(1), "after '日'")
        assertEquals(1, line.toDisplay(3))
    }

    @Test
    fun `emoji map on code point boundaries`() {
        val line = view("**👩‍💻** 🇮🇩", false) // drawn: "👩‍💻 🇮🇩"
        assertEquals("👩‍💻 🇮🇩", line.text)
        val womanTechnologist = "👩‍💻".length // 5 UTF-16 chars
        assertEquals(2, line.toSource(0), "start of the emoji, after the hidden **")
        assertEquals(2 + womanTechnologist + 2, line.toSource(womanTechnologist), "after the emoji: past the closing **")
        assertEquals(2 + 2, line.toSource(2), "inside the emoji maps 1:1 (callers pass code point boundaries)")
        assertEquals(womanTechnologist, line.toDisplay(2 + womanTechnologist + 2), "after the closing **")
        assertEquals(line.text.length, line.toDisplay(line.sourceLength))
        assertEquals(line.sourceLength, line.toSource(line.text.length))
    }

    @Test
    fun `bullet replacement maps to the end of the dot`() {
        val line = view("- 日本", false) // drawn: "• 日本"
        assertEquals(2, line.toDisplay(0))
        assertEquals(2, line.toDisplay(1))
        assertEquals(0, line.toSource(0))
        assertEquals(2, line.toSource(1), "inside the dot lands after the marker")
        assertEquals(3, line.toSource(3))
    }

    @Test
    fun `mapping is total and monotonic`() {
        val samples =
            listOf(
                "## Title 😀",
                "a **b** `c` [d](e) ~~f~~",
                "- [x] 日本 **語**",
                "- 👍🏽 item",
                "![cat](c.png)",
                "> quote 🇮🇩",
                "---",
                "",
            )
        for (sample in samples) {
            for (revealed in listOf(true, false)) {
                val line = view(sample, revealed)
                var lastDisplay = 0
                for (source in -2..sample.length + 2) {
                    val display = line.toDisplay(source)
                    assertTrue(display in 0..line.text.length, "$sample: toDisplay($source) = $display")
                    assertTrue(display >= lastDisplay, "$sample: toDisplay not monotonic at $source")
                    lastDisplay = display
                }
                var lastSource = 0
                for (display in -2..line.text.length + 2) {
                    val source = line.toSource(display)
                    assertTrue(source in 0..sample.length, "$sample: toSource($display) = $source")
                    assertTrue(source >= lastSource, "$sample: toSource not monotonic at $display")
                    lastSource = source
                }
                assertEquals(line.text.length, line.toDisplay(sample.length))
                assertEquals(sample.length, line.toSource(line.text.length))
            }
        }
    }

    @Test
    fun `line kinds`() {
        assertEquals(LineKind.Numbered, view("2) two", false).kind)
        assertEquals(LineKind.Quote, view("> hi", false).kind)
        assertEquals(LineKind.Fence, view("```kotlin", false).kind)
        assertEquals("```kotlin", view("```kotlin", false).text, "fence markers stay visible")
        assertEquals(LineKind.Paragraph, view(">", false).kind, "a lone > isn't a quote yet")
        val code = LiveView.lines("```\nval x = 1\n```")
        assertEquals(LineKind.CodeBlock, LiveView.lineView("val x = 1", code[1].spans, false).kind)
    }
}
