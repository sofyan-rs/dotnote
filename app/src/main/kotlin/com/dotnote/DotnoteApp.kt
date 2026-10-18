/**
 * The Application object: created once when the app process starts, before any screen.
 * It builds the [AppContainer] that every screen shares.
 */
package com.dotnote

import android.app.Application
import android.content.res.Configuration
import com.dotnote.widget.NoteWidgetData
import kotlinx.coroutines.launch

class DotnoteApp : Application() {
    lateinit var container: AppContainer
        private set

    /** The light/dark bits of the last configuration, to notice a theme switch. */
    private var nightMode = 0

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        nightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        // Widgets may still show "loading" (e.g. after the app was force-stopped); redraw them.
        container.appScope.launch { NoteWidgetData.refreshAll(this@DotnoteApp) }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val newNightMode = newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK
        if (newNightMode != nightMode) {
            nightMode = newNightMode
            // Before Android 12 a widget keeps the text colours of its last redraw (see NoteWidget).
            container.appScope.launch { NoteWidgetData.refreshAll(this@DotnoteApp) }
        }
    }
}
