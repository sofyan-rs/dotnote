/**
 * The editor's top bar (SPEC 3.1–3.3) and its ⋮ menu (3.4, mockup Editor-Menu): back, the note's
 * title over `PERSONAL / EXAMPLE.MD`, the save state (✓ SAVED / ● SAVING), and ⋮.
 *
 * Menu: Rename, Move to…, Pin note, Copy path, Share, divider, Lock note, Move to trash (red).
 */
package com.kunotes.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kunotes.ui.dotline.DotlineIcons
import com.kunotes.ui.dotline.DotlineMenu
import com.kunotes.ui.dotline.DotlineMenuDivider
import com.kunotes.ui.dotline.DotlineMenuItem
import com.kunotes.ui.dotline.MoreButton
import com.kunotes.ui.dotline.ScreenTitleAction
import com.kunotes.ui.theme.Dotline

/** What the editor menu can do; the screen decides how. */
enum class EditorMenuAction { Rename, Move, Pin, CopyPath, Share, Lock, Trash }

/**
 * @param title the note's title ("Example"), [location] the label under it ("PERSONAL / EXAMPLE.MD").
 * @param saveState shown before ⋮; `null` hides it (Preview mode, mockup).
 * @param pinned whether the menu says "Unpin note" instead of "Pin note".
 */
@Composable
fun EditorTopBar(
    title: String,
    location: String,
    saveState: SaveState?,
    pinned: Boolean,
    onBack: () -> Unit,
    onMenuAction: (EditorMenuAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val select = { action: EditorMenuAction ->
        menuOpen = false
        onMenuAction(action)
    }
    Row(
        modifier.fillMaxWidth().padding(horizontal = Dotline.space.s1),
        horizontalArrangement = Arrangement.spacedBy(Dotline.space.s1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ScreenTitleAction(DotlineIcons.Back, "Back to files", onBack)
        Column(Modifier.weight(1f)) {
            Text(title, style = Dotline.type.headline, color = Dotline.colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(location, style = Dotline.type.label, color = Dotline.colors.inkFaint, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (saveState != null) SaveStateLabel(saveState)
        // The menu opens below the Box that holds its anchor.
        Box {
            MoreButton(
                onClick = { menuOpen = true },
                contentDescription = "Note actions",
                open = menuOpen,
                tint = Dotline.colors.ink,
            )
            DotlineMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DotlineMenuItem("Rename", DotlineIcons.Edit, onClick = { select(EditorMenuAction.Rename) })
                DotlineMenuItem("Move to…", DotlineIcons.MoveTo, onClick = { select(EditorMenuAction.Move) })
                DotlineMenuItem(if (pinned) "Unpin note" else "Pin note", DotlineIcons.Pin, onClick = { select(EditorMenuAction.Pin) })
                DotlineMenuItem("Copy path", DotlineIcons.Copy, onClick = { select(EditorMenuAction.CopyPath) })
                DotlineMenuItem("Share", DotlineIcons.Share, onClick = { select(EditorMenuAction.Share) })
                DotlineMenuDivider()
                DotlineMenuItem("Lock note", DotlineIcons.Lock, onClick = { select(EditorMenuAction.Lock) })
                DotlineMenuItem("Move to trash", DotlineIcons.Trash, destructive = true, onClick = { select(EditorMenuAction.Trash) })
            }
        }
    }
}

/** ✓ SAVED, ● SAVING, or NOT SAVED (red) after a failed save. */
@Composable
private fun SaveStateLabel(state: SaveState) {
    val colors = Dotline.colors
    Row(
        Modifier.padding(end = Dotline.space.s1),
        horizontalArrangement = Arrangement.spacedBy(Dotline.space.s1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (state) {
            SaveState.Saved -> {
                Icon(DotlineIcons.Check, contentDescription = null, tint = colors.inkMuted, modifier = Modifier.size(14.dp))
                Text("SAVED", style = Dotline.type.label, color = colors.inkMuted)
            }
            SaveState.Saving -> Text("● SAVING", style = Dotline.type.label, color = colors.inkMuted)
            is SaveState.Failed -> Text("NOT SAVED", style = Dotline.type.label, color = colors.accentText)
        }
    }
}
