/**
 * Actions for one vault (SPEC 6.3): open, rename in the list, sync settings, notes password,
 * remove (with the confirm dialog of 6.4).
 *
 * Removing only forgets the vault: the folder and notes stay on the phone. ("Also delete the
 * folder" from the mockup is left out on purpose: Android has no trash for a picked folder, so it
 * would delete notes for good. See PLAN.)
 */
package com.dotnote.ui.vaults

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import com.dotnote.settings.SavedVault
import com.dotnote.ui.dotline.DestructiveButton
import com.dotnote.ui.dotline.DotlineBottomSheet
import com.dotnote.ui.dotline.DotlineDialog
import com.dotnote.ui.dotline.DotlineIcons
import com.dotnote.ui.dotline.DotlineTextField
import com.dotnote.ui.dotline.ListDivider
import com.dotnote.ui.dotline.ListGroup
import com.dotnote.ui.dotline.ListRow
import com.dotnote.ui.dotline.ListRowValue
import com.dotnote.ui.dotline.PrimaryButton
import com.dotnote.ui.dotline.SecondaryButton
import com.dotnote.ui.dotline.SheetHeader
import com.dotnote.ui.theme.Dotline

private enum class Step { Actions, Rename, ConfirmRemove }

@Composable
fun VaultActionsSheet(
    vault: SavedVault,
    item: VaultUi?,
    viewModel: VaultsViewModel,
    navigation: VaultNavigation,
    onDismiss: () -> Unit,
) {
    var step by remember { mutableStateOf(Step.Actions) }
    // produceState runs the suspend check once and gives its result as state (null = checking).
    val hasPassword by produceState<Boolean?>(null, vault.id) { value = viewModel.hasPassword(vault) }

    /** Opens the vault first, then runs [then] (sync and password settings act on the open vault). */
    fun openThen(then: () -> Unit) {
        onDismiss()
        if (viewModel.open(vault)) then()
    }

    when (step) {
        Step.Actions ->
            DotlineBottomSheet(onDismissRequest = onDismiss) {
                SheetHeader(icon = DotlineIcons.Vault, title = vault.name, subtitle = item?.meta)
                ListGroup(inSheet = true) {
                    ListRow("Open vault", leadingIcon = DotlineIcons.Folder, inSheet = true, onClick = { openThen(navigation.onOpenFiles) })
                    ListDivider()
                    ListRow("Rename in list", leadingIcon = DotlineIcons.Edit, inSheet = true, onClick = { step = Step.Rename })
                    ListDivider()
                    ListRow(
                        "Sync settings",
                        leadingIcon = DotlineIcons.Sync,
                        inSheet = true,
                        onClick = { openThen(navigation.onSyncSettings) },
                        trailing = { ListRowValue(if (item?.syncOn == true) "On" else "Off") },
                    )
                    ListDivider()
                    ListRow(
                        "Notes password",
                        leadingIcon = DotlineIcons.Lock,
                        inSheet = true,
                        onClick = { openThen { navigation.onNotesPassword(hasPassword == true) } },
                        trailing = { ListRowValue(if (hasPassword == true) "Set" else "Not set") },
                    )
                    ListDivider()
                    ListRow(
                        "Remove vault",
                        leadingIcon = DotlineIcons.RemoveVault,
                        destructive = true,
                        inSheet = true,
                        onClick = { step = Step.ConfirmRemove },
                    )
                }
            }
        Step.Rename -> RenameVaultSheet(vault, onRename = { viewModel.renameInList(vault, it) }, onDismiss = onDismiss)
        Step.ConfirmRemove ->
            DotlineDialog(
                onDismissRequest = onDismiss,
                title = "Remove “${vault.name}”?",
                body = "Dotnote forgets this vault. The folder and its notes stay on your phone, and you can open it again any time.",
                dismissButton = { SecondaryButton("Cancel", onClick = onDismiss, compact = true, modifier = Modifier.weight(1f)) },
                confirmButton = {
                    DestructiveButton(
                        "Remove",
                        onClick = {
                            onDismiss()
                            viewModel.remove(vault, onNoVaultLeft = navigation.onNoVaultLeft)
                        },
                        compact = true,
                        modifier = Modifier.weight(1f),
                    )
                },
            )
    }
}

/** Renames the vault in the list only; the folder on disk keeps its name. */
@Composable
private fun RenameVaultSheet(
    vault: SavedVault,
    onRename: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val name = rememberTextFieldState(vault.name, initialSelection = TextRange(0, vault.name.length))
    val focus = remember { FocusRequester() }
    val blank = name.text.isBlank()
    LaunchedEffect(Unit) { focus.requestFocus() }
    DotlineBottomSheet(onDismissRequest = onDismiss, bottomPadding = Dotline.space.s4) {
        SheetHeader(icon = DotlineIcons.Vault, title = "Rename in list", subtitle = "The folder on disk keeps its name.")
        DotlineTextField(
            inSheet = true,
            state = name,
            label = "NAME",
            errorText = if (blank) "The name can't be empty" else null,
            textFieldModifier = Modifier.focusRequester(focus),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Dotline.space.s2)) {
            SecondaryButton("Cancel", onClick = onDismiss, compact = true, modifier = Modifier.weight(1f))
            PrimaryButton(
                "Rename",
                onClick = {
                    onRename(name.text.toString())
                    onDismiss()
                },
                enabled = !blank,
                compact = true,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
