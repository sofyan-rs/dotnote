/**
 * Phase 0 placeholder: the app name in Doto on black, to check fonts and theme on a device.
 * Replaced by the Welcome screen (SPEC 1.1) in Phase 3.
 */
package com.kunotes.ui.welcome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.kunotes.ui.theme.Dotline
import com.kunotes.ui.theme.KuNotesTheme

@Composable
fun SplashScreen(modifier: Modifier = Modifier) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(Dotline.colors.bg)
                .safeDrawingPadding()
                .padding(horizontal = Dotline.space.s4),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("KUNOTES", style = Dotline.type.dotM, color = Dotline.colors.ink)
        Spacer(Modifier.height(Dotline.space.s3))
        Text(
            "Markdown notes in a folder you own.",
            style = Dotline.type.title,
            color = Dotline.colors.ink,
        )
        Spacer(Modifier.height(Dotline.space.s2))
        Text(
            "PHASE 0 · CORE ${com.kunotes.core.CoreInfo.VERSION}",
            style = Dotline.type.label,
            color = Dotline.colors.inkFaint,
        )
    }
}

@Preview(name = "Dark")
@Composable
private fun SplashScreenDarkPreview() {
    KuNotesTheme(dark = true) { SplashScreen() }
}

@Preview(name = "Light")
@Composable
private fun SplashScreenLightPreview() {
    KuNotesTheme(dark = false) { SplashScreen() }
}
