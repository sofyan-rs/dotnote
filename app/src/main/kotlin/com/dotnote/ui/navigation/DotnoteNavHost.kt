/**
 * The app's navigation graph: which composable shows for each route in [Routes.kt].
 *
 * The start destination depends on [StartScreen]: the files of the last vault, or the
 * welcome screen. Opening a vault from the welcome screen replaces it, so Back doesn't
 * return to the welcome screen.
 */
package com.dotnote.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.dotnote.core.VaultPath
import com.dotnote.ui.dotline.BottomNav
import com.dotnote.ui.dotline.MainTab
import com.dotnote.ui.dotnoteViewModel
import com.dotnote.ui.editor.EditorScreen
import com.dotnote.ui.files.FilesScreen
import com.dotnote.ui.files.MoveToScreen
import com.dotnote.ui.lock.ChangePasswordScreen
import com.dotnote.ui.lock.LockActionsViewModel
import com.dotnote.ui.lock.SetPasswordScreen
import com.dotnote.ui.lock.UnlockScreen
import com.dotnote.ui.search.SearchScreen
import com.dotnote.ui.sync.CloneScreen
import com.dotnote.ui.sync.SyncScreen
import com.dotnote.ui.vaults.VaultNavigation
import com.dotnote.ui.vaults.VaultsScreen
import com.dotnote.ui.welcome.WelcomeScreen

/**
 * @param noteRequest a note path a home-screen widget asked to open, or `null`.
 * @param onNoteRequestHandled called once that note is shown.
 */
