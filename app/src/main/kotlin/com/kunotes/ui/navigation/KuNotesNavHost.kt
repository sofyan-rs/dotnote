/**
 * The app's navigation graph: which composable shows for each route in [Routes.kt].
 *
 * The start destination depends on [StartScreen]: the files of the last vault, or the
 * welcome screen. Opening a vault from the welcome screen replaces it, so Back doesn't
 * return to the welcome screen.
 */
package com.kunotes.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.kunotes.ui.files.FilesScreen
import com.kunotes.ui.files.MoveToScreen
import com.kunotes.ui.sync.CloneScreen
import com.kunotes.ui.sync.SyncScreen
import com.kunotes.ui.welcome.WelcomeScreen

@Composable
fun KuNotesNavHost(start: StartScreen) {
    val navController = rememberNavController()
    val startRoute: Any = if (start is StartScreen.Files) Files else Welcome

    NavHost(navController = navController, startDestination = startRoute) {
        composable<Welcome> {
            WelcomeScreen(
                lostAccessTo = (start as? StartScreen.Welcome)?.lostAccessTo,
                onVaultOpened = {
                    navController.navigate(Files) { popUpTo<Welcome> { inclusive = true } }
                },
                onCloneFromGit = { navController.navigate(Clone) },
            )
        }
        composable<Files> {
            FilesScreen(
                onTabClick = navController::navigateToTab,
                onOpenNote = { path -> navController.navigate(Editor(path.value)) },
                onMoveItem = { path -> navController.navigate(MoveTo(path.value)) },
                onSwitchVault = { navController.navigateToTab(com.kunotes.ui.dotline.MainTab.Vaults) },
                onSearch = { navController.navigateToTab(com.kunotes.ui.dotline.MainTab.Search) },
                onLockNote = { /* Phase 8: locked notes */ },
            )
        }
        composable<MoveTo> { entry ->
            MoveToScreen(path = entry.toRoute<MoveTo>().path, onDone = { navController.popBackStack() })
        }
        composable<Search> { PlaceholderTab(com.kunotes.ui.dotline.MainTab.Search, navController::navigateToTab) }
        composable<Sync> {
            SyncScreen(onTabClick = navController::navigateToTab, onCloneFromGit = { navController.navigate(Clone) })
        }
        composable<Clone> {
            CloneScreen(
                // The new vault is open: show its files, with nothing to go back to (like Welcome).
                onCloned = { navController.navigate(Files) { popUpTo(navController.graph.id) { inclusive = true } } },
                onBack = { navController.popBackStack() },
            )
        }
        composable<Vaults> { PlaceholderTab(com.kunotes.ui.dotline.MainTab.Vaults, navController::navigateToTab) }
        composable<Editor> { entry ->
            PlaceholderEditor(path = entry.toRoute<Editor>().path, onBack = { navController.popBackStack() })
        }
    }
}
