/**
 * The tree of a vault (folders and notes), built by scanning the vault through [VaultFs].
 * Port of desktop `node.rs`; the hide and sort rules must match desktop.
 */
package com.kunotes.core

/**
 * One folder or note in the vault.
 *
 * Notes have an empty [children] list. Folders list their contents folders first, then by
 * [NaturalOrder] ("a2" before "a10", letter case ignored).
 */
data class VaultNode(
    val path: VaultPath,
    val name: String,
    val isDir: Boolean,
    val children: List<VaultNode> = emptyList(),
) {
    companion object {
        /** Folders first, then natural, case-insensitive name order. */
        val ORDER: Comparator<VaultNode> =
            compareBy<VaultNode> { !it.isDir }.thenBy(NaturalOrder) { it.name }

        /**
         * Scans [root] and everything below it into a tree. Slow: call it on `Dispatchers.IO`.
         *
         * - Only folders and notes (`.md`, `.md.age`, see [NoteNames.isNote]) are kept.
         * - Hidden entries (names starting with `.`) are skipped. That also hides `.git`,
         *   `.trash`, image folders (`.img`) and temp files from safe writes.
         * - A folder that can't be read shows up empty instead of failing the whole scan.
         *
         * The root node's name is [root]'s name (empty for the vault root).
         */
        fun scan(
            fs: VaultFs,
            root: VaultPath = VaultPath.ROOT,
        ): VaultNode = VaultNode(path = root, name = root.name, isDir = true, children = scanChildren(fs, root))

        private fun scanChildren(
            fs: VaultFs,
            dir: VaultPath,
        ): List<VaultNode> {
            val entries =
                try {
                    fs.list(dir)
                } catch (_: CoreError) {
                    // Unreadable (permission lost, deleted mid-scan): show it as empty, like desktop.
                    return emptyList()
                }
            return entries.mapNotNull { nodeFor(fs, it) }.sortedWith(ORDER)
        }

        /** Turns one entry into a node, or `null` if the tree shouldn't show it. */
        private fun nodeFor(
            fs: VaultFs,
            entry: FsEntry,
        ): VaultNode? =
            when {
                entry.name.startsWith('.') -> null
                entry.isDir -> VaultNode(entry.path, entry.name, isDir = true, children = scanChildren(fs, entry.path))
                NoteNames.isNote(entry.path) -> VaultNode(entry.path, entry.name, isDir = false)
                else -> null
            }
    }
}
