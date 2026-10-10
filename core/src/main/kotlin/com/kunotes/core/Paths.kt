/**
 * Small path helpers: note titles, breadcrumbs, the rename pre-selection, and following paths
 * after a rename or move. Port of desktop `paths.rs`, on [VaultPath] instead of OS paths.
 */
package com.kunotes.core

object Paths {
    /** The display title of a note: the name without `.md` (or `.md.age`). Folders and other files keep their name. */
    fun noteTitle(path: VaultPath): String {
        val name = path.name
        return when {
            // `Keys.md.age` → "Keys". Checked first, because isNote() is also true for locked notes.
            NoteNames.isLockedNote(path) -> name.dropLast(".md.age".length)
            NoteNames.isNote(path) -> name.dropLast(".md".length)
            else -> name
        }
    }

    /**
     * Breadcrumb parts for a file: its folders, then its title.
     * `Projects/Plan.md` gives `["Projects", "Plan"]`. The vault root itself gives an empty list.
     */
    fun breadcrumb(file: VaultPath): List<String> {
        if (file.isRoot) return emptyList()
        return file.segments.dropLast(1) + noteTitle(file)
    }

    /**
     * The part of [name] to pre-select when renaming: everything except `.md` (or `.md.age`) for
     * notes, so typing replaces "Plan" in "Plan.md"; the whole name for folders.
     *
     * The range is in UTF-16 indexes. The removed suffix is plain ASCII, so the end never falls
     * inside an emoji.
     */
    fun renameSelection(
        name: String,
        isDir: Boolean,
    ): TextRange {
        // "> length" keeps a name that is only ".md" fully selected, like desktop.
        val hasLocked = name.length > ".md.age".length && name.endsWith(".md.age", ignoreCase = true)
        val hasMd = name.length > ".md".length && name.endsWith(".md", ignoreCase = true)
        return when {
            !isDir && hasLocked -> TextRange(0, name.length - ".md.age".length)
            !isDir && hasMd -> TextRange(0, name.length - ".md".length)
            else -> TextRange(0, name.length)
        }
    }

    /**
     * After [old] was renamed or moved to [new], returns where [path] is now.
     * Returns `null` if [path] was neither [old] nor inside it. Used to keep the selection,
     * expanded folders and open notes correct.
     */
    fun remapPath(
        path: VaultPath,
        old: VaultPath,
        new: VaultPath,
    ): VaultPath? {
        if (!path.isInside(old)) return null
        // The parts of `path` below `old`, e.g. ["Plan.md"] for "Projects/Plan.md" under "Projects".
        val rest = path.segments.drop(old.segments.size)
        return VaultPath.of(new.value, *rest.toTypedArray())
    }

    /**
     * True if [path] is hidden: one of its parts starts with "." (like `.git/HEAD`, `.trash/a.md`
     * or `a/.b.md.kunotes.tmp`). The vault root is not hidden.
     *
     * Desktop's `is_hidden_within(root, path)`; the root argument isn't needed with vault paths.
     */
    fun isHidden(path: VaultPath): Boolean = path.segments.any { it.startsWith('.') }

    /**
     * True if moving [path] into [folder] would do something: the folder isn't the item itself,
     * isn't inside it, and isn't where the item already is. Only compares paths;
     * [FsOps.moveInto] still checks the storage.
     */
    fun canMoveInto(
        path: VaultPath,
        folder: VaultPath,
    ): Boolean = !folder.isInside(path) && path.parent != folder

    /**
     * All folders between [root] and [path] (both left out), outermost first.
     * Used to expand the tree so a file becomes visible: `a/b/c.md` gives `[a, a/b]`.
     */
    fun ancestorsWithin(
        path: VaultPath,
        root: VaultPath = VaultPath.ROOT,
    ): List<VaultPath> {
        val folders = mutableListOf<VaultPath>()
        var folder = path.parent
        while (folder != null && folder != root && folder.isInside(root)) {
            folders.add(folder)
            folder = folder.parent
        }
        return folders.reversed()
    }

    /**
     * The file an image link in a note points to, or `null` if it isn't a file in the vault.
     * Relative links (like `.img/photo.png`) start at the note's folder [noteDir].
     *
     * Differences from desktop, because Android can only read files inside the vault:
     * - absolute links (`/Users/me/cat.png`) give `null`;
     * - `..` is resolved here, and a link that climbs above the vault root gives `null`.
     */
    fun imageFile(
        noteDir: VaultPath,
        link: String,
    ): VaultPath? {
        if (link.contains("://") || link.startsWith("data:") || link.startsWith('/')) return null
        val parts = noteDir.segments.toMutableList()
        for (part in link.split('/')) {
            when (part) {
                "", "." -> Unit
                ".." -> if (parts.isEmpty()) return null else parts.removeAt(parts.lastIndex)
                else -> parts.add(part)
            }
        }
        // A link to the vault root (or to nothing) isn't an image file.
        if (parts.isEmpty()) return null
        return VaultPath.of(*parts.toTypedArray())
    }
}
