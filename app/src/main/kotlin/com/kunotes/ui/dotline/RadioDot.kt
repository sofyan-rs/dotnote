/**
 * Dotline radio button (SPEC §3 `RadioDot`): a round ring, with a red dot inside when selected.
 *
 * Used on: Move-To (destination folder list, 22 dp) and Vault-Switcher (vault list, 24 dp).
 *
 * From the mockups: 1.5 dp ring. Selected = `ink` ring + `accent` centre (12 dp in a 24 dp ring,
 * 10 dp in a 22 dp ring). Unselected = `lineStrong` ring, empty. Disabled (the folder the note is
 * already in) = `line` ring, empty.
 */
package com.kunotes.ui.dotline

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kunotes.ui.theme.Dotline
import com.kunotes.ui.theme.KuNotesTheme

/** Ring diameter: 24 dp (Vault-Switcher) or 22 dp when `small` (Move-To). */
private val RingSize = 24.dp
private val SmallRingSize = 22.dp

/** The red centre is always 12 dp smaller than the ring: 12 dp in 24, 10 dp in 22. */
private val CentreInset = 12.dp

/**
 * A radio button drawn as a ring with a red centre dot.
 *
 * @param selected whether this option is the chosen one.
 * @param onClick called when tapped. Pass `null` when the whole list row is the radio button
 *   (the row handles taps and semantics; the dot only draws). This is the usual case.
 * @param enabled when false the ring is dimmed and taps are ignored.
 * @param small true for the 22 dp version used in the Move-To folder list.
 */
@Composable
fun RadioDot(
    selected: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    small: Boolean = false,
) {
    val colors = Dotline.colors
    val interactionSource = remember { MutableInteractionSource() }
    val ringSize = if (small) SmallRingSize else RingSize

    val ringColor by animateColorAsState(
        targetValue =
            when {
                selected -> colors.ink
                enabled -> colors.lineStrong
                else -> colors.line
            },
        animationSpec = tween(DOTLINE_MOTION_MILLIS),
        label = "ring",
    )
    val centreColor by animateColorAsState(
        targetValue = if (selected) colors.accent else Color.Transparent,
        animationSpec = tween(DOTLINE_MOTION_MILLIS),
        label = "centre",
    )

    // Only tappable on its own when it has a callback; a parent row usually handles taps.
    val selectModifier =
        if (onClick != null) {
            Modifier
                .selectable(
                    selected = selected,
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled,
                    role = Role.RadioButton,
                    onClick = onClick,
                ).sizeIn(minWidth = Dotline.size.touchTarget, minHeight = Dotline.size.touchTarget)
        } else {
            Modifier
        }

    Box(modifier = modifier.then(selectModifier), contentAlignment = Alignment.Center) {
        Box(
            modifier =
                Modifier
                    .focusRing(interactionSource, CircleShape)
                    .size(ringSize)
                    .border(Dotline.size.stroke, ringColor, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(ringSize - CentreInset)
                    .clip(CircleShape)
                    .background(centreColor),
            )
        }
    }
}

// ---- Previews -------------------------------------------------------------------------------

@Composable
private fun RadioPreviewContent() {
    var picked by remember { mutableIntStateOf(0) }
    Box(Modifier.background(Dotline.colors.bg).padding(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioDot(selected = picked == 0, onClick = { picked = 0 })
            RadioDot(selected = picked == 1, onClick = { picked = 1 })
            RadioDot(selected = picked == 2, onClick = { picked = 2 }, small = true)
            RadioDot(selected = false, onClick = {}, enabled = false, small = true)
        }
    }
}

@Preview(name = "Dark")
@Composable
private fun RadioDarkPreview() {
    KuNotesTheme(dark = true) { RadioPreviewContent() }
}

@Preview(name = "Light")
@Composable
private fun RadioLightPreview() {
    KuNotesTheme(dark = false) { RadioPreviewContent() }
}
