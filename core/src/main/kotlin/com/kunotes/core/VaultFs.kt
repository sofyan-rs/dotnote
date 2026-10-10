/**
 * The file system of one vault, as `:core` sees it.
 *
 * The app implements it on top of Android's Storage Access Framework (`SafVaultFs`);
 * [FileVaultFs] uses normal files and [MemoryVaultFs] keeps everything in memory for tests.
 *
 * Every function can be slow, so call them from a background thread (`Dispatchers.IO`).
 * Failures throw [CoreError].
 */
package com.kunotes.core

/** One file or folder found by [VaultFs.list] or [VaultFs.stat]. */
data class FsEntry(
    val path: VaultPath,
    val isDir: Boolean,
    /** Size in bytes (0 for folders). */
    val size: Long,
    /** Last change time, in milliseconds since 1970 (0 if unknown). */
    val lastModified: Long,
) {
    val name: String get() = path.name
}

interface VaultFs {
    /** Everything directly inside [dir], in no particular order. */
    fun list(dir: VaultPath): List<FsEntry>

    /** What is at [path], or `null` if nothing is there. */
    fun stat(path: VaultPath): FsEntry?

    /** The whole content of the file at [path]. */
    fun read(path: VaultPath): ByteArray

    /**
     * Replaces (or creates) the file at [path] with [bytes] so that a crash never leaves a
     * half-written file: it writes a hidden temp file first, then swaps it in.
     */
    fun writeAtomic(
        path: VaultPath,
        bytes: ByteArray,
    )

    /** Creates a new file. Throws [CoreError.AlreadyExists] if something is already there. */
    fun createFile(
        path: VaultPath,
        bytes: ByteArray,
    )

    /** Creates a new folder (and missing parent folders). Does nothing if it already exists. */
    fun createDir(path: VaultPath)

    /**
     * Renames [path] in place to [newName]. Throws [CoreError.AlreadyExists] if a file with
     * exactly that name exists. Returns the new path.
     */
    fun rename(
        path: VaultPath,
        newName: String,
    ): VaultPath

    /** Moves [path] into the folder [intoDir], keeping its name. Returns the new path. */
    fun move(
        path: VaultPath,
        intoDir: VaultPath,
    ): VaultPath

    /**
     * Deletes [path] for good (folders with everything inside).
     *
     * Only the trash purge and the save-recovery code may call this. Deleting a note the user
     * asked to delete goes through `Trash.trash`, which moves it to `.trash/` instead.
     */
    fun delete(path: VaultPath)
}

/** True if something is at [path]. */
fun VaultFs.exists(path: VaultPath): Boolean = stat(path) != null
