/**
 * Turns the vault tree into flat lists: all notes (for the quick switcher) and the rows the
 * Files screen draws. Port of desktop `search.rs`.
 */
package com.dotnote.core

/** One row the file tree should draw. */
data class VisibleRow(
    val path: VaultPath,
    val name: String,
    /** 0 for items directly inside the root; each folder level adds 1. */
    val depth: Int,
    val isDir: Boolean,
    val isExpanded: Boolean,
)

object Search {
    /** Every note under [root], in tree order. Folders themselves are left out. */
    fun flattenFiles(root: VaultNode): List<VaultNode> {
        val files = mutableListOf<VaultNode>()
        collectFiles(root, files)
        return files
    }

    private fun collectFiles(
        node: VaultNode,
        files: MutableList<VaultNode>,
    ) {
        for (child in node.children) {
            if (child.isDir) collectFiles(child, files) else files.add(child)
        }
    }

    /**
     * The [files] whose name contains [query], ignoring letter case. Keeps the order of [files].
     * An empty (or whitespace-only) query matches everything.
     */
    fun filterFiles(
        files: List<VaultNode>,
        query: String,
    ): List<VaultNode> {
        val needle = Names.trim(query).lowercase()
        if (needle.isEmpty()) return files
        return files.filter { it.name.lowercase().contains(needle) }
    }

    /**
     * The rows to draw for [root], given which folders are [expanded].
     * The root itself is not a row; its children are at depth 0. A folder inside a collapsed
     * folder stays hidden even if it is in [expanded].
     */
    fun visibleRows(
        root: VaultNode,
        expanded: Set<VaultPath>,
    ): List<VisibleRow> {
        val rows = mutableListOf<VisibleRow>()
        collectRows(root, expanded, depth = 0, rows = rows)
        return rows
    }

    private fun collectRows(
        node: VaultNode,
        expanded: Set<VaultPath>,
        depth: Int,
        rows: MutableList<VisibleRow>,
    ) {
        for (child in node.children) {
            val isExpanded = child.isDir && child.path in expanded
            rows.add(VisibleRow(child.path, child.name, depth, child.isDir, isExpanded))
            if (isExpanded) collectRows(child, expanded, depth + 1, rows)
        }
    }
}
