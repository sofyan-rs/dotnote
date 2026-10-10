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
import com.kunotes.ui.kuNotesViewModel
import com.kunotes.ui.lock.ChangePasswordScreen
import com.kunotes.ui.lock.LockActionsViewModel
import com.kunotes.ui.lock.SetPasswordScreen
import com.kunotes.ui.lock.UnlockScreen
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
                onCloneFromGit = { navController.navigate(Sync) },
            )
        }
        composable<Files> {
            val lockActions = kuNotesViewModel { LockActionsViewModel(it) }
            FilesScreen(
                onTabClick = navController::navigateToTab,
                onOpenNote = { path ->
                    // A locked note while the vault is locked asks for the password first (5.2).
                    navController.navigate(if (lockActions.needsUnlock(path)) Unlock(path.value) else Editor(path.value))
                },
                onMoveItem = { path -> navController.navigate(MoveTo(path.value)) },
                onSwitchVault = { navController.navigateToTab(com.kunotes.ui.dotline.MainTab.Vaults) },
                onSearch = { navController.navigateToTab(com.kunotes.ui.dotline.MainTab.Search) },
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
        composable<Search> { PlaceholderTab(com.kunotes.ui.dotline.MainTab.Search, navController::navigateToTab) }
        composable<Sync> { PlaceholderTab(com.kunotes.ui.dotline.MainTab.Sync, navController::navigateToTab) }
        composable<Vaults> { PlaceholderTab(com.kunotes.ui.dotline.MainTab.Vaults, navController::navigateToTab) }
        composable<Editor> { entry ->
            PlaceholderEditor(path = entry.toRoute<Editor>().path, onBack = { navController.popBackStack() })
        }
    }
}
