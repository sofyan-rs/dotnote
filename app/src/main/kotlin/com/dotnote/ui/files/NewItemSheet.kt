/**
 * "New note / folder" sheet: NOTE | FOLDER switch, the location ("In /Projects ▾"),
 * and the name. An empty name gives "Untitled" ("Untitled 2", ...). Sits above the keyboard.
 */
package com.dotnote.ui.files

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import com.dotnote.core.VaultPath
import com.dotnote.ui.dotline.DotlineBottomSheet
import com.dotnote.ui.dotline.DotlineIcons
import com.dotnote.ui.dotline.DotlineMenu
import com.dotnote.ui.dotline.DotlineMenuDivider
import com.dotnote.ui.dotline.DotlineMenuItem
import com.dotnote.ui.dotline.DotlineTextField
import com.dotnote.ui.dotline.PrimaryButton
import com.dotnote.ui.dotline.SecondaryButton
import com.dotnote.ui.dotline.SegmentOption
import com.dotnote.ui.dotline.SegmentedControl
import com.dotnote.ui.theme.Dotline

@Composable
fun NewItemSheet(
    initialDir: VaultPath,
    initialFolder: Boolean,
    folders: List<VaultPath>,
    existingNames: (VaultPath) -> List<String>,
    onCreate: (dir: VaultPath, name: String?, folder: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var folder by remember { mutableStateOf(initialFolder) }
    var dir by remember { mutableStateOf(initialDir) }
    var locationMenuOpen by remember { mutableStateOf(false) }
    val name = rememberTextFieldState()
    val focus = remember { FocusRequester() }
    val error = nameError(name.text.toString(), if (folder) null else ".md", existingNames(dir))

    // Focus the name field (and open the keyboard) as soon as the sheet appears.
    LaunchedEffect(Unit) { focus.requestFocus() }

    val create = {
        if (error == null) onCreate(dir, name.text.toString().ifBlank { null }, folder)
    }

    DotlineBottomSheet(onDismissRequest = onDismiss, bottomPadding = Dotline.space.s4) {
        SegmentedControl(
            options = listOf(SegmentOption("NOTE", DotlineIcons.Note), SegmentOption("FOLDER", DotlineIcons.Folder)),
            selectedIndex = if (folder) 1 else 0,
            onSelect = { folder = it == 1 },
        )
        Column(Modifier.padding(horizontal = Dotline.space.s1)) {
            Text(if (folder) "New folder" else "New note", style = Dotline.type.title, color = Dotline.colors.ink)
            Box {
                Row(
                    Modifier
                        .heightIn(min = 48.dp)
                        .clickable(onClickLabel = "Change location") { locationMenuOpen = true },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dotline.space.s1),
                ) {
                    Text("In ${displayFolder(dir)}", style = Dotline.type.data, color = Dotline.colors.inkMuted)
                    Icon(
                        DotlineIcons.ChevronDown,
                        contentDescription = null,
                        tint = Dotline.colors.inkMuted,
                        modifier = Modifier.size(16.dp),
                    )
                }
                DotlineMenu(expanded = locationMenuOpen, onDismissRequest = { locationMenuOpen = false }) {
                    folders.forEachIndexed { index, option ->
                        if (index > 0) DotlineMenuDivider()
                        DotlineMenuItem(
                            text = displayFolder(option),
                            icon = DotlineIcons.Folder,
                            onClick = {
                                dir = option
                                locationMenuOpen = false
                            },
                        )
                    }
                }
            }
        }
        DotlineTextField(
            inSheet = true,
            state = name,
            label = "NAME",
            placeholder = "Untitled",
            suffix = if (folder) null else ".md",
            errorText = error,
            onKeyboardAction = { create() },
            textFieldModifier = Modifier.focusRequester(focus),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Dotline.space.s2)) {
            SecondaryButton("Cancel", onClick = onDismiss, compact = true, modifier = Modifier.weight(1f))
            PrimaryButton("Create", onClick = create, compact = true, enabled = error == null, modifier = Modifier.weight(1f))
        }
    }
}

/** "/" for the vault root, "/Projects/Archive" for a folder. */
fun displayFolder(path: VaultPath): String = "/" + path.value

/**
 * The error to show under a name field, or `null` if the name can be used. An empty name is
 * fine (it becomes "Untitled"). [suffix] is added for the duplicate check (".md" for notes).
 */
fun nameError(
    typed: String,
    suffix: String?,
    existingNames: List<String>,
): String? {
    val name =
        com.dotnote.core.Names
            .trim(typed)
    if (name.isEmpty()) return null
    com.dotnote.core.Names
        .validateName(name)
        ?.let { return "Invalid name: ${it.message}" }
    val full = if (suffix != null && !name.lowercase().endsWith(suffix)) name + suffix else name
    // Android's shared storage ignores letter case, so "plan.md" and "Plan.md" collide.
    if (existingNames.any { it.equals(full, ignoreCase = true) }) return "“$full” already exists here"
    return null
}
