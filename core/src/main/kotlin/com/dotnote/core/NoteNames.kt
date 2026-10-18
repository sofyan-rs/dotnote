/**
 * Small rules about note file names, shared by the tree, file operations, locking and git sync.
 *
 * - A note is a `.md` file. A locked (encrypted) note is `Name.md.age`. Letter case doesn't matter.
 * - New names that are taken get a number: `Untitled.md`, `Untitled 2.md`, `Untitled 3.md`, ...
 */
package com.dotnote.core

object NoteNames {
    /** The extension of a note. */
    const val NOTE_EXTENSION = "md"

    /** What a locked note adds to the note's name: `Name.md` → `Name.md.age`. */
    const val LOCKED_SUFFIX = ".age"

    /** True if [path] is a locked note (`*.md.age`, any letter case). */
    fun isLockedNote(path: VaultPath): Boolean = path.name.lowercase().endsWith(".md.age")

    /** True if [path] is a note: `.md`, or a locked `.md.age` (any letter case). */
    fun isNote(path: VaultPath): Boolean = path.name.lowercase().endsWith(".md") || isLockedNote(path)

    /** `Name.md` → `Name.md.age`. */
    fun lockedPath(note: VaultPath): VaultPath = note.parent!!.child(note.name + LOCKED_SUFFIX)

    /** `Name.md.age` → `Name.md`. */
    fun unlockedPath(locked: VaultPath): VaultPath = locked.parent!!.child(locked.name.dropLast(LOCKED_SUFFIX.length))

    /**
     * A free path in [dir]: `base.ext`, then `base 2.ext`, `base 3.ext`, ...
     * Pass `extension = null` for folders. The extension can have dots (`"md.age"`).
     */
    fun uniquePath(
        fs: VaultFs,
        dir: VaultPath,
        base: String,
        extension: String?,
    ): VaultPath {
        fun withExtension(name: String) = if (extension == null) name else "$name.$extension"

        var candidate = dir.child(withExtension(base))
        var suffix = 2
        while (fs.exists(candidate)) {
            candidate = dir.child(withExtension("$base $suffix"))
            suffix++
        }
        return candidate
    }
}
