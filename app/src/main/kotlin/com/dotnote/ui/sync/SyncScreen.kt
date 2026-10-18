/**
 * The SYNC tab (SPEC 7.1, mockup `Git-Sync.dc.html`): the LAST SYNC widget with **Sync now**,
 * the REPOSITORY group (remote, sign-in, auto-sync) and a note about conflicts.
 *
 * Only vaults cloned from git (kept in Dotnote' storage) can sync (ARCHITECTURE §9.1). For a
 * folder picked on the phone the tab explains that and offers **Clone from git** instead.
 */
package com.dotnote.ui.sync

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dotnote.sync.SyncPhase
import com.dotnote.ui.dotline.DotlineIcons
import com.dotnote.ui.dotline.KuSwitch
import com.dotnote.ui.dotline.ListDivider
import com.dotnote.ui.dotline.ListGroup
import com.dotnote.ui.dotline.ListRow
import com.dotnote.ui.dotline.ListRowChevron
import com.dotnote.ui.dotline.MainTab
import com.dotnote.ui.dotline.PrimaryButton
import com.dotnote.ui.dotline.ScreenTitle
import com.dotnote.ui.dotline.SectionLabel
import com.dotnote.ui.dotnoteViewModel
import com.dotnote.ui.navigation.MainTabs
import com.dotnote.ui.theme.Dotline
import com.dotnote.ui.theme.DotnoteTheme

/** Git-Sync rows are 64 dp tall (mockup). */
private val SyncRowHeight = 64.dp

/** Screen entry: wires the ViewModel to [SyncContent] and shows the sign-in sheet. */
@Composable
fun SyncScreen(
    onTabClick: (MainTab) -> Unit,
    onCloneFromGit: () -> Unit,
) {
    val viewModel =
        dotnoteViewModel { SyncViewModel(it.syncController, it.vaultStore, it.settingsStore, it.tokenStore) }
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    var signInOpen by rememberSaveable { mutableStateOf(false) }

    MainTabs(selected = MainTab.Sync, onTabClick = onTabClick) {
        SyncContent(
            ui = ui,
            onSyncNow = viewModel::syncNow,
            onSignIn = { signInOpen = true },
            onAutoSyncChange = viewModel::setAutoSync,
            onCloneFromGit = onCloneFromGit,
        )
    }
    if (signInOpen) {
        SignInSheet(
            username = viewModel.savedUsername,
            hasToken = ui.hasSignIn,
            onSave = { username, token ->
                viewModel.saveSignIn(username, token)
                signInOpen = false
            },
            onDismiss = { signInOpen = false },
        )
    }
}

@Composable
private fun SyncContent(
    ui: SyncUi,
    onSyncNow: () -> Unit,
    onSignIn: () -> Unit,
    onAutoSyncChange: (Boolean) -> Unit,
    onCloneFromGit: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        ScreenTitle(title = "Sync")
        if (!ui.loaded) return@Column
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = Dotline.space.s4, end = Dotline.space.s4, top = Dotline.space.s6, bottom = Dotline.space.s6),
            verticalArrangement = Arrangement.spacedBy(Dotline.space.s5),
        ) {
            if (ui.connected) {
                LastSyncWidget(ui, onSyncNow)
                Repository(ui, onSignIn, onAutoSyncChange)
                Text(
                    "A note changed on two devices keeps both versions, so nothing is overwritten.",
                    style = Dotline.type.bodyS,
                    color = Dotline.colors.inkMuted,
                )
            } else {
                NotSyncable(ui.vaultName, onCloneFromGit)
            }
        }
    }
}

