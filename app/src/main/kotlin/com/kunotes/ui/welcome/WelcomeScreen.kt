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

/** Screen entry: wires the ViewModel and the folder picker to [WelcomeContent]. */
@Composable
fun WelcomeScreen(
    lostAccessTo: String?,
    onVaultOpened: () -> Unit,
    onCloneFromGit: () -> Unit,
) {
    val viewModel = kuNotesViewModel { WelcomeViewModel(it.vaultRegistry, it.vaultStore) }
    val error by viewModel.error.collectAsStateWithLifecycle()
    // `rememberSaveable` keeps the sheet open across rotation.
    var sheetOpen by rememberSaveable { mutableStateOf(lostAccessTo != null) }

    // Android's folder picker. It returns the folder's URI, or null if the user backed out.
    val pickFolder =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
            if (uri != null) viewModel.addFolder(uri, onVaultOpened)
        }

    WelcomeContent(
        error = error,
        onOpenFolder = { sheetOpen = true },
        onCloneFromGit = onCloneFromGit,
    )
    if (sheetOpen) {
        OpenFolderSheet(
            lostAccessTo = lostAccessTo,
            onChooseFolder = { pickFolder.launch(DOCUMENTS_FOLDER) },
            onDismiss = { sheetOpen = false },
        )
    }
}

/**
 * Where the folder picker starts: the phone's Documents folder (the explainer suggests
 * "Documents/Notes"). The picker falls back to its default if this folder doesn't exist.
 */
private val DOCUMENTS_FOLDER: Uri =
    android.provider.DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:Documents")

@Composable
private fun WelcomeContent(
    error: String?,
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
            if (error != null) Text(error, style = Dotline.type.bodyS, color = Dotline.colors.accentText)
            PrimaryButton("Open a folder", onClick = onOpenFolder, icon = DotlineIcons.Folder, modifier = Modifier.fillMaxWidth())
            SecondaryButton("Clone from git", onClick = onCloneFromGit, icon = DotlineIcons.GitBranch, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** The explainer sheet before Android's folder picker opens (1.2). */
@Composable
private fun OpenFolderSheet(
    lostAccessTo: String?,
    onChooseFolder: () -> Unit,
    onDismiss: () -> Unit,
) {
    DotlineBottomSheet(onDismissRequest = onDismiss, spacing = 20.dp) {
        Column(Modifier.padding(horizontal = Dotline.space.s1), verticalArrangement = Arrangement.spacedBy(Dotline.space.s1)) {
            Text(
                if (lostAccessTo == null) "Pick a folder for your vault" else "Open “$lostAccessTo” again",
                style = Dotline.type.title,
                color = Dotline.colors.ink,
            )
            Text(
                if (lostAccessTo == null) {
                    "Android's folder picker opens next."
                } else {
                    "KuNotes lost access to this folder (it was moved, or access was turned off). Pick it again."
                },
                style = Dotline.type.body,
                color = Dotline.colors.inkMuted,
            )
        }
        Column(Modifier.padding(horizontal = Dotline.space.s1), verticalArrangement = Arrangement.spacedBy(Dotline.space.s3)) {
            Step(
                number = "1",
                text =
                    buildAnnotatedString {
                        append("Choose a folder with your notes, or create a new one like ")
                        withStyle(SpanStyle(fontFamily = SpaceMono, fontSize = 13.sp)) { append("Documents/Notes") }
                    },
            )
            Step(
                number = "2",
                text =
                    buildAnnotatedString {
                        append("Tap ")
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append("Use this folder") }
                        append(", then ")
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append("Allow") }
                        append(".")
                    },
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .background(Dotline.colors.bg, Dotline.radius.m)
                .padding(Dotline.space.s4),
            horizontalArrangement = Arrangement.spacedBy(Dotline.space.s3),
        ) {
            Icon(DotlineIcons.Lock, contentDescription = null, tint = Dotline.colors.inkMuted, modifier = Modifier.size(20.dp))
            Text(
                "KuNotes can only read and write inside the folder you pick. Nothing else on your phone.",
                style = Dotline.type.bodyS,
                color = Dotline.colors.inkMuted,
            )
        }
        PrimaryButton("Choose folder", onClick = onChooseFolder, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun Step(
    number: String,
    text: androidx.compose.ui.text.AnnotatedString,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(Dotline.space.s4), verticalAlignment = Alignment.Top) {
        // Numbers in Doto: display type at 24 sp, the smallest size Doto may be used at.
        Text(
            number,
            style = Dotline.type.dotM.copy(fontFamily = Doto, fontSize = 24.sp, lineHeight = 24.sp, fontWeight = FontWeight.Black),
            color = Dotline.colors.ink,
            modifier = Modifier.width(20.dp),
        )
        Text(text, style = Dotline.type.body, color = Dotline.colors.ink)
    }
}

@Preview(name = "Dark")
@Composable
private fun WelcomeDarkPreview() {
    KuNotesTheme(dark = true) { WelcomeContent(error = null, onOpenFolder = {}, onCloneFromGit = {}) }
}

@Preview(name = "Light")
@Composable
private fun WelcomeLightPreview() {
    KuNotesTheme(dark = false) { WelcomeContent(error = null, onOpenFolder = {}, onCloneFromGit = {}) }
}
