/**
 * Line-based helpers for [Format]: finding line starts/ends and rewriting every line a
 * selection touches (used by headings, quotes and lists).
 */
package com.dotnote.core

/** Helpers for the line-prefix transforms in [Format]. Positions are UTF-16 indexes. */
internal object LineFormat {
    /**
     * Rewrites every line touched by [selection] with `transform(lineIndex, line)`, where
     * `lineIndex` counts from 0 at the first touched line. [selection] must already be clamped.
     */
    fun transformLines(
        text: String,
        selection: TextRange,
        transform: (index: Int, line: String) -> String,
    ): Edit {
        // A selection that ends exactly at the start of a line (e.g. after selecting
        // whole lines) does not include that next line.
        var last = selection.end
        if (selection.end > selection.start && selection.end == lineStart(text, selection.end)) {
            last -= 1
        }
        val start = lineStart(text, selection.start)
        val end = lineEnd(text, last)
        val block = text.substring(start, end)

        val oldLines = block.split('\n')
        val newLines = oldLines.mapIndexed { index, line -> transform(index, line) }
        val replacement = newLines.joinToString("\n")

        // Shift the selection by how much its first line and the whole block grew.
        val firstDelta = newLines.first().length - oldLines.first().length
        val totalDelta = replacement.length - block.length

        val newStart = shift(selection.start, firstDelta).coerceAtLeast(start)
        val newEnd = shift(selection.end, totalDelta).coerceAtLeast(newStart)

        return Edit(
            range = TextRange(start, end),
            replacement = replacement,
            newSelection = TextRange(newStart, newEnd),
        )
    }

    /** Removes a leading ATX heading marker (`#` to `######` followed by a space). */
    fun stripHeading(line: String): String {
        val hashes = line.takeWhile { it == '#' }.length
        return if (hashes in 1..6 && line.startsWith(" ", startIndex = hashes)) {
            line.substring(hashes + 1)
        } else {
            line
        }
    }

    /** Index where the line containing [index] starts. */
    fun lineStart(
        text: String,
        index: Int,
    ): Int {
        // lastIndexOf returns -1 when there is no '\n' before, so the line starts at 0.
        return text.lastIndexOf('\n', startIndex = index - 1) + 1
    }

    /** Index where the line containing [index] ends (before its `\n`, or the end of the text). */
    fun lineEnd(
        text: String,
        index: Int,
    ): Int {
        val newline = text.indexOf('\n', startIndex = index)
        return if (newline == -1) text.length else newline
    }

    /** Moves [offset] by [delta], never below 0 (desktop: `saturating_add_signed`). */
    private fun shift(
        offset: Int,
        delta: Int,
    ): Int = (offset + delta).coerceAtLeast(0)
}
