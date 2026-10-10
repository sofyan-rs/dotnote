/**
 * A [VaultFs] kept in memory. Fast and isolated, for tests.
 *
 * Folders are implied by the files and folders inside them, plus [dirs] for empty ones.
 */
package com.kunotes.core

class MemoryVaultFs : VaultFs {
    private val files = sortedMapOf<VaultPath, ByteArray>()
    private val dirs = sortedSetOf<VaultPath>()
    private var clock = 1L

    private val modified = mutableMapOf<VaultPath, Long>()

    /** Test helper: puts a text file at [path], creating its folders. */
    fun put(
        path: String,
        text: String = "",
    ) {
        val vaultPath = VaultPath(path)
        vaultPath.parent?.let { createDir(it) }
        files[vaultPath] = text.toByteArray()
        modified[vaultPath] = clock++
    }

    /** Test helper: the text of the file at [path], or `null` if there's none. */
    fun text(path: String): String? = files[VaultPath(path)]?.decodeToString()

    /** Test helper: every file and folder path, sorted. */
    fun allPaths(): List<String> = (files.keys + dirs).map { it.value }.sorted()

    private fun isDir(path: VaultPath): Boolean = path.isRoot || path in dirs || files.keys.any { it.isInside(path) && it != path }

    override fun list(dir: VaultPath): List<FsEntry> {
        if (!isDir(dir)) throw CoreError.NotFound(dir)
        val childNames =
            (files.keys + dirs)
                .filter { it != dir && it.isInside(dir) }
                .map { it.segments[dir.segments.size] }
                .toSortedSet()
        return childNames.mapNotNull { stat(dir.child(it)) }
    }

    override fun stat(path: VaultPath): FsEntry? {
        files[path]?.let { return FsEntry(path, isDir = false, size = it.size.toLong(), lastModified = modified[path] ?: 0) }
        return if (isDir(path)) FsEntry(path, isDir = true, size = 0, lastModified = 0) else null
    }

    override fun read(path: VaultPath): ByteArray = files[path]?.copyOf() ?: throw CoreError.NotFound(path)

    override fun writeAtomic(
        path: VaultPath,
        bytes: ByteArray,
    ) {
        path.parent?.let { if (!isDir(it)) throw CoreError.NotFound(it) }
        files[path] = bytes.copyOf()
        modified[path] = clock++
    }

    override fun createFile(
        path: VaultPath,
        bytes: ByteArray,
    ) {
        if (stat(path) != null) throw CoreError.AlreadyExists(path)
        writeAtomic(path, bytes)
    }

    override fun createDir(path: VaultPath) {
        var current: VaultPath? = path
        while (current != null && !current.isRoot) {
            dirs.add(current)
            current = current.parent
        }
    }

    override fun rename(
        path: VaultPath,
        newName: String,
    ): VaultPath {
        val destination = (path.parent ?: throw CoreError.Io("The vault root can't be renamed")).child(newName)
        if (stat(destination) != null) throw CoreError.AlreadyExists(destination)
        relocate(path, destination)
        return destination
    }

    override fun move(
        path: VaultPath,
        intoDir: VaultPath,
    ): VaultPath {
        val destination = intoDir.child(path.name)
        if (stat(destination) != null) throw CoreError.AlreadyExists(destination)
        relocate(path, destination)
        return destination
    }

    override fun delete(path: VaultPath) {
        files.keys.filter { it.isInside(path) }.forEach {
            files.remove(it)
            modified.remove(it)
        }
        dirs.removeAll { it.isInside(path) }
    }

    /** Moves [from] (and everything inside it) to [to]. */
    private fun relocate(
        from: VaultPath,
        to: VaultPath,
    ) {
        if (stat(from) == null) throw CoreError.NotFound(from)

        fun moved(p: VaultPath) = VaultPath.of(to.value, p.value.removePrefix(from.value).removePrefix("/"))
        files.keys.filter { it.isInside(from) }.forEach { old ->
            files[moved(old)] = files.remove(old)!!
            modified[moved(old)] = clock++
        }
        val oldDirs = dirs.filter { it.isInside(from) }
        dirs.removeAll(oldDirs.toSet())
        oldDirs.forEach { dirs.add(moved(it)) }
        if (stat(to) == null) createDir(to)
    }
}
