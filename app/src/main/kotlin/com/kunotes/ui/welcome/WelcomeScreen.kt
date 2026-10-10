/**
 * First launch (SPEC 1.1) and the open-folder explainer sheet (1.2).
 *
 * "Open a folder" first shows a sheet that explains Android's folder picker, then
 * "Choose folder" opens the picker. A cancelled pick just leaves the sheet open.
 */
package com.kunotes.ui.welcome

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kunotes.ui.dotline.DotArt
import com.kunotes.ui.dotline.DotArtPatterns
import com.kunotes.ui.dotline.DotlineBottomSheet
import com.kunotes.ui.dotline.DotlineIcons
import com.kunotes.ui.dotline.PrimaryButton
import com.kunotes.ui.dotline.SecondaryButton
import com.kunotes.ui.kuNotesViewModel
import com.kunotes.ui.theme.Dotline
import com.kunotes.ui.theme.Doto
import com.kunotes.ui.theme.KuNotesTheme
import com.kunotes.ui.theme.SpaceMono

/** Screen entry: the welcome content plus the open-folder flow (sheet → picker). */
@Composable
fun WelcomeScreen(
    lostAccessTo: String?,
    onVaultOpened: () -> Unit,
    onCloneFromGit: () -> Unit,
) {
    // `rememberSaveable` keeps the sheet open across rotation.
    var sheetOpen by rememberSaveable { mutableStateOf(lostAccessTo != null) }
    WelcomeContent(onOpenFolder = { sheetOpen = true }, onCloneFromGit = onCloneFromGit)
    OpenFolderFlow(
        open = sheetOpen,
        lostAccessTo = lostAccessTo,
        onDismiss = { sheetOpen = false },
        onVaultOpened = onVaultOpened,
    )
}

@Composable
private fun WelcomeContent(
    onOpenFolder: () -> Unit,
    onCloneFromGit: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Dotline.colors.bg)
            .safeDrawingPadding(),
    ) {
        Column(
            Modifier
                .weight(1f)
                .padding(start = Dotline.space.s5, end = Dotline.space.s5, top = 64.dp),
            verticalArrangement = Arrangement.spacedBy(Dotline.space.s6),
        ) {
            DotArt(DotArtPatterns.NotePage, contentDescription = null)
            Column(verticalArrangement = Arrangement.spacedBy(Dotline.space.s3)) {
                Text("KUNOTES", style = Dotline.type.dotM, color = Dotline.colors.ink)
                Column(verticalArrangement = Arrangement.spacedBy(Dotline.space.s1)) {
                    Text("Markdown notes in a folder you own.", style = Dotline.type.title, color = Dotline.colors.ink)
                    Text(
                        "No account, no database. Pick a folder and every note stays a plain .md file you can open anywhere.",
                        style = Dotline.type.body,
                        color = Dotline.colors.inkMuted,
                    )
                }
            }
        }
        Column(
            Modifier.padding(start = Dotline.space.s4, end = Dotline.space.s4, bottom = Dotline.space.s6),
            verticalArrangement = Arrangement.spacedBy(Dotline.space.s2),
        ) {
            PrimaryButton("Open a folder", onClick = onOpenFolder, icon = DotlineIcons.Folder, modifier = Modifier.fillMaxWidth())
            SecondaryButton("Clone from git", onClick = onCloneFromGit, icon = DotlineIcons.GitBranch, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Preview(name = "Dark")
@Composable
private fun WelcomeDarkPreview() {
    KuNotesTheme(dark = true) { WelcomeContent(onOpenFolder = {}, onCloneFromGit = {}) }
}

@Preview(name = "Light")
@Composable
private fun WelcomeLightPreview() {
    KuNotesTheme(dark = false) { WelcomeContent(onOpenFolder = {}, onCloneFromGit = {}) }
}