/** The `surface` r28 widget: LAST SYNC, status, hero time, summary, error, **Sync now**. */
@Composable
private fun LastSyncWidget(
    ui: SyncUi,
    onSyncNow: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Dotline.colors.surface, Dotline.radius.l)
            .padding(Dotline.space.s4),
        verticalArrangement = Arrangement.spacedBy(Dotline.space.s4),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("LAST SYNC", style = Dotline.type.label, color = Dotline.colors.inkMuted, modifier = Modifier.weight(1f))
            StatusChip(ui.phase)
        }
        // One TalkBack sentence for the hero ("Last sync 2 min ago") instead of two fragments.
        Row(
            Modifier.semantics(mergeDescendants = true) {
                contentDescription = if (ui.heroUnit == "NEVER") "Never synced" else "Last sync ${ui.heroNumber} ${ui.heroUnit.lowercase()}"
            },
            horizontalArrangement = Arrangement.spacedBy(Dotline.space.s2),
        ) {
            // alignByBaseline: the small unit sits on the same text line as the big number.
            // At weight 900 Doto's dots merge into bars for "--"; a lighter weight keeps them dots.
            val heroStyle =
                if (ui.heroUnit ==
                    "NEVER"
                ) {
                    Dotline.type.dotXl.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Normal)
                } else {
                    Dotline.type.dotXl
                }
            Text(ui.heroNumber, style = heroStyle, color = Dotline.colors.ink, modifier = Modifier.alignByBaseline())
            Text(ui.heroUnit, style = Dotline.type.data, color = Dotline.colors.inkMuted, modifier = Modifier.alignByBaseline())
        }
        if (ui.summary != null) Text(ui.summary, style = Dotline.type.data, color = Dotline.colors.inkMuted)
        if (ui.message != null) {
            Text(
                ui.message,
                style = Dotline.type.bodyS,
                color = if (ui.isError) Dotline.colors.accentText else Dotline.colors.ink,
                // Read out by TalkBack when it appears.
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        val syncing = ui.phase == SyncPhase.Syncing
        PrimaryButton(
            text = if (syncing) "Syncing…" else "Sync now",
            onClick = onSyncNow,
            icon = DotlineIcons.Sync,
            enabled = !syncing,
            compact = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** "✓ Up to date" in `positive`, "Sync failed" in `accentText`, otherwise muted. */
@Composable
private fun StatusChip(phase: SyncPhase) {
    val (text, color) =
        when (phase) {
            SyncPhase.UpToDate -> "Up to date" to Dotline.colors.positive
            SyncPhase.Syncing -> "Syncing…" to Dotline.colors.inkMuted
            SyncPhase.Error -> "Sync failed" to Dotline.colors.accentText
            SyncPhase.Idle -> "Not synced yet" to Dotline.colors.inkMuted
        }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        val icon =
            when (phase) {
                SyncPhase.UpToDate -> DotlineIcons.Check
                SyncPhase.Error -> DotlineIcons.Warning
                else -> null
            }
        if (icon != null) Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Text(text, style = Dotline.type.data, color = color)
    }
}

/** The REPOSITORY group: remote, sign-in, auto-sync switch. */
@Composable
private fun Repository(
    ui: SyncUi,
    onSignIn: () -> Unit,
    onAutoSyncChange: (Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Dotline.space.s2)) {
        SectionLabel("Repository", Modifier.padding(horizontal = Dotline.space.s1))
        ListGroup {
            ListRow(title = "Remote", subtitle = ui.remote, subtitleIsData = true, minHeight = SyncRowHeight)
            ListDivider()
            ListRow(
                title = "Sign-in",
                subtitle = if (ui.hasSignIn) "Access token" else "Not set",
                subtitleIsData = true,
                minHeight = SyncRowHeight,
                onClick = onSignIn,
                trailing = { ListRowChevron() },
            )
            ListDivider()
            // The whole row is the switch: it handles the tap, the switch only draws.
            ListRow(
                title = "Auto-sync",
                subtitle = "After edits and when you open the app",
                minHeight = SyncRowHeight,
                role = Role.Switch,
                onClick = { onAutoSyncChange(!ui.autoSync) },
                trailing = { KuSwitch(checked = ui.autoSync, onCheckedChange = null) },
            )
        }
    }
}

/** For a picked folder (or no vault): why it can't sync, and the way to get a synced vault. */
@Composable
private fun NotSyncable(
    vaultName: String?,
    onCloneFromGit: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Dotline.colors.surface, Dotline.radius.l)
            .padding(Dotline.space.s4),
        verticalArrangement = Arrangement.spacedBy(Dotline.space.s4),
    ) {
        Icon(DotlineIcons.GitBranch, contentDescription = null, tint = Dotline.colors.ink, modifier = Modifier.size(Dotline.size.icon))
        Column(verticalArrangement = Arrangement.spacedBy(Dotline.space.s1)) {
            Text(
                if (vaultName == null) "No vault is open" else "“$vaultName” doesn't sync",
                style = Dotline.type.title,
                color = Dotline.colors.ink,
            )
            Text(
                "Git sync works with vaults cloned from git, which Dotnote keeps in its own storage. " +
                    "A folder you picked on your phone can't sync. To sync these notes, push them to a git " +
                    "repository from a computer, then clone it here.",
                style = Dotline.type.body,
                color = Dotline.colors.inkMuted,
            )
        }
        PrimaryButton(
            "Clone from git",
            onClick = onCloneFromGit,
            icon = DotlineIcons.GitBranch,
            compact = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ---- Previews ----

private val previewUi =
    SyncUi(
        loaded = true,
        vaultName = "Notes",
        connected = true,
        phase = SyncPhase.UpToDate,
        heroNumber = "2",
        heroUnit = "MIN AGO",
        summary = "main · 3 files pushed · 0 pulled",
        remote = "github.com/sofyan-rs/notes.git",
        autoSync = true,
        hasSignIn = true,
    )

@Composable
private fun PreviewContent(ui: SyncUi) {
    MainTabs(selected = MainTab.Sync, onTabClick = {}) {
        SyncContent(ui = ui, onSyncNow = {}, onSignIn = {}, onAutoSyncChange = {}, onCloneFromGit = {})
    }
}

@Preview(name = "Dark", heightDp = 844, widthDp = 390)
@Composable
private fun SyncDarkPreview() {
    DotnoteTheme(dark = true) { PreviewContent(previewUi) }
}

@Preview(name = "Light", heightDp = 844, widthDp = 390)
@Composable
private fun SyncLightPreview() {
    DotnoteTheme(dark = false) { PreviewContent(previewUi) }
}

@Preview(name = "Error, dark", heightDp = 844, widthDp = 390)
@Composable
private fun SyncErrorPreview() {
    DotnoteTheme(dark = true) {
        PreviewContent(
            previewUi.copy(
                phase = SyncPhase.Error,
                message = "Git couldn't sign in to the remote. Check the user name and access token, then sync again.",
                isError = true,
            ),
        )
    }
}

@Preview(name = "Picked folder, light", heightDp = 844, widthDp = 390)
@Composable
private fun SyncNotSyncablePreview() {
    DotnoteTheme(dark = false) { PreviewContent(SyncUi(loaded = true, vaultName = "Personal")) }
}
