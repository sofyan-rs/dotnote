/**
 * Keeps a file's line endings (`\n` or `\r\n`) unchanged across load and save.
 *
 * The editor always works with `\n`. When a note is opened we remember its original style and convert
 * back when saving, so Windows-made files stay `\r\n` and git doesn't see every line as changed.
 */
package com.kunotes.core

/** The line ending style of a file. Port of desktop `line_ending.rs`. */
enum class LineEnding {
    /** `\n` (Linux, macOS, Android). The default. */
    LF,

    /** `\r\n` (Windows). */
    CRLF,
    ;

    /** Converts editor text (`\n`) back to this style for saving. */
    fun apply(text: String): String =
        when (this) {
            LF -> text
            CRLF -> text.replace("\n", "\r\n")
        }

    // A `companion object` holds functions called on the type itself: `LineEnding.detect(text)`.
    companion object {
        /** Detects the style from the first line break. Files without any line break count as `\n`. */
        fun detect(text: String): LineEnding {
            val index = text.indexOf('\n')
            return if (index > 0 && text[index - 1] == '\r') CRLF else LF
        }

        /** Converts any `\r\n` to `\n`, for loading into the editor. */
        fun normalize(text: String): String = text.replace("\r\n", "\n")
    }
}
