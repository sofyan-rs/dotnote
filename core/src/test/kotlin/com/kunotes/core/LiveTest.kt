package com.kunotes.core

import com.kunotes.core.SpanKind.Bold
import com.kunotes.core.SpanKind.BoldItalic
import com.kunotes.core.SpanKind.Code
import com.kunotes.core.SpanKind.CodeBlock
import com.kunotes.core.SpanKind.Heading
import com.kunotes.core.SpanKind.Italic
import com.kunotes.core.SpanKind.LinkText
import com.kunotes.core.SpanKind.LinkUrl
import com.kunotes.core.SpanKind.ListMarker
import com.kunotes.core.SpanKind.Marker
import com.kunotes.core.SpanKind.Quote
import com.kunotes.core.SpanKind.Rule
import com.kunotes.core.SpanKind.Strike
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LiveTest {
    /** Spans as (text, kind) pairs, which are easier to read than ranges. */
    private fun pieces(text: String): List<Pair<String, SpanKind>> = Live.spans(text).map { text.substring(it.range) to it.kind }

    @Test
    fun `headings split marker and text`() {
        assertEquals(listOf("# " to Marker, "Title" to Heading(1)), pieces("# Title"))
        assertEquals(listOf("### " to Marker, "Deep" to Heading(3)), pieces("### Deep"))
        assertEquals(emptyList<Any>(), pieces("#hashtag"), "no space after # is not a heading")
        assertEquals(emptyList<Any>(), pieces("####### seven"))
    }

    @Test
    fun `bold italic and both`() {
        assertEquals(listOf("**" to Marker, "b" to Bold, "**" to Marker), pieces("a **b** c"))
        assertEquals(listOf("*" to Marker, "i" to Italic, "*" to Marker), pieces("*i*"))
        assertEquals(listOf("***" to Marker, "x" to BoldItalic, "***" to Marker), pieces("***x***"))
        assertEquals(listOf("__" to Marker, "b" to Bold, "__" to Marker), pieces("__b__"))
    }

    @Test
    fun `underscores inside words are plain`() {
        assertEquals(emptyList<Any>(), pieces("snake_case_name"))
        assertEquals(emptyList<Any>(), pieces("2 * 3 * 4"), "spaced asterisks aren't emphasis")
    }

    @Test
    fun `strikethrough and images`() {
        assertEquals(listOf("~~" to Marker, "gone" to Strike, "~~" to Marker), pieces("a ~~gone~~ b"))
        assertFalse(pieces("a ~~~x~~~ b").any { it.second == Strike }, "three tildes aren't strikethrough")
        assertEquals(
            listOf("![" to Marker, "cat" to LinkText, "](" to Marker, ".img/cat.png" to LinkUrl, ")" to Marker),
            pieces("![cat](.img/cat.png)"),
        )
        assertTrue(pieces("wow!").isEmpty(), "a lone ! is plain text")
    }

    @Test
    fun `inline code hides formatting inside`() {
        assertEquals(listOf("`" to Marker, "cargo **x**" to Code, "`" to Marker), pieces("run `cargo **x**` now"))
        assertEquals(listOf("``" to Marker, "a ` b" to Code, "``" to Marker), pieces("``a ` b``"))
        assertEquals(emptyList<Any>(), pieces("`unclosed"))
    }

    @Test
    fun `links split text and url`() {
        assertEquals(
            listOf("[" to Marker, "docs" to LinkText, "](" to Marker, "https://x.dev" to LinkUrl, ")" to Marker),
            pieces("see [docs](https://x.dev)!"),
        )
        assertEquals(emptyList<Any>(), pieces("[not a link] here"))
    }

    @Test
    fun `list markers and task boxes`() {
        assertEquals(listOf("- " to ListMarker), pieces("- item"))
        assertEquals(listOf("12. " to ListMarker), pieces("  12. step"))
        assertEquals(listOf("- [x] " to ListMarker), pieces("- [x] done"))
        assertEquals(
            listOf("* " to ListMarker, "**" to Marker, "bold" to Bold, "**" to Marker),
            pieces("* **bold** item"),
        )
    }

    @Test
    fun `quotes rules and code blocks`() {
        assertEquals(listOf("> " to Marker, "wise words" to Quote), pieces("> wise words"))
        assertEquals(listOf("---" to Rule), pieces("---"))
        assertEquals(listOf("* * *" to Rule), pieces("* * *"))
        assertEquals(
            listOf(
                "```rust" to Marker,
                "let x = **1**;" to CodeBlock,
                "```" to Marker,
                "**" to Marker,
                "b" to Bold,
                "**" to Marker,
            ),
            pieces("```rust\nlet x = **1**;\n```\nafter **b**"),
        )
    }

    @Test
    fun `ranges are correct with multibyte text and crlf`() {
        val text = "日本 **語** 👩‍💻\r\n# 見出し\r\n"
        assertEquals(
            listOf("**" to Marker, "語" to Bold, "**" to Marker, "# " to Marker, "見出し" to Heading(1)),
            pieces(text),
        )
    }

    @Test
    fun `ranges are utf-16 indexes around emoji`() {
        // 😀 is two UTF-16 chars, so "**" starts at 3, not 2.
        val spans = Live.spans("😀 **🎉** `👩‍💻`")
        assertEquals(TextRange(3, 5), spans[0].range)
        assertEquals(Span(TextRange(5, 7), Bold), spans[1])
        assertEquals(
            listOf("**" to Marker, "🎉" to Bold, "**" to Marker, "`" to Marker, "👩‍💻" to Code, "`" to Marker),
            pieces("😀 **🎉** `👩‍💻`"),
        )
        assertEquals(listOf("## " to Marker, "🇮🇩 日本" to Heading(2)), pieces("## 🇮🇩 日本"))
        assertEquals(
            listOf("[" to Marker, "猫" to LinkText, "](" to Marker, "😺.md" to LinkUrl, ")" to Marker),
            pieces("[猫](😺.md)"),
        )
    }

    @Test
    fun `spans are ordered and never overlap`() {
        val spans = Live.spans(EXAMPLE_NOTE)
        assertTrue(spans.isNotEmpty())
        for ((a, b) in spans.zipWithNext()) {
            assertTrue(a.range.end <= b.range.start, "$a $b")
        }
        for (span in spans) {
            assertTrue(isCodePointBoundary(EXAMPLE_NOTE, span.range.start) && isCodePointBoundary(EXAMPLE_NOTE, span.range.end))
        }
    }

    @Test
    fun `empty text has no spans`() {
        assertEquals(emptyList<Span>(), Live.spans(""))
        assertEquals(emptyList<Span>(), Live.spans("\n\r\n"))
    }

    companion object {
        private fun isCodePointBoundary(
            text: String,
            index: Int,
        ): Boolean =
            index == 0 ||
                index == text.length ||
                !Character.isLowSurrogate(text[index]) ||
                !Character.isHighSurrogate(text[index - 1])

        /** Copy of desktop's `fixtures/sample-vault/Example.md`. */
        val EXAMPLE_NOTE =
            """
            |# Example
            |
            |This is a **bold** word, this is *italic*, and here's `inline code`.
            |
            |## Task List
            |
            |- [ ] Write the report
            |- [x] Buy groceries
            |- [ ] Call the bank
            |
            |## Lists
            |
            |- Plain bullet one
            |- Plain bullet two
            |  - Nested bullet
            |
            |1. First step
            |2. Second step
            |3. Third step
            |
            |## Quote
            |
            |> This is a blockquote.
            |> It can span multiple lines.
            |
            |---
            |
            |## Link
            |
            |Check out [GPUI Kit](https://gpui-kit.com) for more info.
            |
            |## Code Block
            |
            |```rust
            |fn greet(name: &str) -> String {
            |    format!("Hello, {name}!")
            |}
            |```
            |
            |### Heading 3
            |
            |#### Heading 4
            |
            |##### Heading 5
            |
            |###### Heading 6
            |
            |Unicode check: café, 日本語, emoji 👩‍💻 and 🇮🇩.
            |
            |That's the end of the example file.
            |
            """.trimMargin()
    }
}
