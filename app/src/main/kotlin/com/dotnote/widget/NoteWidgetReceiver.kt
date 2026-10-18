/**
 * The broadcast receiver Android talks to about note widgets (added, resized, removed,
 * periodic update). Glance does the work; this only says which widget it is.
 *
 * It also redraws every widget after the app is updated: until then the launcher shows the
 * "loading" layout, and the next periodic update can be up to 30 minutes away.
 */
package com.dotnote.widget

import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

class NoteWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NoteWidget()

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        super.onReceive(context, intent)
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            // goAsync keeps the receiver alive while the widgets redraw in a coroutine.
            val pending = goAsync()
            MainScope().launch {
                try {
                    NoteWidgetData.refreshAll(context)
                } finally {
                    pending.finish()
                }
            }
        }
    }
}
