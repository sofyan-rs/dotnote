/**
 * The frame of the four main tabs (FILES, SEARCH, SYNC, VAULTS): the screen content on top,
 * the bottom nav under it, and a snackbar host for messages like "moved to trash · UNDO".
 */
package com.kunotes.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.kunotes.ui.dotline.BottomNav
import com.kunotes.ui.dotline.MainTab
import com.kunotes.ui.theme.Dotline

/**
 * Lays out a tab screen. [content] gets the full area above the bottom nav; [overlay] is
 * drawn on top of it (floating button, snackbar), aligned by the caller.
 */
@Composable
fun MainTabs(
    selected: MainTab,
    onTabClick: (MainTab) -> Unit,
    modifier: Modifier = Modifier,
    overlay: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit = {},
    content: @Composable () -> Unit,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(Dotline.colors.bg),
    ) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .statusBarsPadding(),
        ) {
            content()
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) { overlay() }
        }
        BottomNav(tabs = MainTab.entries, selectedTab = selected, onTabClick = onTabClick)
    }
}

/**
 * Switches tabs the way bottom navigation should: one copy of each tab on the back stack, and
 * each tab keeps its scroll position when you come back to it.
 */
fun NavController.navigateToTab(tab: MainTab) {
    val route: Any =
        when (tab) {
            MainTab.Files -> Files
            MainTab.Search -> Search
            MainTab.Sync -> Sync
            MainTab.Vaults -> Vaults
        }
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
