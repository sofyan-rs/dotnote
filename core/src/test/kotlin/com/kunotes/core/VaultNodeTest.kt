package com.kunotes.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class VaultNodeTest {
    @TempDir
    lateinit var temp: File

    private fun childNames(node: VaultNode) = node.children.map { it.name }

    private fun touch(path: String) {
        val file = File(temp, path)
        file.parentFile.mkdirs()
        file.writeText("")
    }

    @Test
    fun `sorts folders first then natural order`() {
        fun node(
            name: String,
            isDir: Boolean,
        ) = VaultNode(VaultPath(name), name, isDir)
        val nodes =
            listOf(node("b.md", false), node("a10.md", false), node("Zeta", true), node("a2.md", false), node("alpha", true))
        assertEquals(listOf("alpha", "Zeta", "a2.md", "a10.md", "b.md"), nodes.sortedWith(VaultNode.ORDER).map { it.name })
    }

    @Test
    fun `scan keeps folders and markdown only`() {
        // Real files, like desktop's integration test.
        listOf(
            "Welcome.md",
            "Shout.MD",
            "notes.txt",
            ".hidden.md",
            ".git/config",
            ".obsidian/app.json",
            "Projects/a10.md",
            "Projects/a2.md",
            "Keys.md.age",
            ".trash/Old.md",
            "Projects/.img/cat.png",
        ).forEach { touch(it) }
        File(temp, "Empty").mkdir()

        val tree = VaultNode.scan(FileVaultFs(temp))
        assertTrue(tree.isDir)
        assertEquals(VaultPath.ROOT, tree.path)
        assertEquals(listOf("Empty", "Projects", "Keys.md.age", "Shout.MD", "Welcome.md"), childNames(tree))

        val projects = tree.children[1]
        assertEquals(VaultPath("Projects"), projects.path)
        assertEquals(listOf("a2.md", "a10.md"), childNames(projects))
        assertTrue(projects.children.all { !it.isDir && it.children.isEmpty() })
    }

    @Test
    fun `scan of sample vault fixture`() {
        // The desktop fixture's top level, rebuilt in memory.
        val fs = MemoryVaultFs()
        fs.put(".obsidian/app.json", "{}")
        fs.put("Journal/2026-01-01.md")
        fs.put("Projects/Plan.md")
        fs.put(".hidden.md")
        fs.put("Example.md")
        fs.put("Welcome.md")
        assertEquals(listOf("Journal", "Projects", "Example.md", "Welcome.md"), childNames(VaultNode.scan(fs)))
    }

    @Test
    fun `scan of missing folder is empty`() {
        val tree = VaultNode.scan(FileVaultFs(File(temp, "does-not-exist")))
        assertTrue(tree.children.isEmpty())
        assertTrue(VaultNode.scan(MemoryVaultFs(), VaultPath("nope")).children.isEmpty())
    }

    @Test
    fun `scan of a sub folder keeps vault paths`() {
        val fs = MemoryVaultFs()
        fs.put("A/B/c.md")
        val tree = VaultNode.scan(fs, VaultPath("A"))
        assertEquals("A", tree.name)
        assertEquals(
            VaultPath("A/B/c.md"),
            tree.children
                .single()
                .children
                .single()
                .path,
        )
    }

    @Test
    fun `scan sorts emoji and cjk names by code point`() {
        val fs = MemoryVaultFs()
        listOf("😀 b.md", "日本 10.md", "日本 2.md", "a.md").forEach { fs.put(it) }
        assertEquals(listOf("a.md", "日本 2.md", "日本 10.md", "😀 b.md"), childNames(VaultNode.scan(fs)))
    }
}
