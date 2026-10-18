/**
 * A range of characters in a note's text: from [start] (included) to [end] (not included).
 *
 * Positions are indexes into the Kotlin `String` (UTF-16 code units). They must never point
 * into the middle of a surrogate pair (an emoji is two code units).
 */
package com.dotnote.core

data class TextRange(
    val start: Int,
    val end: Int,
) {
    init {
        require(start in 0..end) { "Invalid range $start..$end" }
    }

    val length: Int get() = end - start
    val isEmpty: Boolean get() = start == end

    operator fun contains(index: Int): Boolean = index in start until end

    companion object {
        /** An empty range at [index] (a cursor with nothing selected). */
        fun at(index: Int) = TextRange(index, index)
    }
}

/** The part of this string covered by [range]. */
fun String.substring(range: TextRange): String = substring(range.start, range.end)
