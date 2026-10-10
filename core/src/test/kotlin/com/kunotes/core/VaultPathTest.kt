package com.kunotes.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VaultPathTest {
    @Test
    fun `name and parent`() {
        val path = VaultPath("Projects/Plan.md")
        assertEquals("Plan.md", path.name)
        assertEquals(VaultPath("Projects"), path.parent)
        assertEquals(VaultPath.ROOT, VaultPath("Plan.md").parent)
        assertNull(VaultPath.ROOT.parent)
    }

    @Test
    fun `child of root has no leading slash`() {
        assertEquals("a.md", VaultPath.ROOT.child("a.md").value)
        assertEquals("x/a.md", VaultPath("x").child("a.md").value)
    }

    @Test
    fun `isInside compares whole parts`() {
        assertTrue(VaultPath("a/b").isInside(VaultPath("a")))
        assertTrue(VaultPath("a").isInside(VaultPath("a")))
        assertTrue(VaultPath("a").isInside(VaultPath.ROOT))
        assertFalse(VaultPath("ab").isInside(VaultPath("a")))
    }
}
