/**
 * File operations on a vault: create, rename, move, import images, and fixing leftovers of
 * interrupted saves. Port of desktop `fs_ops.rs`. Deleting is in [Trash].
 *
 * Safety rules: nothing here overwrites an existing file, and only [Trash.purge] and
 * [FsOps.recoverInterruptedWrites] delete anything for good.
 */
package com.kunotes.core

import java.util.UUID

object FsOps {
    /** The folder, next to each note, that imported images are copied into. Hidden by the dot rule. */
    const val IMAGE_FOLDER = ".img"

    /** Image types [importImage] accepts (lowercase file extensions). Same list as desktop. */
    val IMAGE_EXTENSIONS: List<String> = listOf("png", "jpg", "jpeg", "gif", "webp", "svg", "bmp")

    private const val SAVE_TEMP_SUFFIX = ".kunotes.tmp"
    private const val SAVE_BACKUP_SUFFIX = ".kunotes.bak"

    // ---- Create ----

    /**
     * Creates a new note in [dir] named `base.md` (or `base 2.md`, ...), starting with a heading
     * that matches its name. Returns the new path.
     */
    fun createFile(
        fs: VaultFs,
        dir: VaultPath,
        base: String = "Untitled",
    ): VaultPath {
        Names.requireValidName(base)
        val path = NoteNames.uniquePath(fs, dir, base, NoteNames.NOTE_EXTENSION)
        // createFile() fails instead of overwriting if the file appeared in the meantime.
        fs.createFile(path, seed(path))
        return path
    }

    /**
     * Creates a note with exactly the name the user typed (`.md` is added unless it's already
     * there), starting with a matching heading. Fails if the name is taken.
     */
    fun createFileNamed(
        fs: VaultFs,
        dir: VaultPath,
        name: String,
    ): VaultPath {
        var fileName = Names.trim(name)
        Names.requireValidName(fileName)
        if (!fileName.lowercase().endsWith(".md")) fileName += ".md"
        val path = dir.child(fileName)
        if (fs.exists(path)) throw CoreError.AlreadyExists(path)
        fs.createFile(path, seed(path))
        return path
    }

    /** Creates a new folder in [dir] named `base` (or `base 2`, ...). Returns the new path. */
    fun createFolder(
        fs: VaultFs,
        dir: VaultPath,
        base: String = "New Folder",
    ): VaultPath {
        Names.requireValidName(base)
        val path = NoteNames.uniquePath(fs, dir, base, extension = null)
        fs.createDir(path)
        return path
    }

    /** Creates a folder with exactly the name the user typed. Fails if the name is taken. */
    fun createFolderNamed(
        fs: VaultFs,
        dir: VaultPath,
        name: String,
    ): VaultPath {
        val folderName = Names.trim(name)
        Names.requireValidName(folderName)
        val path = dir.child(folderName)
        if (fs.exists(path)) throw CoreError.AlreadyExists(path)
        fs.createDir(path)
        return path
    }

    /** The first text of a new note: a heading with its title. */
    private fun seed(path: VaultPath): ByteArray = "# ${Paths.noteTitle(path)}\n".toByteArray()

    // ---- Rename and move ----

