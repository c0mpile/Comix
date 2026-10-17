package com.c0mpile.comix.data.remote.drive

import android.app.Activity
import android.content.Context
import app.cash.turbine.test
import com.c0mpile.comix.data.local.dao.DriveAccountDao
import com.c0mpile.comix.data.local.entity.DriveAccountEntity
import com.c0mpile.comix.domain.model.DriveAuthState
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [DriveAuthorizerImpl].
 */
class DriveAuthorizerImplTest {

    private val context = mockk<Context>(relaxed = true)
    private val driveAccountDao = mockk<DriveAccountDao>()
    private val accountFlow = MutableStateFlow<DriveAccountEntity?>(null)
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var authorizer: DriveAuthorizerImpl

    @Before
    fun setUp() {
        every { driveAccountDao.observeAccount() } returns accountFlow
        authorizer = DriveAuthorizerImpl(
            context = context,
            driveAccountDao = driveAccountDao,
            ioDispatcher = testDispatcher
        )
    }

    @Test
    fun observeAuthStateEmitsSignedOutWhenNoAccountInDb() = runTest(testDispatcher) {
        accountFlow.value = null

        authorizer.observeAuthState().test {
            assertEquals(DriveAuthState.SignedOut, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun observeAuthStateEmitsSignedInWhenAccountPresentInDb() = runTest(testDispatcher) {
        accountFlow.value = DriveAccountEntity(
            email = "user@example.com",
            signedInAtEpochMillis = 123456789L
        )

        authorizer.observeAuthState().test {
            val state = awaitItem()
            assertTrue(state is DriveAuthState.SignedIn)
            val signedIn = state as DriveAuthState.SignedIn
            assertEquals("user@example.com", signedIn.account.email)
            assertEquals(123456789L, signedIn.account.signedInAtEpochMillis)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun onAuthExpiredTransitionsStateToReauthRequired() = runTest(testDispatcher) {
        accountFlow.value = DriveAccountEntity(
            email = "user@example.com",
            signedInAtEpochMillis = 123456789L
        )

        authorizer.observeAuthState().test {
            assertTrue(awaitItem() is DriveAuthState.SignedIn)

            authorizer.onAuthExpired("Token expired")
            val reauthState = awaitItem()
            assertTrue(reauthState is DriveAuthState.ReauthRequired)
            assertEquals("Token expired", (reauthState as DriveAuthState.ReauthRequired).reason)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun signInFailsWhenWebClientIdIsBlank() = runTest(testDispatcher) {
        val blankAuthorizer = DriveAuthorizerImpl(
            context = context,
            driveAccountDao = driveAccountDao,
            ioDispatcher = testDispatcher,
            webClientId = ""
        )
        val mockActivity = mockk<Activity>()
        val result = blankAuthorizer.signIn(mockActivity)

        assertTrue(result.isFailure)
        assertEquals("Google Drive is not configured in this build", result.exceptionOrNull()?.message)
    }
}
