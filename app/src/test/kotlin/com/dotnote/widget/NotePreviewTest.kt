package com.dotnote.widget

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** The plain lines the home-screen widget shows for a note. */
class NotePreviewTest {
    private fun texts(
        markdown: String,
        title: String = "Note",
    ) = NotePreview.lines(markdown, title).map { it.text }

    @Test
    fun `drops a first heading that repeats the title`() {
        assertEquals(listOf("Today."), texts("# Journal\n\nToday.", title = "Journal"))
    }

    @Test
    fun `keeps a first heading that differs from the title`() {
        val lines = NotePreview.lines("# Plans\nShip it", "Roadmap")
        assertEquals(PreviewLine("Plans", PreviewLineKind.Heading), lines.first())
    }

    @Test
    fun `tasks, bullets and numbered items get readable markers`() {
        val markdown = "- [ ] Write\n- [x] Buy\n- Plain\n2. Second"
        val lines = NotePreview.lines(markdown, "Note")
        assertEquals(listOf("☐ Write", "☑ Buy", "• Plain", "2. Second"), lines.map { it.text })
        assertEquals(
            listOf(PreviewLineKind.Task, PreviewLineKind.DoneTask, PreviewLineKind.Bullet, PreviewLineKind.Bullet),
            lines.map { it.kind },
        )
    }

    @Test
    fun `strips inline markers, links and images`() {
        assertEquals(
            listOf("A bold and italic word with code, a link and cat."),
            texts("A **bold** and *italic* word with `code`, a [link](https://x.y) and ![cat](.img/cat.png)."),
        )
    }

    @Test
    fun `keeps underscores and stars inside words`() {
        assertEquals(listOf("snake_case and 2*3*4"), texts("snake_case and 2*3*4"))
    }

    @Test
    fun `collapses blank lines and drops trailing ones`() {
        assertEquals(listOf("One", "", "Two"), texts("One\n\n\n\nTwo\n\n"))
    }

    @Test
    fun `code fences are hidden but their lines stay`() {
        assertEquals(listOf("fn main() {}"), texts("```rust\nfn main() {}\n```"))
    }

    @Test
    fun `quotes lose their marker`() {
        assertEquals(listOf("Quoted"), texts("> Quoted"))
    }

    @Test
    fun `stops at the line cap`() {
        val markdown = (1..100).joinToString("\n") { "Line $it" }
        assertEquals(NotePreview.MAX_LINES, NotePreview.lines(markdown, "Note").size)
    }

    @Test
    fun `tables become cell rows without the separator or empty rows`() {
        assertEquals(listOf("Name · Size", "Cat · 4 kg"), texts("| Name | Size |\n|---|:--:|\n| Cat | 4 kg |\n| | |"))
    }
}
