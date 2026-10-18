/**
 * The Application object: created once when the app process starts, before any screen.
 * It builds the [AppContainer] that every screen shares.
 */
package com.dotnote

import android.app.Application

class DotnoteApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
