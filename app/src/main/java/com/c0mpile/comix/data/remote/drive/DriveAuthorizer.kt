package com.c0mpile.comix.data.remote.drive

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import com.c0mpile.comix.domain.model.DriveAccount
import com.c0mpile.comix.domain.model.DriveAuthState
import kotlinx.coroutines.flow.Flow

/**
 * Exception indicating that Google Play Services requires interactive user consent
 * via [pendingIntent] before issuing a Google Drive OAuth access token.
 */
class PendingConsentResolutionException(
    val pendingIntent: PendingIntent,
    message: String = "User consent required for Google Drive access"
) : Exception(message)

/**
 * Manages Google sign-in, OAuth 2.0 scope authorization, and in-memory access token retrieval.
 *
 * Access tokens must never be logged or written to disk by the application.
 */
interface DriveAuthorizer {
    /**
     * Observes the current [DriveAuthState].
     */
    fun observeAuthState(): Flow<DriveAuthState>

    /**
     * Initiates interactive Google sign-in and Drive scope authorization using [activity].
     */
    suspend fun signIn(activity: Activity): Result<DriveAccount>

    /**
     * Completes a pending OAuth consent resolution flow after the user finishes [PendingIntent].
     */
    suspend fun completeAuthorizationFromIntent(
        activity: Activity,
        data: Intent?
    ): Result<DriveAccount> =
        Result.failure(UnsupportedOperationException("Not implemented"))

    /**
     * Returns a valid OAuth 2.0 Bearer access token, refreshing it if [forceRefresh] is true.
     */
    suspend fun getAccessToken(forceRefresh: Boolean = false): Result<String>

    /**
     * Transitions the auth state to [DriveAuthState.ReauthRequired] when a token refresh fails
     * or access has been revoked.
     */
    suspend fun onAuthExpired(reason: String)

    /**
     * Clears in-memory tokens, Credential Manager state, and local Drive account state.
     */
    suspend fun signOut()
}
