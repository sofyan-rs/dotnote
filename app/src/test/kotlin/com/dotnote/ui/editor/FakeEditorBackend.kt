/**
 * An [EditorBackend] for JVM tests: the note lives in a `:core` [MemoryVaultFs], read and saved
 * with the same line-ending rules as `NoteFiles`, and every write is recorded.
 */
package com.dotnote.ui.editor

import com.dotnote.core.FsOps
import com.dotnote.core.LineEnding
import com.dotnote.core.MemoryVaultFs
import com.dotnote.core.NoteNames
import com.dotnote.core.VaultFs
import com.dotnote.core.VaultPath
import com.dotnote.settings.ViewMode
import com.dotnote.vault.NoteLockedException
import com.dotnote.vault.NoteText
import com.dotnote.vault.VaultEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf

class FakeEditorBackend(
    val memoryFs: MemoryVaultFs = MemoryVaultFs(),
) : EditorBackend {
    val vaultEvents = MutableSharedFlow<VaultEvent>(extraBufferCapacity = 8)

    /** Paths written, in order. */
    val writes = mutableListOf<VaultPath>()
    val trashed = mutableListOf<VaultPath>()
    var viewMode = ViewMode.Live

    /** Set to make the next writes fail. */
    var failWrites: Exception? = null

    override val events: Flow<VaultEvent> = vaultEvents
    override val fs: VaultFs = memoryFs
    override val vaultName = "Personal"

    override suspend fun read(path: VaultPath): NoteText {
        if (NoteNames.isLockedNote(path)) throw NoteLockedException(path)
        val raw = memoryFs.read(path).decodeToString()
        return NoteText(LineEnding.normalize(raw), LineEnding.detect(raw))
    }

    override suspend fun write(
        path: VaultPath,
        text: String,
        lineEnding: LineEnding,
    ) {
        failWrites?.let { throw it }
        writes += path
        memoryFs.writeAtomic(path, lineEnding.apply(text).toByteArray())
    }

    override suspend fun loadViewMode(): ViewMode = viewMode

    override suspend fun saveViewMode(mode: ViewMode) {
        viewMode = mode
    }

    override fun isPinned(path: VaultPath): Flow<Boolean> = flowOf(false)

    override fun togglePin(path: VaultPath) = Unit

    override fun namesIn(folder: VaultPath): List<String> = memoryFs.list(folder).map { it.name }

    override suspend fun rename(
        path: VaultPath,
        newName: String,
    ): VaultPath {
        val renamed = FsOps.rename(memoryFs, path, newName)
        vaultEvents.emit(VaultEvent.Moved(path, renamed))
        return renamed
    }

    override suspend fun trash(path: VaultPath) {
        trashed += path
    }

    override suspend fun importImage(
        note: VaultPath,
        name: String,
        bytes: ByteArray,
    ): String = FsOps.importImage(memoryFs, note, name, bytes)

    override suspend fun reportError(message: String) = Unit
}
