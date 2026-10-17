package com.c0mpile.comix.domain.model

/**
 * Authentication state for the Google Drive integration.
 */
sealed interface DriveAuthState {
    data object SignedOut : DriveAuthState
    data class SignedIn(val account: DriveAccount) : DriveAuthState
    data class ReauthRequired(val reason: String) : DriveAuthState
}
