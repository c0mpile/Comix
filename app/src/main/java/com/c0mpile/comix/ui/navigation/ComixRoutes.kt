package com.c0mpile.comix.ui.navigation

import kotlinx.serialization.Serializable

/**
 * Type-safe navigation destinations for the single-activity Compose NavHost.
 */
@Serializable
data object LibraryRoute

@Serializable
data class ReaderRoute(val comicKey: String)
