/**
 * The data side of the note widget: which note each widget shows, loading that note, and
 * the intent that opens it in the app.
 *
 * Each widget remembers its note in Glance's per-widget preferences (vault id + path), so
 * several widgets can show different notes, even from different vaults.
 */
package com.dotnote.widget

import android.content.Context
import android.content.Intent
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import com.dotnote.AppContainer
import com.dotnote.MainActivity
import com.dotnote.core.NoteNames
import com.dotnote.core.Paths
import com.dotnote.core.VaultPath
import com.dotnote.core.exists
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Per-widget preference keys. */
object NoteWidgetKeys {
    val VAULT_ID = stringPreferencesKey("vault_id")
    val NOTE_PATH = stringPreferencesKey("note_path")

    /** Changed to make the widget read its note again (after a save). */
    val REFRESH = longPreferencesKey("refresh")
}

/** What a widget can show. */
sealed class WidgetNote {
    /** No note chosen yet (the picker was closed). */
    data object NotPicked : WidgetNote()

    /** The vault was removed from the app, or the note was deleted or renamed. */
    data class Missing(
        val title: String,
    ) : WidgetNote()

    /** The app lost permission to the vault's folder. */
    data class NoAccess(
        val vaultName: String,
    ) : WidgetNote()

    /** A locked note: never decrypted on the home screen. */
    data class Locked(
        val title: String,
        val vaultName: String,
    ) : WidgetNote()

    data class Ready(
        val title: String,
        /** "NOTES · PROJECTS": vault name and folder, shown above the title. */
        val label: String,
        val lines: List<PreviewLine>,
    ) : WidgetNote()
}

object NoteWidgetData {
    /** Reads the note at [path] in the vault [vaultId] and turns it into what the widget shows. */
    suspend fun load(
        container: AppContainer,
        vaultId: String?,
        path: String?,
    ): WidgetNote =
        withContext(Dispatchers.IO) {
            if (vaultId == null || path == null) return@withContext WidgetNote.NotPicked
            val notePath = VaultPath(path)
            val title = Paths.noteTitle(notePath)
            val vault =
                container.settingsStore
                    .current()
                    .vaults
                    .firstOrNull { it.id == vaultId }
                    ?: return@withContext WidgetNote.Missing(title)
            if (!container.vaultRegistry.hasAccess(vault)) return@withContext WidgetNote.NoAccess(vault.name)
            if (NoteNames.isLockedNote(notePath)) return@withContext WidgetNote.Locked(title, vault.name)
            try {
                val fs = container.vaultRegistry.openFs(vault)
                if (!fs.exists(notePath)) return@withContext WidgetNote.Missing(title)
                val markdown = fs.read(notePath).decodeToString()
                val folder = notePath.parent?.takeIf { !it.isRoot }?.name
                val label = listOfNotNull(vault.name, folder).joinToString(" · ").uppercase()
                WidgetNote.Ready(title, label, NotePreview.lines(markdown, title))
            } catch (e: Exception) {
                android.util.Log.w("NoteWidget", "Couldn't read $path", e)
                WidgetNote.Missing(title)
            }
        }

    /** Remembers that widget [appWidgetId] shows [path] in [vaultId], and redraws it. */
    suspend fun pick(
        context: Context,
        appWidgetId: Int,
        vaultId: String,
        path: VaultPath,
    ) {
        val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
        updateAppWidgetState(context, glanceId) { prefs ->
            prefs[NoteWidgetKeys.VAULT_ID] = vaultId
            prefs[NoteWidgetKeys.NOTE_PATH] = path.value
            prefs[NoteWidgetKeys.REFRESH] = System.currentTimeMillis()
        }
        NoteWidget().update(context, glanceId)
    }

    /** Makes every note widget read its note again. Called after a note is saved. */
    suspend fun refreshAll(context: Context) {
        val manager = GlanceAppWidgetManager(context)
        for (glanceId in manager.getGlanceIds(NoteWidget::class.java)) {
            updateAppWidgetState(context, glanceId) { it[NoteWidgetKeys.REFRESH] = System.currentTimeMillis() }
            NoteWidget().update(context, glanceId)
        }
    }
}

/** The intent a widget tap sends to [MainActivity] to open its note. */
object OpenNoteIntent {
    private const val EXTRA_VAULT_ID = "com.dotnote.extra.VAULT_ID"
    private const val EXTRA_NOTE_PATH = "com.dotnote.extra.NOTE_PATH"

    fun create(
        context: Context,
        vaultId: String,
        path: String,
    ): Intent =
        Intent(context, MainActivity::class.java)
            // A unique action per note, so Android doesn't merge the taps of different widgets.
            .setAction("com.dotnote.action.OPEN_NOTE/$vaultId/$path")
            .putExtra(EXTRA_VAULT_ID, vaultId)
            .putExtra(EXTRA_NOTE_PATH, path)
            // Reuse the running app instead of starting a second copy on top of it.
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    /** The vault id and note path in [intent], or `null` if it isn't a widget tap. */
    fun parse(intent: Intent?): Pair<String, String>? {
        val vaultId = intent?.getStringExtra(EXTRA_VAULT_ID) ?: return null
        val path = intent.getStringExtra(EXTRA_NOTE_PATH) ?: return null
        return vaultId to path
    }

    /** Removes the extras, so recreating the activity (rotation) doesn't open the note again. */
    fun consume(intent: Intent) {
        intent.removeExtra(EXTRA_VAULT_ID)
        intent.removeExtra(EXTRA_NOTE_PATH)
    }
}
