/**
 * Finds the markdown parts of a note so the editor can style them (headings, bold, code, links,
 * lists, quotes, code blocks, rules) plus the marker characters around them (`#`, `**`, `` ` ``...).
 * A light line-by-line scan for styling while typing, not a CommonMark parser. Port of desktop `live.rs`.
 */
package com.dotnote.core

/** What a piece of text is. */
sealed class SpanKind {
    /** Markdown syntax itself: `#`, `**`, `` ` ``, `[`, `](`, `)`, `>`, fences. */
    data object Marker : SpanKind()

    /** Heading text (level 1-6), without the `#` marker. */
    data class Heading(
        val level: Int,
    ) : SpanKind()

    data object Bold : SpanKind()

    data object Italic : SpanKind()

    data object BoldItalic : SpanKind()

    /** Text inside `~~strikethrough~~`. */
    data object Strike : SpanKind()

    /** Text inside `` `inline code` ``. */
    data object Code : SpanKind()

    /** A line inside a fenced code block. */
    data object CodeBlock : SpanKind()

    /** The visible text of a link `[this](...)`, or an image's alt text `![this](...)`. */
    data object LinkText : SpanKind()

    /** The target of a link: `[...](this)`. */
    data object LinkUrl : SpanKind()

    /** A list bullet or number: `-`, `*`, `+`, `1.`, `2)`, and a task box `[ ]`/`[x]`. */
    data object ListMarker : SpanKind()

    /** Text of a blockquote line, after the `>` marker. */
    data object Quote : SpanKind()

    /** A horizontal rule: `---`, `***`, `___`. */
    data object Rule : SpanKind()
}

/** One styled piece of the text. [range] is a UTF-16 range in the scanned text. */
data class Span(
    val range: TextRange,
    val kind: SpanKind,
)

/**
 * The markdown scanner.
 *
 * Why scanning `Char`s gives the same result as desktop's byte scan: every character the scanner
 * looks for is ASCII, and in UTF-16 no part of a non-ASCII character (not even half of an emoji)
 * equals an ASCII char. So every range starts and ends next to an ASCII marker or at a line edge,
 * which is never inside a surrogate pair.
 */
object Live {
    /**
     * All styled pieces of [text], in order and never overlapping.
     * Text that isn't listed is plain.
     */
    fun spans(text: String): List<Span> {
        val spans = mutableListOf<Span>()
        var fence: Fence? = null // set while inside a fenced code block
        var lineStart = 0

        while (lineStart < text.length) {
            val newline = text.indexOf('\n', lineStart)
            val rawEnd = if (newline == -1) text.length else newline + 1
            // A line without its break; `\r`s at the end are dropped too (CRLF files).
            val line = text.substring(lineStart, rawEnd).trimEnd('\n', '\r')
            val start = lineStart
            lineStart = rawEnd
            if (line.isEmpty()) continue

            val open = fence
            if (open != null) {
                val kind = if (isFenceClose(line, open)) SpanKind.Marker else SpanKind.CodeBlock
                if (kind == SpanKind.Marker) fence = null
                push(spans, start, start + line.length, kind)
                continue
            }
            val opened = fenceOpening(line)
            if (opened != null) {
                push(spans, start, start + line.length, SpanKind.Marker)
                fence = opened
                continue
            }

            lineSpans(line, start, spans)
        }
        return spans
    }

    /** An open code fence: its character (`` ` `` or `~`) and how many of them opened it. */
    private data class Fence(
        val char: Char,
        val length: Int,
    )

