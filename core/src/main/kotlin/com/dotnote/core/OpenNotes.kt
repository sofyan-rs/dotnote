/**
 * The list of open notes shown as cards on the Files screen: the mobile form of desktop tabs.
 * Port of desktop `tabs.rs`, with the ordering rules adapted to a phone (see [OpenNotes]).
 */
package com.dotnote.core

/** One open note. A pinned note stays at the front and survives "close others". */
data class OpenNote(
    val path: VaultPath,
    val pinned: Boolean = false,
)

/**
 * The open notes, in display order. Immutable: every change returns a new `OpenNotes`, which
 * fits a `StateFlow` (the UI sees a new value and redraws).
 *
 * Rules (same as desktop unless noted):
 * - Pinned notes always come first.
 * - Pinning adds the note to the end of the pinned group; unpinning puts it first after it.
 * - "Close others" and "close unpinned" never close pinned notes.
 * - After a rename or move, [remap] makes entries follow, including notes inside a renamed folder.
 *
 * Differences from desktop tabs:
 * - Order is "most recent first": opening a note moves it to the front of the unpinned notes.
 *   Desktop inserts new tabs right after the active tab and leaves open tabs where they are.
 *   Opening a pinned note doesn't move it, so pinned cards stay where the user put them.
 * - No "preview tab" (desktop's temporary italic tab), no active tab (the open editor is the
 *   active note), no drag to reorder and no "close to the right".
 */
data class OpenNotes(
    val entries: List<OpenNote> = emptyList(),
) {
    val isEmpty: Boolean get() = entries.isEmpty()

    /** The paths in display order. */
    val paths: List<VaultPath> get() = entries.map { it.path }

    operator fun contains(path: VaultPath): Boolean = entries.any { it.path == path }

    /**
     * Opens [path]: a new note goes to the front of the unpinned notes (after the pinned ones);
     * an already open unpinned note moves there. An open pinned note stays where it is.
     */
    fun open(path: VaultPath): OpenNotes {
        val existing = entries.firstOrNull { it.path == path }
        if (existing != null && existing.pinned) return this
        val others = entries.filter { it.path != path }
        return OpenNotes(insertAt(others, pinnedCountOf(others), OpenNote(path)))
    }

    /** Pins [path], moving it to the end of the pinned group. Does nothing if it isn't open or is pinned. */
    fun pin(path: VaultPath): OpenNotes {
        val existing = entries.firstOrNull { it.path == path } ?: return this
        if (existing.pinned) return this
        val others = entries.filter { it.path != path }
        return OpenNotes(insertAt(others, pinnedCountOf(others), existing.copy(pinned = true)))
    }

    /** Unpins [path], moving it to the first place after the pinned group. */
    fun unpin(path: VaultPath): OpenNotes {
        val existing = entries.firstOrNull { it.path == path } ?: return this
        if (!existing.pinned) return this
        val others = entries.filter { it.path != path }
        return OpenNotes(insertAt(others, pinnedCountOf(others), existing.copy(pinned = false)))
    }

    /** Pins an unpinned note, or unpins a pinned one. */
    fun togglePin(path: VaultPath): OpenNotes {
        val existing = entries.firstOrNull { it.path == path } ?: return this
        return if (existing.pinned) unpin(path) else pin(path)
    }

    /** Closes [path] (pinned or not). */
    fun close(path: VaultPath): OpenNotes = OpenNotes(entries.filter { it.path != path })

    /**
     * Closes every note that is (or is inside) [path], e.g. after the note or its folder was
     * moved to the trash.
     */
    fun closeInside(path: VaultPath): OpenNotes = OpenNotes(entries.filter { !it.path.isInside(path) })

    /** Closes all unpinned notes except [keep]. */
    fun closeOthers(keep: VaultPath): OpenNotes = OpenNotes(entries.filter { it.pinned || it.path == keep })

    /** Closes every unpinned note. */
    fun closeUnpinned(): OpenNotes = OpenNotes(entries.filter { it.pinned })

    /**
     * After [old] was renamed or moved to [new], updates every entry that is [old] or inside it.
     * The order and pinned flags don't change.
     */
    fun remap(
        old: VaultPath,
        new: VaultPath,
    ): OpenNotes {
        val moved = entries.map { entry -> entry.copy(path = Paths.remapPath(entry.path, old, new) ?: entry.path) }
        // Two entries can only end up on the same path if the storage changed under us; keep the first.
        return OpenNotes(moved.distinctBy { it.path })
    }

    private fun pinnedCountOf(list: List<OpenNote>): Int = list.count { it.pinned }

    private fun insertAt(
        list: List<OpenNote>,
        index: Int,
        note: OpenNote,
    ): List<OpenNote> = list.take(index) + note + list.drop(index)
}
