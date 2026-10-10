/**
 * How each line of a note looks in Live mode: which characters are drawn, in which style, and what
 * kind of line it is. The line holding the cursor is *revealed* (every character drawn, markers
 * dimmed); other lines hide their markers. Port of desktop `live_view.rs` (UTF-16 positions here).
 */
package com.kunotes.core

/**
 * Builds Live-mode line views.
 *
 * On a line that isn't revealed: `## Title` draws "Title" as a heading, `- [x] done` draws "done" next
 * to a checkbox, `- item` draws "• item", and a rule `---` draws nothing (the UI draws a line).
 * See [LineView] for how positions map between the drawn text and the markdown.
 */
object LiveView {
    /**
     * Splits [text] into lines, each with its own styled parts (ranges relative to the line).
     * A trailing line break gives a final empty line (where the cursor can go), so an empty text
     * gives one empty line.
     */
    fun lines(text: String): List<LiveLine> {
        val all = Live.spans(text)
        val result = mutableListOf<LiveLine>()
        var start = 0
        var nextSpan = 0
        while (true) {
            val newline = text.indexOf('\n', start)
            val end = if (newline == -1) text.length else newline
            val contentEnd = if (end > start && text[end - 1] == '\r') end - 1 else end

            // Spans are in text order, so each line takes the ones that start before its end.
            // `maxOf(..., start + 1)` lets an empty line still look at the span right at its start.
            // A span running past the line's content (never made by the scanner) is dropped.
            val lineSpans = mutableListOf<Span>()
            while (nextSpan < all.size && all[nextSpan].range.start < maxOf(contentEnd, start + 1)) {
                val span = all[nextSpan]
                if (span.range.end <= contentEnd) {
                    lineSpans.add(Span(TextRange(span.range.start - start, span.range.end - start), span.kind))
                }
                nextSpan++
            }
            result.add(LiveLine(TextRange(start, contentEnd), lineSpans))
            if (end == text.length) break
            start = end + 1
        }
        return result
    }

    /**
     * Builds the drawn form of [lineText] (one line, without its line break).
     * [lineSpans] are the line's spans from [lines]. A [revealed] line draws every character.
     */
    fun lineView(
        lineText: String,
        lineSpans: List<Span>,
        revealed: Boolean,
    ): LineView {
        val kind = lineKind(lineText, lineSpans)
        val builder = LineView.Builder(lineText)

        // A rule is drawn as a line, not text (unless the cursor is on it).
        if (kind == LineKind.Rule && !revealed) return builder.build(kind)

        var position = 0
        for (span in lineSpans) {
            if (position < span.range.start) {
                builder.draw(TextRange(position, span.range.start), kind = null)
            }
            val isBulletMarker = span.kind == SpanKind.ListMarker && kind == LineKind.Bullet
            when {
                !revealed && isBulletMarker -> builder.draw(span.range, SpanKind.ListMarker, replacement = "• ")
                !revealed && isHidden(span, kind) -> builder.hide(span.range)
                else -> builder.draw(span.range, span.kind)
            }
            position = span.range.end
        }
        if (position < lineText.length) {
            builder.draw(TextRange(position, lineText.length), kind = null)
        }
        return builder.build(kind)
    }

    /**
     * For an image line (`![alt](path)` alone, spaces around allowed), the range of `path` in the line.
     * Null for any other line, including an image with text around it.
     */
    fun imageLink(
        lineText: String,
        lineSpans: List<Span>,
    ): TextRange? {
        val first = lineSpans.firstOrNull() ?: return null
        val last = lineSpans.last()
        val isImage =
            first.kind == SpanKind.Marker &&
                first.range.start == LiveText.leadingWhitespace(lineText) &&
                lineText.substring(first.range) == "![" &&
                last.kind == SpanKind.Marker &&
                last.range.end == LiveText.endWithoutWhitespace(lineText) &&
                lineSpans.size <= 5
        if (!isImage) return null
        return lineSpans.firstOrNull { it.kind == SpanKind.LinkUrl }?.range
    }

    /**
     * A line that draws no text (an image shown as a picture instead).
     * Every position maps to the end of the line.
     */
    fun emptyView(
        kind: LineKind,
        sourceLength: Int,
    ): LineView = LineView(kind, text = "", runs = emptyList(), segments = emptyList(), sourceLength = sourceLength)

    /** Whether a part is hidden on a line that isn't revealed. */
    private fun isHidden(
        span: Span,
        kind: LineKind,
    ): Boolean =
        when (span.kind) {
            SpanKind.Marker -> kind != LineKind.Fence
            SpanKind.ListMarker -> kind is LineKind.Task
            SpanKind.LinkUrl -> true
            else -> false
        }

    /** The kind of line, from its parts. */
    private fun lineKind(
        lineText: String,
        lineSpans: List<Span>,
    ): LineKind {
        if (imageLink(lineText, lineSpans) != null) return LineKind.Image
        for (span in lineSpans) {
            when (val spanKind = span.kind) {
                is SpanKind.Heading -> return LineKind.Heading(spanKind.level)
                SpanKind.Rule -> return LineKind.Rule
                SpanKind.CodeBlock -> return LineKind.CodeBlock
                SpanKind.Quote -> return LineKind.Quote
                else -> {}
            }
        }

        val first = lineSpans.firstOrNull() ?: return LineKind.Paragraph
        val marker = lineText.substring(first.range)
        val trimmedLine = lineText.substring(LiveText.leadingWhitespace(lineText))
        if (first.kind == SpanKind.ListMarker) {
            return when {
                "[x]" in marker || "[X]" in marker -> LineKind.Task(checked = true)
                "[ ]" in marker -> LineKind.Task(checked = false)
                marker.substring(LiveText.leadingWhitespace(marker)).firstOrNull()?.let { LiveText.isAsciiDigit(it) } == true ->
                    LineKind.Numbered
                else -> LineKind.Bullet
            }
        }
        if (first.kind == SpanKind.Marker) {
            val wholeLine = lineSpans.size == 1 && first.range == TextRange(0, lineText.length)
            if (wholeLine) {
                // A marker covering the whole line is a fence, or else plain (a lone `>` isn't a quote yet).
                val isFence = trimmedLine.startsWith("```") || trimmedLine.startsWith("~~~")
                return if (isFence) LineKind.Fence else LineKind.Paragraph
            }
            if (trimmedLine.startsWith('>')) return LineKind.Quote
        }
        return LineKind.Paragraph
    }
}
