/**
 * The SEARCH tab: the quick switcher and "no results".
 *
 * The field is focused when the tab opens. With an empty query it shows RECENT notes as chips;
 * while typing it lists matching note names (top result highlighted, opened by the keyboard's
 * Go key) and a "Create note “query”" row.
 */
package com.dotnote.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dotnote.core.NoteNames
import com.dotnote.core.Paths
import com.dotnote.core.VaultPath
import com.dotnote.ui.dotline.DotArt
import com.dotnote.ui.dotline.DotArtPatterns
import com.dotnote.ui.dotline.DotlineIcons
import com.dotnote.ui.dotline.DotlineTextField
import com.dotnote.ui.dotline.DotlineTextFieldStatus
import com.dotnote.ui.dotline.ListDivider
import com.dotnote.ui.dotline.ListGroup
import com.dotnote.ui.dotline.ListRow
import com.dotnote.ui.dotline.MainTab
import com.dotnote.ui.dotline.PrimaryButton
import com.dotnote.ui.dotline.ScreenTitle
import com.dotnote.ui.dotline.SectionLabel
import com.dotnote.ui.dotnoteViewModel
import com.dotnote.ui.navigation.MainTabs
import com.dotnote.ui.theme.Dotline
import com.dotnote.ui.theme.DotnoteTheme

@Composable
fun SearchScreen(
    onTabClick: (MainTab) -> Unit,
    onOpenNote: (VaultPath) -> Unit,
) {
    val viewModel = dotnoteViewModel { SearchViewModel(it.vaultStore) }
    // Collecting the vault state recomposes the results when the vault changes.
    val vaultState by viewModel.state.collectAsStateWithLifecycle()
    val query = rememberTextFieldState()
    val focus = remember { FocusRequester() }
    val text = query.text.toString()
    val matches = remember(text, vaultState.root) { viewModel.matches(text) }
    val recent = remember(vaultState.openNotes) { viewModel.recent() }

    val open = { path: VaultPath ->
        viewModel.openNote(path)
        onOpenNote(path)
    }
    val create = { viewModel.createNote(text.trim()) { onOpenNote(it) } }

    LaunchedEffect(Unit) { focus.requestFocus() }

    MainTabs(selected = MainTab.Search, onTabClick = onTabClick) {
        Column(Modifier.fillMaxSize().imePadding()) {
            ScreenTitle(title = "SEARCH")
            DotlineTextField(
                state = query,
                label = null,
                placeholder = "Find a note by name",
                leadingIcon = DotlineIcons.Search,
                trailing = { if (text.isNotBlank()) DotlineTextFieldStatus(text = matches.size.toString()) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                // "Go" on the keyboard opens the top result.
                onKeyboardAction = { matches.firstOrNull()?.let { open(it.path) } },
                textFieldModifier = Modifier.focusRequester(focus),
                modifier = Modifier.padding(horizontal = Dotline.space.s4, vertical = Dotline.space.s2),
            )
            when {
                text.isBlank() -> RecentNotes(recent, onOpen = open)
                matches.isEmpty() -> NoResults(text.trim(), onCreate = create)
                else -> Results(text.trim(), matches, onOpen = open, onCreate = create)
            }
        }
    }
}

@Composable
private fun Results(
    query: String,
    matches: List<SearchMatch>,
    onOpen: (VaultPath) -> Unit,
    onCreate: () -> Unit,
) {
    LazyColumn(contentPadding = PaddingValues(Dotline.space.s4), verticalArrangement = Arrangement.spacedBy(Dotline.space.s2)) {
        item { SectionLabel("MATCHES") }
        item {
            ListGroup {
                matches.forEachIndexed { index, match ->
                    if (index > 0) ListDivider()
                    MatchRow(match, highlighted = index == 0, onClick = { onOpen(match.path) })
                }
                ListDivider()
                ListRow(title = "Create note “$query”", leadingIcon = DotlineIcons.Plus, onClick = onCreate)
            }
        }
    }
}

/** A result: the matched part of the name in ink semibold + underline, the rest muted. */
@Composable
private fun MatchRow(
    match: SearchMatch,
    highlighted: Boolean,
    onClick: () -> Unit,
) {
    val colors = Dotline.colors
    val title =
        buildAnnotatedString {
            withStyle(SpanStyle(color = colors.inkMuted)) { append(match.title.substring(0, match.matchStart)) }
            withStyle(SpanStyle(color = colors.ink, fontWeight = FontWeight.SemiBold, textDecoration = TextDecoration.Underline)) {
                append(match.title.substring(match.matchStart, match.matchEnd))
            }
            withStyle(SpanStyle(color = colors.inkMuted)) { append(match.title.substring(match.matchEnd)) }
        }
    Row(
        Modifier
            .fillMaxWidth()
            // The top result (opened by the keyboard's Go key) gets the raised background.
            .background(if (highlighted) colors.surfaceRaised else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Dotline.space.s4)
            .height(64.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dotline.space.s3),
    ) {
        Icon(
            if (match.isLocked) DotlineIcons.Lock else DotlineIcons.Note,
            contentDescription = null,
            tint = colors.inkMuted,
            modifier = Modifier.size(22.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(title, style = Dotline.type.headline.copy(fontWeight = FontWeight.Normal), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(match.folder, style = Dotline.type.data, color = colors.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun RecentNotes(
    recent: List<VaultPath>,
    onOpen: (VaultPath) -> Unit,
) {
    if (recent.isEmpty()) return
    Column(Modifier.padding(Dotline.space.s4), verticalArrangement = Arrangement.spacedBy(Dotline.space.s2)) {
        SectionLabel("RECENT")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Dotline.space.s2),
            verticalArrangement = Arrangement.spacedBy(Dotline.space.s2),
        ) {
            recent.forEach { path -> RecentChip(path, onClick = { onOpen(path) }) }
        }
    }
}

@Composable
private fun RecentChip(
    path: VaultPath,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .height(40.dp)
            .border(Dotline.size.stroke, Dotline.colors.lineStrong, Dotline.radius.pill)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Dotline.space.s4),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (NoteNames.isLockedNote(path)) {
            Icon(DotlineIcons.Lock, contentDescription = "Locked", tint = Dotline.colors.ink, modifier = Modifier.size(16.dp))
        }
        Text(Paths.noteTitle(path), style = Dotline.type.bodyS, color = Dotline.colors.ink)
    }
}

/** No note matches. */
@Composable
private fun NoResults(
    query: String,
    onCreate: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = Dotline.space.s6, vertical = Dotline.space.s6),
        verticalArrangement = Arrangement.spacedBy(Dotline.space.s5),
    ) {
        DotArt(DotArtPatterns.Magnifier, contentDescription = null, dotSize = 6.dp)
        Column(verticalArrangement = Arrangement.spacedBy(Dotline.space.s2)) {
            Text("No note named “$query”", style = Dotline.type.title, color = Dotline.colors.ink)
            Text("Search matches note names in this vault.", style = Dotline.type.body, color = Dotline.colors.inkMuted)
        }
        PrimaryButton("Create note “$query”", onClick = onCreate, icon = DotlineIcons.Plus, compact = true)
    }
}

@Preview(name = "Dark")
@Composable
private fun NoResultsDarkPreview() {
    DotnoteTheme(dark = true) { NoResults("budget", onCreate = {}) }
}

@Preview(name = "Light")
@Composable
private fun NoResultsLightPreview() {
    DotnoteTheme(dark = false) { NoResults("budget", onCreate = {}) }
}
