/**
 * Checks file and folder names so they work on macOS, Windows, Linux and Android at once.
 *
 * Vaults are often synced between devices (git, Dropbox, ...), so the strictest rules (Windows)
 * apply everywhere. Port of desktop `names.rs`; the messages are the same as desktop's.
 */
package com.kunotes.core

/**
 * Why a file or folder name was rejected. [message] is shown to the user (after "Invalid name: ").
 *
 * A `sealed class` lists every possible reason, so a `when` over a `NameError` must handle all of them.
 * `data object` is a singleton with a readable `toString()`, used for reasons without extra data.
 */
sealed class NameError(
    val message: String,
) {
    data object Empty : NameError("the name can't be empty")

    data object DotName : NameError("\".\" and \"..\" can't be used as names")

    data object Hidden : NameError("names starting with \".\" are hidden")

    data class InvalidChar(
        val char: Char,
    ) : NameError("the name can't contain '$char'")

    data object ControlChar : NameError("the name can't contain control characters")

    data class Reserved(
        val name: String,
    ) : NameError("\"$name\" is a reserved name on Windows")

    data object TrailingSpaceOrDot : NameError("the name can't end with a space or a dot")
}

object Names {
    /** Characters that are not allowed in names on Windows (plus both path separators). */
    val INVALID_CHARS: List<Char> = listOf('/', '\\', '<', '>', ':', '"', '|', '?', '*')

    /** Device names that Windows reserves, with or without an extension. */
    private val RESERVED_NAMES =
        listOf(
            "CON",
            "PRN",
            "AUX",
            "NUL",
            "COM1",
            "COM2",
            "COM3",
            "COM4",
            "COM5",
            "COM6",
            "COM7",
            "COM8",
            "COM9",
            "LPT1",
            "LPT2",
            "LPT3",
            "LPT4",
            "LPT5",
            "LPT6",
            "LPT7",
            "LPT8",
            "LPT9",
        )

    /**
     * Checks that [name] is safe to use as a single file or folder name.
     * Returns `null` if it is fine, or the reason it isn't.
     *
     * The caller should [trim] the name first; this function checks the name as given.
     */
    fun validateName(name: String): NameError? {
        if (trim(name).isEmpty()) return NameError.Empty
        if (name == "." || name == "..") return NameError.DotName
        if (name.startsWith('.')) return NameError.Hidden

        // Walk code points (not UTF-16 chars) so an emoji is checked as one character.
        var index = 0
        while (index < name.length) {
            val codePoint = name.codePointAt(index)
            val invalid = INVALID_CHARS.firstOrNull { it.code == codePoint }
            if (invalid != null) return NameError.InvalidChar(invalid)
            // ISO control = Unicode category Cc, the same set as Rust's `char::is_control`.
            if (Character.isISOControl(codePoint)) return NameError.ControlChar
            index += Character.charCount(codePoint)
        }
        if (name.endsWith(' ') || name.endsWith('.')) return NameError.TrailingSpaceOrDot

        // "CON.md" is just as reserved as "CON", so only look at the part before the first dot.
        val stem = name.substringBefore('.')
        if (RESERVED_NAMES.any { asciiEqualsIgnoreCase(it, stem) }) return NameError.Reserved(stem)

        return null
    }

    /** Like [validateName], but throws [CoreError.InvalidName] instead of returning the reason. */
    fun requireValidName(name: String) {
        val error = validateName(name)
        if (error != null) throw CoreError.InvalidName(error.message)
    }

    /**
     * [name] without leading and trailing whitespace, using the same whitespace set as desktop
     * (Rust's `str::trim`). Kotlin's own `trim()` uses a slightly different set.
     */
    fun trim(name: String): String {
        var start = 0
        var end = name.length
        while (start < end && isWhitespace(name.codePointAt(start))) {
            start += Character.charCount(name.codePointAt(start))
        }
        while (end > start && isWhitespace(name.codePointBefore(end))) {
            end -= Character.charCount(name.codePointBefore(end))
        }
        return name.substring(start, end)
    }

    /**
     * True if [codePoint] has the Unicode `White_Space` property, like Rust's `char::is_whitespace`.
     *
     * Java's `Character.isWhitespace` differs (it skips no-break spaces and counts some control
     * characters), so the list is written out here to sort and trim exactly like desktop.
     */
    fun isWhitespace(codePoint: Int): Boolean =
        when (codePoint) {
            in 0x09..0x0D, 0x20, 0x85, 0xA0, 0x1680, in 0x2000..0x200A, 0x2028, 0x2029, 0x202F, 0x205F, 0x3000 -> true
            else -> false
        }

    /** Compares letter case only for A-Z, like Rust's `eq_ignore_ascii_case`. */
    private fun asciiEqualsIgnoreCase(
        a: String,
        b: String,
    ): Boolean {
        if (a.length != b.length) return false
        return a.indices.all { i -> asciiLower(a[i]) == asciiLower(b[i]) }
    }

    private fun asciiLower(char: Char): Char = if (char in 'A'..'Z') char + ('a' - 'A') else char
}
