/**
 * The editor's layout (SPEC 3.1 Live, 3.2 Source, 3.3 Preview), without a ViewModel so it can be
 * previewed: top bar, LIVE | SOURCE | PREVIEW, the content of the chosen mode, and in Live and
 * Source the formatter bar with the status line, which sits right above the keyboard.
 */
package com.kunotes.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kunotes.core.Cursor
import com.kunotes.core.LineEnding
import com.kunotes.core.VaultPath
import com.kunotes.settings.ViewMode
import com.kunotes.ui.dotline.DotlineIcons
import com.kunotes.ui.dotline.FormatAction
import com.kunotes.ui.dotline.FormatterBar
import com.kunotes.ui.dotline.FormatterBarDefaults
import com.kunotes.ui.dotline.SegmentOption
import com.kunotes.ui.dotline.SegmentedControl
import com.kunotes.ui.dotline.pressScale
import com.kunotes.ui.editor.live.LiveEditor
import com.kunotes.ui.theme.Dotline
import com.kunotes.ui.theme.KuNotesTheme

private val ModeOptions =
    listOf(
        SegmentOption("Live", DotlineIcons.Edit),
        SegmentOption("Source", DotlineIcons.Code),
        SegmentOption("Preview", DotlineIcons.Eye),
    )

/** Everything the editor layout calls back into. */
class EditorActions(
    val onBack: () -> Unit,
    val onMode: (ViewMode) -> Unit,
    val onFormat: (FormatAction) -> Unit,
    val onToggleTask: (Int) -> Unit,
    val onMenuAction: (EditorMenuAction) -> Unit,
    val readImage: ImageReader,
    /** A link to another note was tapped in Preview; the argument is the link as written. */
    val onOpenNoteLink: (String) -> Unit = {},
)

@Composable
fun EditorContent(
    ui: EditorUi,
    saveState: SaveState,
    pinned: Boolean,
    text: TextFieldState,
    actions: EditorActions,
    modifier: Modifier = Modifier,
    overlay: @Composable () -> Unit = {},
) {
    // safeDrawingPadding includes the keyboard, so the formatter bar sits right above it.
    Column(modifier.fillMaxSize().background(Dotline.colors.bg).safeDrawingPadding()) {
        EditorTopBar(
            title = ui.title,
            location = ui.location,
            saveState = if (ui.mode == ViewMode.Preview) null else saveState,
            pinned = pinned,
            onBack = actions.onBack,
            onMenuAction = actions.onMenuAction,
        )
        SegmentedControl(
            options = ModeOptions,
            selectedIndex = ui.mode.ordinal,
            onSelect = { actions.onMode(ViewMode.entries[it]) },
            modifier = Modifier.fillMaxWidth().padding(start = Dotline.space.s4, end = Dotline.space.s4, top = Dotline.space.s2),
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                ui.isLocked -> Message("This note is locked. Unlock the vault to read it.")
                ui.loadError != null -> Message(ui.loadError)
                !ui.loaded -> Unit
                ui.mode == ViewMode.Live ->
                    LiveEditor(text, actions.onToggleTask, actions.readImage, Modifier.fillMaxSize())
                ui.mode == ViewMode.Source -> SourceEditor(text, Modifier.fillMaxSize())
                else -> PreviewPane(text, actions)
            }
            Box(Modifier.align(Alignment.BottomCenter)) { overlay() }
        }
        if (ui.loaded && ui.mode != ViewMode.Preview) {
            StatusAndFormatter(ui, text, actions.onFormat)
        }
    }
}

@Composable
private fun StatusAndFormatter(
    ui: EditorUi,
    text: TextFieldState,
    onFormat: (FormatAction) -> Unit,
) {
    val current = text.text.toString()
    val cursor = text.selection.start
    val position = Cursor.lineCol(current, cursor)
    // Counting graphemes walks the whole note; only redo it when the text changes.
    val chars = remember(current) { Cursor.charCount(current) }
    val source = ui.mode == ViewMode.Source
    FormatterBar(
        actions = if (source) FormatterBarDefaults.SourceActions else FormatterBarDefaults.LiveActions,
        onAction = onFormat,
        line = position.line,
        column = position.column,
        chars = chars,
        activeActions = EditorFormatting.activeActions(current, cursor),
        headingLevel = EditorFormatting.headingLevel(current, cursor),
        lineEnding = if (source) ui.lineEnding.name else null,
    )
}

