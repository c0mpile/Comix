package com.c0mpile.comix

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.c0mpile.comix.ui.navigation.ComixNavHost
import com.c0mpile.comix.ui.theme.ComixTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single host activity for the Comix application.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ComixTheme {
                ComixNavHost()
            }
        }
    }
}
