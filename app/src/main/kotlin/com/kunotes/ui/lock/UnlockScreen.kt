/**
 * "This note is locked" (SPEC 5.2, mockup `Unlock.dc.html`): shown when opening a `.md.age`
 * note while the vault is locked, and before "Lock note" when the vault is locked.
 *
 * DotArt padlock, the 5-minute auto-lock explanation, VAULT PASSWORD, fingerprint (button when
 * it's on, "next time" checkbox when it's off), **Unlock** and **Not now**.
 */
package com.kunotes.ui.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kunotes.ui.dotline.DotArt
import com.kunotes.ui.dotline.DotArtPatterns
import com.kunotes.ui.dotline.DotlineCheckboxRow
import com.kunotes.ui.dotline.DotlineIcons
import com.kunotes.ui.dotline.DotlinePasswordField
import com.kunotes.ui.dotline.PrimaryButton
import com.kunotes.ui.dotline.ScreenTitleAction
import com.kunotes.ui.dotline.SecondaryButton
import com.kunotes.ui.dotline.pressScale
import com.kunotes.ui.kuNotesViewModel
import com.kunotes.ui.theme.Dotline
import com.kunotes.ui.theme.KuNotesTheme

/**
 * @param path the note (vault-relative).
 * @param toggleLock true when the user chose "Lock note" (or "Unlock note"): after unlocking the
 *   vault, the note is locked/unlocked and [onDone] goes back. False: [onDone] opens the editor.
 * @param onDone called once the vault is unlocked (and the toggle is done).
 * @param onCancel "Not now" and back.
 */
@Composable
fun UnlockScreen(
    path: String,
    toggleLock: Boolean,
    onDone: () -> Unit,
    onCancel: () -> Unit,
) {
    val viewModel = kuNotesViewModel(key = "unlock-$path-$toggleLock") { UnlockViewModel(it, path, toggleLock) }
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val password = rememberTextFieldState()
    var rememberFingerprint by rememberSaveable { mutableStateOf(false) }
    val authenticator = rememberBiometricAuthenticator()

    SecureWindowEffect()
    LaunchedEffect(password) { snapshotFlow { password.text.toString() }.collect { viewModel.clearError() } }
    // Fingerprint on: open the dialog right away, like other apps do. Once per screen.
    LaunchedEffect(ui.fingerprintEnabled) {
        if (authenticator != null && viewModel.shouldAutoPrompt()) viewModel.unlockWithFingerprint(authenticator, onDone)
    }

    UnlockContent(
        ui = ui,
        password = password,
        rememberFingerprint = rememberFingerprint,
        onRememberFingerprintChange = { rememberFingerprint = it },
        onUnlock = {
            viewModel.unlockWithPassword(password.text.toString(), rememberFingerprint, authenticator, onDone)
        },
        onFingerprint = { authenticator?.let { viewModel.unlockWithFingerprint(it, onDone) } },
        onCancel = onCancel,
    )
}

