/**
 * "Set a notes password" (SPEC 5.1, mockup `Set-Password.dc.html`). Shown on the first
 * "Lock note" in a vault without a password (and from Vaults → Notes password).
 *
 * Password field with a strength meter, confirm field with MATCH, the fingerprint checkbox,
 * the "There's no reset." card and **Set password and lock**.
 */
package com.dotnote.ui.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dotnote.core.PasswordStrength
import com.dotnote.ui.dotline.DotMeter
import com.dotnote.ui.dotline.DotlineCheckboxRow
import com.dotnote.ui.dotline.DotlineIcons
import com.dotnote.ui.dotline.DotlinePasswordField
import com.dotnote.ui.dotline.PrimaryButton
import com.dotnote.ui.dotnoteViewModel
import com.dotnote.ui.theme.Dotline
import com.dotnote.ui.theme.DotnoteTheme

/**
 * @param notePath the note to lock once the password is set, or null.
 * @param onDone called after the password is set (or on Cancel): go back.
 */
@Composable
fun SetPasswordScreen(
    notePath: String?,
    onDone: () -> Unit,
) {
    val viewModel = dotnoteViewModel(key = "set-password-$notePath") { SetPasswordViewModel(it, notePath) }
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val password = rememberTextFieldState()
    val confirm = rememberTextFieldState()
    // rememberSaveable: the tick survives rotation. On by default when the phone supports it (mockup).
    var fingerprint by rememberSaveable { mutableStateOf(ui.fingerprintUnavailable == null) }
    val authenticator = rememberBiometricAuthenticator()

    SecureWindowEffect()
    // snapshotFlow watches the field text; typing clears an old error.
    LaunchedEffect(password, confirm) {
        snapshotFlow { password.text.toString() + "\u0000" + confirm.text.toString() }.collect { viewModel.clearError() }
    }

    SetPasswordContent(
        ui = ui,
        password = password,
        confirm = confirm,
        fingerprint = fingerprint,
        onFingerprintChange = { fingerprint = it },
        onSubmit = {
            viewModel.submit(password.text.toString(), confirm.text.toString(), fingerprint, authenticator, onDone)
        },
        onCancel = onDone,
    )
}

/** The stateless layout of [SetPasswordScreen]. */
@Composable
private fun SetPasswordContent(
    ui: SetPasswordUi,
    password: TextFieldState,
    confirm: TextFieldState,
    fingerprint: Boolean,
    onFingerprintChange: (Boolean) -> Unit,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
) {
    var showPassword by rememberSaveable { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val rating = PasswordStrength.rate(password.text.toString())
    val locksNote = ui.noteTitle != null

    LaunchedEffect(Unit) { focus.requestFocus() }

    Column(
        Modifier
            .fillMaxSize()
            .background(Dotline.colors.bg)
            .safeDrawingPadding(),
    ) {
        LockTopBar(DotlineIcons.Close, "Cancel", onCancel, caption = "VAULT · ${ui.vaultName.uppercase()}")
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = Dotline.space.s4, end = Dotline.space.s4, top = Dotline.space.s5, bottom = Dotline.space.s5),
            verticalArrangement = Arrangement.spacedBy(Dotline.space.s5),
        ) {
            val forNote = ui.noteTitle?.let { " You'll need it to lock “$it”." }.orEmpty()
            LockHeading("Set a notes password", "One password for every locked note in this vault.$forNote")
            Column(verticalArrangement = Arrangement.spacedBy(Dotline.space.s4)) {
                Column(verticalArrangement = Arrangement.spacedBy(Dotline.space.s2)) {
                    DotlinePasswordField(
                        state = password,
                        label = "PASSWORD",
                        passwordVisible = showPassword,
                        onPasswordVisibleChange = { showPassword = it },
                        errorText = ui.error,
                        enabled = !ui.busy,
                        textFieldModifier = Modifier.focusRequester(focus),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(Dotline.space.s3), verticalAlignment = Alignment.CenterVertically) {
                        DotMeter(count = PasswordStrength.METER_DOTS, lit = rating.level, contentDescription = "Password strength")
                        Text(rating.label, style = Dotline.type.label, color = Dotline.colors.inkMuted)
                    }
                }
                DotlinePasswordField(
                    state = confirm,
                    label = "CONFIRM PASSWORD",
                    passwordVisible = showPassword,
                    onPasswordVisibleChange = null,
                    trailing = { ConfirmStatus(password.text, confirm.text) },
                    enabled = !ui.busy,
                    onKeyboardAction = { onSubmit() },
                )
                DotlineCheckboxRow(
                    text = "Unlock with fingerprint",
                    checked = fingerprint && ui.fingerprintUnavailable == null,
                    onCheckedChange = onFingerprintChange,
                    supportingText = ui.fingerprintUnavailable,
                    enabled = ui.fingerprintUnavailable == null && !ui.busy,
                )
            }
            NoResetWarning()
        }
        PrimaryButton(
            text =
                when {
                    ui.busy -> "Setting password…"
                    locksNote -> "Set password and lock"
                    else -> "Set password"
                },
            onClick = onSubmit,
            icon = DotlineIcons.Lock,
            enabled = !ui.busy && password.text.isNotEmpty() && confirm.text.isNotEmpty(),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = Dotline.space.s4, end = Dotline.space.s4, bottom = Dotline.space.s6),
        )
    }
}

// ---- Previews -------------------------------------------------------------------------------

@Composable
private fun SetPasswordPreview(dark: Boolean) {
    DotnoteTheme(dark = dark) {
        SetPasswordContent(
            ui = SetPasswordUi(vaultName = "Personal", noteTitle = "Example"),
            password = rememberTextFieldState("correct-horse-87"),
            confirm = rememberTextFieldState("correct-horse-87"),
            fingerprint = true,
            onFingerprintChange = {},
            onSubmit = {},
            onCancel = {},
        )
    }
}

@Preview(name = "Dark", widthDp = 390, heightDp = 844)
@Composable
private fun SetPasswordDarkPreview() = SetPasswordPreview(dark = true)

@Preview(name = "Light", widthDp = 390, heightDp = 844)
@Composable
private fun SetPasswordLightPreview() = SetPasswordPreview(dark = false)
