package com.dotnote.ui.editor.live

import com.dotnote.core.LineKind
import com.dotnote.core.SpanKind
import com.dotnote.core.TextRange
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LiveRendererTest {
    /** The text Live mode draws for [text] with the cursor at [cursor]. */
    private fun drawn(
        text: String,
        cursor: Int,
    ): String {
        val render = LiveRenderer.render(text, cursor, cursor)
        val builder = StringBuilder(text)
        for (replacement in render.replacements.asReversed()) {
            builder.replace(replacement.range.start, replacement.range.end, replacement.text)
        }
        return builder.toString()
    }

    @Test
    fun `hides markers except on the cursor line`() {
        val text = "# Title\nsome **bold** text"
        assertEquals("# Title\nsome bold text", drawn(text, cursor = 2))
        assertEquals("Title\nsome **bold** text", drawn(text, cursor = text.length))
    }

    @Test
    fun `bullets, tasks, links and rules`() {
        val text = "- item\n- [x] done\n[site](https://a.b)\n---\nend"
        val tasks = LiveRenderer.TASK_SPACER
        assertEquals("• item\n${tasks}done\nsite\n\nend", drawn(text, cursor = text.length))
    }

    @Test
    fun `task marks point at the drawn box and the source line`() {
        val text = "# H\n- [ ] one\n- [x] two\nend"
        val render = LiveRenderer.render(text, text.length, text.length)
        // Drawn: "H\n" + box + "one\n" + box + "two\nend".
        assertEquals(listOf(2, 8), render.tasks.map { it.display })
        assertEquals(listOf(4, 14), render.tasks.map { it.lineStart })
        assertEquals(listOf(false, true), render.tasks.map { it.checked })
    }

    @Test
    fun `styled parts are in drawn positions`() {
        val text = "x\na **b** c"
        val render = LiveRenderer.render(text, 0, 0)
        val bold = render.spans.single { it.kind == SpanKind.Bold }
        // Drawn: "x\na b c" -> "b" at 4.
        assertEquals(TextRange(4, 5), bold.range)
    }

    @Test
    fun `an image line becomes one placeholder with its link`() {
        val text = "intro\n![cat](.img/cat.png)\n"
        val render = LiveRenderer.render(text, 0, 0)
        val image = render.blocks.single()
        assertEquals(LineKind.Image, image.kind)
        assertEquals(".img/cat.png", image.link)
        assertEquals(6, image.display)
        assertTrue(drawn(text, 0).startsWith("intro\n${LiveRenderer.IMAGE_PLACEHOLDER}\n"))
    }

    @Test
    fun `emoji and CJK keep their positions`() {
        val text = "😀 **日本** x\nnext"
        assertEquals("😀 日本 x\nnext", drawn(text, cursor = text.length))
    }
}
