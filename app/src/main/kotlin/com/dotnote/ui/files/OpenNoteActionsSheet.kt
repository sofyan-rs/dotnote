/**
 * Long press on a card in the OPEN NOTES row (Files tab): pin it, or close cards.
 *
 * The row is the phone's version of desktop tabs. Closing a card only removes it from the row;
 * the note file stays. Pinned cards stay at the front and aren't closed by "Close others" or
 * "Close all unpinned". (Not in the original design.)
 */
package com.dotnote.ui.files

import androidx.compose.runtime.Composable
import com.dotnote.ui.dotline.DotlineBottomSheet
import com.dotnote.ui.dotline.DotlineIcons
import com.dotnote.ui.dotline.ListDivider
import com.dotnote.ui.dotline.ListGroup
import com.dotnote.ui.dotline.ListRow
import com.dotnote.ui.dotline.SheetHeader

@Composable
fun OpenNoteActionsSheet(
    title: String,
    pinned: Boolean,
    onPin: () -> Unit,
    onClose: () -> Unit,
    onCloseOthers: () -> Unit,
    onCloseAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    // Every action closes the sheet afterwards.
    fun then(action: () -> Unit): () -> Unit =
        {
            action()
            onDismiss()
        }

    DotlineBottomSheet(onDismissRequest = onDismiss) {
        SheetHeader(icon = DotlineIcons.Note, title = title, subtitle = if (pinned) "Open note · pinned" else "Open note")
        ListGroup(inSheet = true) {
            ListRow(
                if (pinned) "Unpin" else "Pin",
                subtitle = if (pinned) null else "Keeps it at the front of Open notes",
                leadingIcon = DotlineIcons.Pin,
                inSheet = true,
                onClick = then(onPin),
            )
            ListDivider()
            ListRow(
                "Close",
                subtitle = "Removes it from Open notes; the note stays",
                leadingIcon = DotlineIcons.Close,
                inSheet = true,
                onClick = then(onClose),
            )
            ListDivider()
            ListRow(
                "Close others",
                subtitle = "Pinned notes stay",
                leadingIcon = DotlineIcons.Close,
                inSheet = true,
                onClick = then(onCloseOthers),
            )
            ListDivider()
            ListRow("Close all unpinned", leadingIcon = DotlineIcons.Close, inSheet = true, onClick = then(onCloseAll))
        }
    }
}
