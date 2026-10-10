/**
 * The VAULTS tab (SPEC 6.2): the vaults on this phone (the open one tagged ● OPEN), and
 * "Add a vault" (open a folder, clone from git). ⋮ opens the vault actions (6.3) — also for
 * the open vault (the gap noted in SPEC 6.2 is closed here).
 */
package com.kunotes.ui.vaults

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kunotes.settings.SavedVault
import com.kunotes.ui.dotline.DotlineIcons
import com.kunotes.ui.dotline.ListDivider
import com.kunotes.ui.dotline.ListGroup
import com.kunotes.ui.dotline.ListRow
import com.kunotes.ui.dotline.ListRowChevron
import com.kunotes.ui.dotline.MainTab
import com.kunotes.ui.dotline.ScreenTitle
import com.kunotes.ui.dotline.SectionLabel
import com.kunotes.ui.kuNotesViewModel
import com.kunotes.ui.navigation.MainTabs
import com.kunotes.ui.theme.Dotline
import com.kunotes.ui.welcome.OpenFolderFlow

/** What the vault screens can ask the app to show. */
class VaultNavigation(
    val onOpenFiles: () -> Unit,
    val onCloneFromGit: () -> Unit,
    val onSyncSettings: () -> Unit,
    /** Set or change the notes password of the open vault. */
    val onNotesPassword: (hasPassword: Boolean) -> Unit,
    /** No vault is left after removing one: back to the welcome screen. */
    val onNoVaultLeft: () -> Unit,
)

@Composable
fun VaultsScreen(
    onTabClick: (MainTab) -> Unit,
    navigation: VaultNavigation,
) {
    val viewModel = kuNotesViewModel { VaultsViewModel(it) }
    val vaults by viewModel.vaults.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    var actionsFor by remember { mutableStateOf<SavedVault?>(null) }
    var openFolder by remember { mutableStateOf(false) }

    MainTabs(selected = MainTab.Vaults, onTabClick = onTabClick) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            ScreenTitle(title = "VAULTS")
            Column(
                Modifier.padding(horizontal = Dotline.space.s4),
                verticalArrangement = Arrangement.spacedBy(Dotline.space.s2),
            ) {
                Text(
                    "A vault is any folder of markdown files. Your notes stay plain .md files on this phone.",
                    style = Dotline.type.body,
                    color = Dotline.colors.inkMuted,
                )
                if (message != null) Text(message.orEmpty(), style = Dotline.type.bodyS, color = Dotline.colors.accentText)
                SectionLabel("ON THIS PHONE", Modifier.padding(top = Dotline.space.s4))
                ListGroup {
                    vaults.forEachIndexed { index, item ->
                        if (index > 0) ListDivider()
                        ListRow(
                            title = item.vault.name,
                            subtitle = if (item.hasAccess) item.meta else "No access · tap to fix",
                            subtitleIsData = true,
                            leadingIcon = DotlineIcons.Vault,
                            minHeight = 72.dp,
                            onClick = {
                                if (viewModel.open(item.vault)) navigation.onOpenFiles() else openFolder = true
                            },
                            onMoreClick = { actionsFor = item.vault },
                            trailing = if (item.isOpen) ({ OpenTag() }) else null,
                        )
                    }
                }
                SectionLabel("ADD A VAULT", Modifier.padding(top = Dotline.space.s4))
                ListGroup(Modifier.padding(bottom = Dotline.space.s5)) {
                    ListRow(
                        "Open a folder",
                        leadingIcon = DotlineIcons.Folder,
                        onClick = { openFolder = true },
                        trailing = { ListRowChevron() },
                    )
                    ListDivider()
                    ListRow(
                        "Clone from git",
                        leadingIcon = DotlineIcons.GitBranch,
                        onClick = navigation.onCloneFromGit,
                        trailing = { ListRowChevron() },
                    )
                }
            }
        }
    }

    actionsFor?.let { vault ->
        VaultActionsSheet(
            vault = vault,
            item = vaults.firstOrNull { it.vault.id == vault.id },
            viewModel = viewModel,
            navigation = navigation,
            onDismiss = { actionsFor = null },
        )
    }
    OpenFolderFlow(
        open = openFolder,
        lostAccessTo = null,
        onDismiss = {
            openFolder = false
            viewModel.clearMessage()
        },
        onVaultOpened = navigation.onOpenFiles,
    )
}

/** "● OPEN" on the vault that's open now. */
@Composable
private fun OpenTag() {
    // Paired with the word OPEN, so the colour is never the only signal.
    Text("● OPEN", style = Dotline.type.label, color = Dotline.colors.positive)
}
