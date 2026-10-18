/**
 * Finds the edges of user-perceived characters ("grapheme clusters", Unicode UAX #29), so the
 * cursor steps over 👩‍💻, 🇮🇩, 👍🏽 or "é" (e + accent) as one character. Used by [LiveBuffer].
 */
package com.dotnote.core

/**
 * Grapheme cluster boundaries, following the extended grapheme cluster rules of UAX #29 (what
 * desktop's `unicode-segmentation` crate does).
 *
 * Why not `java.text.BreakIterator`? On the JVM 17 that runs our tests it splits ZWJ emoji (👩‍💻),
 * flags (🇮🇩) and skin tones (👍🏽) into several characters. Android's version (ICU) is right, but
 * the same rules written here give the same answers everywhere.
 *
 * Simplifications: the Indic conjunct rule (GB9c) is left out, and "Extended_Pictographic" uses the
 * main emoji blocks rather than the exact Unicode list. Both only matter for rare text.
 */
internal object Graphemes {
    /** The start of the character before [offset] (0 at the start of the text). */
    fun previousBoundary(
        text: String,
        offset: Int,
    ): Int {
        val target = offset.coerceIn(0, text.length)
        if (target == 0) return 0
        // A cluster never crosses a `\n`, so the start of the line before `target - 1` is a safe
        // place to start counting from. (`lastIndexOf` with a negative start returns -1.)
        var boundary = text.lastIndexOf('\n', target - 2) + 1
        while (true) {
            val next = clusterEnd(text, boundary)
            if (next >= target) return boundary
            boundary = next
        }
    }

    /** The end of the character after [offset] (the text length at the end). */
    fun nextBoundary(
        text: String,
        offset: Int,
    ): Int {
        val target = offset.coerceIn(0, text.length)
        if (target == text.length) return target
        var boundary = text.lastIndexOf('\n', target - 1) + 1
        while (true) {
            val next = clusterEnd(text, boundary)
            if (next > target) return next
            boundary = next
        }
    }

    /** Where the cluster starting at [start] ends. [start] must be a cluster boundary before the end. */
    internal fun clusterEnd(
        text: String,
        start: Int,
    ): Int {
        var previous = text.codePointAt(start)
        var previousType = typeOf(previous)
        var i = start + Character.charCount(previous)
        // State for the emoji rule (GB11) and the flag rule (GB12/13).
        var inPictographic = previousType == Type.PICTOGRAPHIC // after "Pictographic Extend*"
        var zwjAfterPictographic = false // previous is a ZWJ that followed "Pictographic Extend*"
        var regionalCount = if (previousType == Type.REGIONAL) 1 else 0

        while (i < text.length) {
            val current = text.codePointAt(i)
            val type = typeOf(current)
            if (isBreak(previousType, type, zwjAfterPictographic, regionalCount)) break

            zwjAfterPictographic = type == Type.ZWJ && inPictographic
            inPictographic = type == Type.PICTOGRAPHIC || (type == Type.EXTEND && inPictographic)
            regionalCount = if (type == Type.REGIONAL) regionalCount + 1 else 0
            previousType = type
            i += Character.charCount(current)
        }
        return i
    }

    /** The UAX #29 rules GB3-GB999: is there a boundary between a [before] and an [after] character? */
    private fun isBreak(
        before: Type,
        after: Type,
        zwjAfterPictographic: Boolean,
        regionalCount: Int,
    ): Boolean =
        when {
            before == Type.CR && after == Type.LF -> false // GB3
            before in LINE_TYPES || after in LINE_TYPES -> true // GB4, GB5
            before == Type.L && after in setOf(Type.L, Type.V, Type.LV, Type.LVT) -> false // GB6 (Hangul)
            before in setOf(Type.LV, Type.V) && after in setOf(Type.V, Type.T) -> false // GB7
            before in setOf(Type.LVT, Type.T) && after == Type.T -> false // GB8
            after == Type.EXTEND || after == Type.ZWJ || after == Type.SPACING_MARK -> false // GB9, GB9a
            before == Type.PREPEND -> false // GB9b
            before == Type.ZWJ && after == Type.PICTOGRAPHIC && zwjAfterPictographic -> false // GB11
            before == Type.REGIONAL && after == Type.REGIONAL && regionalCount % 2 == 1 -> false // GB12, GB13
            else -> true // GB999
        }

