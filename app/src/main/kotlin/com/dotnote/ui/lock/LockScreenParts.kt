/**
 * Small pieces shared by the three password screens: Set password (5.1), Locked note (5.2) and
 * Change password (5.3). Each screen file has the layout; these keep the repeated bits equal.
 */
package com.dotnote.ui.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.dotnote.ui.dotline.DotlineIcons
import com.dotnote.ui.dotline.DotlineTextFieldStatus
import com.dotnote.ui.dotline.ScreenTitleAction
import com.dotnote.ui.theme.Dotline

/** Top bar: a close/back button and a `label` caption ("VAULT · PERSONAL"). */
@Composable
internal fun LockTopBar(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    caption: String,
) {
    Row(
        Modifier.padding(horizontal = Dotline.space.s1),
        horizontalArrangement = Arrangement.spacedBy(Dotline.space.s1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ScreenTitleAction(icon, contentDescription, onClick)
        Text(caption, style = Dotline.type.label, color = Dotline.colors.inkMuted)
    }
}

/** The screen's title (`title` style) with a muted paragraph under it. */
@Composable
internal fun LockHeading(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Dotline.space.s2)) {
        Text(title, style = Dotline.type.title, color = Dotline.colors.ink)
        Text(body, style = Dotline.type.body, color = Dotline.colors.inkMuted)
    }
}

/**
 * The status at the end of a "confirm password" field: MATCH (green, with a check) once both
 * are equal, NO MATCH while they differ, nothing while the confirm field is empty.
 */
@Composable
internal fun ConfirmStatus(
    password: CharSequence,
    confirm: CharSequence,
) {
    if (confirm.isEmpty()) return
    if (password.toString() == confirm.toString()) {
        DotlineTextFieldStatus("Match", positive = true)
    } else {
        DotlineTextFieldStatus("No match")
    }
}

/** The "There's no reset." card on Set password (5.1). The warning icon is the screen's red. */
@Composable
internal fun NoResetWarning(modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .background(Dotline.colors.surface, Dotline.radius.m)
            .padding(Dotline.space.s4),
        horizontalArrangement = Arrangement.spacedBy(Dotline.space.s3),
    ) {
        Icon(
            DotlineIcons.Warning,
            contentDescription = null,
            tint = Dotline.colors.accentText,
            // 20 dp icon, 1 dp down to sit on the first line (mockup: margin-top 1px).
            modifier = Modifier.padding(top = 1.dp).size(20.dp),
        )
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = Dotline.colors.ink)) { append("There's no reset.") }
                append(" If you forget this password, locked notes can't be opened, here or on your computer.")
            },
            style = Dotline.type.bodyS,
            color = Dotline.colors.inkMuted,
        )
    }
}

/** Passwords are counted in characters (code points), like `Lock.MIN_PASSWORD_LEN`. */
internal fun CharSequence.passwordLength(): Int = toString().let { it.codePointCount(0, it.length) }
