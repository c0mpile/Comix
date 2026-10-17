package com.c0mpile.comix.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.c0mpile.comix.ui.drive.DriveFolderPickerScreen
import com.c0mpile.comix.ui.library.LibraryScreen
import com.c0mpile.comix.ui.reader.ReaderScreen
import com.c0mpile.comix.ui.settings.SettingsScreen

/**
 * Top-level navigation host for the Comix single-activity application.
 */
@Composable
fun ComixNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = LibraryRoute,
        modifier = modifier
    ) {
        composable<LibraryRoute> {
            LibraryScreen(
                onOpenComic = { comicKey ->
                    navController.navigate(ReaderRoute(comicKey))
                },
                onOpenSettings = {
                    navController.navigate(SettingsRoute)
                }
            )
        }

        composable<ReaderRoute> {
            ReaderScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable<SettingsRoute> {
            SettingsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToDriveFolderPicker = {
                    navController.navigate(DriveFolderPickerRoute)
                }
            )
        }

        composable<DriveFolderPickerRoute> {
            DriveFolderPickerScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
