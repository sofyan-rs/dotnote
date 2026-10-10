/**
 * List rules for the Live editor: ticking a task box (`- [ ]` ↔ `- [x]`) and the marker a new line
 * gets when Enter is pressed in a list (`- `, `1. ` → `2. `, `- [ ] `). Part of desktop `live_buffer.rs`.
 */
package com.kunotes.core

/** What to change to flip a task box: replace [range] (the `[ ]`/`[x]`) with [replacement]. */
data class TaskToggle(
    val range: TextRange,
    val replacement: String,
)

/** For a list line: how long its marker is (indent included) and the marker for the next line. */
internal data class ListContinuation(
    val markerLength: Int,
    val nextMarker: String,
)

object LiveLists {
    /**
     * The `[ ]` / `[x]` box on the task line containing [offset], and what to replace it with to
     * flip it. Null if that line isn't a task (or [offset] is inside an emoji's surrogate pair).
     */
    fun taskToggle(
        text: String,
        offset: Int,
    ): TaskToggle? {
        val position = offset.coerceIn(0, text.length)
        // Desktop returns None for an offset that isn't on a character boundary; do the same.
        if (position in 1 until text.length && Character.isLowSurrogate(text[position]) && Character.isHighSurrogate(text[position - 1])) {
            return null
        }
        val start = text.lastIndexOf('\n', position - 1) + 1
        val newline = text.indexOf('\n', start)
        val end = if (newline == -1) text.length else newline
        val line = text.substring(start, end)
        val indent = LiveText.leadingWhitespace(line)
        val rest = line.substring(indent)

        val first = rest.firstOrNull()
        val markerLength =
            if ((first == '-' || first == '*' || first == '+') && rest.getOrNull(1) == ' ') {
                2
            } else {
                // A numbered item: digits, then `.` or `)`. (Desktop doesn't check the space after it here.)
                val digits = rest.takeWhile { LiveText.isAsciiDigit(it) }.length
                val separator = rest.getOrNull(digits)
                if (digits == 0 || (separator != '.' && separator != ')')) return null
                digits + 2
            }

        val boxStart = start + indent + markerLength
        if (boxStart + 3 > text.length) return null
        val replacement =
            when (text.substring(boxStart, boxStart + 3)) {
                "[ ]" -> "[x]"
                "[x]", "[X]" -> "[ ]"
                else -> return null
            }
        return TaskToggle(TextRange(boxStart, boxStart + 3), replacement)
    }

    /** For a list [line]: its marker length and the marker for the next line, or null if it isn't a list item. */
    internal fun continuation(line: String): ListContinuation? {
        val indent = line.substring(0, LiveText.leadingWhitespace(line))
        val rest = line.substring(indent.length)
        val first = rest.firstOrNull() ?: return null

        val markerLength: Int
        val next: String
        if ((first == '-' || first == '*' || first == '+') && rest.getOrNull(1) == ' ') {
            val bullet = rest.substring(0, 2)
            val afterBullet = rest.substring(2)
            if (afterBullet.startsWith("[ ] ") || afterBullet.startsWith("[x] ") || afterBullet.startsWith("[X] ")) {
                markerLength = 6
                next = "$bullet[ ] " // a new task starts unticked
            } else {
                markerLength = 2
                next = bullet
            }
        } else if (LiveText.isAsciiDigit(first)) {
            val digits = rest.takeWhile { LiveText.isAsciiDigit(it) }.length
            val separator = rest.getOrNull(digits) ?: return null
            if ((separator != '.' && separator != ')') || rest.getOrNull(digits + 1) != ' ') return null
            // Desktop parses into a u64, so numbers too big for it aren't list items.
            val number = rest.substring(0, digits).toULongOrNull() ?: return null
            markerLength = digits + 2
            next = "${number + 1u}$separator "
        } else {
            return null
        }
        return ListContinuation(indent.length + markerLength, indent + next)
    }
}
