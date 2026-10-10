/**
 * The ⋮ sheets of the file tree: folder actions (SPEC 2.2) and note actions (2.3).
 */
package com.kunotes.ui.files

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.kunotes.core.NoteNames
import com.kunotes.core.Paths
import com.kunotes.core.VaultNode
import com.kunotes.core.VaultPath
import com.kunotes.ui.dotline.DotlineBottomSheet
import com.kunotes.ui.dotline.DotlineIcons
import com.kunotes.ui.dotline.ListDivider
import com.kunotes.ui.dotline.ListGroup
import com.kunotes.ui.dotline.ListRow
import com.kunotes.ui.dotline.QuickAction
import com.kunotes.ui.dotline.QuickActionRow
import com.kunotes.ui.dotline.SheetHeader

@Composable
fun FolderActionsSheet(
    node: VaultNode?,
    onNewNote: () -> Unit,
    onNewFolder: () -> Unit,
    onCopyPath: () -> Unit,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onTrash: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (node == null) {
        // The folder disappeared (deleted outside the app): close the sheet. A LaunchedEffect
        // runs after drawing, because a composable must not change state while it draws.
        LaunchedEffect(Unit) { onDismiss() }
        return
    }
    val folders = node.children.count { it.isDir }
    val notes = node.children.size - folders
    val inside = countInside(node)

    DotlineBottomSheet(onDismissRequest = onDismiss) {
        SheetHeader(
            icon = DotlineIcons.Folder,
            title = node.name,
            subtitle = "${displayFolder(node.path)} · ${plural(folders, "folder")} · ${plural(notes, "note")}",
        )
        QuickActionRow(
            listOf(
                QuickAction("NEW NOTE", DotlineIcons.NewNote, onNewNote),
                QuickAction("NEW FOLDER", DotlineIcons.NewFolder, onNewFolder),
                QuickAction("COPY PATH", DotlineIcons.Copy) {
                    onCopyPath()
                    onDismiss()
                },
            ),
        )
        ListGroup(inSheet = true) {
            ListRow("Rename folder", leadingIcon = DotlineIcons.Edit, inSheet = true, onClick = onRename)
            ListDivider()
            ListRow("Move to…", leadingIcon = DotlineIcons.MoveTo, inSheet = true, onClick = onMove)
            ListDivider()
            ListRow(
                "Move to trash",
                subtitle = if (inside == 0) "The folder is empty" else "With everything inside: ${plural(inside, "item")}",
                leadingIcon = DotlineIcons.Trash,
                destructive = true,
                inSheet = true,
                onClick = onTrash,
            )
        }
    }
}

@Composable
fun NoteActionsSheet(
    path: VaultPath,
    charCount: Int?,
    pinned: Boolean,
    onPin: () -> Unit,
    onCopyPath: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onLock: () -> Unit,
    onTrash: () -> Unit,
    onDismiss: () -> Unit,
) {
    val locked = NoteNames.isLockedNote(path)
    DotlineBottomSheet(onDismissRequest = onDismiss) {
        SheetHeader(
            icon = if (locked) DotlineIcons.Lock else DotlineIcons.Note,
            title = Paths.noteTitle(path),
            subtitle = listOfNotNull(path.name, charCount?.let { "$it chars" }, if (locked) "Locked" else null).joinToString(" · "),
        )
        QuickActionRow(
            listOf(
                QuickAction(if (pinned) "UNPIN" else "PIN", DotlineIcons.Pin, onPin),
                QuickAction("COPY PATH", DotlineIcons.Copy) {
                    onCopyPath()
                    onDismiss()
                },
                QuickAction("SHARE", DotlineIcons.Share, onShare),
            ),
        )
        ListGroup(inSheet = true) {
            ListRow("Rename", leadingIcon = DotlineIcons.Edit, inSheet = true, onClick = onRename)
            ListDivider()
            ListRow("Move to…", leadingIcon = DotlineIcons.MoveTo, inSheet = true, onClick = onMove)
            ListDivider()
            ListRow(
                if (locked) "Unlock note" else "Lock note",
                subtitle = if (locked) "Saves it as a plain .md file again" else "Encrypts it on disk with the vault password",
                leadingIcon = DotlineIcons.Lock,
                inSheet = true,
                onClick = onLock,
            )
            ListDivider()
            ListRow("Move to trash", leadingIcon = DotlineIcons.Trash, destructive = true, inSheet = true, onClick = onTrash)
        }
    }
}

/** "1 note", "3 notes". */
fun plural(
    count: Int,
    word: String,
): String = if (count == 1) "1 $word" else "$count ${word}s"

/** Every item inside [node], at any depth (only what the tree shows). */
private fun countInside(node: VaultNode): Int = node.children.sumOf { 1 + countInside(it) }
