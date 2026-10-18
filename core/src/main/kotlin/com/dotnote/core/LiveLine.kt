/**
 * One line of a note as Live mode sees it ([LiveLine]) and what kind of line it is ([LineKind]).
 * Built by [LiveView.lines]; part of the port of desktop `live_view.rs`.
 */
package com.dotnote.core

/** What kind of line this is, which decides its font size and decorations. */
sealed class LineKind {
    data object Paragraph : LineKind()

    data class Heading(
        val level: Int,
    ) : LineKind()

    data object Quote : LineKind()

    /** A line inside a fenced code block. */
    data object CodeBlock : LineKind()

    /** The ```` ``` ```` line that opens or closes a code block. */
    data object Fence : LineKind()

    data object Rule : LineKind()

    /** A line that is only an image: `![alt](path)`. */
    data object Image : LineKind()

    data object Bullet : LineKind()

    data object Numbered : LineKind()

    /** A task list item; [checked] is `[x]`. */
    data class Task(
        val checked: Boolean,
    ) : LineKind()
}

/**
 * One line of the note.
 *
 * @property range where the line is in the note, without its line break (and without a `\r` before it).
 * @property spans styled parts, with ranges relative to the line start (0 = first char of the line).
 */
data class LiveLine(
    val range: TextRange,
    val spans: List<Span>,
)
