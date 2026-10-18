/**
 * Debug-only gallery of every Dotline component, to compare against the mockups on a device.
 * Launch with `adb shell am start -n com.dotnote.android/com.dotnote.gallery.GalleryActivity`.
 * Tap the DARK / LIGHT chip at the top to switch theme.
 */
package com.dotnote.gallery

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.dotnote.ui.dotline.ControlsGallerySection
import com.dotnote.ui.dotline.ListsGallerySection
import com.dotnote.ui.dotline.SheetsGallerySection
import com.dotnote.ui.theme.Dotline
import com.dotnote.ui.theme.DotnoteTheme

class GalleryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Optional start section from adb: --es section lists|controls|sheets
        val start = intent.getStringExtra("section")
        setContent {
            val systemDark = isSystemInDarkTheme()
            var dark by rememberSaveable { mutableStateOf(intent.getStringExtra("theme")?.let { it == "dark" } ?: systemDark) }
            DotnoteTheme(dark = dark) {
                GalleryScreen(dark = dark, onToggleTheme = { dark = !dark }, section = start)
            }
        }
    }
}

@Composable
private fun GalleryScreen(
    dark: Boolean,
    onToggleTheme: () -> Unit,
    section: String?,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Dotline.colors.bg)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(Dotline.space.s4),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("GALLERY", style = Dotline.type.dotM, color = Dotline.colors.ink)
            Text(
                if (dark) "DARK" else "LIGHT",
                style = Dotline.type.label,
                color = Dotline.colors.ink,
                modifier = Modifier.clickable(onClick = onToggleTheme).padding(Dotline.space.s3),
            )
        }
        if (section == null || section == "controls") ControlsGallerySection(Modifier.padding(horizontal = Dotline.space.s4))
        Spacer(Modifier.height(Dotline.space.s5))
        if (section == null || section == "lists") ListsGallerySection()
        Spacer(Modifier.height(Dotline.space.s5))
        if (section == null || section == "sheets") SheetsGallerySection(Modifier.padding(horizontal = Dotline.space.s4))
        Spacer(Modifier.height(Dotline.space.s7))
    }
}
