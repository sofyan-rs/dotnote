/**
 * The only Activity. It draws edge to edge and hosts the Compose UI.
 */
package com.kunotes

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kunotes.settings.ThemeMode
import com.kunotes.ui.kuNotesViewModel
import com.kunotes.ui.navigation.AppViewModel
import com.kunotes.ui.navigation.KuNotesNavHost
import com.kunotes.ui.navigation.StartScreen
import com.kunotes.ui.theme.Dotline
import com.kunotes.ui.theme.KuNotesTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Transparent system bars; their icons turn dark or light with the system theme.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        setContent {
            val appViewModel = kuNotesViewModel { AppViewModel(it) }
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
            KuNotesTheme(dark = dark) {
                // Settings load in a few milliseconds; show the plain background until then.
                if (start == StartScreen.Loading) {
                    Box(Modifier.fillMaxSize().background(Dotline.colors.bg))
                } else {
                    KuNotesNavHost(start)
                }
            }
        }
    }
}
