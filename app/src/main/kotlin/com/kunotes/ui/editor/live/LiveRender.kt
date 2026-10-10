/**
 * Works out how Live mode (SPEC 3.1) draws the note: which marker characters are hidden or
 * replaced, and which parts get which style. Pure Kotlin on top of `:core` [LiveView], so it is
 * cheap to test and has no Compose types.
 *
 * Lines holding the selection are *revealed* (every character drawn, markers dimmed) so they can
 * be edited; the other lines hide their markers (`**`, `#`, link targets), draw `- ` as `• `,
 * draw a task box instead of `- [ ] `, and draw rules and images instead of their markdown.
 *
 * Positions in [LiveRender.spans], [LiveRender.lines], [LiveRender.tasks] and [LiveRender.blocks]
 * are in the *drawn* text (after the replacements).
 */
package com.kunotes.ui.editor.live

import com.kunotes.core.LineKind
import com.kunotes.core.LiveView
import com.kunotes.core.SpanKind
import com.kunotes.core.TextRange
import com.kunotes.core.substring

/** Replace [range] of the markdown with [text] when drawing (an empty [text] hides it). */
internal data class Replacement(
    val range: TextRange,
    val text: String,
)

/** A styled part of the drawn text. [revealed] parts are on a line being edited. */
internal data class StyledPart(
    val range: TextRange,
    val kind: SpanKind,
    val revealed: Boolean,
)

/** A whole drawn line and its kind (for line height and line-wide colours). */
internal data class StyledLine(
    val range: TextRange,
    val kind: LineKind,
)

/** A task box drawn at [display]; tapping it toggles the task on the line starting at [lineStart]. */
internal data class TaskMark(
    val display: Int,
    val lineStart: Int,
    val checked: Boolean,
)

/** A rule or image drawn over the (placeholder) line at [display]. [link] is the image's path. */
internal data class BlockMark(
    val display: Int,
    val kind: LineKind,
    val link: String? = null,
)

internal class LiveRender(
    val replacements: List<Replacement>,
    val spans: List<StyledPart>,
    val lines: List<StyledLine>,
    val tasks: List<TaskMark>,
    val blocks: List<BlockMark>,
)

internal object LiveRenderer {
    /** Drawn instead of `- [ ] `: room for the 20 dp box and the 12 dp gap (two em spaces). */
    const val TASK_SPACER = "  "

    /** Drawn instead of `- `. */
    const val BULLET = "• "

    /** Drawn instead of an image line: one char, so the line exists and can be made tall. */
    const val IMAGE_PLACEHOLDER = " "

    // One-entry cache: the transformation and the screen ask for the same text right after each other.
    private var cachedKey: Triple<String, Int, Int>? = null
    private var cachedValue: LiveRender? = null

    /** How to draw [text] with the selection [selectionStart]..[selectionEnd]. */
    @Synchronized
    fun render(
        text: String,
        selectionStart: Int,
        selectionEnd: Int,
    ): LiveRender {
        val key = Triple(text, minOf(selectionStart, selectionEnd), maxOf(selectionStart, selectionEnd))
        cachedValue?.let { if (cachedKey == key) return it }
        return build(text, key.second, key.third).also {
            cachedKey = key
            cachedValue = it
        }
    }

    private fun build(
        text: String,
        selectionStart: Int,
        selectionEnd: Int,
    ): LiveRender {
        val replacements = mutableListOf<Replacement>()
        val parts = mutableListOf<Pair<TextRange, SpanKind>>() // source positions for now
        val revealedParts = mutableListOf<Boolean>()
        val lineKinds = mutableListOf<Pair<TextRange, LineKind>>()
        val taskSources = mutableListOf<Pair<Int, Boolean>>() // replacement start, checked
        val blockSources = mutableListOf<BlockMark>() // display filled in later

        for (line in LiveView.lines(text)) {
            val lineText = text.substring(line.range)
            val revealed = line.range.start <= selectionEnd && line.range.end >= selectionStart
            val kind = LiveView.lineView(lineText, line.spans, revealed = false).kind
            // A revealed image line shows its markdown, so it gets a normal line height.
            lineKinds += line.range to if (revealed && kind == LineKind.Image) LineKind.Paragraph else kind
            val offset = line.range.start

            if (!revealed && (kind == LineKind.Rule || kind == LineKind.Image)) {
                val placeholder = if (kind == LineKind.Image) IMAGE_PLACEHOLDER else ""
                replacements += Replacement(line.range, placeholder)
                val link = LiveView.imageLink(lineText, line.spans)?.let { lineText.substring(it) }
                blockSources += BlockMark(display = line.range.start, kind = kind, link = link)
                continue
            }
            for (span in line.spans) {
                val range = TextRange(offset + span.range.start, offset + span.range.end)
                val replacement = if (revealed) null else replacementFor(span.kind, kind)
                if (replacement != null) {
                    replacements += Replacement(range, replacement)
                    if (kind is LineKind.Task && span.kind == SpanKind.ListMarker) taskSources += range.start to kind.checked
                }
                if (replacement == null || replacement == BULLET) {
                    parts += range to span.kind
                    revealedParts += revealed
                }
            }
        }

        val mapper = DisplayMapper(replacements)
        return LiveRender(
            replacements = replacements,
            spans =
                parts.mapIndexed { index, (range, kind) ->
                    StyledPart(mapper.range(range), kind, revealedParts[index])
                },
            lines = lineKinds.map { (range, kind) -> StyledLine(mapper.range(range), kind) },
            tasks =
                taskSources.map { (start, checked) ->
                    val lineStart = text.lastIndexOf('\n', start - 1) + 1
                    TaskMark(display = mapper.toDisplay(start), lineStart = lineStart, checked = checked)
                },
            blocks = blockSources.map { it.copy(display = mapper.toDisplay(it.display)) },
        )
    }

    /** What a span is drawn as on a line that isn't revealed: `null` = as it is, "" = hidden. */
    private fun replacementFor(
        span: SpanKind,
        line: LineKind,
    ): String? =
        when (span) {
            SpanKind.Marker -> if (line == LineKind.Fence) null else ""
            SpanKind.LinkUrl -> ""
            SpanKind.ListMarker ->
                when (line) {
                    is LineKind.Task -> TASK_SPACER
                    LineKind.Bullet -> BULLET
                    else -> null
                }
            else -> null
        }

    /** Maps markdown positions to drawn positions, given the replacements (in text order). */
    private class DisplayMapper(
        private val replacements: List<Replacement>,
    ) {
        fun toDisplay(source: Int): Int {
            var shift = 0
            for (replacement in replacements) {
                if (replacement.range.start >= source) break
                if (source < replacement.range.end) {
                    // Inside a replaced part: the end of what is drawn instead.
                    return replacement.range.start + shift + replacement.text.length
                }
                shift += replacement.text.length - replacement.range.length
            }
            return source + shift
        }

        fun range(source: TextRange): TextRange {
            val start = toDisplay(source.start)
            return TextRange(start, maxOf(start, toDisplay(source.end)))
        }
    }
}