    /** Block-level parts of one line (heading, rule, quote, list), then inline parts. */
    private fun lineSpans(
        line: String,
        start: Int,
        spans: MutableList<Span>,
    ) {
        val indent = line.length - line.trimStart(' ').length
        val rest = line.substring(indent)

        // # Heading
        val hashes = LiveText.runLength(rest, 0, '#')
        val afterHashes = rest.substring(hashes)
        if (indent <= 3 && hashes in 1..6 && (afterHashes.isEmpty() || afterHashes.startsWith(' '))) {
            val markerEnd = indent + hashes + (if (afterHashes.isEmpty()) 0 else 1)
            push(spans, start, start + markerEnd, SpanKind.Marker)
            push(spans, start + markerEnd, start + line.length, SpanKind.Heading(hashes))
            return
        }

        // --- rule
        if (indent <= 3 && isRule(rest)) {
            push(spans, start, start + line.length, SpanKind.Rule)
            return
        }

        // > quote
        if (indent <= 3 && rest.startsWith('>')) {
            val markerLength = if (rest.startsWith("> ")) 2 else 1
            val quoteStart = start + indent + markerLength
            push(spans, start + indent, quoteStart, SpanKind.Marker)
            push(spans, quoteStart, start + line.length, SpanKind.Quote)
            return
        }

        // - list item, 1. list item, optional [ ] task box
        val markerLength = listMarkerLength(rest)
        if (markerLength != null) {
            var markerEnd = indent + markerLength
            val after = line.substring(markerEnd)
            if (after.startsWith("[ ] ") || after.startsWith("[x] ") || after.startsWith("[X] ")) {
                markerEnd += 4
            }
            push(spans, start + indent, start + markerEnd, SpanKind.ListMarker)
            inlineSpans(line.substring(markerEnd), start + markerEnd, spans)
            return
        }

        inlineSpans(line, start, spans)
    }

