/**
 * A [VaultFs] on a folder the user picked with Android's folder picker (Storage Access Framework).
 *
 * SAF works with "documents" that have ids, not with paths. This class turns each [VaultPath]
 * into a document id by walking down from the picked folder, and caches the answers.
 *
 * Two SAF quirks this class protects against (ARCHITECTURE §7.1–7.2):
 * - Creating or renaming to a name that's taken doesn't fail: the provider silently picks
 *   "Name (1)". So we check names ourselves first, and check the name we got afterwards.
 * - There is no "replace this file in one step". [writeAtomic] writes a temp file, moves the
 *   old file aside to a backup name, puts the temp file in place, then deletes the backup.
 *   `FsOps.recoverInterruptedWrites` fixes leftovers if the app dies in between.
 *
 * Every call does disk I/O through Android's content resolver: call it on `Dispatchers.IO`.
 */
package com.kunotes.vault

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import com.kunotes.core.CoreError
import com.kunotes.core.FsEntry
import com.kunotes.core.VaultFs
import com.kunotes.core.VaultPath
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

class SafVaultFs(
    private val resolver: ContentResolver,
    /** The tree URI returned by the folder picker. */
    private val treeUri: Uri,
) : VaultFs {
    /** VaultPath → document id. Thread-safe, because scans and saves can run at the same time. */
    private val idCache = ConcurrentHashMap<VaultPath, String>()

    init {
        idCache[VaultPath.ROOT] = DocumentsContract.getTreeDocumentId(treeUri)
    }

    // ---- Reading ----

    override fun list(dir: VaultPath): List<FsEntry> {
        val dirId = documentId(dir) ?: throw CoreError.NotFound(dir)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, dirId)
        val entries = mutableListOf<FsEntry>()
        query(childrenUri) { cursor ->
            val row = Row.from(cursor)
            if (row.name.isEmpty() || '/' in row.name) return@query // can't be a VaultPath part
            val path = dir.child(row.name)
            idCache[path] = row.id
            entries += row.toEntry(path)
        }
        return entries
    }

    override fun stat(path: VaultPath): FsEntry? {
        val id = documentId(path) ?: return null
        var entry: FsEntry? = null
        query(documentUri(id)) { cursor -> entry = Row.from(cursor).toEntry(path) }
        if (entry == null) forget(path) // the cached id is stale
        return entry
    }

    override fun read(path: VaultPath): ByteArray {
        val id = documentId(path) ?: throw CoreError.NotFound(path)
        return io("read \"${path.name}\"") {
            resolver.openInputStream(documentUri(id))?.use { it.readBytes() }
                ?: throw CoreError.Io("Couldn't read \"${path.name}\"")
        }
    }

    // ---- Writing ----

    override fun writeAtomic(
        path: VaultPath,
        bytes: ByteArray,
    ) {
        val parent = path.parent ?: throw CoreError.Io("The vault root can't be written")
        val existingId = documentId(path)
        if (existingId == null) {
            // A new file: nothing to protect yet, so write it directly.
            val id = createDocument(parent, path.name, MIME_FILE)
            writeBytes(id, bytes, path)
            return
        }
        // 1. Write the new content to a hidden temp file next to the target.
        val tempName = ".${path.name}.kunotes.tmp"
        val backupName = ".${path.name}.kunotes.bak"
        documentId(parent.child(tempName))?.let { deleteDocument(parent.child(tempName), it) }
        val tempId = createDocument(parent, tempName, MIME_FILE)
        writeBytes(tempId, bytes, parent.child(tempName))
        // 2. Move the old file aside, 3. put the new one in place, 4. drop the old one.
        documentId(parent.child(backupName))?.let { deleteDocument(parent.child(backupName), it) }
        renameDocument(path, existingId, backupName)
        renameDocument(parent.child(tempName), tempId, path.name)
        documentId(parent.child(backupName))?.let { deleteDocument(parent.child(backupName), it) }
    }

    override fun createFile(
        path: VaultPath,
        bytes: ByteArray,
    ) {
        val parent = path.parent ?: throw CoreError.Io("The vault root can't be written")
        if (exactNameExists(parent, path.name)) throw CoreError.AlreadyExists(path)
        val id = createDocument(parent, path.name, MIME_FILE)
        writeBytes(id, bytes, path)
    }

    override fun createDir(path: VaultPath) {
        var current = VaultPath.ROOT
        for (segment in path.segments) {
            val next = current.child(segment)
            val entry = stat(next)
            if (entry == null) {
                createDocument(current, segment, Document.MIME_TYPE_DIR)
            } else if (!entry.isDir) {
                throw CoreError.AlreadyExists(next)
            }
            current = next
        }
    }

    override fun rename(
        path: VaultPath,
        newName: String,
    ): VaultPath {
        val parent = path.parent ?: throw CoreError.Io("The vault root can't be renamed")
        val id = documentId(path) ?: throw CoreError.NotFound(path)
        if (exactNameExists(parent, newName)) throw CoreError.AlreadyExists(parent.child(newName))
        renameDocument(path, id, newName)
        return parent.child(newName)
    }

    override fun move(
        path: VaultPath,
        intoDir: VaultPath,
    ): VaultPath {
        val parent = path.parent ?: throw CoreError.Io("The vault root can't be moved")
        val id = documentId(path) ?: throw CoreError.NotFound(path)
        val parentId = documentId(parent) ?: throw CoreError.NotFound(parent)
        val targetId = documentId(intoDir) ?: throw CoreError.NotFound(intoDir)
        val destination = intoDir.child(path.name)
        if (exactNameExists(intoDir, path.name)) throw CoreError.AlreadyExists(destination)
        val movedUri =
            io("move \"${path.name}\"") {
                DocumentsContract.moveDocument(resolver, documentUri(id), documentUri(parentId), documentUri(targetId))
            } ?: throw CoreError.Io("Couldn't move \"${path.name}\": this storage doesn't support moving")
        forget(path)
        idCache[destination] = DocumentsContract.getDocumentId(movedUri)
        return destination
    }

    override fun delete(path: VaultPath) {
        if (path.isRoot) throw CoreError.Io("The vault root can't be deleted")
        val id = documentId(path) ?: return
        deleteDocument(path, id)
    }

    /** A content URI other apps can read (for the share sheet), or `null` if [path] is gone. */
    fun shareUri(path: VaultPath): Uri? = documentId(path)?.let(::documentUri)

    // ---- Document helpers ----

    /** The document id for [path], walking down from the closest cached folder. `null` if missing. */
    private fun documentId(path: VaultPath): String? {
        idCache[path]?.let { return it }
        val parent = path.parent ?: return null
        val parentId = documentId(parent) ?: return null
        // Listing the parent fills the cache for all its children at once.
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
        var found: String? = null
        query(childrenUri) { cursor ->
            val row = Row.from(cursor)
            if (row.name.isEmpty() || '/' in row.name) return@query
            idCache[parent.child(row.name)] = row.id
            if (row.name == path.name) found = row.id
        }
        return found
    }

    private fun documentUri(id: String): Uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id)

    /** Checks the exact letter case: shared storage on Android ignores case when comparing names. */
    private fun exactNameExists(
        dir: VaultPath,
        name: String,
    ): Boolean = list(dir).any { it.name == name }

    private fun createDocument(
        parent: VaultPath,
        name: String,
        mimeType: String,
    ): String {
        val parentId = documentId(parent) ?: throw CoreError.NotFound(parent)
        val uri =
            io("create \"$name\"") { DocumentsContract.createDocument(resolver, documentUri(parentId), mimeType, name) }
                ?: throw CoreError.Io("Couldn't create \"$name\"")
        val id = DocumentsContract.getDocumentId(uri)
        // The provider may have changed the name (taken → "Name (1)", or added an extension).
        val actualName = queryName(id)
        if (actualName != name) {
            io("clean up") { DocumentsContract.deleteDocument(resolver, uri) }
            throw CoreError.AlreadyExists(parent.child(name))
        }
        idCache[parent.child(name)] = id
        return id
    }

    private fun renameDocument(
        path: VaultPath,
        id: String,
        newName: String,
    ) {
        val parent = path.parent ?: throw CoreError.Io("The vault root can't be renamed")
        val newUri =
            io("rename \"${path.name}\"") { DocumentsContract.renameDocument(resolver, documentUri(id), newName) }
        // Some providers keep the id on rename and return null; others return a new URI.
        val newId = newUri?.let { DocumentsContract.getDocumentId(it) } ?: id
        val actualName = queryName(newId)
        forget(path)
        if (actualName != newName) {
            // The provider picked another name ("Name (1)"): put the old name back and fail.
            io("undo rename") { DocumentsContract.renameDocument(resolver, documentUri(newId), path.name) }
            throw CoreError.AlreadyExists(parent.child(newName))
        }
        idCache[parent.child(newName)] = newId
    }

    private fun deleteDocument(
        path: VaultPath,
        id: String,
    ) {
        val deleted = io("delete \"${path.name}\"") { DocumentsContract.deleteDocument(resolver, documentUri(id)) }
        forget(path)
        if (!deleted) throw CoreError.Io("Couldn't delete \"${path.name}\"")
    }

    /** Writes [bytes] over the document's content and flushes them to disk. */
    private fun writeBytes(
        id: String,
        bytes: ByteArray,
        path: VaultPath,
    ) {
        io("save \"${path.name}\"") {
            // "wt" = write and truncate (plain "w" may leave old bytes at the end on some providers).
            val descriptor =
                resolver.openFileDescriptor(documentUri(id), "wt")
                    ?: throw CoreError.Io("Couldn't save \"${path.name}\"")
            descriptor.use {
                FileOutputStream(it.fileDescriptor).use { out ->
                    out.write(bytes)
                    out.flush()
                    out.fd.sync() // on disk before we swap names
                }
            }
        }
    }

    private fun queryName(id: String): String? {
        var name: String? = null
        query(documentUri(id)) { cursor -> name = Row.from(cursor).name }
        return name
    }

    /** Drops [path] and everything cached inside it (after a rename, move, or delete). */
    private fun forget(path: VaultPath) {
        idCache.keys.removeAll { it.isInside(path) && !it.isRoot }
    }

    private fun query(
        uri: Uri,
        onRow: (Cursor) -> Unit,
    ) {
        io("read the folder") {
            resolver.query(uri, Row.COLUMNS, null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) onRow(cursor)
            }
        }
    }

    /**
     * Runs [block] and turns platform errors into a readable [CoreError.Io]. A lost permission
     * shows up as a `SecurityException`.
     */
    private inline fun <T> io(
        action: String,
        block: () -> T,
    ): T =
        try {
            block()
        } catch (error: CoreError) {
            throw error
        } catch (error: FileNotFoundException) {
            throw CoreError.Io("Couldn't $action: it doesn't exist anymore", error)
        } catch (error: SecurityException) {
            throw CoreError.Io("Couldn't $action: KuNotes lost access to this folder. Open it again.", error)
        } catch (error: IOException) {
            throw CoreError.Io("Couldn't $action: ${error.message}", error)
        } catch (error: IllegalArgumentException) {
            throw CoreError.Io("Couldn't $action: ${error.message}", error)
        }

    /** One row of a document query. */
    private class Row(
        val id: String,
        val name: String,
        val isDir: Boolean,
        val size: Long,
        val lastModified: Long,
    ) {
        fun toEntry(path: VaultPath) = FsEntry(path = path, isDir = isDir, size = if (isDir) 0 else size, lastModified = lastModified)

        companion object {
            val COLUMNS =
                arrayOf(
                    Document.COLUMN_DOCUMENT_ID,
                    Document.COLUMN_DISPLAY_NAME,
                    Document.COLUMN_MIME_TYPE,
                    Document.COLUMN_SIZE,
                    Document.COLUMN_LAST_MODIFIED,
                )

            fun from(cursor: Cursor) =
                Row(
                    id = cursor.getString(0),
                    name = cursor.getString(1) ?: "",
                    isDir = cursor.getString(2) == Document.MIME_TYPE_DIR,
                    size = if (cursor.isNull(3)) 0 else cursor.getLong(3),
                    lastModified = if (cursor.isNull(4)) 0 else cursor.getLong(4),
                )
        }
    }

    private companion object {
        /**
         * Created files use this type so the provider never adds an extension to our names.
         * (With `text/markdown` some providers would turn "Plan.md" into "Plan.md.md" or "Plan.md.txt".)
         */
        const val MIME_FILE = "application/octet-stream"
    }
}
