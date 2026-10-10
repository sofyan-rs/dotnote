/**
 * DotlineMenu: the dropdown menu that opens from a ⋮ button (SPEC §3 "DropdownMenu", screen 3.4
 * Editor-Menu).
 *
 * `surfaceRaised` panel, radius 16, 1.5 dp `lineStrong` border, no shadow, 248 dp wide,
 * rows 48 dp with a 20 dp icon and `body` text, an optional divider, and a red destructive row.
 * It wraps Material 3 `DropdownMenu`, which handles the popup, positioning and dismissal.
 *
 * Also in this file: [MoreButton], the round 48 dp ⋮ button used by the editor top bar and by
 * [ListRow] / [TreeRow].
 *
 * Usage:
 * ```
 * var open by remember { mutableStateOf(false) }
 * Box { // The menu opens below the Box that holds the anchor.
 *     MoreButton(onClick = { open = true }, contentDescription = "Note actions", open = open)
 *     DotlineMenu(expanded = open, onDismissRequest = { open = false }) {
 *         DotlineMenuItem("Rename", DotlineIcons.Edit, onClick = { open = false; ... })
 *         DotlineMenuDivider()
 *         DotlineMenuItem("Move to trash", DotlineIcons.Trash, destructive = true, onClick = { ... })
 *     }
 * }
 * ```
 */
package com.kunotes.ui.dotline

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.kunotes.ui.theme.Dotline
import com.kunotes.ui.theme.KuNotesTheme

/** Menu width from the Editor-Menu mockup. */
private val MenuWidth = 248.dp

/** Icon size in menu rows and in the ⋮ button. */
private val SmallIconSize = 20.dp

/**
 * The menu panel. Place it in the same `Box` as its anchor (usually a [MoreButton]).
 *
 * The Material `DropdownMenu` already adds 8 dp above and below the rows, as in the mockup.
 *
 * @param expanded whether the menu is shown.
 * @param onDismissRequest called on a tap outside or Back; set your `expanded` state to `false`.
 * @param offset shift from the anchor's bottom-start (bottom-end in a right-aligned anchor).
 * @param content [DotlineMenuItem]s and [DotlineMenuDivider]s.
 */
@Composable
fun DotlineMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset(0.dp, 0.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier.width(MenuWidth),
        offset = offset,
        shape = Dotline.radius.m,
        containerColor = Dotline.colors.surfaceRaised,
        // Depth comes from the border and surface step, never from shadows or tonal tint.
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(Dotline.size.stroke, Dotline.colors.lineStrong),
        content = content,
    )
}

/**
 * One 48 dp menu row: icon + text.
 *
 * @param destructive draws text and icon in `accentText` ("Move to trash").
 */
@Composable
fun DotlineMenuItem(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val contentColor = if (destructive) Dotline.colors.accentText else Dotline.colors.ink
    // The panel is already surfaceRaised, so a pressed row steps one further, to `line`.
    val background = if (pressed) Dotline.colors.line else Color.Transparent

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = Dotline.size.touchTarget)
                .background(background)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null, // No Material ripple.
                    role = Role.Button,
                    onClick = onClick,
                ).insetFocusRing(interactionSource)
                .padding(horizontal = Dotline.space.s4),
        horizontalArrangement = Arrangement.spacedBy(Dotline.space.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(SmallIconSize))
        Text(text = text, style = Dotline.type.body, color = contentColor)
    }
}

/** The 1 dp `line` divider between two groups of menu rows, with 8 dp above and below. */
@Composable
fun DotlineMenuDivider(modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .padding(vertical = Dotline.space.s2)
                .fillMaxWidth()
                .height(1.dp)
                .background(Dotline.colors.line),
    )
}

/**
 * The round 48 dp ⋮ button that opens actions.
 *
 * @param contentDescription what TalkBack says, e.g. "Actions for Example".
 * @param open `true` while its menu is open: the button gets a `surfaceRaised` fill (Editor-Menu).
 * @param tint icon colour: `inkMuted` in list rows, pass `Dotline.colors.ink` in top bars.
 */
@Composable
fun MoreButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    open: Boolean = false,
    tint: Color = Dotline.colors.inkMuted,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val background = if (open || pressed) Dotline.colors.surfaceRaised else Color.Transparent
    Box(
        // Order: the focus ring is drawn before clip, so the clip doesn't cut it off.
        modifier =
            modifier
                .size(Dotline.size.touchTarget)
                .focusRing(interactionSource, Dotline.radius.pill)
                .clip(Dotline.radius.pill)
                .background(background)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.Button,
                    onClick = onClick,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = DotlineIcons.More,
            contentDescription = contentDescription,
            tint = if (open) Dotline.colors.ink else tint,
            modifier = Modifier.size(SmallIconSize),
        )
    }
}

@Composable
private fun DotlineMenuPreviewContent() {
    var open by remember { mutableStateOf(true) }
    Box(Modifier.width(MenuWidth).height(460.dp), contentAlignment = Alignment.TopEnd) {
        Box {
            MoreButton(onClick = { open = true }, contentDescription = "Note actions", open = open, tint = Dotline.colors.ink)
            DotlineMenu(expanded = open, onDismissRequest = { open = false }) {
                DotlineMenuItem("Rename", DotlineIcons.Edit, onClick = {})
                DotlineMenuItem("Move to…", DotlineIcons.MoveTo, onClick = {})
                DotlineMenuItem("Pin note", DotlineIcons.Pin, onClick = {})
                DotlineMenuItem("Copy path", DotlineIcons.Copy, onClick = {})
                DotlineMenuItem("Share", DotlineIcons.Share, onClick = {})
                DotlineMenuDivider()
                DotlineMenuItem("Lock note", DotlineIcons.Lock, onClick = {})
                DotlineMenuItem("Move to trash", DotlineIcons.Trash, destructive = true, onClick = {})
            }
        }
    }
}

@Preview(name = "Dark")
@Composable
private fun DotlineMenuDarkPreview() {
    KuNotesTheme(dark = true) {
        Box(Modifier.background(Dotline.colors.bg).padding(16.dp)) { DotlineMenuPreviewContent() }
    }
}

@Preview(name = "Light")
@Composable
private fun DotlineMenuLightPreview() {
    KuNotesTheme(dark = false) {
        Box(Modifier.background(Dotline.colors.bg).padding(16.dp)) { DotlineMenuPreviewContent() }
    }
}