    /** The grapheme break property of a code point (only the values the rules need). */
    private enum class Type { CR, LF, CONTROL, EXTEND, ZWJ, REGIONAL, PREPEND, SPACING_MARK, L, V, T, LV, LVT, PICTOGRAPHIC, OTHER }

    private val LINE_TYPES = setOf(Type.CR, Type.LF, Type.CONTROL)

    private fun typeOf(cp: Int): Type =
        when {
            cp == '\r'.code -> Type.CR
            cp == '\n'.code -> Type.LF
            cp == 0x200D -> Type.ZWJ
            isExtend(cp) -> Type.EXTEND
            isRegionalIndicator(cp) -> Type.REGIONAL
            cp in PREPEND -> Type.PREPEND
            isControl(cp) -> Type.CONTROL
            Character.getType(cp) == Character.COMBINING_SPACING_MARK.toInt() -> Type.SPACING_MARK
            cp in 0x1100..0x115F || cp in 0xA960..0xA97C -> Type.L
            cp in 0x1160..0x11A7 || cp in 0xD7B0..0xD7C6 -> Type.V
            cp in 0x11A8..0x11FF || cp in 0xD7CB..0xD7FB -> Type.T
            cp in 0xAC00..0xD7A3 -> if ((cp - 0xAC00) % 28 == 0) Type.LV else Type.LVT
            isPictographic(cp) -> Type.PICTOGRAPHIC
            else -> Type.OTHER
        }

    /** The few "Prepend" characters that stick to the character after them (Arabic number signs...). */
    private val PREPEND = setOf(0x0600, 0x0601, 0x0602, 0x0603, 0x0604, 0x0605, 0x06DD, 0x070F, 0x0890, 0x0891, 0x08E2, 0x110BD, 0x110CD)

    /** Marks that attach to the previous character: accents, ZWNJ, skin tones, emoji tag characters. */
    fun isExtend(cp: Int): Boolean {
        val type = Character.getType(cp)
        return type == Character.NON_SPACING_MARK.toInt() ||
            type == Character.ENCLOSING_MARK.toInt() ||
            cp == 0x200C || // zero-width non-joiner
            cp in 0x1F3FB..0x1F3FF || // skin tone modifiers
            cp in 0xE0020..0xE007F || // tags (flags like England)
            cp == 0xFF9E || cp == 0xFF9F // half-width katakana sound marks
    }

    /** One half of a flag: 🇦..🇿. */
    fun isRegionalIndicator(cp: Int): Boolean = cp in 0x1F1E6..0x1F1FF

    private fun isControl(cp: Int): Boolean {
        val type = Character.getType(cp)
        return type == Character.CONTROL.toInt() ||
            type == Character.LINE_SEPARATOR.toInt() ||
            type == Character.PARAGRAPH_SEPARATOR.toInt() ||
            type == Character.FORMAT.toInt() ||
            type == Character.UNASSIGNED.toInt() && cp in 0xFFF0..0xFFF8
    }

    /** Emoji and other pictographs (approximation of Unicode's Extended_Pictographic). */
    fun isPictographic(cp: Int): Boolean =
        cp == 0x00A9 || cp == 0x00AE || cp == 0x203C || cp == 0x2049 || cp == 0x2122 || cp == 0x2139 ||
            cp in 0x2194..0x21AA ||
            cp in 0x2300..0x23FF ||
            cp in 0x24C2..0x24C2 ||
            cp in 0x25AA..0x25FE ||
            cp in 0x2600..0x27BF ||
            cp in 0x2934..0x2935 ||
            cp in 0x2B05..0x2B55 ||
            cp == 0x3030 || cp == 0x303D || cp == 0x3297 || cp == 0x3299 ||
            (cp in 0x1F000..0x1FAFF && !isRegionalIndicator(cp) && cp !in 0x1F3FB..0x1F3FF) ||
            cp in 0x1FC00..0x1FFFD
}