@OptIn(ExperimentalLayoutApi::class) // isImeVisible
@Composable
fun DotnoteNavHost(
    start: StartScreen,
    noteRequest: String? = null,
    onNoteRequestHandled: () -> Unit = {},
) {
    val navController = rememberNavController()
    val startRoute: Any = if (start is StartScreen.Welcome) Welcome(start.lostAccessTo) else Files

    // A widget tap: show its note on top of the Files tab (locked notes ask for the password first).
    val widgetLockActions = dotnoteViewModel { LockActionsViewModel(it) }
    LaunchedEffect(noteRequest) {
        val requested = noteRequest ?: return@LaunchedEffect
        if (navController.currentDestination?.hasRoute<Welcome>() == true) {
            navController.navigate(Files) { popUpTo<Welcome> { inclusive = true } }
        }
        val path = VaultPath(requested)
        val route: Any = if (widgetLockActions.needsUnlock(path)) Unlock(path.value) else Editor(path.value)
        navController.navigate(route) { popUpTo<Files>() }
        onNoteRequestHandled()
    }

    Box(Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = startRoute,
            // Screens slide in from the side. Between tabs the direction follows the bar (a tab to
            // the right comes in from the right); a new screen comes from the right, Back returns.
            enterTransition = { slideIntoContainer(slideDirection(), animationSpec = slideSpec) },
            exitTransition = { slideOutOfContainer(slideDirection(), animationSpec = slideSpec) },
            popEnterTransition = { slideIntoContainer(SlideDirection.End, animationSpec = slideSpec) },
            popExitTransition = { slideOutOfContainer(SlideDirection.End, animationSpec = slideSpec) },
        ) {
            composable<Welcome> { entry ->
                WelcomeScreen(
                    lostAccessTo = entry.toRoute<Welcome>().lostAccessTo,
                    onVaultOpened = {
                        navController.navigate(Files) { popUpTo<Welcome> { inclusive = true } }
                    },
                    onCloneFromGit = { navController.navigate(Clone) },
                )
            }
            composable<Files> {
                val lockActions = dotnoteViewModel { LockActionsViewModel(it) }
                FilesScreen(
                    onOpenNote = { path ->
                        // A locked note while the vault is locked asks for the password first (5.2).
                        navController.navigate(if (lockActions.needsUnlock(path)) Unlock(path.value) else Editor(path.value))
                    },
                    onMoveItem = { path -> navController.navigate(MoveTo(path.value)) },
                    onManageVaults = { navController.navigateToTab(MainTab.Vaults) },
                    onCloneFromGit = { navController.navigate(Clone) },
                    onSearch = { navController.navigateToTab(MainTab.Search) },
                    onLockNote = { path ->
                        lockActions.toggleLock(
                            path,
                            onNeedsPassword = { navController.navigate(SetPassword(path.value)) },
                            onNeedsUnlock = { navController.navigate(Unlock(path.value, toggleLock = true)) },
                        )
                    },
                )
            }
            composable<MoveTo> { entry ->
                MoveToScreen(path = entry.toRoute<MoveTo>().path, onDone = { navController.popBackStack() })
            }
            composable<Unlock> { entry ->
                val route = entry.toRoute<Unlock>()
                UnlockScreen(
                    path = route.path,
                    toggleLock = route.toggleLock,
                    onDone = {
                        if (route.toggleLock) {
                            navController.popBackStack()
                        } else {
                            // Replace the unlock screen, so Back from the editor returns to Files.
                            navController.navigate(Editor(route.path)) { popUpTo<Unlock> { inclusive = true } }
                        }
                    },
                    onCancel = { navController.popBackStack() },
                )
            }
            composable<SetPassword> { entry ->
                SetPasswordScreen(notePath = entry.toRoute<SetPassword>().path, onDone = { navController.popBackStack() })
            }
            composable<ChangePassword> {
                ChangePasswordScreen(onDone = { navController.popBackStack() })
            }
            composable<Search> {
                val lockActions = dotnoteViewModel { LockActionsViewModel(it) }
                SearchScreen(
                    onOpenNote = { path ->
                        navController.navigate(if (lockActions.needsUnlock(path)) Unlock(path.value) else Editor(path.value))
                    },
                )
            }
            composable<Sync> {
                SyncScreen(onCloneFromGit = { navController.navigate(Clone) })
            }
            composable<Clone> {
                CloneScreen(
                    // The new vault is open: show its files, with nothing to go back to (like Welcome).
                    onCloned = { navController.navigate(Files) { popUpTo(navController.graph.id) { inclusive = true } } },
                    onBack = { navController.popBackStack() },
                )
            }
            composable<Vaults> {
                VaultsScreen(
                    navigation =
                        VaultNavigation(
                            onOpenFiles = { navController.navigateToTab(MainTab.Files) },
                            onCloneFromGit = { navController.navigate(Clone) },
                            onSyncSettings = { navController.navigateToTab(MainTab.Sync) },
                            onNotesPassword = { hasPassword ->
                                navController.navigate(if (hasPassword) ChangePassword else SetPassword(null))
                            },
                            onNoVaultLeft = {
                                navController.navigate(Welcome()) { popUpTo(navController.graph.id) { inclusive = true } }
                            },
                        ),
                )
            }
            composable<Editor> { entry ->
                val lockActions = dotnoteViewModel { LockActionsViewModel(it) }
                EditorScreen(
                    path = entry.toRoute<Editor>().path,
                    onBack = { navController.popBackStack() },
                    onMove = { path -> navController.navigate(MoveTo(path.value)) },
                    onOpenNote = { path ->
                        navController.navigate(if (lockActions.needsUnlock(path)) Unlock(path.value) else Editor(path.value))
                    },
                    onNeedsUnlock = { path ->
                        navController.navigate(Unlock(path.value)) { popUpTo<Editor> { inclusive = true } }
                    },
                    onLockNote = { path ->
                        lockActions.toggleLock(
                            path,
                            onNeedsPassword = { navController.navigate(SetPassword(path.value)) },
                            onNeedsUnlock = { navController.navigate(Unlock(path.value, toggleLock = true)) },
                            // Locked or unlocked: the file has a new name, so reopen the editor on it.
                            onDone = { newPath -> navController.navigate(Editor(newPath.value)) { popUpTo<Editor> { inclusive = true } } },
                        )
                    },
                )
            }
        }

        // One bottom nav shared by the four tabs, floating over them. Its circle slides to the new
        // tab while the screens slide underneath.
        val backStackEntry by navController.currentBackStackEntryAsState()
        val currentTab = backStackEntry?.destination?.let(::tabOf)
        if (currentTab != null && !WindowInsets.isImeVisible) {
            BottomNav(
                tabs = MainTab.entries,
                selectedTab = currentTab,
                onTabClick = navController::navigateToTab,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

/** How long a screen takes to slide in or out. */
private val slideSpec = tween<IntOffset>(durationMillis = 320, easing = FastOutSlowInEasing)

/** The tab a destination belongs to, or `null` for other screens (editor, sheets, welcome…). */
private fun tabOf(destination: NavDestination): MainTab? =
    when {
        destination.hasRoute<Files>() -> MainTab.Files
        destination.hasRoute<Search>() -> MainTab.Search
        destination.hasRoute<Sync>() -> MainTab.Sync
        destination.hasRoute<Vaults>() -> MainTab.Vaults
        else -> null
    }

/**
 * Which way a forward navigation slides. Tab to tab: toward the tab's side of the bar.
 * Anything else (opening a note, a setting): the new screen comes from the right.
 */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.slideDirection(): SlideDirection {
    val from = tabOf(initialState.destination)?.ordinal
    val to = tabOf(targetState.destination)?.ordinal
    return if (from != null && to != null && to < from) SlideDirection.End else SlideDirection.Start
}
