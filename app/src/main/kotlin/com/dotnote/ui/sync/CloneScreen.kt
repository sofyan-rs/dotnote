/**
 * Clone from git (designed in the Dotline style
 * of Move-To and the sheets): repository address, sign-in, folder name and a **Clone** button
 * with a dot progress line. Errors (sign-in, not found, no network) show under the form.
 *
 * Reached from Welcome (1.1) "Clone from git", the Vaults tab and the SYNC tab.
 */
package com.dotnote.ui.sync

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dotnote.ui.dotline.DotMeter
import com.dotnote.ui.dotline.DotlineIcons
import com.dotnote.ui.dotline.DotlinePasswordField
import com.dotnote.ui.dotline.DotlineTextField
import com.dotnote.ui.dotline.PrimaryButton
import com.dotnote.ui.dotline.ScreenTitleAction
import com.dotnote.ui.dotnoteViewModel
import com.dotnote.ui.theme.Dotline
import com.dotnote.ui.theme.DotnoteTheme
import kotlinx.coroutines.delay
import java.io.File

/** Dots in the progress line while cloning. */
private const val PROGRESS_DOTS = 24

/** Screen entry: wires the [CloneViewModel] to [CloneContent]. */
@Composable
fun CloneScreen(
    onCloned: () -> Unit,
    onBack: () -> Unit,
) {
    val viewModel =
        dotnoteViewModel { container ->
            CloneViewModel(
                vaultsDir = File(container.appFilesDir, "vaults"),
                registry = container.vaultRegistry,
                vaultStore = container.vaultStore,
                settingsStore = container.settingsStore,
                tokenStore = container.tokenStore,
            )
        }
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val url = rememberTextFieldState()
    val username = rememberTextFieldState()
    val token = rememberTextFieldState()
    val folderName = rememberTextFieldState()

    // Suggest the folder name from the address, until the user types their own name.
    LaunchedEffect(Unit) {
        var suggested = ""
        snapshotFlow { url.text.toString() }.collect { address ->
            if (folderName.text.toString() == suggested) {
                suggested = if (address.isBlank()) "" else defaultFolderName(address)
                folderName.setTextAndPlaceCursorAtEnd(suggested)
            }
        }
    }

    CloneContent(
        ui = ui,
        url = url,
        username = username,
        token = token,
        folderName = folderName,
        onClone = {
            val form = CloneForm(url.text.toString(), username.text.toString(), token.text.toString(), folderName.text.toString())
            viewModel.clone(form, onCloned)
        },
        onBack = onBack,
    )
}

@Composable
private fun CloneContent(
    ui: CloneUi,
    url: TextFieldState,
    username: TextFieldState,
    token: TextFieldState,
    folderName: TextFieldState,
    onClone: () -> Unit,
    onBack: () -> Unit,
) {
    var showToken by rememberSaveable { mutableStateOf(false) }
    val enabled = !ui.cloning
    Column(
        Modifier
            .fillMaxSize()
            .background(Dotline.colors.bg)
            // safeDrawing includes the keyboard, so the Clone button stays above it.
            .safeDrawingPadding(),
    ) {
        // Header like Move-To: back button + mono label.
        Row(Modifier.padding(horizontal = Dotline.space.s1), verticalAlignment = Alignment.CenterVertically) {
            ScreenTitleAction(DotlineIcons.Back, "Back", onBack)
            Text("CLONE FROM GIT", style = Dotline.type.label, color = Dotline.colors.inkMuted)
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = Dotline.space.s4, end = Dotline.space.s4, top = Dotline.space.s5, bottom = Dotline.space.s4),
            verticalArrangement = Arrangement.spacedBy(Dotline.space.s4),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Dotline.space.s1)) {
                Text("Clone a repository", style = Dotline.type.title, color = Dotline.colors.ink)
                Text(
                    "Dotnote copies it into its own storage and keeps it in sync both ways.",
                    style = Dotline.type.bodyS,
                    color = Dotline.colors.inkMuted,
                )
            }
            DotlineTextField(
                state = url,
                label = "REPOSITORY URL",
                placeholder = "https://github.com/you/notes.git",
                enabled = enabled,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next, autoCorrectEnabled = false),
            )
            DotlineTextField(
                state = username,
                label = "USERNAME",
                enabled = enabled,
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next,
                        autoCorrectEnabled = false,
                    ),
            )
            DotlinePasswordField(
                state = token,
                label = "ACCESS TOKEN",
                passwordVisible = showToken,
                onPasswordVisibleChange = { showToken = it },
                enabled = enabled,
                helperText = "A personal access token with read and write access. Leave both empty for a public repository.",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
            )
            DotlineTextField(
                state = folderName,
                label = "FOLDER NAME",
                enabled = enabled,
                helperText = "Shown as App storage/${folderName.text.ifEmpty { "…" }} in your vaults.",
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                onKeyboardAction = { if (enabled) onClone() },
            )
        }
        Column(
            Modifier.padding(start = Dotline.space.s4, end = Dotline.space.s4, bottom = Dotline.space.s4),
            verticalArrangement = Arrangement.spacedBy(Dotline.space.s3),
        ) {
            if (ui.cloning) CloneProgress()
            if (ui.error != null) {
                Text(
                    ui.error,
                    style = Dotline.type.bodyS,
                    color = Dotline.colors.accentText,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            PrimaryButton(
                text = if (ui.cloning) "Cloning…" else "Clone",
                onClick = onClone,
                icon = DotlineIcons.GitBranch,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** A line of dots that fills up and starts over while git works (git doesn't tell us how far it is). */
@Composable
private fun CloneProgress() {
    var lit by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(PROGRESS_STEP_MILLIS)
            lit = (lit + 1) % (PROGRESS_DOTS + 1)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(Dotline.space.s2)) {
        DotMeter(count = PROGRESS_DOTS, lit = lit, contentDescription = "Cloning")
        Text("Copying the notes. Big repositories can take a minute.", style = Dotline.type.bodyS, color = Dotline.colors.inkMuted)
    }
}

private const val PROGRESS_STEP_MILLIS = 120L

@Composable
private fun ClonePreview(ui: CloneUi) {
    CloneContent(
        ui = ui,
        url = rememberTextFieldState("https://github.com/sofyan-rs/notes.git"),
        username = rememberTextFieldState("sofyan-rs"),
        token = rememberTextFieldState("ghp_example"),
        folderName = rememberTextFieldState("notes"),
        onClone = {},
        onBack = {},
    )
}

@Preview(name = "Dark", heightDp = 844, widthDp = 390)
@Composable
private fun CloneDarkPreview() {
    DotnoteTheme(dark = true) { ClonePreview(CloneUi(error = "The remote repository wasn't found. Check the address.")) }
}

@Preview(name = "Light", heightDp = 844, widthDp = 390)
@Composable
private fun CloneLightPreview() {
    DotnoteTheme(dark = false) { ClonePreview(CloneUi(cloning = true)) }
}