    /** Inline parts of [text] (which starts at [offset] in the note): code, links, images, emphasis. */
    private fun inlineSpans(
        text: String,
        offset: Int,
        spans: MutableList<Span>,
    ) {
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c == '`' -> {
                    val run = LiveText.runLength(text, i, '`')
                    val close = findRun(text, i + run, '`', run)
                    if (close == null) {
                        i += run
                        continue
                    }
                    push(spans, offset + i, offset + i + run, SpanKind.Marker)
                    push(spans, offset + i + run, offset + close, SpanKind.Code)
                    push(spans, offset + close, offset + close + run, SpanKind.Marker)
                    i = close + run
                }

                // An image `![alt](path)` is styled like a link, with `![` as its marker.
                c == '[' || c == '!' -> {
                    val bang = if (c == '!') 1 else 0 // length of the "!"
                    if (c == '!' && text.getOrNull(i + 1) != '[') {
                        i += 1
                        continue
                    }
                    val link = linkAt(text, i + bang)
                    if (link == null) {
                        i += 1
                        continue
                    }
                    push(spans, offset + i, offset + i + bang + 1, SpanKind.Marker)
                    push(spans, offset + i + bang + 1, offset + link.textEnd, SpanKind.LinkText)
                    push(spans, offset + link.textEnd, offset + link.textEnd + 2, SpanKind.Marker)
                    push(spans, offset + link.textEnd + 2, offset + link.urlEnd, SpanKind.LinkUrl)
                    push(spans, offset + link.urlEnd, offset + link.urlEnd + 1, SpanKind.Marker)
                    i = link.urlEnd + 1
                }

                // Exactly two tildes. (In a run of three, the scan steps one char and then sees two.)
                c == '~' && LiveText.runLength(text, i, '~') == 2 -> {
                    val close = emphasisClose(text, i, '~', 2)
                    if (close == null) {
                        i += 2
                        continue
                    }
                    push(spans, offset + i, offset + i + 2, SpanKind.Marker)
                    push(spans, offset + i + 2, offset + close, SpanKind.Strike)
                    push(spans, offset + close, offset + close + 2, SpanKind.Marker)
                    i = close + 2
                }

                c == '*' || c == '_' -> {
                    val run = minOf(LiveText.runLength(text, i, c), 3)
                    val close = emphasisClose(text, i, c, run)
                    if (close == null) {
                        i += LiveText.runLength(text, i, c)
                        continue
                    }
                    val kind =
                        when (run) {
                            1 -> SpanKind.Italic
                            2 -> SpanKind.Bold
                            else -> SpanKind.BoldItalic
                        }
                    push(spans, offset + i, offset + i + run, SpanKind.Marker)
                    push(spans, offset + i + run, offset + close, kind)
                    push(spans, offset + close, offset + close + run, SpanKind.Marker)
                    i = close + run
                }

                else -> i += 1
            }
        }
    }

    /** For an opening delimiter run at [open], the position of its matching closing run, or null. */
    private fun emphasisClose(
        text: String,
        open: Int,
        delimiter: Char,
        run: Int,
    ): Int? {
        val innerStart = open + run
        // The text right after the opener must not be a space ("* item" isn't emphasis).
        val first = text.getOrNull(innerStart)
        if (first == null || LiveText.isAsciiWhitespace(first)) return null
        // `_` only counts at word edges, so snake_case_names stay plain.
        if (delimiter == '_' && open > 0 && LiveText.isAsciiAlphanumeric(text[open - 1])) return null

        var j = innerStart
        while (j < text.length) {
            if (text[j] == delimiter && LiveText.runLength(text, j, delimiter) == run) {
                val before = text[j - 1]
                val after = text.getOrNull(j + run)
                val wordOk = delimiter != '_' || after == null || !LiveText.isAsciiAlphanumeric(after)
                if (!LiveText.isAsciiWhitespace(before) && wordOk) return j
            }
            j += if (text[j] == delimiter) LiveText.runLength(text, j, delimiter) else 1
        }
        return null
    }

    /** Where a link's `]` ([textEnd]) and closing `)` ([urlEnd]) are. */
    private data class LinkEnds(
        val textEnd: Int,
        val urlEnd: Int,
    )

    /** For `[` at [open]: the positions of `]` and of the closing `)` of `[text](url)`. */
    private fun linkAt(
        text: String,
        open: Int,
    ): LinkEnds? {
        val textEnd = text.indexOf(']', open + 1)
        if (textEnd == -1 || text.getOrNull(textEnd + 1) != '(') return null
        val urlEnd = text.indexOf(')', textEnd + 2)
        if (urlEnd == -1) return null
        return LinkEnds(textEnd, urlEnd)
    }

    /** The next run of exactly [length] [c]s at or after [from]. */
    private fun findRun(
        text: String,
        from: Int,
        c: Char,
        length: Int,
    ): Int? {
        var j = from
        while (j < text.length) {
            if (text[j] == c) {
                val run = LiveText.runLength(text, j, c)
                if (run == length) return j
                j += run
            } else {
                j += 1
            }
        }
        return null
    }

    /** Length of a list marker plus its space (`- `, `* `, `+ `, `12. `, `3) `), or null. */
    private fun listMarkerLength(text: String): Int? {
        val first = text.firstOrNull() ?: return null
        if ((first == '-' || first == '*' || first == '+') && text.getOrNull(1) == ' ') return 2
        if (!LiveText.isAsciiDigit(first)) return null
        val digits = text.takeWhile { LiveText.isAsciiDigit(it) }.length
        val separator = text.getOrNull(digits)
        val ok = digits <= 9 && (separator == '.' || separator == ')') && text.getOrNull(digits + 1) == ' '
        return if (ok) digits + 2 else null
    }

    /** `---`, `***`, `___` (three or more, spaces allowed between). */
    private fun isRule(text: String): Boolean {
        val compact = text.filter { it != ' ' }
        return compact.length >= 3 && compact[0] in "-*_" && compact.all { it == compact[0] }
    }

    /** A code fence opening (```` ``` ```` or `~~~`, three or more), or null. */
    private fun fenceOpening(line: String): Fence? {
        val rest = line.trimStart(' ')
        if (line.length - rest.length > 3) return null
        val fenceChar = rest.firstOrNull() ?: return null
        if (fenceChar != '`' && fenceChar != '~') return null
        val length = LiveText.runLength(rest, 0, fenceChar)
        return if (length >= 3) Fence(fenceChar, length) else null
    }

    /** A line that closes [fence]: at least as many fence chars, and nothing else. */
    private fun isFenceClose(
        line: String,
        fence: Fence,
    ): Boolean {
        val rest = LiveText.trim(line)
        return LiveText.runLength(rest, 0, fence.char) >= fence.length && rest.all { it == fence.char }
    }

    /** Adds a span, skipping empty ones (so callers don't have to check). */
    private fun push(
        spans: MutableList<Span>,
        start: Int,
        end: Int,
        kind: SpanKind,
    ) {
        if (start < end) spans.add(Span(TextRange(start, end), kind))
    }
}
