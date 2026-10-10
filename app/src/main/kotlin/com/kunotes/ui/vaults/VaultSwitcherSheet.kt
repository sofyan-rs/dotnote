/**
 * "Change vault" sheet (SPEC 6.1), opened from the vault name on the Files tab: a radio list of
 * the saved vaults, Manage (→ Vaults tab), Open folder, Clone repo, and "Open {name}".
 */
package com.kunotes.ui.vaults

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kunotes.ui.dotline.DotlineBottomSheet
import com.kunotes.ui.dotline.DotlineIcons
import com.kunotes.ui.dotline.ListDivider
import com.kunotes.ui.dotline.ListGroup
import com.kunotes.ui.dotline.ListRow
import com.kunotes.ui.dotline.PrimaryButton
import com.kunotes.ui.dotline.RadioDot
import com.kunotes.ui.dotline.SecondaryButton
import com.kunotes.ui.kuNotesViewModel
import com.kunotes.ui.theme.Dotline

@Composable
fun VaultSwitcherSheet(
    onManage: () -> Unit,
    onOpenFolder: () -> Unit,
    onCloneFromGit: () -> Unit,
    onDismiss: () -> Unit,
) {
    val viewModel = kuNotesViewModel { VaultsViewModel(it) }
    val vaults by viewModel.vaults.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    var selectedId by remember { mutableStateOf<String?>(null) }
    val selected = vaults.firstOrNull { it.vault.id == selectedId } ?: vaults.firstOrNull { it.isOpen }

    DotlineBottomSheet(onDismissRequest = onDismiss) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Dotline.space.s1),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Change vault", style = Dotline.type.title, color = Dotline.colors.ink)
            Text(
                "Manage",
                style = Dotline.type.body,
                color = Dotline.colors.inkMuted,
                modifier =
                    Modifier
                        .clickable(role = Role.Button) {
                            onDismiss()
                            onManage()
                        }.padding(Dotline.space.s3),
            )
        }
        ListGroup(inSheet = true) {
            vaults.forEachIndexed { index, item ->
                if (index > 0) ListDivider()
                ListRow(
                    title = item.vault.name,
                    subtitle = item.meta,
                    subtitleIsData = true,
                    inSheet = true,
                    minHeight = 72.dp,
                    role = Role.RadioButton,
                    selected = item == selected,
                    onClick = { selectedId = item.vault.id },
                    trailing = { RadioDot(selected = item == selected, onClick = null) },
                )
            }
        }
        if (message != null) Text(message.orEmpty(), style = Dotline.type.bodyS, color = Dotline.colors.accentText)
        Column(verticalArrangement = Arrangement.spacedBy(Dotline.space.s2)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Dotline.space.s2)) {
                SecondaryButton(
                    "Open folder",
                    onClick = onOpenFolder,
                    icon = DotlineIcons.Folder,
                    compact = true,
                    modifier = Modifier.weight(1f),
                )
                SecondaryButton(
                    "Clone repo",
                    onClick = onCloneFromGit,
                    icon = DotlineIcons.GitBranch,
                    compact = true,
                    modifier = Modifier.weight(1f),
                )
            }
            PrimaryButton(
                text = "Open ${selected?.vault?.name ?: ""}",
                onClick = {
                    val target = selected ?: return@PrimaryButton
                    if (viewModel.open(target.vault)) onDismiss()
                },
                enabled = selected != null,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
