/**
 * Splits text into words, spaces and punctuation (Unicode UAX #29 word boundaries), for word-wise
 * cursor moves and double-click selection in [LiveBuffer]. Works on code points, so it never
 * splits an emoji's surrogate pair.
 */
package com.dotnote.core

/**
 * Word segmentation like desktop's `split_word_bound_indices` (`unicode-segmentation` crate):
 * "one, two  three" → `one` `,` ` ` `two` `  ` `three`. Letters and digits join into words
 * (`don't`, `3.14`, `snake_case`), CJK ideographs are one segment each, and an emoji is one segment.
 *
 * Simplifications: Thai/Lao/Khmer/Myanmar text (which needs a dictionary) is split per character,
 * and the Hebrew quote rules (WB7a-c) are left out.
 */
internal object WordSegments {
    /** The segments of [text], in order, covering all of it. Ranges are UTF-16 indexes. */
    fun split(text: String): List<TextRange> {
        // Work on code points: `starts[k]` is where code point k begins in the string.
        val starts = mutableListOf<Int>()
        val types = mutableListOf<Type>()
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            starts.add(i)
            types.add(typeOf(cp))
            i += Character.charCount(cp)
        }

        val segments = mutableListOf<TextRange>()
        var segmentStart = 0
        for (k in 1 until types.size) {
            if (isBreak(types, k)) {
                segments.add(TextRange(segmentStart, starts[k]))
                segmentStart = starts[k]
            }
        }
        if (text.isNotEmpty()) segments.add(TextRange(segmentStart, text.length))
        return segments
    }

    /** True if a segment contains a letter or digit (Rust's `char::is_alphanumeric`). */
    fun hasAlphanumeric(text: String): Boolean {
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            val type = Character.getType(cp)
            val isNumber =
                type == Character.DECIMAL_DIGIT_NUMBER.toInt() ||
                    type == Character.LETTER_NUMBER.toInt() ||
                    type == Character.OTHER_NUMBER.toInt()
            if (Character.isAlphabetic(cp) || isNumber) return true
            i += Character.charCount(cp)
        }
        return false
    }

    /** Is there a boundary before code point [k]? The UAX #29 rules WB3-WB999. */
    private fun isBreak(
        types: List<Type>,
        k: Int,
    ): Boolean {
        val before = types[k - 1]
        val after = types[k]
        if (before == Type.CR && after == Type.LF) return false // WB3
        if (before in NEWLINES || after in NEWLINES) return true // WB3a, WB3b
        if (before == Type.ZWJ && after == Type.PICTOGRAPHIC) return false // WB3c
        if (before == Type.SPACE && after == Type.SPACE) return false // WB3d
        if (after in IGNORED) return false // WB4: marks stick to what's before them

        // From here on, marks are skipped: compare the real characters around the boundary.
        val left = skipIgnoredBackward(types, k - 1)
        val l = types[left]
        val ll = previousReal(types, left)?.let { types[it] }
        val r = after
        val rr = nextReal(types, k)?.let { types[it] }

        return when {
            l.isLetter() && r.isLetter() -> false // WB5
            l.isLetter() && r.isMidLetter() && rr?.isLetter() == true -> false // WB6
            ll?.isLetter() == true && l.isMidLetter() && r.isLetter() -> false // WB7
            l == Type.NUMERIC && r == Type.NUMERIC -> false // WB8
            l.isLetter() && r == Type.NUMERIC -> false // WB9
            l == Type.NUMERIC && r.isLetter() -> false // WB10
            ll == Type.NUMERIC && l.isMidNum() && r == Type.NUMERIC -> false // WB11
            l == Type.NUMERIC && r.isMidNum() && rr == Type.NUMERIC -> false // WB12
            l == Type.KATAKANA && r == Type.KATAKANA -> false // WB13
            l in JOINS_UNDERSCORE && r == Type.EXTEND_NUM_LET -> false // WB13a
            l == Type.EXTEND_NUM_LET && r in JOINS_UNDERSCORE && r != Type.EXTEND_NUM_LET -> false // WB13b
            l == Type.REGIONAL && r == Type.REGIONAL && regionalRunBefore(types, left) % 2 == 1 -> false // WB15, WB16
            else -> true // WB999
        }
    }

    /** From [index], steps back over marks (WB4) to the character they belong to. */
    private fun skipIgnoredBackward(
        types: List<Type>,
        index: Int,
    ): Int {
        var p = index
        while (p > 0 && types[p] in IGNORED && types[p - 1] !in NEWLINES) p--
        return p
    }

    /** The real (non-mark) character before [index], or null. */
    private fun previousReal(
        types: List<Type>,
        index: Int,
    ): Int? {
        if (index == 0 || types[index - 1] in NEWLINES) return null
        return skipIgnoredBackward(types, index - 1)
    }

    /** The real (non-mark) character after [index], or null. */
    private fun nextReal(
        types: List<Type>,
        index: Int,
    ): Int? {
        var p = index + 1
        while (p < types.size && types[p] in IGNORED) p++
        return if (p < types.size) p else null
    }

    /** How many flag halves in a row end at [index] (marks skipped). */
    private fun regionalRunBefore(
        types: List<Type>,
        index: Int,
    ): Int {
        var count = 0
        var p: Int? = index
        while (p != null && types[p] == Type.REGIONAL) {
            count++
            p = previousReal(types, p)
        }
        return count
    }

    /** The word break property of a code point (only the values the rules need). */
    private enum class Type {
        CR,
        LF,
        NEWLINE,
        EXTEND,
        FORMAT,
        ZWJ,
        REGIONAL,
        KATAKANA,
        HEBREW_LETTER,
        A_LETTER,
        SINGLE_QUOTE,
        MID_NUM_LET,
        MID_LETTER,
        MID_NUM,
        NUMERIC,
        EXTEND_NUM_LET,
        SPACE,
        PICTOGRAPHIC,
        OTHER,
        ;

        fun isLetter() = this == A_LETTER || this == HEBREW_LETTER

        /** Joins two letters: `don't`, `e.g`, `a:b`. */
        fun isMidLetter() = this == MID_LETTER || this == MID_NUM_LET || this == SINGLE_QUOTE

        /** Joins two numbers: `3.14`, `1,000`. */
        fun isMidNum() = this == MID_NUM || this == MID_NUM_LET || this == SINGLE_QUOTE
    }

    private val NEWLINES = setOf(Type.CR, Type.LF, Type.NEWLINE)
    private val IGNORED = setOf(Type.EXTEND, Type.FORMAT, Type.ZWJ)
    private val JOINS_UNDERSCORE = setOf(Type.A_LETTER, Type.HEBREW_LETTER, Type.NUMERIC, Type.KATAKANA, Type.EXTEND_NUM_LET)

    private val MID_LETTER_CHARS = setOf(0x003A, 0x00B7, 0x0387, 0x055F, 0x05F4, 0x2027, 0xFE13, 0xFE55, 0xFF1A)
    private val MID_NUM_LET_CHARS = setOf(0x002E, 0x2018, 0x2019, 0x2024, 0xFE52, 0xFF07, 0xFF0E)
    private val MID_NUM_CHARS =
        setOf(0x002C, 0x003B, 0x037E, 0x0589, 0x060C, 0x060D, 0x066C, 0x07F8, 0x2044, 0xFE10, 0xFE14, 0xFE50, 0xFE54, 0xFF0C, 0xFF1B)
    private val EXTEND_NUM_LET_CHARS = setOf(0x005F, 0x202F, 0x203F, 0x2040, 0x2054, 0xFE33, 0xFE34, 0xFE4D, 0xFE4E, 0xFE4F, 0xFF3F)

    /** Scripts written without spaces between words; their letters are not joined into words. */
    private val NO_SPACE_SCRIPTS =
        setOf(
            Character.UnicodeScript.HAN,
            Character.UnicodeScript.HIRAGANA,
            Character.UnicodeScript.THAI,
            Character.UnicodeScript.LAO,
            Character.UnicodeScript.KHMER,
            Character.UnicodeScript.MYANMAR,
        )

    private fun typeOf(cp: Int): Type {
        val category = Character.getType(cp)
        val script = Character.UnicodeScript.of(cp)
        return when {
            cp == '\r'.code -> Type.CR
            cp == '\n'.code -> Type.LF
            cp == 0x0B || cp == 0x0C || cp == 0x85 || cp == 0x2028 || cp == 0x2029 -> Type.NEWLINE
            cp == 0x200D -> Type.ZWJ
            Graphemes.isExtend(cp) || category == Character.COMBINING_SPACING_MARK.toInt() -> Type.EXTEND
            category == Character.FORMAT.toInt() && cp != 0x200B -> Type.FORMAT
            Graphemes.isRegionalIndicator(cp) -> Type.REGIONAL
            script == Character.UnicodeScript.KATAKANA || cp == 0x30FC || cp in 0x3031..0x3035 || cp == 0x309B || cp == 0x309C ->
                Type.KATAKANA
            cp == '\''.code -> Type.SINGLE_QUOTE
            cp in MID_NUM_LET_CHARS -> Type.MID_NUM_LET
            cp in MID_LETTER_CHARS -> Type.MID_LETTER
            cp in MID_NUM_CHARS -> Type.MID_NUM
            category == Character.DECIMAL_DIGIT_NUMBER.toInt() -> Type.NUMERIC
            cp in EXTEND_NUM_LET_CHARS -> Type.EXTEND_NUM_LET
            Character.isAlphabetic(cp) && !Character.isIdeographic(cp) && script !in NO_SPACE_SCRIPTS ->
                if (script == Character.UnicodeScript.HEBREW) Type.HEBREW_LETTER else Type.A_LETTER
            category == Character.SPACE_SEPARATOR.toInt() && cp != 0x00A0 && cp != 0x2007 && cp != 0x202F -> Type.SPACE
            Graphemes.isPictographic(cp) -> Type.PICTOGRAPHIC
            else -> Type.OTHER
        }
    }
}
