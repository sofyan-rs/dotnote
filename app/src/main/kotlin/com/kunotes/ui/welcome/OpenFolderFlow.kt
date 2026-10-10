/**
 * "Open a folder" as a reusable flow (SPEC 1.2): the explainer sheet, then Android's folder
 * picker, then the folder is saved as a vault and opened. Used by the welcome screen and the
 * vault screens. A cancelled pick leaves the sheet open; errors show inside the sheet.
 */
package com.kunotes.ui.welcome

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kunotes.ui.dotline.DotlineBottomSheet
import com.kunotes.ui.dotline.DotlineIcons
import com.kunotes.ui.dotline.PrimaryButton
import com.kunotes.ui.kuNotesViewModel
import com.kunotes.ui.theme.Dotline
import com.kunotes.ui.theme.Doto
import com.kunotes.ui.theme.SpaceMono

/**
 * Shows the explainer sheet while [open] is true. [lostAccessTo] switches the copy to
 * "open it again" for a vault whose folder permission is gone.
 */
@Composable
fun OpenFolderFlow(
    open: Boolean,
    lostAccessTo: String?,
    onDismiss: () -> Unit,
    onVaultOpened: () -> Unit,
) {
    val viewModel = kuNotesViewModel { WelcomeViewModel(it.vaultRegistry, it.vaultStore) }
    val error by viewModel.error.collectAsStateWithLifecycle()
    // Android's folder picker. It returns the folder's URI, or null if the user backed out.
    val pickFolder =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
            if (uri != null) {
                viewModel.addFolder(uri) {
                    onDismiss()
                    onVaultOpened()
                }
            }
        }
    if (open) {
        OpenFolderSheet(
            lostAccessTo = lostAccessTo,
            error = error,
            onChooseFolder = { pickFolder.launch(DOCUMENTS_FOLDER) },
            onDismiss = onDismiss,
        )
    }
}

/**
 * Where the folder picker starts: the phone's Documents folder (the explainer suggests
 * "Documents/Notes"). The picker falls back to its default if this folder doesn't exist.
 */
private val DOCUMENTS_FOLDER: Uri =
    android.provider.DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:Documents")

/** The explainer sheet before Android's folder picker opens (1.2). */
@Composable
private fun OpenFolderSheet(
    lostAccessTo: String?,
    error: String?,
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
        if (error != null) Text(error, style = Dotline.type.bodyS, color = Dotline.colors.accentText)
        PrimaryButton("Choose folder", onClick = onChooseFolder, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun Step(
    number: String,
    text: AnnotatedString,
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
