package com.c0mpile.comix.data.remote.drive

import android.app.Activity
import android.content.Intent
import com.c0mpile.comix.domain.model.ComicError
import com.c0mpile.comix.domain.model.DriveAccount
import com.c0mpile.comix.domain.model.DriveAuthState
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Baseline implementation of [DriveAuthorizer] providing the required Hilt binding ahead of full Credential Manager wiring.
 */
@Singleton
class DriveAuthorizerImpl @Inject constructor() : DriveAuthorizer {

    private val _authState = MutableStateFlow<DriveAuthState>(DriveAuthState.SignedOut)

    override fun observeAuthState(): Flow<DriveAuthState> = _authState.asStateFlow()

    override suspend fun signIn(activity: Activity): Result<DriveAccount> {
        return Result.failure(UnsupportedOperationException("Sign-in will be implemented in Task 4"))
    }

    override suspend fun completeAuthorizationFromIntent(
        activity: Activity,
        data: Intent?
    ): Result<DriveAccount> {
        return Result.failure(UnsupportedOperationException("Authorization resolution will be implemented in Task 4"))
    }

    override suspend fun getAccessToken(forceRefresh: Boolean): Result<String> {
        return Result.failure(DriveApiException(ComicError.AuthExpired))
    }

    override suspend fun onAuthExpired(reason: String) {
        _authState.value = DriveAuthState.ReauthRequired(reason)
    }

    override suspend fun signOut() {
        _authState.value = DriveAuthState.SignedOut
    }
}
