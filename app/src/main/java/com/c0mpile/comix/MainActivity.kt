package com.c0mpile.comix

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.c0mpile.comix.domain.model.AppThemeMode
import com.c0mpile.comix.ui.navigation.ComixNavHost
import com.c0mpile.comix.ui.theme.ComixTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single host activity for the Comix application.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val userSettings by mainViewModel.userSettings.collectAsStateWithLifecycle()
            val isDarkTheme = when (userSettings.themeMode) {
                AppThemeMode.SYSTEM -> isSystemInDarkTheme()
                AppThemeMode.LIGHT -> false
                AppThemeMode.DARK -> true
            }

            ComixTheme(darkTheme = isDarkTheme) {
                ComixNavHost()
            }
        }
    }
}
