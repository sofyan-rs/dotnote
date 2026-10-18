/**
 * The frame of the four main tabs (FILES, SEARCH, SYNC, VAULTS): the screen content and a
 * layer for floating things (the "New note" button, snackbars).
 *
 * The bottom nav itself is not drawn here: [DotnoteNavHost] draws one shared nav over all
 * tabs, so its active circle can slide from tab to tab. The nav floats over the content, so
 * this frame tells each screen how much space to leave at the bottom ([LocalBottomNavSpace]).
 */
package com.dotnote.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.dotnote.ui.dotline.MainTab
import com.dotnote.ui.dotline.bottomNavHeight
import com.dotnote.ui.theme.Dotline

/**
 * Space the floating bottom nav covers at the bottom of a tab screen. Scrolling lists add it
 * as bottom padding so their last row can scroll above the nav. 0 while the keyboard is open
 * (the nav hides then).
 */
val LocalBottomNavSpace = compositionLocalOf { 0.dp }

/**
 * Lays out a tab screen. [content] gets the whole screen below the status bar (the nav floats
 * over its bottom); [overlay] is drawn above the nav (floating button, snackbar), aligned by
 * the caller.
 */
@OptIn(ExperimentalLayoutApi::class) // isImeVisible
@Composable
fun MainTabs(
    modifier: Modifier = Modifier,
    overlay: @Composable BoxScope.() -> Unit = {},
    content: @Composable () -> Unit,
) {
    // While the keyboard is open the nav hides, and screens use imePadding() instead.
    val navSpace: Dp = if (WindowInsets.isImeVisible) 0.dp else bottomNavHeight()
    CompositionLocalProvider(LocalBottomNavSpace provides navSpace) {
        Box(
            modifier
                .fillMaxSize()
                .background(Dotline.colors.bg)
                .statusBarsPadding(),
        ) {
            content()
            Box(Modifier.fillMaxSize().padding(bottom = navSpace), contentAlignment = Alignment.BottomCenter) { overlay() }
        }
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
