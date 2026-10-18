/**
 * The home-screen widget that shows one note, in the Dotline look.
 *
 * Built with Jetpack Glance: it looks like Compose, but Glance turns it into the RemoteViews
 * a launcher can draw. It resizes from 2×2 to 4×4 cells; [SizeMode.Responsive] picks one of
 * three layouts (2×2, 4×2, 4×4) for the size the user drags it to. Long notes scroll.
 *
 * Tapping opens the note in the app. Locked notes only show their title.
 */
package com.dotnote.widget

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import com.dotnote.DotnoteApp
import com.dotnote.R
import com.dotnote.ui.theme.DotlineDark
import com.dotnote.ui.theme.DotlineLight

class NoteWidget : GlanceAppWidget() {
    // Per-widget preferences hold the chosen note (see NoteWidgetKeys).
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override val sizeMode = SizeMode.Responsive(setOf(SMALL, WIDE, LARGE))

    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        val container = (context.applicationContext as DotnoteApp).container
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        provideContent {
            val prefs = currentState<Preferences>()
            val vaultId = prefs[NoteWidgetKeys.VAULT_ID]
            val path = prefs[NoteWidgetKeys.NOTE_PATH]
            val refresh = prefs[NoteWidgetKeys.REFRESH]
            var note by remember { mutableStateOf<WidgetNote?>(null) }
            // Reads the note again when the widget gets another note or a refresh after a save.
            LaunchedEffect(vaultId, path, refresh) { note = NoteWidgetData.load(container, vaultId, path) }

            val openAction =
                if (vaultId != null && path != null && note !is WidgetNote.Missing) {
                    actionStartActivity(OpenNoteIntent.create(context, vaultId, path))
                } else {
                    // Not set up yet, or its note is gone: tapping opens the note picker for this widget.
                    actionStartActivity(NoteWidgetConfigActivity.intent(context, appWidgetId))
                }
            WidgetContent(note, openAction)
        }
    }

    companion object {
        /** The three layouts: 2×2, 4×2 and 4×4 cells (sizes in dp, as launchers report them). */
        private val SMALL = DpSize(110.dp, 110.dp)
        private val WIDE = DpSize(250.dp, 110.dp)
        private val LARGE = DpSize(250.dp, 250.dp)
    }
}

// ---- Colours: the Dotline tokens, light by day and dark by night ----

private val Ink = ColorProvider(day = DotlineLight.ink, night = DotlineDark.ink)
private val InkMuted = ColorProvider(day = DotlineLight.inkMuted, night = DotlineDark.inkMuted)
private val InkFaint = ColorProvider(day = DotlineLight.inkFaint, night = DotlineDark.inkFaint)

/**
 * Android 12+ switches the background (drawable / drawable-night) and the day/night text
 * colours by itself when the phone changes theme. Older versions can't change text colours
 * that way: they keep the colours of the last redraw. So there the
 * background is picked here too, from the same moment, and both always match. The app
 * redraws the widgets when the theme changes (see DotnoteApp.onConfigurationChanged).
 */
private fun backgroundDrawable(context: Context): Int {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) return R.drawable.note_widget_background
    val night = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    return if (night) R.drawable.note_widget_background_dark else R.drawable.note_widget_background_light
}

// ---- Layout ----

@Composable
private fun WidgetContent(
    note: WidgetNote?,
    openAction: Action,
) {
    // The 2×2 size gets a one-line title and smaller text.
    val compact = LocalSize.current.height < 180.dp
    Column(
        modifier =
            GlanceModifier
                .fillMaxSize()
                .appWidgetBackground()
                // A rounded drawable, not cornerRadius: that needs Android 12.
                .background(ImageProvider(backgroundDrawable(LocalContext.current)))
                .padding(16.dp)
                .clickable(openAction),
    ) {
        when (note) {
            null -> Header(label = "DOTNOTE", title = "", compact = compact)
            WidgetNote.NotPicked -> {
                Header(label = "DOTNOTE", title = "Choose a note", compact = compact)
                Message("Tap to pick the note this widget shows.")
            }
            is WidgetNote.Missing -> {
                Header(label = "DOTNOTE", title = note.title, compact = compact)
                Message("This note was moved or deleted. Tap to pick another.")
            }
            is WidgetNote.NoAccess -> {
                Header(label = note.vaultName.uppercase(), title = "No access", compact = compact)
                Message("Open Dotnote to give access to this vault's folder again.")
            }
            is WidgetNote.Locked -> {
                Header(label = note.vaultName.uppercase(), title = note.title, compact = compact)
                Message("Locked note. Tap to unlock it in Dotnote.")
            }
            is WidgetNote.Ready -> {
                Header(label = note.label, title = note.title, compact = compact)
                if (note.lines.isEmpty()) Message("Empty note.") else NoteLines(note.lines, compact, openAction)
            }
        }
    }
}

/** A red dot and the label in mono caps, then the note title. */
@Composable
private fun Header(
    label: String,
    title: String,
    compact: Boolean,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(provider = ImageProvider(R.drawable.note_widget_dot), contentDescription = null, modifier = GlanceModifier.size(6.dp))
        Spacer(GlanceModifier.width(6.dp))
        Text(
            text = label,
            style = TextStyle(color = InkFaint, fontSize = 10.sp, fontFamily = FontFamily.Monospace),
            maxLines = 1,
        )
    }
    Spacer(GlanceModifier.height(6.dp))
    if (title.isNotEmpty()) {
        Text(
            text = title,
            style = TextStyle(color = Ink, fontSize = if (compact) 16.sp else 18.sp, fontWeight = FontWeight.Medium),
            maxLines = if (compact) 1 else 2,
        )
        Spacer(GlanceModifier.height(6.dp))
    }
}

@Composable
private fun Message(text: String) {
    Text(text = text, style = TextStyle(color = InkMuted, fontSize = 13.sp))
}

/** The note body. A list scrolls inside the widget when the note is longer than the space. */
@Composable
private fun NoteLines(
    lines: List<PreviewLine>,
    compact: Boolean,
    openAction: Action,
) {
    val bodySize = if (compact) 12.sp else 13.sp
    LazyColumn {
        items(lines) { line ->
            // A blank line between paragraphs is a small gap, not a full empty line.
            if (line.text.isEmpty()) {
                Spacer(GlanceModifier.height(6.dp))
                return@items
            }
            val style =
                when (line.kind) {
                    PreviewLineKind.Heading -> TextStyle(color = Ink, fontSize = bodySize, fontWeight = FontWeight.Bold)
                    PreviewLineKind.DoneTask ->
                        TextStyle(color = InkFaint, fontSize = bodySize, textDecoration = TextDecoration.LineThrough)
                    else -> TextStyle(color = InkMuted, fontSize = bodySize)
                }
            // Items in a list need their own click action; the column's doesn't reach them.
            Text(
                text = line.text,
                style = style,
                modifier = GlanceModifier.fillMaxWidth().padding(vertical = 1.dp).clickable(openAction),
            )
        }
    }
}
