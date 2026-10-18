/**
 * "Change notes password": current, new and confirm
 * fields. A wrong current password shows on the current field (`accent` border, `accentText`
 * message). Reached from Vaults → vault actions → Notes password.
 */
package com.dotnote.ui.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dotnote.ui.dotline.DotlineIcons
import com.dotnote.ui.dotline.DotlinePasswordField
import com.dotnote.ui.dotline.PrimaryButton
import com.dotnote.ui.dotnoteViewModel
import com.dotnote.ui.theme.Dotline
import com.dotnote.ui.theme.DotnoteTheme

/** @param onDone called after the password changed, and on back. */
@Composable
fun ChangePasswordScreen(onDone: () -> Unit) {
    val viewModel = dotnoteViewModel { ChangePasswordViewModel(it) }
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val current = rememberTextFieldState()
    val new = rememberTextFieldState()
    val confirm = rememberTextFieldState()

    SecureWindowEffect()
    LaunchedEffect(current, new, confirm) {
        snapshotFlow { listOf(current.text.toString(), new.text.toString(), confirm.text.toString()) }
            .collect { viewModel.clearErrors() }
    }

    ChangePasswordContent(
        ui = ui,
        current = current,
        new = new,
        confirm = confirm,
        onSubmit = { viewModel.submit(current.text.toString(), new.text.toString(), confirm.text.toString(), onDone) },
        onBack = onDone,
    )
}

/** The stateless layout of [ChangePasswordScreen]. */
@Composable
private fun ChangePasswordContent(
    ui: ChangePasswordUi,
    current: TextFieldState,
    new: TextFieldState,
    confirm: TextFieldState,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
) {
    var showPassword by rememberSaveable { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxSize()
            .background(Dotline.colors.bg)
            .safeDrawingPadding(),
    ) {
        LockTopBar(DotlineIcons.Back, "Back", onBack, caption = "VAULT · ${ui.vaultName.uppercase()}")
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = Dotline.space.s4, end = Dotline.space.s4, top = Dotline.space.s5, bottom = Dotline.space.s5),
            verticalArrangement = Arrangement.spacedBy(Dotline.space.s5),
        ) {
            LockHeading("Change notes password", lockedNotesText(ui.lockedNotes))
            Column(verticalArrangement = Arrangement.spacedBy(Dotline.space.s4)) {
                DotlinePasswordField(
                    state = current,
                    label = "CURRENT PASSWORD",
                    passwordVisible = showPassword,
                    onPasswordVisibleChange = null,
                    errorText = ui.currentError,
                    enabled = !ui.busy,
                )
                DotlinePasswordField(
                    state = new,
                    label = "NEW PASSWORD",
                    passwordVisible = showPassword,
                    onPasswordVisibleChange = { showPassword = it },
                    errorText = ui.newError,
                    enabled = !ui.busy,
                )
                DotlinePasswordField(
                    state = confirm,
                    label = "CONFIRM NEW PASSWORD",
                    passwordVisible = showPassword,
                    onPasswordVisibleChange = null,
                    trailing = { ConfirmStatus(new.text, confirm.text) },
                    errorText = ui.confirmError,
                    enabled = !ui.busy,
                    onKeyboardAction = { onSubmit() },
                )
                Text(
                    "Other devices synced with this vault ask for the new password next time.",
                    style = Dotline.type.bodyS,
                    color = Dotline.colors.inkMuted,
                )
            }
        }
        PrimaryButton(
            text = if (ui.busy) "Changing password…" else "Change password",
            onClick = onSubmit,
            enabled = !ui.busy && current.text.isNotEmpty() && new.text.isNotEmpty() && confirm.text.isNotEmpty(),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = Dotline.space.s4, end = Dotline.space.s4, bottom = Dotline.space.s6),
        )
    }
}

/** "Your 3 locked notes stay locked. …" with the right plural. */
private fun lockedNotesText(count: Int): String {
    val notes =
        when (count) {
            0 -> "Your locked notes stay locked."
            1 -> "Your 1 locked note stays locked."
            else -> "Your $count locked notes stay locked."
        }
    return "$notes Only the password that opens them changes."
}

// ---- Previews -------------------------------------------------------------------------------

@Composable
private fun ChangePasswordPreview(dark: Boolean) {
    DotnoteTheme(dark = dark) {
        ChangePasswordContent(
            ui =
                ChangePasswordUi(
                    vaultName = "Work",
                    lockedNotes = 3,
                    currentError = "That's not the current password. Try again.",
                ),
            current = rememberTextFieldState("wrongpass"),
            new = rememberTextFieldState("battery-staple-42"),
            confirm = rememberTextFieldState("battery-staple-42"),
            onSubmit = {},
            onBack = {},
        )
    }
}

@Preview(name = "Dark", widthDp = 390, heightDp = 844)
@Composable
private fun ChangePasswordDarkPreview() = ChangePasswordPreview(dark = true)

@Preview(name = "Light", widthDp = 390, heightDp = 844)
@Composable
private fun ChangePasswordLightPreview() = ChangePasswordPreview(dark = false)
