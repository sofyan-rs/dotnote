/**
 * The note picker the launcher opens when a note widget is added (and, on Android 12+, when
 * the user long-presses it and taps the settings button). It lists the notes of every saved
 * vault, grouped by vault, with a search field. Picking one sets up the widget and closes.
 *
 * Android's rule for widget setup screens: answer RESULT_OK with the widget id to keep the
 * widget, or RESULT_CANCELED (the default here) to drop a widget that was just added.
 */
package com.dotnote.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.dotnote.DotnoteApp
import com.dotnote.core.NoteNames
import com.dotnote.core.Paths
import com.dotnote.core.Search
import com.dotnote.core.VaultNode
import com.dotnote.core.VaultPath
import com.dotnote.settings.ThemeMode
import com.dotnote.ui.dotline.DotlineIcons
import com.dotnote.ui.dotline.DotlineTextField
import com.dotnote.ui.dotline.ListDivider
import com.dotnote.ui.dotline.ListGroup
import com.dotnote.ui.dotline.ListRow
import com.dotnote.ui.dotline.ScreenTitle
import com.dotnote.ui.dotline.ScreenTitleAction
import com.dotnote.ui.dotline.SectionLabel
import com.dotnote.ui.theme.Dotline
import com.dotnote.ui.theme.DotnoteTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The notes of one vault, for the picker. */
private data class VaultNotes(
    val vaultId: String,
    val vaultName: String,
    val notes: List<VaultPath>,
)

class NoteWidgetConfigActivity : ComponentActivity() {
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // If the user backs out, the launcher removes the widget it was adding.
        setResult(RESULT_CANCELED)
        appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        enableEdgeToEdge()
        val container = (application as DotnoteApp).container
        // Made once here, not inside setContent: a flow built during composition restarts on every redraw.
        val themeFlow = container.settingsStore.settings.map { it.theme }
        setContent {
            val theme by themeFlow.collectAsState(ThemeMode.System)
            val dark =
                when (theme) {
                    ThemeMode.System -> isSystemInDarkTheme()
                    ThemeMode.Light -> false
                    ThemeMode.Dark -> true
                }
            // null while the vaults are being scanned.
            val vaults by produceState<List<VaultNotes>?>(null) { value = loadNotes() }
            DotnoteTheme(dark = dark) {
                NotePicker(vaults = vaults, onPick = ::pick, onCancel = ::finish)
            }
        }
    }

    /** Lists the notes of every vault the app can still open. */
    private suspend fun loadNotes(): List<VaultNotes> =
        withContext(Dispatchers.IO) {
            val container = (application as DotnoteApp).container
            container.settingsStore
                .current()
                .vaults
                .filter { container.vaultRegistry.hasAccess(it) }
                .mapNotNull { vault ->
                    try {
                        val root = VaultNode.scan(container.vaultRegistry.openFs(vault))
                        val notes = Search.flattenFiles(root).map { it.path }
                        VaultNotes(vault.id, vault.name, notes)
                    } catch (e: Exception) {
                        android.util.Log.w("NoteWidget", "Couldn't scan ${vault.name}", e)
                        null
                    }
                }
        }

    private fun pick(
        vaultId: String,
        path: VaultPath,
    ) {
        lifecycleScope.launch {
            NoteWidgetData.pick(this@NoteWidgetConfigActivity, appWidgetId, vaultId, path)
            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
            finish()
        }
    }

    companion object {
        /** Opens the picker for the existing widget [appWidgetId]. */
        fun intent(
            context: Context,
            appWidgetId: Int,
        ): Intent =
            Intent(context, NoteWidgetConfigActivity::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

@Composable
private fun NotePicker(
    vaults: List<VaultNotes>?,
    onPick: (vaultId: String, path: VaultPath) -> Unit,
    onCancel: () -> Unit,
) {
    val query = rememberTextFieldState()
    Column(
        Modifier
            .fillMaxSize()
            .background(Dotline.colors.bg)
            .systemBarsPadding()
            .imePadding(),
    ) {
        ScreenTitle(
            title = "CHOOSE NOTE",
            subline = "Shown on your home screen",
            actions = { ScreenTitleAction(DotlineIcons.Close, "Cancel", onCancel) },
        )
        DotlineTextField(
            state = query,
            label = null,
            placeholder = "Find a note by name",
            leadingIcon = DotlineIcons.Search,
            modifier = Modifier.padding(horizontal = Dotline.space.s4, vertical = Dotline.space.s2),
        )
        val text = query.text.toString().trim()
        when {
            vaults == null -> Hint("Looking for notes…")
            vaults.all { it.notes.isEmpty() } -> Hint("No notes yet. Create one in Dotnote first.")
            else -> {
                // Only vaults with a matching note are shown while searching.
                val shown =
                    vaults
                        .map { vault -> vault.copy(notes = vault.notes.filter { matches(it, text) }) }
                        .filter { it.notes.isNotEmpty() }
                if (shown.isEmpty()) Hint("No note matches “$text”.") else VaultNoteList(shown, onPick)
            }
        }
    }
}

/** True if the note's name contains [query] (letter case ignored). */
private fun matches(
    path: VaultPath,
    query: String,
): Boolean = query.isEmpty() || Paths.noteTitle(path).contains(query, ignoreCase = true)

@Composable
private fun VaultNoteList(
    vaults: List<VaultNotes>,
    onPick: (vaultId: String, path: VaultPath) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = Dotline.space.s4, vertical = Dotline.space.s2),
        verticalArrangement = Arrangement.spacedBy(Dotline.space.s2),
    ) {
        items(vaults, key = { it.vaultId }) { vault ->
            Column(verticalArrangement = Arrangement.spacedBy(Dotline.space.s2)) {
                SectionLabel(vault.vaultName.uppercase(), Modifier.padding(top = Dotline.space.s2))
                ListGroup {
                    vault.notes.forEachIndexed { index, path ->
                        if (index > 0) ListDivider()
                        ListRow(
                            title = Paths.noteTitle(path),
                            subtitle = "/" + (path.parent?.value ?: ""),
                            subtitleIsData = true,
                            leadingIcon = if (NoteNames.isLockedNote(path)) DotlineIcons.Lock else DotlineIcons.Note,
                            onClick = { onPick(vault.vaultId, path) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text = text,
        style = Dotline.type.body,
        color = Dotline.colors.inkMuted,
        modifier = Modifier.padding(horizontal = Dotline.space.s4, vertical = Dotline.space.s4),
    )
}
