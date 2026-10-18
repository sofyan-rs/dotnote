/**
 * BottomNav: the tab bar at the bottom of the main screens.
 *
 * 4 tabs: FILES, SEARCH, SYNC, VAULTS ([MainTab]). Styled after Nothing OS 5.0: a floating
 * pill (`navBar`) with icons only. The active tab sits in a circle (`navSelected`) and is `ink`;
 * inactive tabs are `inkFaint`. The tab names are read out by TalkBack, not shown.
 *
 * Seen in: Main (2.1), Empty-Vault (1.3), Vaults (6.2), Git-Sync (7.1).
 *
 * The bar works with any tab type that implements [BottomNavTab], so tests or other screens can
 * use their own tabs.
 */
package com.dotnote.ui.dotline

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dotnote.ui.theme.Dotline
import com.dotnote.ui.theme.DotnoteTheme

/** Anything that can be a tab in [BottomNav]: a label (shown in caps) and an icon. */
interface BottomNavTab {
    val label: String
    val icon: ImageVector
}

/** The four main tabs of the app, in bar order. */
enum class MainTab(
    override val label: String,
    override val icon: ImageVector,
) : BottomNavTab {
    Files("FILES", DotlineIcons.Folder),
    Search("SEARCH", DotlineIcons.Search),
    Sync("SYNC", DotlineIcons.GitBranch),
    Vaults("VAULTS", DotlineIcons.Sliders),
}

/**
 * The bottom tab bar.
 *
 * @param tabs the tabs in order, e.g. `MainTab.entries`.
 * @param selectedTab the active tab.
 * @param onTabClick called with the tapped tab; navigate there.
 */
@Composable
fun <T : BottomNavTab> BottomNav(
    tabs: List<T>,
    selectedTab: T,
    onTabClick: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .background(Dotline.colors.bg)
                // Adds space for the system navigation bar (gesture pill or buttons) below the tabs.
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(vertical = Dotline.space.s3),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier =
                Modifier
                    .height(Dotline.size.tabBarHeight)
                    .background(Dotline.colors.navBar, Dotline.radius.pill)
                    .padding(PillPadding)
                    // selectableGroup tells TalkBack these tabs belong together ("tab 1 of 4").
                    .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(Dotline.space.s3),
        ) {
            tabs.forEach { tab ->
                BottomNavItem(
                    tab = tab,
                    selected = tab == selectedTab,
                    onClick = { onTabClick(tab) },
                )
            }
        }
    }
}

/** Space between the pill's edge and the circle of the active tab. */
private val PillPadding = 6.dp

/** Each tab is a circle as tall as the pill's inside (64 − 2 × 6 = 52 dp, above the 48 dp touch minimum). */
private val ItemSize = Dotline.size.tabBarHeight - PillPadding * 2

/** One tab: an icon, in a circle when active. */
@Composable
private fun BottomNavItem(
    tab: BottomNavTab,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    // The circle and icon colour fade between states instead of jumping.
    val circleColor by animateColorAsState(
        targetValue = if (selected) Dotline.colors.navSelected else Color.Transparent,
        animationSpec = tween(DOTLINE_MOTION_MILLIS),
        label = "tabCircle",
    )
    val iconColor by animateColorAsState(
        targetValue = if (selected) Dotline.colors.ink else Dotline.colors.inkFaint,
        animationSpec = tween(DOTLINE_MOTION_MILLIS),
        label = "tabIcon",
    )
    Box(
        modifier =
            Modifier
                .size(ItemSize)
                .pressScale(interactionSource)
                .focusRing(interactionSource, CircleShape)
                .background(circleColor, CircleShape)
                .selectable(
                    selected = selected,
                    interactionSource = interactionSource,
                    indication = null, // No Material ripple.
                    role = Role.Tab,
                    onClick = onClick,
                ).semantics { contentDescription = tab.label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(tab.icon, contentDescription = null, tint = iconColor)
    }
}

@Composable
private fun BottomNavPreviewContent() {
    var selected by remember { mutableStateOf(MainTab.Files) }
    BottomNav(tabs = MainTab.entries, selectedTab = selected, onTabClick = { selected = it })
}

@Preview(name = "Dark", widthDp = 390)
@Composable
private fun BottomNavDarkPreview() {
    DotnoteTheme(dark = true) {
        Box(Modifier.background(Dotline.colors.bg).padding(16.dp)) { BottomNavPreviewContent() }
    }
}

@Preview(name = "Light", widthDp = 390)
@Composable
private fun BottomNavLightPreview() {
    DotnoteTheme(dark = false) {
        Box(Modifier.background(Dotline.colors.bg).padding(16.dp)) { BottomNavPreviewContent() }
    }
}
