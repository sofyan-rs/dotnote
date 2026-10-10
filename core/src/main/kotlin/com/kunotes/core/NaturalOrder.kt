/**
 * "Natural" name order, so "a2" comes before "a10", ignoring letter case.
 *
 * Port of `natord::compare_ignore_case` (Rust crate `natord` 1.0.9) used by desktop to sort the
 * file tree. It must stay identical, or the same vault would be sorted differently on each device.
 */
package com.kunotes.core

/**
 * How it compares (same as natord):
 * - Both texts are lowercased one character at a time, then compared character by character.
 * - Whitespace is skipped (so "pic 4" sorts like "pic4"), but it still ends a number.
 * - A run of ASCII digits is compared as a number: "15" < "123".
 * - If either number starts with `0`, the runs are compared digit by digit like decimals
 *   ("015" < "12", "1.010" < "1.02").
 *
 * `Comparator<String>` lets you write `names.sortedWith(NaturalOrder)`.
 */
object NaturalOrder : Comparator<String> {
    override fun compare(
        left: String,
        right: String,
    ): Int {
        val a = Reader(lowercaseCodePoints(left))
        val b = Reader(lowercaseCodePoints(right))

        while (true) {
            // Skip whitespace before each comparison step.
            a.skipWhitespace()
            b.skipWhitespace()

            val l = a.current
            val r = b.current
            // A text that ended first sorts first.
            if (l == null || r == null) {
                return when {
                    l == null && r == null -> 0
                    l == null -> -1
                    else -> 1
                }
            }
            val leftDigit = a.digit
            val rightDigit = b.digit
            if (leftDigit != null && rightDigit != null) {
                val result =
                    if (leftDigit == 0 || rightDigit == 0) {
                        compareLeftAligned(a, b)
                    } else {
                        compareRightAligned(a, b)
                    }
                if (result != 0) return result
                // Both readers already stand on the first character after the numbers.
                continue
            }
            val result = l.compareTo(r)
            if (result != 0) return result
            a.next()
            b.next()
        }
    }

    /** Numbers starting with 0: compare digit by digit; the first difference wins ("015" < "12"). */
    private fun compareLeftAligned(
        a: Reader,
        b: Reader,
    ): Int {
        // `!!` is safe: the caller only calls this when both readers stand on a digit.
        var result = a.digit!!.compareTo(b.digit!!)
        while (result == 0) {
            a.next()
            b.next()
            val l = a.digit
            val r = b.digit
            result =
                when {
                    l != null && r != null -> l.compareTo(r)
                    l != null -> return 1
                    r != null -> return -1
                    else -> return 0
                }
        }
        return result
    }

    /** Normal numbers: the longer run is bigger; same length → the first differing digit decides. */
    private fun compareRightAligned(
        a: Reader,
        b: Reader,
    ): Int {
        // `!!` is safe: the caller only calls this when both readers stand on a digit.
        var firstDifference = a.digit!!.compareTo(b.digit!!)
        while (true) {
            a.next()
            b.next()
            val l = a.digit
            val r = b.digit
            when {
                l != null && r != null -> if (firstDifference == 0) firstDifference = l.compareTo(r)
                l != null -> return 1
                r != null -> return -1
                else -> return firstDifference
            }
        }
    }

    /** Lowercases each character on its own, like Rust's `char::to_lowercase` (one may become two). */
    private fun lowercaseCodePoints(text: String): IntArray {
        val result = ArrayList<Int>(text.length)
        var index = 0
        while (index < text.length) {
            val codePoint = text.codePointAt(index)
            String(Character.toChars(codePoint)).lowercase().codePoints().forEach { result.add(it) }
            index += Character.charCount(codePoint)
        }
        return result.toIntArray()
    }

    /** Walks through code points one at a time; [current] is `null` at the end. */
    private class Reader(
        private val codePoints: IntArray,
    ) {
        private var index = 0

        val current: Int? get() = codePoints.getOrNull(index)

        /** The value of [current] if it is an ASCII digit `0`-`9` (Rust's `to_digit(10)`), else `null`. */
        val digit: Int? get() = current?.let { if (it in '0'.code..'9'.code) it - '0'.code else null }

        fun next() {
            index++
        }

        fun skipWhitespace() {
            while (current?.let { Names.isWhitespace(it) } == true) next()
        }
    }
}
