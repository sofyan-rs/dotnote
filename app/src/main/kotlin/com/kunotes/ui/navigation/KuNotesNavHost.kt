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
import com.kunotes.ui.dotline.MainTab
import com.kunotes.ui.files.FilesScreen
import com.kunotes.ui.files.MoveToScreen
import com.kunotes.ui.kuNotesViewModel
import com.kunotes.ui.lock.ChangePasswordScreen
import com.kunotes.ui.lock.LockActionsViewModel
import com.kunotes.ui.lock.SetPasswordScreen
import com.kunotes.ui.lock.UnlockScreen
import com.kunotes.ui.search.SearchScreen
import com.kunotes.ui.vaults.VaultNavigation
import com.kunotes.ui.vaults.VaultsScreen
import com.kunotes.ui.welcome.WelcomeScreen

@Composable
fun KuNotesNavHost(start: StartScreen) {
    val navController = rememberNavController()
    val startRoute: Any = if (start is StartScreen.Welcome) Welcome(start.lostAccessTo) else Files

    NavHost(navController = navController, startDestination = startRoute) {
        composable<Welcome> { entry ->
            WelcomeScreen(
                lostAccessTo = entry.toRoute<Welcome>().lostAccessTo,
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
                onManageVaults = { navController.navigateToTab(MainTab.Vaults) },
                onCloneFromGit = { navController.navigateToTab(MainTab.Sync) },
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
            val lockActions = kuNotesViewModel { LockActionsViewModel(it) }
            SearchScreen(
                onTabClick = navController::navigateToTab,
                onOpenNote = { path ->
                    navController.navigate(if (lockActions.needsUnlock(path)) Unlock(path.value) else Editor(path.value))
                },
            )
        }
        composable<Sync> { PlaceholderTab(MainTab.Sync, navController::navigateToTab) }
        composable<Vaults> {
            VaultsScreen(
                onTabClick = navController::navigateToTab,
                navigation =
                    VaultNavigation(
                        onOpenFiles = { navController.navigateToTab(MainTab.Files) },
                        onCloneFromGit = { navController.navigateToTab(MainTab.Sync) },
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
            PlaceholderEditor(path = entry.toRoute<Editor>().path, onBack = { navController.popBackStack() })
        }
    }
}
