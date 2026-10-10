/**
 * BottomNav: the tab bar at the bottom of the main screens (SPEC §3 "BottomNav").
 *
 * 4 tabs: FILES, SEARCH, SYNC, VAULTS ([MainTab]). The active tab is `ink` with a 4 dp red dot
 * above its icon; inactive tabs are `inkFaint`. The bar is 72 dp plus the navigation-bar inset
 * (88 dp in the mockups), on `bg` with a `line` hairline on top.
 *
 * Seen in: Main (2.1), Empty-Vault (1.3), Vaults (6.2), Git-Sync (7.1).
 *
 * The bar works with any tab type that implements [BottomNavTab], so tests or other screens can
 * use their own tabs.
 */
package com.kunotes.ui.dotline

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.Text
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kunotes.ui.theme.Dotline
import com.kunotes.ui.theme.KuNotesTheme

/** Anything that can be a tab in [BottomNav]: a label (shown in caps) and an icon. */
interface BottomNavTab {
    val label: String
    val icon: ImageVector
}

/** The four main tabs of the app, in bar order (SPEC §4). */
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
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(Dotline.colors.bg)
                // Adds space for the system navigation bar (gesture pill or buttons) below the tabs.
                .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        // The hairline on top of the bar.
        Box(Modifier.fillMaxWidth().height(1.dp).background(Dotline.colors.line))
        Row(
            // selectableGroup tells TalkBack these tabs belong together ("tab 1 of 4").
            modifier = Modifier.fillMaxWidth().height(Dotline.size.tabBarHeight).selectableGroup(),
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

/** One tab: dot (red when active), icon, label. Every tab gets an equal share of the width. */
@Composable
private fun RowScope.BottomNavItem(
    tab: BottomNavTab,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val color = if (selected) Dotline.colors.ink else Dotline.colors.inkFaint
    Column(
        modifier =
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .focusRing(interactionSource, Dotline.radius.m)
                .selectable(
                    selected = selected,
                    interactionSource = interactionSource,
                    indication = null, // No Material ripple.
                    role = Role.Tab,
                    onClick = onClick,
                ),
        verticalArrangement = Arrangement.spacedBy(Dotline.space.s1, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // The dot keeps its 4 dp space on inactive tabs too, so the icons don't jump.
        Box(
            Modifier
                .size(Dotline.size.dot)
                .background(if (selected) Dotline.colors.accent else Color.Transparent, CircleShape),
        )
        Icon(tab.icon, contentDescription = null, tint = color) // The label below names the tab.
        Text(tab.label, style = Dotline.type.label, color = color, maxLines = 1)
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
    KuNotesTheme(dark = true) {
        Box(Modifier.background(Dotline.colors.bg).padding(16.dp)) { BottomNavPreviewContent() }
    }
}

@Preview(name = "Light", widthDp = 390)
@Composable
private fun BottomNavLightPreview() {
    KuNotesTheme(dark = false) {
        Box(Modifier.background(Dotline.colors.bg).padding(16.dp)) { BottomNavPreviewContent() }
    }
}