/** Preview: the rendered note, the floating Edit pill (→ Live), and `READ ONLY · N CHARS`. */
@Composable
private fun PreviewPane(
    text: TextFieldState,
    actions: EditorActions,
) {
    val current = text.text.toString()
    val chars = remember(current) { Cursor.charCount(current) }
    // The footer sits under the scrolling note (not on top of it); the Edit pill floats above both.
    Box(Modifier.fillMaxSize()) {
        androidx.compose.foundation.layout.Column(Modifier.fillMaxSize()) {
            // Links in the preview open through LocalUriHandler; catch our note links on the way.
            val browser = androidx.compose.ui.platform.LocalUriHandler.current
            val handler =
                remember(browser, actions) {
                    object : androidx.compose.ui.platform.UriHandler {
                        override fun openUri(uri: String) {
                            if (uri.startsWith(
                                    NOTE_LINK_SCHEME,
                                )
                            ) {
                                actions.onOpenNoteLink(uri.removePrefix(NOTE_LINK_SCHEME))
                            } else {
                                browser.openUri(uri)
                            }
                        }
                    }
                }
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalUriHandler provides handler) {
                MarkdownPreview(current, actions.readImage, Modifier.weight(1f).fillMaxWidth())
            }
            Text(
                "READ ONLY · $chars CHARS",
                style = Dotline.type.label,
                color = Dotline.colors.inkFaint,
                modifier = Modifier.padding(horizontal = Dotline.space.s4, vertical = Dotline.space.s4),
            )
        }
        EditPill(
            onClick = { actions.onMode(ViewMode.Live) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = Dotline.space.s4, bottom = 56.dp),
        )
    }
}

/** 56 dp `ink` pill with a pencil and "Edit" (mockup: 15 px medium, padding 20/24). */
@Composable
private fun EditPill(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier
            .pressScale(interactionSource)
            .height(56.dp)
            .background(Dotline.colors.ink, Dotline.radius.pill)
            .clickable(interactionSource = interactionSource, indication = null, role = Role.Button, onClick = onClick)
            .padding(start = 20.dp, end = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(Dotline.space.s2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(DotlineIcons.Edit, contentDescription = null, tint = Dotline.colors.onInk, modifier = Modifier.size(20.dp))
        Text("Edit", style = Dotline.type.body.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium), color = Dotline.colors.onInk)
    }
}

@Composable
private fun Message(text: String) {
    Text(text, style = Dotline.type.body, color = Dotline.colors.inkMuted, modifier = Modifier.padding(Dotline.space.s5))
}

// ---- Previews -------------------------------------------------------------------------------

private const val SAMPLE =
    "# Example\n\nThis is a **bold** word, this is _italic_, and here's `inline code`.\n\n## Task list\n\n" +
        "- [ ] Write the report\n- [x] Buy groceries\n- [ ] Call the bank\n\n## Lists\n\n- Plain bullet one\n" +
        "- Plain bullet two\n\n1. First step\n2. Second step\n\n> Files on disk are the truth.\n"

@Composable
private fun EditorPreviewContent(mode: ViewMode) {
    val text = remember { TextFieldState(SAMPLE) }
    EditorContent(
        ui =
            EditorUi(
                path = VaultPath("Example.md"),
                location = "PERSONAL / EXAMPLE.MD",
                mode = mode,
                loaded = true,
                lineEnding = LineEnding.LF,
            ),
        saveState = SaveState.Saved,
        pinned = false,
        text = text,
        actions = EditorActions({}, {}, {}, {}, {}, readImage = { null }),
    )
}

@Preview(name = "Live dark", widthDp = 390, heightDp = 844)
@Composable
private fun EditorLiveDarkPreview() {
    KuNotesTheme(dark = true) { EditorPreviewContent(ViewMode.Live) }
}

@Preview(name = "Live light", widthDp = 390, heightDp = 844)
@Composable
private fun EditorLiveLightPreview() {
    KuNotesTheme(dark = false) { EditorPreviewContent(ViewMode.Live) }
}

@Preview(name = "Source dark", widthDp = 390, heightDp = 844)
@Composable
private fun EditorSourceDarkPreview() {
    KuNotesTheme(dark = true) { EditorPreviewContent(ViewMode.Source) }
}

@Preview(name = "Source light", widthDp = 390, heightDp = 844)
@Composable
private fun EditorSourceLightPreview() {
    KuNotesTheme(dark = false) { EditorPreviewContent(ViewMode.Source) }
}

@Preview(name = "Preview dark", widthDp = 390, heightDp = 844)
@Composable
private fun EditorPreviewDarkPreview() {
    KuNotesTheme(dark = true) { EditorPreviewContent(ViewMode.Preview) }
}

@Preview(name = "Preview light", widthDp = 390, heightDp = 844)
@Composable
private fun EditorPreviewLightPreview() {
    KuNotesTheme(dark = false) { EditorPreviewContent(ViewMode.Preview) }
}
