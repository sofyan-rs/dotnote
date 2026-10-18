package com.dotnote.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class NamesTest {
    @Test
    fun `accepts normal names`() {
        for (name in listOf("Note", "My Note.md", "2026-10-09", "日本語", "café notes", "a.b.c", "Party 🎉")) {
            assertNull(Names.validateName(name), name)
        }
    }

    @Test
    fun `rejects empty and whitespace`() {
        assertEquals(NameError.Empty, Names.validateName(""))
        assertEquals(NameError.Empty, Names.validateName("   "))
        // No-break space and ideographic space are whitespace for desktop too.
        assertEquals(NameError.Empty, Names.validateName(" 　"))
    }

    @Test
    fun `rejects dot names and hidden names`() {
        assertEquals(NameError.DotName, Names.validateName("."))
        assertEquals(NameError.DotName, Names.validateName(".."))
        assertEquals(NameError.Hidden, Names.validateName(".secret"))
    }

    @Test
    fun `rejects path separators and windows chars`() {
        for (c in Names.INVALID_CHARS) {
            assertEquals(NameError.InvalidChar(c), Names.validateName("a${c}b"))
        }
    }

    @Test
    fun `rejects control characters`() {
        assertEquals(NameError.ControlChar, Names.validateName("a\tb"))
        assertEquals(NameError.ControlChar, Names.validateName("a\nb"))
    }

    @Test
    fun `rejects trailing space or dot`() {
        assertEquals(NameError.TrailingSpaceOrDot, Names.validateName("note "))
        assertEquals(NameError.TrailingSpaceOrDot, Names.validateName("note."))
    }

    @Test
    fun `rejects reserved windows names in any case`() {
        assertEquals(NameError.Reserved("CON"), Names.validateName("CON"))
        assertEquals(NameError.Reserved("nul"), Names.validateName("nul.md"))
        assertEquals(NameError.Reserved("Com1"), Names.validateName("Com1.txt"))
        assertNull(Names.validateName("CONSOLE"))
    }

    @Test
    fun `messages match desktop`() {
        assertEquals("the name can't contain '/'", NameError.InvalidChar('/').message)
        assertEquals("\"CON\" is a reserved name on Windows", NameError.Reserved("CON").message)
        val error = assertThrows<CoreError.InvalidName> { Names.requireValidName("a:b") }
        assertEquals("Invalid name: the name can't contain ':'", error.message)
    }

    @Test
    fun `trim uses unicode whitespace and keeps emoji intact`() {
        assertEquals("🎉 a", Names.trim("  🎉 a　\n"))
        assertEquals("", Names.trim("  "))
    }
}
