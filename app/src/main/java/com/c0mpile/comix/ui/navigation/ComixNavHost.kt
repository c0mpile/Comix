package com.c0mpile.comix.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.c0mpile.comix.ui.library.LibraryScreen
import com.c0mpile.comix.ui.reader.ReaderScreen

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
    }
}
