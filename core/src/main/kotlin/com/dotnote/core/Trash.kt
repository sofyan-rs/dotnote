/**
 * Android's trash: deleting moves items into `.trash/<day>/` at the vault root, so the user
 * can undo, and old days are deleted for good after 30 days.
 *
 * Android has no system trash for folders picked with the file picker, so the vault keeps its
 * own. `.trash` starts with a dot, so the tree never shows it (same rule as desktop).
 *
 * Each item goes into a folder named after the day it was deleted, like
 * `.trash/2026-10-10/Plan.md`. That folder name is what [purge] goes by, so a note that
 * wasn't edited for months still gets its full 30 days after being deleted.
 */
package com.dotnote.core

import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeParseException

/**
 * Something the user deleted, now in `.trash/`. Keep it to offer UNDO ([Trash.restore]).
 *
 * The original location is only kept in this object (in memory). If the app is closed, the
 * item stays in `.trash/` until it's purged, but there's no way back to [originalPath].
 */
data class TrashedItem(
    /** Where the item was before it was trashed. */
    val originalPath: VaultPath,
    /** Where the item is now, inside `.trash/<day>/`. */
    val trashPath: VaultPath,
)

object Trash {
    /** Folder at the vault root that holds deleted items. */
    const val FOLDER = ".trash"

    /** How many days [purge] keeps deleted items, by default. */
    const val KEEP_DAYS = 30L

    private val trashDir = VaultPath.ROOT.child(FOLDER)

    /** Today's date in UTC: the name of today's trash folder. */
    fun today(): LocalDate = LocalDate.now(ZoneOffset.UTC)

    /**
     * Moves [path] (a file or a folder) into `.trash/<today>/`. Nothing is deleted.
     *
     * If that day's folder already has an item with that name, the item gets a free name
     * (`Plan 2.md`, `Plan 3.md`, ...). Returns what [restore] needs to put it back.
     */
    fun trash(
        fs: VaultFs,
        path: VaultPath,
        today: LocalDate = today(),
    ): TrashedItem {
        if (path.isRoot) throw CoreError.Io("The vault root can't be deleted")
        if (path.isInside(trashDir)) throw CoreError.Io("\"${path.name}\" is already in the trash")
        if (!fs.exists(path)) throw CoreError.NotFound(path)
        val dayDir = trashDir.child(today.toString()) // ISO format: 2026-10-10
        fs.createDir(dayDir)
        val trashPath = FsOps.moveWithFreeName(fs, path, dayDir)
        return TrashedItem(originalPath = path, trashPath = trashPath)
    }

    /**
     * Moves a trashed item back to where it was (UNDO). Returns where it is now.
     *
     * The original folder is created again if it's gone. If its old name was taken in the
     * meantime, the item gets a free name instead (`Plan 2.md`).
     */
    fun restore(
        fs: VaultFs,
        item: TrashedItem,
    ): VaultPath {
        if (!fs.exists(item.trashPath)) throw CoreError.NotFound(item.trashPath)
        val targetDir = item.originalPath.parent ?: VaultPath.ROOT
        fs.createDir(targetDir)
        return FsOps.moveWithFreeName(fs, item.trashPath, targetDir, wantedName = item.originalPath.name)
    }

    /**
     * Deletes, for good, the day folders in `.trash/` older than [keepDays] days before [today].
     * Run it when a vault is opened. Returns the deleted day folders.
     *
     * Anything in `.trash/` that isn't a day folder (put there by hand, or by another app) is
     * never deleted.
     */
    fun purge(
        fs: VaultFs,
        keepDays: Long = KEEP_DAYS,
        today: LocalDate = today(),
    ): List<VaultPath> {
        if (!fs.exists(trashDir)) return emptyList()
        val oldestKept = today.minusDays(keepDays)
        val purged = mutableListOf<VaultPath>()
        for (entry in fs.list(trashDir)) {
            val day = dayOf(entry) ?: continue
            if (day.isBefore(oldestKept)) {
                fs.delete(entry.path)
                purged.add(entry.path)
            }
        }
        return purged.sorted()
    }

    /** The date a day folder stands for, or `null` if [entry] isn't a day folder. */
    private fun dayOf(entry: FsEntry): LocalDate? {
        if (!entry.isDir) return null
        return try {
            LocalDate.parse(entry.name)
        } catch (_: DateTimeParseException) {
            null
        }
    }
}
