/**
 * Rename sheet (SPEC 2.4): the name without its extension, fully selected, with the fixed
 * `.md` suffix for notes. The file is renamed on disk; open notes follow it.
 */
package com.kunotes.ui.files

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import com.kunotes.core.NoteNames
import com.kunotes.core.Paths
import com.kunotes.core.VaultPath
import com.kunotes.ui.dotline.DotlineBottomSheet
import com.kunotes.ui.dotline.DotlineTextField
import com.kunotes.ui.dotline.PrimaryButton
import com.kunotes.ui.dotline.SecondaryButton
import com.kunotes.ui.theme.Dotline

@Composable
fun RenameSheet(
    path: VaultPath,
    isFolder: Boolean,
    existingNames: List<String>,
    onRename: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val suffix =
        when {
            isFolder -> null
            NoteNames.isLockedNote(path) -> ".md.age"
            else -> ".md"
        }
    val current = if (isFolder) path.name else Paths.noteTitle(path)
    // Start with the whole name selected, so typing replaces it.
    val name = rememberTextFieldState(initialText = current, initialSelection = TextRange(0, current.length))
    val focus = remember { FocusRequester() }
    val typed = name.text.toString()
    // The item's own name doesn't count as "taken" (renaming only the letter case is allowed).
    val others = existingNames.filter { it != path.name }
    val error = if (typed.isBlank()) "The name can't be empty" else nameError(typed, suffix, others)
    val rename = {
        if (error == null && typed != current) {
            onRename(typed)
        } else if (typed == current) {
            onDismiss()
        }
    }

    LaunchedEffect(Unit) { focus.requestFocus() }

    DotlineBottomSheet(onDismissRequest = onDismiss, bottomPadding = Dotline.space.s4) {
        Column(Modifier.padding(horizontal = Dotline.space.s1), verticalArrangement = Arrangement.spacedBy(Dotline.space.s1)) {
            Text(if (isFolder) "Rename folder" else "Rename note", style = Dotline.type.title, color = Dotline.colors.ink)
            Text("In ${displayFolder(path.parent ?: VaultPath.ROOT)}", style = Dotline.type.data, color = Dotline.colors.inkMuted)
        }
        DotlineTextField(
            state = name,
            label = "NAME",
            suffix = suffix,
            helperText = if (isFolder) "The folder is renamed on disk." else "The file is renamed on disk. Open tabs follow it.",
            errorText = error,
            onKeyboardAction = { rename() },
            textFieldModifier = Modifier.focusRequester(focus),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Dotline.space.s2)) {
            SecondaryButton("Cancel", onClick = onDismiss, compact = true, modifier = Modifier.weight(1f))
            PrimaryButton("Rename", onClick = rename, compact = true, enabled = error == null, modifier = Modifier.weight(1f))
        }
    }
}
