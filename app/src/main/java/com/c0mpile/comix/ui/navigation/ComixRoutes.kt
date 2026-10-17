package com.c0mpile.comix.ui.navigation

import com.c0mpile.comix.domain.model.ComicLibrary
import kotlinx.serialization.Serializable

/**
 * Type-safe navigation destinations for the single-activity Compose NavHost.
 */
@Serializable
data object LibraryRoute

@Serializable
data class ReaderRoute(val comicKey: String)

/**
 * Route for the application settings screen.
 */
@Serializable
data object SettingsRoute

/**
 * Route for the Google Drive folder picker screen.
 */
@Serializable
data class DriveFolderPickerRoute(val libraryId: String = ComicLibrary.DEFAULT_COMICS_ID)
