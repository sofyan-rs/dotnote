/**
 * Debug-only screen that runs the Phase 0 spikes on the device and shows the results.
 */
package com.kunotes.spike

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.kunotes.ui.theme.Dotline
import com.kunotes.ui.theme.KuNotesTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SpikeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KuNotesTheme(dark = isSystemInDarkTheme()) { SpikeScreen() }
        }
    }
}

@Composable
private fun SpikeScreen() {
    val context = LocalContext.current
    var lockLines by remember { mutableStateOf(listOf("running…")) }
    var gitLines by remember { mutableStateOf(listOf("running…")) }
    val editor = remember { TextFieldState(SAMPLE) }

    // Run the slow checks off the main thread, one after the other.
    LaunchedEffect(Unit) {
        lockLines = withContext(Dispatchers.IO) { runCatchingLines { SpikeChecks.lockCompatibility(context) } }
        gitLines = withContext(Dispatchers.IO) { runCatchingLines { SpikeChecks.gitSync(context) } }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Dotline.colors.bg)
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(Dotline.space.s4),
    ) {
        Text("SPIKES", style = Dotline.type.dotM, color = Dotline.colors.ink)
        Section("Q3 · LOCKED NOTES", lockLines)
        Section("Q4 · JGIT", gitLines)
        Spacer(Modifier.height(Dotline.space.s5))
        Text("Q2 · LIVE EDITOR", style = Dotline.type.label, color = Dotline.colors.inkFaint)
        Spacer(Modifier.height(Dotline.space.s2))
        LivePreviewField(editor, Modifier.fillMaxWidth())
    }
}

@Composable
private fun Section(
    title: String,
    lines: List<String>,
) {
    Spacer(Modifier.height(Dotline.space.s5))
    Text(title, style = Dotline.type.label, color = Dotline.colors.inkFaint)
    Spacer(Modifier.height(Dotline.space.s2))
    lines.forEach { Text(it, style = Dotline.type.data, color = Dotline.colors.ink) }
}

/** A failed spike shows its error instead of crashing the screen. */
private inline fun runCatchingLines(block: () -> List<String>): List<String> =
    try {
        block()
    } catch (error: Throwable) {
        listOf("✗ ${error::class.simpleName}: ${error.message}")
    }

private const val SAMPLE = """# Live mode

Markers like **bold**, *italic* and `code` hide when the cursor is on another line.

## Tasks
- [ ] tap a line to reveal its markers
- [x] ~~done~~

> A quote line
"""
