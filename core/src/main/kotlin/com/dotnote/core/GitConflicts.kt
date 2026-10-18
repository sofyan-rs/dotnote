/**
 * What git sync does when the same file changed on two devices: this device's version stays, and
 * the other one is saved next to it as `Name (conflict YYYY-MM-DD).md`. Port of the conflict part
 * of desktop's `git.rs`; the copy names must match desktop.
 */
package com.dotnote.core

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.dircache.DirCacheEntry
import java.time.LocalDate

object GitConflicts {
    /**
     * Where the copy of a conflicting file goes: next to it, named with [date] (`YYYY-MM-DD`).
     * `Plan.md` becomes `Plan (conflict 2026-10-09).md`; if that is taken, `Plan (conflict 2026-10-09) 2.md`.
     * A locked note keeps both extensions: `Keys (conflict 2026-10-09).md.age`.
     */
    fun conflictCopyPath(
        fs: VaultFs,
        path: VaultPath,
        date: String,
    ): VaultPath {
        val dir = path.parent ?: VaultPath.ROOT
        val name = path.name
        val stem: String
        val extension: String?
        if (NoteNames.isLockedNote(path)) {
            stem = name.dropLast(".md.age".length)
            extension = "md.age"
        } else {
            val dot = name.lastIndexOf('.')
            // Like Rust's `file_stem`/`extension`: a name that only *starts* with a dot
            // (`.gitignore`) has no extension.
            stem = if (dot <= 0) name else name.substring(0, dot)
            extension = if (dot <= 0) null else name.substring(dot + 1)
        }
        return NoteNames.uniquePath(fs, dir, "$stem (conflict $date)", extension)
    }

    /** Today's date as `YYYY-MM-DD`, in UTC (like desktop, so both name copies the same way). */
    fun today(): String = dateFromDays(System.currentTimeMillis() / MILLIS_PER_DAY)

    /** `YYYY-MM-DD` for a number of days since 1970-01-01. */
    internal fun dateFromDays(days: Long): String = LocalDate.ofEpochDay(days).toString()

    private const val MILLIS_PER_DAY = 86_400_000L

    /**
     * Settles a merge that stopped on conflicts. For each file:
     * - changed on both sides: ours stays, theirs is saved as a conflict copy;
     * - deleted here, changed there: their version comes back;
     * - changed here, deleted there: ours stays.
     *
     * Leaves everything staged, ready for the merge commit. Returns the conflict copies.
     */
    internal fun resolve(
        git: Git,
        fs: VaultFs,
    ): List<VaultPath> {
        val repository = git.repository
        // The index keeps up to three versions ("stages") of a conflicting file:
        // 1 = common ancestor, 2 = ours (this device), 3 = theirs (the remote).
        val stagesByPath = sortedMapOf<String, MutableMap<Int, DirCacheEntry>>()
        val index = repository.readDirCache()
        for (i in 0 until index.entryCount) {
            val entry = index.getEntry(i)
            if (entry.stage != DirCacheEntry.STAGE_0) {
                stagesByPath.getOrPut(entry.pathString) { mutableMapOf() }[entry.stage] = entry
            }
        }

        val date = today()
        val copies = mutableListOf<VaultPath>()
        for ((name, stages) in stagesByPath) {
            val path = VaultPath(name)
            val ours = stages[DirCacheEntry.STAGE_2]
            val theirs = stages[DirCacheEntry.STAGE_3]
            val toAdd = mutableListOf(name)
            when {
                ours != null && theirs != null -> {
                    val copy = conflictCopyPath(fs, path, date)
                    fs.writeAtomic(copy, blob(git, theirs))
                    // JGit wrote conflict markers into the file; put our version back.
                    fs.writeAtomic(path, blob(git, ours))
                    toAdd += copy.value
                    copies += copy
                }
                theirs != null -> fs.writeAtomic(path, blob(git, theirs))
                ours != null -> fs.writeAtomic(path, blob(git, ours))
                // Deleted on both sides: nothing to keep; `add` below records the deletion.
            }
            val add = git.add()
            toAdd.forEach { add.addFilepattern(it) }
            add.call()
            // `add` only stages files that exist; a file gone on both sides needs `update` mode.
            if (ours == null && theirs == null) {
                git
                    .add()
                    .setUpdate(true)
                    .addFilepattern(name)
                    .call()
            }
        }
        return copies
    }

    private fun blob(
        git: Git,
        entry: DirCacheEntry,
    ): ByteArray = git.repository.open(entry.objectId).getBytes(Int.MAX_VALUE) // no size limit: images can be big
}
