/**
 * The only Activity. It draws edge to edge and hosts the Compose UI.
 */
package com.dotnote

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.util.Consumer
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dotnote.settings.ThemeMode
import com.dotnote.ui.dotnoteViewModel
import com.dotnote.ui.navigation.AppViewModel
import com.dotnote.ui.navigation.DotnoteNavHost
import com.dotnote.ui.navigation.StartScreen
import com.dotnote.ui.theme.Dotline
import com.dotnote.ui.theme.DotnoteTheme
import com.dotnote.widget.OpenNoteIntent

// FragmentActivity (a ComponentActivity with fragment support) because the fingerprint dialog
// (androidx.biometric BiometricPrompt) is shown through a fragment.
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Transparent system bars; their icons turn dark or light with the system theme.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        setContent {
            val appViewModel = dotnoteViewModel { AppViewModel(it) }
            // A home-screen widget tap carries a note to open: at launch, or later while running.
            DisposableEffect(Unit) {
                openWidgetNote(appViewModel, intent)
                val listener = Consumer<Intent> { openWidgetNote(appViewModel, it) }
                addOnNewIntentListener(listener)
                onDispose { removeOnNewIntentListener(listener) }
            }
            val theme by appViewModel.theme.collectAsStateWithLifecycle()
            val start by appViewModel.start.collectAsStateWithLifecycle()
            val dark =
                when (theme) {
                    ThemeMode.System -> isSystemInDarkTheme()
                    ThemeMode.Light -> false
                    ThemeMode.Dark -> true
                }
            // Status and navigation bar icons follow the app's theme (it can differ from the phone's).
            DisposableEffect(dark) {
                val style =
                    if (dark) {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            DotnoteTheme(dark = dark) {
                // Settings load in a few milliseconds; show the plain background until then.
                if (start == StartScreen.Loading) {
                    Box(Modifier.fillMaxSize().background(Dotline.colors.bg))
                } else {
                    val noteRequest by appViewModel.noteRequest.collectAsStateWithLifecycle()
                    DotnoteNavHost(start, noteRequest, onNoteRequestHandled = appViewModel::noteRequestHandled)
                }
            }
        }
    }

    private fun openWidgetNote(
        appViewModel: AppViewModel,
        intent: Intent,
    ) {
        val (vaultId, path) = OpenNoteIntent.parse(intent) ?: return
        OpenNoteIntent.consume(intent)
        appViewModel.openNote(vaultId, path)
    }
}
