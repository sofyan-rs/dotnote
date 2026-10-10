/**
 * Tiny character tests and trims shared by the Live-mode files (`Live`, `LiveView`, `LiveTable`,
 * `LiveBuffer`). They copy the exact rules of the Rust `str`/`char` methods desktop uses, so both
 * apps split and trim text the same way.
 */
package com.kunotes.core

internal object LiveText {
    /**
     * Rust's `char::is_whitespace` (the Unicode `White_Space` property).
     * Kotlin's `Char.isWhitespace()` is close but also accepts a few control characters
     * (U+001C..U+001F) and rejects U+0085, so we list the set ourselves.
     * All of these are single UTF-16 chars, so checking one `Char` is enough.
     */
    fun isWhitespace(c: Char): Boolean =
        when (c) {
            '\t', '\n', '\u000B', '\u000C', '\r', ' ', '\u0085', ' ', ' ',
            ' ', ' ', ' ', ' ', '　',
            -> true
            in ' '..' ' -> true
            else -> false
        }

    /** Rust's `u8::is_ascii_whitespace`: space, tab, `\n`, form feed, `\r` (not vertical tab). */
    fun isAsciiWhitespace(c: Char): Boolean = c == ' ' || c == '\t' || c == '\n' || c == '\u000C' || c == '\r'

    /** `0-9` only. Kotlin's `isDigit()` also accepts digits from other scripts (`٣`), which desktop doesn't. */
    fun isAsciiDigit(c: Char): Boolean = c in '0'..'9'

    /** `a-z`, `A-Z`, `0-9` only. */
    fun isAsciiAlphanumeric(c: Char): Boolean = c in 'a'..'z' || c in 'A'..'Z' || isAsciiDigit(c)

    /** How many whitespace chars [text] starts with (Rust: `len - trim_start().len()`). */
    fun leadingWhitespace(text: String): Int {
        var i = 0
        while (i < text.length && isWhitespace(text[i])) i++
        return i
    }

    /** The length of [text] without its trailing whitespace (Rust: `trim_end().len()`). */
    fun endWithoutWhitespace(text: String): Int {
        var end = text.length
        while (end > 0 && isWhitespace(text[end - 1])) end--
        return end
    }

    /** [text] without whitespace at either end (Rust: `trim()`). */
    fun trim(text: String): String {
        val end = endWithoutWhitespace(text)
        val start = minOf(leadingWhitespace(text), end)
        return text.substring(start, end)
    }

    /** How many [c]s in a row start at [start] in [text]. */
    fun runLength(
        text: String,
        start: Int,
        c: Char,
    ): Int {
        var i = start
        while (i < text.length && text[i] == c) i++
        return i - start
    }
}