    /**
     * Renames a file or folder in place. Returns the new path.
     *
     * - Surrounding whitespace in [newName] is trimmed.
     * - Notes get `.md` unless the name already ends with `.md`.
     * - A locked note stays locked: "Keys" or "Keys.md" becomes "Keys.md.age".
     * - Changing only the letter case (`note.md` → `Note.md`) works on case-insensitive storage
     *   too (Android shared storage, macOS, Windows): it goes through a temporary hidden name.
     */
    fun rename(
        fs: VaultFs,
        path: VaultPath,
        newName: String,
    ): VaultPath {
        var name = Names.trim(newName)
        Names.requireValidName(name)
        val entry = fs.stat(path) ?: throw CoreError.NotFound(path)
        val lower = name.lowercase()
        if (NoteNames.isLockedNote(path)) {
            if (!lower.endsWith(".md.age")) {
                if (!lower.endsWith(".md")) name += ".md"
                name += NoteNames.LOCKED_SUFFIX
            }
        } else if (!entry.isDir && !lower.endsWith(".md")) {
            name += ".md"
        }

        val parent = path.parent ?: throw CoreError.Io("The vault root can't be renamed")
        val destination = parent.child(name)
        if (destination == path) return destination

        if (isCaseOnlyChange(path.name, name)) {
            // On case-sensitive storage "Note.md" may be a different file next to "note.md".
            if (fs.list(parent).any { it.name == name }) throw CoreError.AlreadyExists(destination)
            // On case-insensitive storage the direct target "already exists" (it is the same
            // file), so go through a temporary name.
            val temp = fs.rename(path, ".kunotes-rename-${UUID.randomUUID()}")
            try {
                return fs.rename(temp, name)
            } catch (error: CoreError) {
                // Don't leave the note under a hidden name: put the old name back, then report
                // the first error (if this rename fails too, its error is thrown instead).
                fs.rename(temp, path.name)
                throw error
            }
        }

        if (fs.exists(destination)) throw CoreError.AlreadyExists(destination)
        return fs.rename(path, name)
    }

    /**
     * Moves a file or folder into [folder], keeping its name. Returns the new path.
     *
     * Moving a folder into itself or one of its own subfolders throws [CoreError.MoveIntoSelf].
     * Moving something into the folder it is already in does nothing.
     */
    fun moveInto(
        fs: VaultFs,
        path: VaultPath,
        folder: VaultPath,
    ): VaultPath {
        if (folder.isInside(path)) throw CoreError.MoveIntoSelf()
        if (path.parent == folder) return path
        val destination = folder.child(path.name)
        if (fs.exists(destination)) throw CoreError.AlreadyExists(destination)
        return fs.move(path, folder)
    }

    private fun isCaseOnlyChange(
        from: String,
        to: String,
    ): Boolean = from != to && from.lowercase() == to.lowercase()

    // ---- Images ----

    /**
     * Copies an image into the `.img` folder next to [note] (creating the folder) and returns
     * the link to write in the note, like `.img/photo.png`.
     *
     * [imageName] is the picked file's name (e.g. from the Photo Picker), [bytes] its content.
     * Whitespace in the name becomes `-` so the markdown link stays simple; the extension is
     * lowercased; a taken name gets `-2`, `-3`, ... added. Not an image → [CoreError.NotAnImage].
     */
    fun importImage(
        fs: VaultFs,
        note: VaultPath,
        imageName: String,
        bytes: ByteArray,
    ): String {
        val fileName = imageName.substringAfterLast('/')
        val (stem, extension) = splitExtension(fileName)
        val lowerExtension = extension?.lowercase()
        if (lowerExtension == null || lowerExtension !in IMAGE_EXTENSIONS) throw CoreError.NotAnImage(fileName)
        val safeStem = replaceWhitespace(stem.ifEmpty { "image" })

        val folder = (note.parent ?: VaultPath.ROOT).child(IMAGE_FOLDER)
        fs.createDir(folder)
        var name = "$safeStem.$lowerExtension"
        var suffix = 2
        while (fs.exists(folder.child(name))) {
            name = "$safeStem-$suffix.$lowerExtension"
            suffix++
        }
        fs.createFile(folder.child(name), bytes)
        // Markdown links always use `/`.
        return "$IMAGE_FOLDER/$name"
    }

    /**
     * Splits `photo.PNG` into `("photo", "PNG")`, like Rust's `file_stem()` and `extension()`:
     * a name with no dot, or whose only dot is the first character (`.png`), has no extension.
     */
    private fun splitExtension(fileName: String): Pair<String, String?> {
        val dot = fileName.lastIndexOf('.')
        if (dot <= 0) return Pair(fileName, null)
        return Pair(fileName.substring(0, dot), fileName.substring(dot + 1))
    }

    private fun replaceWhitespace(text: String): String {
        val result = StringBuilder()
        var index = 0
        while (index < text.length) {
            val codePoint = text.codePointAt(index)
            if (Names.isWhitespace(codePoint)) result.append('-') else result.appendCodePoint(codePoint)
            index += Character.charCount(codePoint)
        }
        return result.toString()
    }

