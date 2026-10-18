/**
 * A [VaultFs] on a normal folder (`java.io.File`). Used for vaults in app storage and in tests.
 */
package com.dotnote.core

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class FileVaultFs(
    private val root: File,
) : VaultFs {
    private fun fileOf(path: VaultPath): File = if (path.isRoot) root else File(root, path.value)

    override fun list(dir: VaultPath): List<FsEntry> {
        val folder = fileOf(dir)
        val children = folder.listFiles() ?: throw CoreError.NotFound(dir)
        return children.map { entryOf(dir.child(it.name), it) }
    }

    override fun stat(path: VaultPath): FsEntry? {
        val file = fileOf(path)
        return if (file.exists()) entryOf(path, file) else null
    }

    override fun read(path: VaultPath): ByteArray {
        val file = fileOf(path)
        if (!file.isFile) throw CoreError.NotFound(path)
        return io("read") { file.readBytes() }
    }

    override fun writeAtomic(
        path: VaultPath,
        bytes: ByteArray,
    ) {
        val target = fileOf(path)
        // Starts with "." so the tree never shows it (and desktop's .gitignore skips it).
        val temp = File(target.parentFile, ".${target.name}.dotnote.tmp")
        try {
            FileOutputStream(temp).use { out ->
                out.write(bytes)
                out.fd.sync() // make sure the bytes are on disk before the swap
            }
            moveReplacing(temp, target)
        } catch (error: IOException) {
            temp.delete() // best effort; the original error is what matters
            throw CoreError.Io("Couldn't save \"${path.name}\": ${error.message}", error)
        }
    }

    override fun createFile(
        path: VaultPath,
        bytes: ByteArray,
    ) {
        val file = fileOf(path)
        // createNewFile() is atomic: it fails instead of overwriting a file that just appeared.
        val created = io("create") { file.createNewFile() }
        if (!created) throw CoreError.AlreadyExists(path)
        io("write") { file.writeBytes(bytes) }
    }

    override fun createDir(path: VaultPath) {
        val folder = fileOf(path)
        if (!folder.isDirectory && !folder.mkdirs()) {
            throw CoreError.Io("Couldn't create folder \"${path.name}\"")
        }
    }

    override fun rename(
        path: VaultPath,
        newName: String,
    ): VaultPath {
        val parent = path.parent ?: throw CoreError.Io("The vault root can't be renamed")
        val destination = parent.child(newName)
        if (exactNameExists(parent, newName)) throw CoreError.AlreadyExists(destination)
        moveOnce(path, destination)
        return destination
    }

    override fun move(
        path: VaultPath,
        intoDir: VaultPath,
    ): VaultPath {
        val destination = intoDir.child(path.name)
        if (fileOf(destination).exists()) throw CoreError.AlreadyExists(destination)
        moveOnce(path, destination)
        return destination
    }

    override fun delete(path: VaultPath) {
        if (path.isRoot) throw CoreError.Io("The vault root can't be deleted")
        val file = fileOf(path)
        if (file.exists() && !file.deleteRecursively()) {
            throw CoreError.Io("Couldn't delete \"${path.name}\"")
        }
    }

    private fun entryOf(
        path: VaultPath,
        file: File,
    ) = FsEntry(
        path = path,
        // A symlinked folder counts as "not a folder", so a scan never follows it into a loop
        // (desktop does the same).
        isDir = file.isDirectory && !Files.isSymbolicLink(file.toPath()),
        size = if (file.isFile) file.length() else 0L,
        lastModified = file.lastModified(),
    )

    /** Checks the exact letter case, which matters on case-insensitive storage. */
    private fun exactNameExists(
        dir: VaultPath,
        name: String,
    ): Boolean = fileOf(dir).list()?.contains(name) ?: false

    private fun moveOnce(
        from: VaultPath,
        to: VaultPath,
    ) {
        if (!fileOf(from).exists()) throw CoreError.NotFound(from)
        io("move") { Files.move(fileOf(from).toPath(), fileOf(to).toPath()) }
    }

    private fun moveReplacing(
        from: File,
        to: File,
    ) {
        try {
            Files.move(from.toPath(), to.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    /** Runs [block] and turns a low-level `IOException` into a readable [CoreError.Io]. */
    private inline fun <T> io(
        action: String,
        block: () -> T,
    ): T =
        try {
            block()
        } catch (error: IOException) {
            throw CoreError.Io("Couldn't $action: ${error.message}", error)
        }
}
