/**
 * "Move to folder": a full screen with every folder of the vault as a radio list,
 * indented by depth. The current folder (and, for a folder, itself and its subfolders) can't
 * be picked. "New folder" creates one inside the selected folder and selects it.
 */
package com.dotnote.ui.files

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.lifecycle.viewModelScope
import com.dotnote.core.Paths
import com.dotnote.core.VaultPath
import com.dotnote.ui.dotline.DotlineIcons
import com.dotnote.ui.dotline.ListDivider
import com.dotnote.ui.dotline.ListGroup
import com.dotnote.ui.dotline.ListRow
import com.dotnote.ui.dotline.PrimaryButton
import com.dotnote.ui.dotline.RadioDot
import com.dotnote.ui.dotline.ScreenTitleAction
import com.dotnote.ui.dotnoteViewModel
import com.dotnote.ui.theme.Dotline
import com.dotnote.vault.VaultStore
import kotlinx.coroutines.launch

class MoveToViewModel(
    private val vaultStore: VaultStore,
) : androidx.lifecycle.ViewModel() {
    val state = vaultStore.state

    fun folders(): List<VaultPath> = vaultStore.allFolders()

    fun move(
        item: VaultPath,
        folder: VaultPath,
        onDone: () -> Unit,
    ) {
        viewModelScope.launch { if (vaultStore.move(item, folder) != null) onDone() }
    }

    fun createFolder(
        dir: VaultPath,
        name: String?,
        onCreated: (VaultPath) -> Unit,
    ) {
        viewModelScope.launch { vaultStore.createFolder(dir, name)?.let(onCreated) }
    }
}

@Composable
fun MoveToScreen(
    path: String,
    onDone: () -> Unit,
) {
    val viewModel = dotnoteViewModel { MoveToViewModel(it.vaultStore) }
    // Read the state so the list updates after "New folder" (the store rescans).
    val vaultState by viewModel.state.collectAsStateWithLifecycle()
    val item = VaultPath(path)
    val currentFolder = item.parent ?: VaultPath.ROOT
    var destination by remember { mutableStateOf<VaultPath?>(null) }
    var newFolderOpen by remember { mutableStateOf(false) }
    val folders = remember(vaultState.root) { viewModel.folders() }

    Column(
        Modifier
            .fillMaxSize()
            .background(Dotline.colors.bg)
            .safeDrawingPadding(),
    ) {
        Row(Modifier.padding(horizontal = Dotline.space.s1), verticalAlignment = Alignment.CenterVertically) {
            ScreenTitleAction(DotlineIcons.Close, "Cancel", onDone)
            Text("MOVE · ${item.name.uppercase()}", style = Dotline.type.label, color = Dotline.colors.inkMuted)
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = Dotline.space.s4, end = Dotline.space.s4, top = Dotline.space.s5),
            verticalArrangement = Arrangement.spacedBy(Dotline.space.s4),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Dotline.space.s1)) {
                Text("Move to folder", style = Dotline.type.title, color = Dotline.colors.ink)
                Text("Now in ${displayFolder(currentFolder)}", style = Dotline.type.bodyS, color = Dotline.colors.inkMuted)
            }
            ListGroup {
                folders.forEachIndexed { index, folder ->
                    if (index > 0) ListDivider()
                    val isCurrent = folder == currentFolder
                    // A folder can't go into itself or one of its own subfolders.
                    val allowed = !isCurrent && Paths.canMoveInto(item, folder)
                    // The indent sits outside the row, so give it the row's selected colour too.
                    val rowBackground =
                        if (folder ==
                            destination
                        ) {
                            Dotline.colors.surfaceRaised
                        } else {
                            androidx.compose.ui.graphics.Color.Transparent
                        }
                    Row(Modifier.background(rowBackground), verticalAlignment = Alignment.CenterVertically) {
                        Spacer(Modifier.width(28.dp * folder.segments.size))
                        ListRow(
                            title = if (folder.isRoot) vaultState.vault?.name ?: "Vault" else folder.name,
                            modifier = Modifier.weight(1f),
                            leadingIcon = DotlineIcons.Folder,
                            selected = folder == destination,
                            role = Role.RadioButton,
                            onClick = if (allowed) ({ destination = folder }) else null,
                            trailing = {
                                if (isCurrent) {
                                    Text("CURRENT", style = Dotline.type.label, color = Dotline.colors.inkFaint)
                                } else {
                                    RadioDot(selected = folder == destination, onClick = null, enabled = allowed, small = true)
                                }
                            },
                        )
                    }
                }
                ListDivider()
                ListRow(
                    title = "New folder",
                    leadingIcon = DotlineIcons.Plus,
                    onClick = { newFolderOpen = true },
                )
            }
            Spacer(Modifier.height(Dotline.space.s4))
        }
        val target = destination
        PrimaryButton(
            text =
                if (target ==
                    null
                ) {
                    "Pick a folder"
                } else {
                    "Move to ${if (target.isRoot) vaultState.vault?.name ?: "vault" else target.name}"
                },
            onClick = { if (target != null) viewModel.move(item, target, onDone) },
            enabled = target != null,
            modifier = Modifier.fillMaxWidth().padding(start = Dotline.space.s4, end = Dotline.space.s4, bottom = Dotline.space.s6),
        )
    }

    if (newFolderOpen) {
        val parent = destination ?: VaultPath.ROOT
        NewItemSheet(
            initialDir = parent,
            initialFolder = true,
            folders = folders,
            existingNames = { dir -> vaultState.root?.let { root -> findChildren(root, dir) }.orEmpty() },
            onCreate = { dir, name, _ ->
                viewModel.createFolder(dir, name) { created ->
                    destination = created
                    newFolderOpen = false
                }
            },
            onDismiss = { newFolderOpen = false },
        )
    }
}

private fun findChildren(
    node: com.dotnote.core.VaultNode,
    dir: VaultPath,
): List<String>? {
    if (node.path == dir) return node.children.map { it.name }
    return node.children.firstNotNullOfOrNull { if (dir.isInside(it.path)) findChildren(it, dir) else null }
}