    // ---- Moving with a free name (used by Trash) ----

    /**
     * Moves [path] into [targetDir] under [wantedName], or `wantedName 2`, `3`, ... if taken there.
     *
     * VaultFs can only rename in place, or move keeping the name, so a name change takes two steps:
     * - Usually: rename in the source folder, then move. If the app dies in between, the item
     *   is still visible, just with the new name.
     * - If the new name is taken in the source folder too: rename to a hidden temporary name,
     *   move, then rename to the new name.
     */
    internal fun moveWithFreeName(
        fs: VaultFs,
        path: VaultPath,
        targetDir: VaultPath,
        wantedName: String = path.name,
    ): VaultPath {
        val sourceDir = path.parent ?: VaultPath.ROOT
        val (base, extension) = splitNoteName(wantedName, fs.stat(path)?.isDir == true)
        var name = wantedName
        var suffix = 2
        while (fs.exists(targetDir.child(name))) {
            name = if (extension == null) "$base $suffix" else "$base $suffix.$extension"
            suffix++
        }
        return when {
            name == path.name -> fs.move(path, targetDir)
            !fs.exists(sourceDir.child(name)) -> fs.move(fs.rename(path, name), targetDir)
            else -> {
                val temp = fs.rename(path, ".kunotes-move-${UUID.randomUUID()}")
                fs.rename(fs.move(temp, targetDir), name)
            }
        }
    }

    /** `Plan.md` → ("Plan", "md"), `Keys.md.age` → ("Keys", "md.age"), folders keep the whole name. */
    private fun splitNoteName(
        name: String,
        isDir: Boolean,
    ): Pair<String, String?> {
        if (isDir) return Pair(name, null)
        if (name.lowercase().endsWith(".md.age")) return Pair(name.dropLast(7), name.takeLast(6))
        val (stem, extension) = splitExtension(name)
        return Pair(stem, extension)
    }

    // ---- Recovery after interrupted saves ----

    /**
     * Repairs what an interrupted safe write left behind (see ARCHITECTURE §7.2). Run it when a
     * vault is opened. Returns the leftover files that were handled.
     *
     * - `.X.kunotes.bak` without `X`: the save stopped before the new file was in place, so
     *   the backup is renamed back to `X` (the last saved version).
     * - `.X.kunotes.bak` with `X`: the new file is complete, so the backup is deleted.
     * - `.X.kunotes.tmp`: an unfinished new version; deleted.
     *
     * The `.git` folder is skipped (it is large and never written this way).
     */
    fun recoverInterruptedWrites(fs: VaultFs): List<VaultPath> {
        val handled = mutableListOf<VaultPath>()
        recoverIn(fs, VaultPath.ROOT, handled)
        return handled
    }

    private fun recoverIn(
        fs: VaultFs,
        dir: VaultPath,
        handled: MutableList<VaultPath>,
    ) {
        val entries =
            try {
                fs.list(dir)
            } catch (_: CoreError) {
                return // unreadable folder: nothing we can fix there
            }
        val names = entries.map { it.name }.toSet()
        for (entry in entries) {
            val name = entry.name
            when {
                entry.isDir -> if (name != ".git") recoverIn(fs, entry.path, handled)
                isLeftover(name, SAVE_BACKUP_SUFFIX) -> {
                    val original = originalName(name, SAVE_BACKUP_SUFFIX)
                    if (original in names) fs.delete(entry.path) else fs.rename(entry.path, original)
                    handled.add(entry.path)
                }
                isLeftover(name, SAVE_TEMP_SUFFIX) -> {
                    fs.delete(entry.path)
                    handled.add(entry.path)
                }
            }
        }
    }

    /** True for `.X<suffix>` with a non-empty `X`. */
    private fun isLeftover(
        name: String,
        suffix: String,
    ): Boolean = name.startsWith('.') && name.endsWith(suffix) && name.length > suffix.length + 1

    /** `.Plan.md.kunotes.bak` → `Plan.md`. */
    private fun originalName(
        name: String,
        suffix: String,
    ): String = name.removePrefix(".").removeSuffix(suffix)
}
