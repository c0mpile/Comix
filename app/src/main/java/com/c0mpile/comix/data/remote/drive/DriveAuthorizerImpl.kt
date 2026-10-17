package com.c0mpile.comix.data.remote.drive

import android.accounts.Account
import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.c0mpile.comix.BuildConfig
import com.c0mpile.comix.data.local.dao.DriveAccountDao
import com.c0mpile.comix.di.IoDispatcher
import com.c0mpile.comix.domain.model.ComicError
import com.c0mpile.comix.domain.model.DriveAccount
import com.c0mpile.comix.domain.model.DriveAuthState
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Implementation of [DriveAuthorizer] using Credential Manager for Google identity
 * and Google Play Services Identity for OAuth 2.0 Drive read-only authorization.
 *
 * Access tokens are retained exclusively in memory and never persisted or logged.
 */
@Singleton
class DriveAuthorizerImpl(
    @param:ApplicationContext private val context: Context,
    private val driveAccountDao: DriveAccountDao,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val webClientId: String = BuildConfig.DRIVE_WEB_CLIENT_ID
) : DriveAuthorizer {

    @Inject
    constructor(
        @ApplicationContext context: Context,
        driveAccountDao: DriveAccountDao,
        @IoDispatcher ioDispatcher: CoroutineDispatcher
    ) : this(
        context = context,
        driveAccountDao = driveAccountDao,
        ioDispatcher = ioDispatcher,
        webClientId = BuildConfig.DRIVE_WEB_CLIENT_ID
    )

    private val _authState = MutableStateFlow<DriveAuthState>(DriveAuthState.SignedOut)
    private var cachedAccessToken: String? = null
    private var pendingAccountEmail: String? = null

    override fun observeAuthState(): Flow<DriveAuthState> =
        combine(_authState, driveAccountDao.observeAccount()) { authState, dbAccount ->
            when {
                authState is DriveAuthState.ReauthRequired -> authState
                dbAccount != null -> DriveAuthState.SignedIn(
                    DriveAccount(
                        email = dbAccount.email,
                        signedInAtEpochMillis = dbAccount.signedInAtEpochMillis
                    )
                )
                else -> DriveAuthState.SignedOut
            }
        }.distinctUntilChanged()

    override suspend fun signIn(activity: Activity): Result<DriveAccount> {
        if (webClientId.isBlank()) {
            return Result.failure(IllegalStateException("Google Drive is not configured in this build"))
        }

        return try {
            val credentialManager = CredentialManager.create(activity)
            val googleIdOption = GetSignInWithGoogleOption.Builder(webClientId)
                .build()
            val credentialRequest = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val credentialResult = credentialManager.getCredential(activity, credentialRequest)
            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credentialResult.credential.data)
            val email = googleIdTokenCredential.id

            val authRequestBuilder = AuthorizationRequest.builder()
                .setRequestedScopes(listOf(Scope(DRIVE_READONLY_SCOPE), Scope(DRIVE_APPDATA_SCOPE)))
            if (email.isNotBlank()) {
                authRequestBuilder.setAccount(Account(email, GOOGLE_ACCOUNT_TYPE))
            }
            val authRequest = authRequestBuilder.build()

            val authResult = Identity.getAuthorizationClient(activity).authorize(authRequest).await()
            if (authResult.hasResolution()) {
                val pendingIntent = authResult.pendingIntent
                if (pendingIntent != null) {
                    pendingAccountEmail = email
                    Result.failure(PendingConsentResolutionException(pendingIntent))
                } else {
                    Result.failure(IllegalStateException("Authorization requires resolution but pendingIntent is null"))
                }
            } else {
                val token = authResult.accessToken
                    ?: return Result.failure(IllegalStateException("No access token in authorization result"))
                cachedAccessToken = token
                val account = DriveAccount(
                    email = email,
                    signedInAtEpochMillis = System.currentTimeMillis()
                )
                _authState.value = DriveAuthState.SignedIn(account)
                Result.success(account)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: PendingConsentResolutionException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun completeAuthorizationFromIntent(
        activity: Activity,
        data: Intent?
    ): Result<DriveAccount> {
        return try {
            if (data == null) {
                return Result.failure(IllegalArgumentException("Authorization intent data is null"))
            }

            val authResult = Identity.getAuthorizationClient(activity).getAuthorizationResultFromIntent(data)
            if (authResult.hasResolution()) {
                val pendingIntent = authResult.pendingIntent
                if (pendingIntent != null) {
                    return Result.failure(PendingConsentResolutionException(pendingIntent))
                }
            }

            val token = authResult.accessToken
                ?: return Result.failure(IllegalStateException("No access token in authorization result"))
            cachedAccessToken = token

            @Suppress("DEPRECATION")
            // Suppressed: toGoogleSignInAccount() is deprecated in Play Services Auth 22 but is the only fallback on AuthorizationResult when pendingAccountEmail is unavailable
            val fallbackEmail = authResult.toGoogleSignInAccount()?.email
            val email = pendingAccountEmail
                ?: fallbackEmail
                ?: withContext(ioDispatcher) { driveAccountDao.getAccount()?.email }
                ?: ""
            pendingAccountEmail = null

            val account = DriveAccount(
                email = email,
                signedInAtEpochMillis = System.currentTimeMillis()
            )
            _authState.value = DriveAuthState.SignedIn(account)
            Result.success(account)
        } catch (e: CancellationException) {
            throw e
        } catch (e: PendingConsentResolutionException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getAccessToken(forceRefresh: Boolean): Result<String> = withContext(ioDispatcher) {
        if (!forceRefresh) {
            val token = cachedAccessToken
            if (!token.isNullOrBlank()) {
                return@withContext Result.success(token)
            }
        }

        val account = driveAccountDao.getAccount()
            ?: return@withContext Result.failure(DriveApiException(ComicError.AuthExpired))

        val result: Result<String> = try {
            val authRequestBuilder = AuthorizationRequest.builder()
                .setRequestedScopes(listOf(Scope(DRIVE_READONLY_SCOPE), Scope(DRIVE_APPDATA_SCOPE)))
            if (account.email.isNotBlank()) {
                authRequestBuilder.setAccount(Account(account.email, GOOGLE_ACCOUNT_TYPE))
            }
            val authRequest = authRequestBuilder.build()

            val authResult = Identity.getAuthorizationClient(context).authorize(authRequest).await()
            val token = authResult.accessToken
            if (!authResult.hasResolution() && token != null) {
                cachedAccessToken = token
                Result.success(token)
            } else {
                val fallbackRequest = AuthorizationRequest.builder()
                    .setRequestedScopes(listOf(Scope(DRIVE_READONLY_SCOPE)))
                    .apply {
                        if (account.email.isNotBlank()) {
                            setAccount(Account(account.email, GOOGLE_ACCOUNT_TYPE))
                        }
                    }.build()
                val fallbackResult = Identity.getAuthorizationClient(context).authorize(fallbackRequest).await()
                val fallbackToken = fallbackResult.accessToken
                if (fallbackResult.hasResolution() || fallbackToken == null) {
                    onAuthExpired("Consent resolution required for token refresh")
                    Result.failure(DriveApiException(ComicError.AuthExpired))
                } else {
                    cachedAccessToken = fallbackToken
                    Result.success(fallbackToken)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            try {
                val fallbackRequest = AuthorizationRequest.builder()
                    .setRequestedScopes(listOf(Scope(DRIVE_READONLY_SCOPE)))
                    .apply {
                        if (account.email.isNotBlank()) {
                            setAccount(Account(account.email, GOOGLE_ACCOUNT_TYPE))
                        }
                    }.build()
                val fallbackResult = Identity.getAuthorizationClient(context).authorize(fallbackRequest).await()
                val fallbackToken = fallbackResult.accessToken
                if (fallbackResult.hasResolution() || fallbackToken == null) {
                    onAuthExpired("Consent resolution required for token refresh")
                    Result.failure(DriveApiException(ComicError.AuthExpired))
                } else {
                    cachedAccessToken = fallbackToken
                    Result.success(fallbackToken)
                }
            } catch (fallbackEx: CancellationException) {
                throw fallbackEx
            } catch (fallbackEx: Exception) {
                onAuthExpired(fallbackEx.message ?: "Failed to refresh token")
                Result.failure(DriveApiException(ComicError.AuthExpired, cause = fallbackEx))
            }
        }
        result
    }

    override suspend fun onAuthExpired(reason: String) {
        cachedAccessToken = null
        _authState.value = DriveAuthState.ReauthRequired(reason)
    }

    override suspend fun signOut() {
        cachedAccessToken = null
        pendingAccountEmail = null
        _authState.value = DriveAuthState.SignedOut
        try {
            val credentialManager = CredentialManager.create(context)
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
        } catch (_: Exception) {
            // Ignore sign-out cleanup failures
        }
    }

    companion object {
        private const val DRIVE_READONLY_SCOPE = "https://www.googleapis.com/auth/drive.readonly"
        private const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"
        private const val GOOGLE_ACCOUNT_TYPE = "com.google"
    }
}
