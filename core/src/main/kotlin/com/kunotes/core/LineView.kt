/**
 * How one line is drawn in Live mode: the text to draw, its style runs, and the mapping between
 * positions in the drawn text ("display") and positions in the line's markdown ("source").
 * Built by [LiveView.lineView]; part of the port of desktop `live_view.rs`.
 */
package com.kunotes.core

/**
 * A style run over [LineView.text]: [length] UTF-16 chars drawn as [kind] (`null` = plain text).
 * Runs follow each other; their lengths add up to the drawn text's length.
 */
data class StyleRun(
    val length: Int,
    val kind: SpanKind?,
)

/**
 * One piece of drawn text and where it came from in the line.
 * Segments follow each other: together they cover the whole line and the whole drawn text.
 *
 * @property display where the piece is in the drawn text (empty for a hidden marker).
 * @property source the part of the line it stands for.
 * @property replaced true if the drawn text isn't the source text itself (hidden, or "• " for "- ").
 */
internal data class Segment(
    val display: TextRange,
    val source: TextRange,
    val replaced: Boolean,
)

/**
 * How one line is drawn. All positions are UTF-16 indexes relative to the start of the line.
 *
 * ## Position mapping
 *
 * [toDisplay] and [toSource] are what a Compose `OffsetMapping` / `OutputTransformation` needs:
 * - **Total:** any `Int` is accepted. Values below 0 act like 0, values past the end act like the end.
 *   Results are always in `0..text.length` (toDisplay) or `0..sourceLength` (toSource).
 * - **Monotonic:** a larger input never gives a smaller output.
 * - **Ends:** `toDisplay(sourceLength) == text.length` and `toSource(text.length) == sourceLength`.
 *   `toSource(0)` is the start of the first drawn piece (after any hidden marker at the line start).
 *   `toDisplay(0)` is 0, except on a bullet line drawn as "• item", where it is 2 (after the "• ").
 *
 * Edge cases at a hidden marker (e.g. `**` in `a **b**`, drawn as "a b"):
 * - A source position *inside or at the start of* a hidden marker maps to the display position where
 *   the marker would be (both sides of an invisible marker are the same display position).
 * - A display position *at* that spot maps to the source position *after* the marker, so the caret
 *   lands at the start of the visible text ("|b", not "|**b"). Typing there inserts after the marker.
 * - Inside replaced text ("• " drawn for "- "), source positions map to the end of the replacement, and
 *   display positions inside the replacement map to the end of the marker.
 *
 * Positions inside a surrogate pair map to positions inside the same pair (the mapping is 1:1 for
 * visible text), so callers that only pass code-point boundaries get code-point boundaries back.
 */
class LineView internal constructor(
    /** What kind of line this is. */
    val kind: LineKind,
    /** The text to draw. */
    val text: String,
    /** Style runs over [text]. */
    val runs: List<StyleRun>,
    private val segments: List<Segment>,
    /** Length of the line's markdown (the "source"), in UTF-16 chars. */
    val sourceLength: Int,
) {
    /**
     * Where a position in the line ([source]) ends up in the drawn text.
     * A position inside hidden or replaced text maps to the end of that piece.
     */
    fun toDisplay(source: Int): Int {
        val position = source.coerceIn(0, sourceLength)
        for (segment in segments) {
            if (position < segment.source.end) {
                return if (segment.replaced) {
                    segment.display.end
                } else {
                    segment.display.start + (position - segment.source.start)
                }
            }
        }
        return text.length
    }

    /**
     * Where a position in the drawn text ([display]) is in the line. At the edge of a hidden
     * marker it lands after the marker, at the start of the visible text.
     */
    fun toSource(display: Int): Int {
        val position = display.coerceIn(0, text.length)
        // Hidden markers draw nothing, so only segments with drawn text can contain a position.
        for (segment in segments) {
            if (segment.display.isEmpty) continue
            if (position <= segment.display.start) return segment.source.start
            if (position < segment.display.end) {
                return if (segment.replaced) {
                    segment.source.end
                } else {
                    segment.source.start + (position - segment.display.start)
                }
            }
        }
        return sourceLength
    }

    override fun toString(): String = "LineView(kind=$kind, text=\"$text\", runs=$runs)"

    /** Collects segments, text and runs while [LiveView.lineView] walks over a line. */
    internal class Builder(
        private val lineText: String,
    ) {
        private val text = StringBuilder()
        private val runs = mutableListOf<StyleRun>()
        private val segments = mutableListOf<Segment>()

        /** Draws [source] of the line as [kind], or draws [replacement] instead of it. */
        fun draw(
            source: TextRange,
            kind: SpanKind?,
            replacement: String? = null,
        ) {
            val drawn = replacement ?: lineText.substring(source)
            val start = text.length
            segments.add(Segment(TextRange(start, start + drawn.length), source, replaced = replacement != null))
            text.append(drawn)
            // Neighbours with the same style share one run.
            val last = runs.lastOrNull()
            if (last != null && last.kind == kind) {
                runs[runs.size - 1] = last.copy(length = last.length + drawn.length)
            } else {
                runs.add(StyleRun(drawn.length, kind))
            }
        }

        /** Hides [source]: it draws nothing, but keeps an empty segment so positions still map. */
        fun hide(source: TextRange) {
            segments.add(Segment(TextRange.at(text.length), source, replaced = true))
        }

        fun build(kind: LineKind): LineView = LineView(kind, text.toString(), runs.toList(), segments.toList(), lineText.length)
    }
}
