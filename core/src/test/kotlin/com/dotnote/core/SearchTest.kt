package com.dotnote.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SearchTest {
    private fun file(path: String) = VaultNode(VaultPath(path), VaultPath(path).name, isDir = false)

    private fun folder(
        path: String,
        children: List<VaultNode>,
    ) = VaultNode(VaultPath(path), VaultPath(path).name, isDir = true, children = children)

    /**
     * Projects/
     *   Archive/old.md
     *   Plan.md
     * Welcome.md
     */
    private fun sample() =
        folder(
            "",
            listOf(
                folder(
                    "Projects",
                    listOf(folder("Projects/Archive", listOf(file("Projects/Archive/old.md"))), file("Projects/Plan.md")),
                ),
                file("Welcome.md"),
            ),
        )

    private fun names(nodes: List<VaultNode>) = nodes.map { it.name }

    @Test
    fun `flatten returns only files in tree order`() {
        assertEquals(listOf("old.md", "Plan.md", "Welcome.md"), names(Search.flattenFiles(sample())))
    }

    @Test
    fun `filter is case insensitive and empty matches all`() {
        val files = Search.flattenFiles(sample())
        assertEquals(listOf("Plan.md"), names(Search.filterFiles(files, "PLAN")))
        assertEquals(3, Search.filterFiles(files, "  ").size)
        assertTrue(Search.filterFiles(files, "nothing").isEmpty())
    }

    @Test
    fun `filter matches emoji and cjk`() {
        val files = listOf(file("日本語メモ.md"), file("Party 🎉.md"), file("ÉCOLE.md"))
        assertEquals(listOf("日本語メモ.md"), names(Search.filterFiles(files, "本語")))
        assertEquals(listOf("Party 🎉.md"), names(Search.filterFiles(files, " 🎉 ")))
        assertEquals(listOf("ÉCOLE.md"), names(Search.filterFiles(files, "école")))
    }

    @Test
    fun `collapsed tree shows only top level`() {
        val rows = Search.visibleRows(sample(), emptySet())
        assertEquals(listOf("Projects" to 0, "Welcome.md" to 0), rows.map { it.name to it.depth })
        assertFalse(rows[0].isExpanded)
    }

    @Test
    fun `expanded folders show children with depth`() {
        val expanded = setOf(VaultPath("Projects"), VaultPath("Projects/Archive"))
        val rows = Search.visibleRows(sample(), expanded)
        assertEquals(
            listOf("Projects" to 0, "Archive" to 1, "old.md" to 2, "Plan.md" to 1, "Welcome.md" to 0),
            rows.map { it.name to it.depth },
        )
        assertTrue(rows[0].isExpanded)
        assertEquals(VaultPath("Projects/Archive/old.md"), rows[2].path)
    }

    @Test
    fun `expanded child of collapsed parent stays hidden`() {
        val rows = Search.visibleRows(sample(), setOf(VaultPath("Projects/Archive")))
        assertEquals(2, rows.size)
    }
}
