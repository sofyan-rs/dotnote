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
import androidx.compose.foundation.isSystemInDarkTheme
import com.kunotes.ui.theme.KuNotesTheme
import com.kunotes.ui.welcome.SplashScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Transparent system bars; their icons turn dark or light with the system theme.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        setContent {
            // Follows the phone's dark/light setting. A Settings choice comes later (PLAN).
            KuNotesTheme(dark = isSystemInDarkTheme()) {
                SplashScreen()
            }
        }
    }
}
