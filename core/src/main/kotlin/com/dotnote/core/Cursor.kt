/**
 * Numbers for the editor's status line: cursor line/column and character count.
 * Port of desktop `cursor.rs`.
 */
package com.dotnote.core

/** Cursor position and character counting for the status line (`LN, COL · CHARS`). */
object Cursor {
    /** A 1-based line and column, as shown to the user. */
    data class Position(
        val line: Int,
        val column: Int,
    )

    /**
     * The 1-based line and column of [offset] (a UTF-16 index into [text]).
     *
     * Columns count code points, not UTF-16 units, so "é", "日" or "😀" move the column by one
     * (same as desktop, which counts Rust `char`s). An offset past the end, or in the middle of a
     * surrogate pair, is clamped safely.
     */
    fun lineCol(
        text: String,
        offset: Int,
    ): Position {
        var safeOffset = offset.coerceIn(0, text.length)
        if (isInsideSurrogatePair(text, safeOffset)) {
            safeOffset -= 1
        }

        val before = text.substring(0, safeOffset)
        val line = before.count { it == '\n' } + 1
        // lastIndexOf returns -1 when there is no '\n', so the line starts at 0.
        val lineStart = before.lastIndexOf('\n') + 1
        val column = before.codePointCount(lineStart, before.length) + 1
        return Position(line, column)
    }

    /**
     * The number of characters as a person would count them (grapheme clusters).
     *
     * An emoji like "👩‍💻" (three code points joined by a zero-width joiner) or a flag "🇮🇩"
     * (two regional indicators) counts as one, like desktop's `unicode-segmentation`.
     * Uses [Graphemes], the same rules the Live editor uses to move the cursor, so the count
     * and the cursor always agree (and JVM tests behave like the phone).
     */
    fun charCount(text: String): Int {
        var count = 0
        var offset = 0
        while (offset < text.length) {
            offset = Graphemes.clusterEnd(text, offset) // offset is always a cluster start here
            count += 1
        }
        return count
    }

    /** True when [index] points at the low half of a surrogate pair (i.e. inside one emoji code point). */
    private fun isInsideSurrogatePair(
        text: String,
        index: Int,
    ): Boolean =
        index in 1 until text.length &&
            Character.isLowSurrogate(text[index]) &&
            Character.isHighSurrogate(text[index - 1])
}
