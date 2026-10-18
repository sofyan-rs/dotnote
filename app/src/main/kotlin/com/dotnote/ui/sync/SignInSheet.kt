/**
 * Sign-in sheet of the SYNC tab (the "Sign-in" row; built like the Rename
 * sheet): the HTTPS user name and personal access token for the vault's git remote.
 *
 * The saved token is never shown. Leaving the token field empty keeps the saved one.
 */
package com.dotnote.ui.sync

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import com.dotnote.ui.dotline.DotlineBottomSheet
import com.dotnote.ui.dotline.DotlinePasswordField
import com.dotnote.ui.dotline.DotlineTextField
import com.dotnote.ui.dotline.PrimaryButton
import com.dotnote.ui.dotline.SecondaryButton
import com.dotnote.ui.theme.Dotline
import com.dotnote.ui.theme.DotnoteTheme

/**
 * @param username the saved user name, filled in.
 * @param hasToken true if a token is saved (then the token field may stay empty).
 * @param onSave called with the user name and the new token ("" = keep the saved one).
 */
@Composable
fun SignInSheet(
    username: String,
    hasToken: Boolean,
    onSave: (username: String, token: String) -> Unit,
    onDismiss: () -> Unit,
) {
    DotlineBottomSheet(onDismissRequest = onDismiss, bottomPadding = Dotline.space.s4) {
        SignInContent(username, hasToken, onSave, onDismiss)
    }
}

@Composable
private fun SignInContent(
    username: String,
    hasToken: Boolean,
    onSave: (username: String, token: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val user = rememberTextFieldState(username)
    val token = rememberTextFieldState()
    var showToken by rememberSaveable { mutableStateOf(false) }
    val canSave = user.text.isNotBlank() && (hasToken || token.text.isNotBlank())
    val save = { if (canSave) onSave(user.text.toString(), token.text.toString()) }

    Column(verticalArrangement = Arrangement.spacedBy(Dotline.space.s4)) {
        Column(Modifier.padding(horizontal = Dotline.space.s1), verticalArrangement = Arrangement.spacedBy(Dotline.space.s1)) {
            Text("Sign in to the remote", style = Dotline.type.title, color = Dotline.colors.ink)
            Text(
                "Use a personal access token with read and write access to the repository. It stays encrypted on this phone.",
                style = Dotline.type.body,
                color = Dotline.colors.inkMuted,
            )
        }
        DotlineTextField(
            inSheet = true,
            state = user,
            label = "USERNAME",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next, autoCorrectEnabled = false),
        )
        DotlinePasswordField(
            inSheet = true,
            state = token,
            label = "ACCESS TOKEN",
            passwordVisible = showToken,
            onPasswordVisibleChange = { showToken = it },
            helperText = if (hasToken) "Leave empty to keep the saved token." else null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            onKeyboardAction = { save() },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Dotline.space.s2)) {
            SecondaryButton("Cancel", onClick = onDismiss, compact = true, modifier = Modifier.weight(1f))
            PrimaryButton("Save and sync", onClick = save, compact = true, enabled = canSave, modifier = Modifier.weight(1f))
        }
    }
}

@Preview(name = "Dark")
@Composable
private fun SignInDarkPreview() {
    DotnoteTheme(dark = true) {
        SheetPreviewFrame {
            SignInContent(username = "sofyan-rs", hasToken = true, onSave = {
                _,
                _,
                ->
            }, onDismiss = {})
        }
    }
}

@Preview(name = "Light")
@Composable
private fun SignInLightPreview() {
    DotnoteTheme(dark = false) {
        SheetPreviewFrame {
            SignInContent(username = "", hasToken = false, onSave = {
                _,
                _,
                ->
            }, onDismiss = {})
        }
    }
}

/** Previews can't show a real sheet; draw the content on the sheet's `surface` instead. */
@Composable
private fun SheetPreviewFrame(content: @Composable () -> Unit) {
    Column(Modifier.background(Dotline.colors.surface).padding(Dotline.space.s4)) { content() }
}