/** The stateless layout of [UnlockScreen]. */
@Composable
private fun UnlockContent(
    ui: UnlockUi,
    password: TextFieldState,
    rememberFingerprint: Boolean,
    onRememberFingerprintChange: (Boolean) -> Unit,
    onUnlock: () -> Unit,
    onFingerprint: () -> Unit,
    onCancel: () -> Unit,
) {
    var showPassword by rememberSaveable { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxSize()
            .background(Dotline.colors.bg)
            .safeDrawingPadding(),
    ) {
        Row(
            Modifier.padding(horizontal = Dotline.space.s1),
            horizontalArrangement = Arrangement.spacedBy(Dotline.space.s1),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ScreenTitleAction(DotlineIcons.Back, "Back to files", onCancel)
            Column(Modifier.weight(1f)) {
                Text(ui.title, style = Dotline.type.headline, color = Dotline.colors.ink, maxLines = 1)
                Text(ui.fileName.uppercase(), style = Dotline.type.label, color = Dotline.colors.inkFaint, maxLines = 1)
            }
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = Dotline.space.s5, end = Dotline.space.s5, top = 64.dp, bottom = Dotline.space.s5),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dotline.space.s6),
        ) {
            DotArt(DotArtPatterns.Lock, contentDescription = "Locked")
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Dotline.space.s2)) {
                Text(
                    when {
                        ui.removesLock -> "Remove the lock?"
                        ui.noteIsLocked -> "This note is locked"
                        else -> "Unlock this vault's notes"
                    },
                    style = Dotline.type.title,
                    color = Dotline.colors.ink,
                    textAlign = TextAlign.Center,
                )
                Text(
                    if (ui.removesLock) {
                        "Enter the vault password. “${ui.title}” is then saved as a plain .md file anyone can read, " +
                            "and the encrypted copy goes to the trash."
                    } else if (ui.noteIsLocked) {
                        "It's encrypted on disk. Enter the vault password to open it. It locks again after 5 minutes."
                    } else {
                        "Enter the vault password to lock “${ui.title}”. Locked notes lock again after 5 minutes."
                    },
                    style = Dotline.type.body,
                    color = Dotline.colors.inkMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 300.dp),
                )
            }
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Dotline.space.s2)) {
                DotlinePasswordField(
                    state = password,
                    label = "VAULT PASSWORD",
                    passwordVisible = showPassword,
                    onPasswordVisibleChange = { showPassword = it },
                    errorText = ui.error,
                    enabled = !ui.busy,
                    onKeyboardAction = { onUnlock() },
                )
                if (ui.fingerprintEnabled) {
                    SecondaryButton(
                        "Unlock with fingerprint",
                        onClick = onFingerprint,
                        enabled = !ui.busy,
                        compact = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    DotlineCheckboxRow(
                        text = "Unlock with fingerprint next time",
                        checked = rememberFingerprint && ui.fingerprintUnavailable == null,
                        onCheckedChange = onRememberFingerprintChange,
                        supportingText = ui.fingerprintUnavailable,
                        enabled = ui.fingerprintUnavailable == null && !ui.busy,
                    )
                }
            }
        }
        Column(
            Modifier.padding(start = Dotline.space.s4, end = Dotline.space.s4, bottom = Dotline.space.s6),
            verticalArrangement = Arrangement.spacedBy(Dotline.space.s2),
        ) {
            PrimaryButton(
                text =
                    when {
                        ui.busy -> "Unlocking…"
                        ui.removesLock -> "Remove lock"
                        else -> "Unlock"
                    },
                onClick = onUnlock,
                enabled = !ui.busy && password.text.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            )
            NotNowButton(onCancel)
        }
    }
}

/** "Not now": a plain 48 dp text button in `inkMuted` (mockup), no border or fill. */
@Composable
private fun NotNowButton(onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        Modifier
            .fillMaxWidth()
            .height(Dotline.size.touchTarget)
            .pressScale(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text("Not now", style = Dotline.type.body, color = Dotline.colors.inkMuted)
    }
}

// ---- Previews -------------------------------------------------------------------------------

@Composable
private fun UnlockPreview(dark: Boolean) {
    KuNotesTheme(dark = dark) {
        UnlockContent(
            ui = UnlockUi(title = "Server Keys", fileName = "Server Keys.md.age"),
            password = rememberTextFieldState("hunter2hunter"),
            rememberFingerprint = false,
            onRememberFingerprintChange = {},
            onUnlock = {},
            onFingerprint = {},
            onCancel = {},
        )
    }
}

@Preview(name = "Dark", widthDp = 390, heightDp = 844)
@Composable
private fun UnlockDarkPreview() = UnlockPreview(dark = true)

@Preview(name = "Light", widthDp = 390, heightDp = 844)
@Composable
private fun UnlockLightPreview() = UnlockPreview(dark = false)
