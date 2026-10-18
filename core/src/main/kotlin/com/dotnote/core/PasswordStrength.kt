/**
 * A rough password-strength rating for the "Set a notes password" screen: the dot
 * meter and the `STRONG · 16 CHARS` label under the password field. Android only (desktop has
 * no meter).
 *
 * It is a hint for the user, not a security check: the only hard rule is
 * [Lock.MIN_PASSWORD_LEN]. The estimate is "bits of guessing work" from the length and the kinds
 * of characters used, with repeated characters counted less.
 */
package com.dotnote.core

import kotlin.math.ln
import kotlin.math.min

/** The result of [PasswordStrength.rate]. */
data class PasswordRating(
    /** How many of [PasswordStrength.METER_DOTS] meter dots to light (0 for an empty password). */
    val level: Int,
    val verdict: PasswordVerdict,
    /** Length in characters (Unicode code points, like [Lock.MIN_PASSWORD_LEN] counts them). */
    val chars: Int,
) {
    /** The label next to the meter, e.g. `STRONG · 16 CHARS` (`1 CHAR` for one). */
    val label: String get() = "${verdict.label} · $chars ${if (chars == 1) "CHAR" else "CHARS"}"
}

/** The word shown next to the meter. */
enum class PasswordVerdict(
    val label: String,
) {
    /** Shorter than [Lock.MIN_PASSWORD_LEN]: can't be used. */
    TooShort("TOO SHORT"),
    Weak("WEAK"),
    Fair("FAIR"),
    Good("GOOD"),
    Strong("STRONG"),
}

object PasswordStrength {
    /** Number of dots in the meter (mockup: 12). */
    const val METER_DOTS = 12

    /** Each lit dot stands for this many bits of guessing work (so ~120 bits lights all 12). */
    private const val BITS_PER_DOT = 10.0

    // Verdict thresholds in bits.
    private const val FAIR_BITS = 40.0
    private const val GOOD_BITS = 60.0
    private const val STRONG_BITS = 80.0

    // How many different symbols an attacker must try per character, per kind of character.
    private const val LOWER_POOL = 26
    private const val UPPER_POOL = 26
    private const val DIGIT_POOL = 10
    private const val SYMBOL_POOL = 33 // printable ASCII punctuation and space
    private const val OTHER_POOL = 100 // letters outside ASCII (é, 日, emoji): a rough guess

    /** Rates [password]. Example: `correct-horse-87` → 9 dots, STRONG, 16 chars. */
    fun rate(password: String): PasswordRating {
        val codePoints = password.codePoints().toArray()
        val chars = codePoints.size
        val bits = estimateBits(codePoints)
        val level = if (chars == 0) 0 else (bits / BITS_PER_DOT).toInt().coerceIn(1, METER_DOTS)
        val verdict =
            when {
                chars < Lock.MIN_PASSWORD_LEN -> PasswordVerdict.TooShort
                bits < FAIR_BITS -> PasswordVerdict.Weak
                bits < GOOD_BITS -> PasswordVerdict.Fair
                bits < STRONG_BITS -> PasswordVerdict.Good
                else -> PasswordVerdict.Strong
            }
        return PasswordRating(level = level, verdict = verdict, chars = chars)
    }

    /** log2(pool) bits per character, over an "effective" length that discounts repeats. */
    private fun estimateBits(codePoints: IntArray): Double {
        if (codePoints.isEmpty()) return 0.0
        var pool = 0
        if (codePoints.any { it in 'a'.code..'z'.code }) pool += LOWER_POOL
        if (codePoints.any { it in 'A'.code..'Z'.code }) pool += UPPER_POOL
        if (codePoints.any { it in '0'.code..'9'.code }) pool += DIGIT_POOL
        if (codePoints.any { it in ' '.code..'~'.code && !Character.isLetterOrDigit(it) }) pool += SYMBOL_POOL
        if (codePoints.any { it > '~'.code }) pool += OTHER_POOL
        // "aaaaaaaa" or "abababab" is far easier to guess than its length suggests: count at most
        // two characters per distinct character.
        val distinct = codePoints.distinct().size
        val effectiveLength = min(codePoints.size, distinct * 2)
        return effectiveLength * ln(pool.toDouble()) / ln(2.0)
    }
}
