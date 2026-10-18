package com.dotnote.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The strength hint on the Set-Password screen (SPEC 5.1). */
class PasswordStrengthTest {
    @Test
    fun `the mockup password is strong with 9 of 12 dots`() {
        val rating = PasswordStrength.rate("correct-horse-87")
        assertEquals(PasswordVerdict.Strong, rating.verdict)
        assertEquals(9, rating.level)
        assertEquals(16, rating.chars)
        assertEquals("STRONG · 16 CHARS", rating.label)
    }

    @Test
    fun `empty password lights nothing`() {
        val rating = PasswordStrength.rate("")
        assertEquals(0, rating.level)
        assertEquals(PasswordVerdict.TooShort, rating.verdict)
        assertEquals("TOO SHORT · 0 CHARS", rating.label)
    }

    @Test
    fun `shorter than the minimum is too short but still lights a dot`() {
        val rating = PasswordStrength.rate("a")
        assertEquals(PasswordVerdict.TooShort, rating.verdict)
        assertEquals(1, rating.level)
        assertEquals("TOO SHORT · 1 CHAR", rating.label)
        assertEquals(PasswordVerdict.TooShort, PasswordStrength.rate("Abc-123").verdict)
    }

    @Test
    fun `repeated characters count less`() {
        assertEquals(PasswordVerdict.Weak, PasswordStrength.rate("aaaaaaaaaaaaaaaa").verdict)
        assertEquals(PasswordVerdict.Weak, PasswordStrength.rate("abababababababab").verdict)
        assertTrue(PasswordStrength.rate("aaaaaaaaaaaa").level < PasswordStrength.rate("qwmzrtplkbvx").level)
    }

    @Test
    fun `verdict grows with length and variety`() {
        assertEquals(PasswordVerdict.Weak, PasswordStrength.rate("password").verdict)
        assertEquals(PasswordVerdict.Fair, PasswordStrength.rate("Password12").verdict)
        assertEquals(PasswordVerdict.Good, PasswordStrength.rate("Tr0ub4dor&3").verdict)
        assertEquals(PasswordStrength.METER_DOTS, PasswordStrength.rate("A long passphrase, with 4 words & more!").level)
    }

    @Test
    fun `characters are code points, so an emoji counts as one`() {
        assertEquals(8, PasswordStrength.rate("🔑🔑🔑🔑abcd").chars)
        assertEquals(3, PasswordStrength.rate("日本語").chars)
    }
}
