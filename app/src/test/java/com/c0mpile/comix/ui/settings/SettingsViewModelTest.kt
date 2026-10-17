package com.c0mpile.comix.ui.settings

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import app.cash.turbine.test
import com.c0mpile.comix.data.remote.drive.PendingConsentResolutionException
import com.c0mpile.comix.domain.model.AppThemeMode
import com.c0mpile.comix.domain.model.DriveAccount
import com.c0mpile.comix.domain.model.DriveAuthState
import com.c0mpile.comix.domain.model.DriveFolder
import com.c0mpile.comix.domain.model.LibraryFolder
import com.c0mpile.comix.domain.model.ReaderScaleMode
import com.c0mpile.comix.domain.model.ReadingDirection
import com.c0mpile.comix.domain.model.ScanResult
import com.c0mpile.comix.domain.model.UserSettings
import com.c0mpile.comix.domain.usecase.AddLibraryFolderUseCase
import com.c0mpile.comix.domain.usecase.CompleteDriveAuthUseCase
import com.c0mpile.comix.domain.usecase.ObserveDriveAuthStateUseCase
import com.c0mpile.comix.domain.usecase.ObserveDriveFoldersUseCase
import com.c0mpile.comix.domain.usecase.ObserveLibraryFoldersUseCase
import com.c0mpile.comix.domain.usecase.ObserveUserSettingsUseCase
import com.c0mpile.comix.domain.usecase.RemoveDriveFolderUseCase
import com.c0mpile.comix.domain.usecase.RemoveLibraryFolderUseCase
import com.c0mpile.comix.domain.usecase.SignInToDriveUseCase
import com.c0mpile.comix.domain.usecase.SignOutOfDriveUseCase
import com.c0mpile.comix.domain.usecase.UpdateKeepScreenOnUseCase
import com.c0mpile.comix.domain.usecase.UpdateReaderScaleModeUseCase
import com.c0mpile.comix.domain.usecase.UpdateReadingDirectionUseCase
import com.c0mpile.comix.domain.usecase.UpdateThemeModeUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [SettingsViewModel].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val observeUserSettingsUseCase = mockk<ObserveUserSettingsUseCase>()
    private val observeLibraryFoldersUseCase = mockk<ObserveLibraryFoldersUseCase>()
    private val updateThemeModeUseCase = mockk<UpdateThemeModeUseCase>()
    private val updateReadingDirectionUseCase = mockk<UpdateReadingDirectionUseCase>()
    private val updateReaderScaleModeUseCase = mockk<UpdateReaderScaleModeUseCase>()
    private val updateKeepScreenOnUseCase = mockk<UpdateKeepScreenOnUseCase>()
    private val addLibraryFolderUseCase = mockk<AddLibraryFolderUseCase>()
    private val removeLibraryFolderUseCase = mockk<RemoveLibraryFolderUseCase>()
    private val observeDriveAuthStateUseCase = mockk<ObserveDriveAuthStateUseCase>()
    private val observeDriveFoldersUseCase = mockk<ObserveDriveFoldersUseCase>()
    private val signInToDriveUseCase = mockk<SignInToDriveUseCase>()
    private val completeDriveAuthUseCase = mockk<CompleteDriveAuthUseCase>()
    private val signOutOfDriveUseCase = mockk<SignOutOfDriveUseCase>()
    private val removeDriveFolderUseCase = mockk<RemoveDriveFolderUseCase>()

    private val settingsFlow = MutableStateFlow(UserSettings())
    private val foldersFlow = MutableStateFlow<List<LibraryFolder>>(emptyList())
    private val driveAuthFlow = MutableStateFlow<DriveAuthState>(DriveAuthState.SignedOut)
    private val driveFoldersFlow = MutableStateFlow<List<DriveFolder>>(emptyList())

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { observeUserSettingsUseCase() } returns settingsFlow
        every { observeLibraryFoldersUseCase() } returns foldersFlow
        every { observeDriveAuthStateUseCase() } returns driveAuthFlow
        every { observeDriveFoldersUseCase() } returns driveFoldersFlow

        coEvery { updateThemeModeUseCase(any()) } returns Unit
        coEvery { updateReadingDirectionUseCase(any()) } returns Unit
        coEvery { updateReaderScaleModeUseCase(any()) } returns Unit
        coEvery { updateKeepScreenOnUseCase(any()) } returns Unit
        coEvery { addLibraryFolderUseCase(any()) } returns Result.success(ScanResult(0, 0, 0, emptyList()))
        coEvery { removeLibraryFolderUseCase(any()) } returns Unit
        coEvery { signInToDriveUseCase(any()) } returns Result.success(DriveAccount("reader@example.com", 1000L))
        coEvery { completeDriveAuthUseCase(any(), any()) } returns Result.success(DriveAccount("reader@example.com", 1000L))
        coEvery { signOutOfDriveUseCase() } returns Unit
        coEvery { removeDriveFolderUseCase(any()) } returns Unit

        viewModel = SettingsViewModel(
            observeUserSettingsUseCase = observeUserSettingsUseCase,
            observeLibraryFoldersUseCase = observeLibraryFoldersUseCase,
            updateThemeModeUseCase = updateThemeModeUseCase,
            updateReadingDirectionUseCase = updateReadingDirectionUseCase,
            updateReaderScaleModeUseCase = updateReaderScaleModeUseCase,
            updateKeepScreenOnUseCase = updateKeepScreenOnUseCase,
            addLibraryFolderUseCase = addLibraryFolderUseCase,
            removeLibraryFolderUseCase = removeLibraryFolderUseCase,
            observeDriveAuthStateUseCase = observeDriveAuthStateUseCase,
            observeDriveFoldersUseCase = observeDriveFoldersUseCase,
            signInToDriveUseCase = signInToDriveUseCase,
            completeDriveAuthUseCase = completeDriveAuthUseCase,
            signOutOfDriveUseCase = signOutOfDriveUseCase,
            removeDriveFolderUseCase = removeDriveFolderUseCase
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun uiStateCombinesSettingsAndFoldersEmissions() = runTest(testDispatcher) {
        val customSettings = UserSettings(
            themeMode = AppThemeMode.DARK,
            readingDirection = ReadingDirection.RIGHT_TO_LEFT,
            readerScaleMode = ReaderScaleMode.FIT_WIDTH,
            keepScreenOn = false
        )
        val customFolders = listOf(
            LibraryFolder("content://tree/f1", "Folder 1", 1000L, 2000L, true),
            LibraryFolder("content://tree/f2", "Folder 2", 3000L, null, false)
        )
        val customDriveFolders = listOf(
            DriveFolder("folder_1", "Drive Manga", 1000L, null)
        )
        val signedInAuth = DriveAuthState.SignedIn(DriveAccount("reader@example.com", 1000L))

        settingsFlow.value = customSettings
        foldersFlow.value = customFolders
        driveAuthFlow.value = signedInAuth
        driveFoldersFlow.value = customDriveFolders

        viewModel.uiState.test {
            awaitItem()
            testScheduler.advanceUntilIdle()
            val state = viewModel.uiState.value
            assertEquals(AppThemeMode.DARK, state.userSettings.themeMode)
            assertEquals(ReadingDirection.RIGHT_TO_LEFT, state.userSettings.readingDirection)
            assertEquals(ReaderScaleMode.FIT_WIDTH, state.userSettings.readerScaleMode)
            assertFalse(state.userSettings.keepScreenOn)
            assertEquals(2, state.folders.size)
            assertEquals("Folder 1", state.folders[0].displayName)
            assertFalse(state.folders[1].hasPersistedPermission)
            assertEquals(signedInAuth, state.driveAuthState)
            assertEquals(1, state.driveFolders.size)
            assertEquals("Drive Manga", state.driveFolders[0].displayName)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun themeModeSelectedInvokesUpdateThemeModeUseCase() = runTest(testDispatcher) {
        viewModel.uiState.test {
            awaitItem()

            viewModel.onEvent(SettingsUiEvent.ThemeModeSelected(AppThemeMode.LIGHT))
            testScheduler.advanceUntilIdle()

            coVerify(exactly = 1) { updateThemeModeUseCase(AppThemeMode.LIGHT) }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun readingDirectionSelectedInvokesUpdateReadingDirectionUseCase() = runTest(testDispatcher) {
        viewModel.uiState.test {
            awaitItem()

            viewModel.onEvent(SettingsUiEvent.ReadingDirectionSelected(ReadingDirection.RIGHT_TO_LEFT))
            testScheduler.advanceUntilIdle()

            coVerify(exactly = 1) { updateReadingDirectionUseCase(ReadingDirection.RIGHT_TO_LEFT) }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun readerScaleModeSelectedInvokesUpdateReaderScaleModeUseCase() = runTest(testDispatcher) {
        viewModel.uiState.test {
            awaitItem()

            viewModel.onEvent(SettingsUiEvent.ReaderScaleModeSelected(ReaderScaleMode.FIT_WIDTH))
            testScheduler.advanceUntilIdle()

            coVerify(exactly = 1) { updateReaderScaleModeUseCase(ReaderScaleMode.FIT_WIDTH) }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun keepScreenOnToggledInvokesUpdateKeepScreenOnUseCase() = runTest(testDispatcher) {
        viewModel.uiState.test {
            awaitItem()

            viewModel.onEvent(SettingsUiEvent.KeepScreenOnToggled(false))
            testScheduler.advanceUntilIdle()

            coVerify(exactly = 1) { updateKeepScreenOnUseCase(false) }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun folderPickedInvokesAddLibraryFolderUseCase() = runTest(testDispatcher) {
        viewModel.uiState.test {
            awaitItem()

            viewModel.onEvent(SettingsUiEvent.FolderPicked("content://tree/new_folder"))
            testScheduler.advanceUntilIdle()

            coVerify(exactly = 1) { addLibraryFolderUseCase("content://tree/new_folder") }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun removeFolderRequestedInvokesRemoveLibraryFolderUseCase() = runTest(testDispatcher) {
        viewModel.uiState.test {
            awaitItem()

            viewModel.onEvent(SettingsUiEvent.RemoveFolderRequested("content://tree/to_remove"))
            testScheduler.advanceUntilIdle()

            coVerify(exactly = 1) { removeLibraryFolderUseCase("content://tree/to_remove") }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun signInToDriveClickedInvokesSignInToDriveUseCase() = runTest(testDispatcher) {
        val mockActivity = mockk<Activity>()

        viewModel.uiState.test {
            awaitItem()

            viewModel.onEvent(SettingsUiEvent.SignInToDriveClicked(mockActivity))
            testScheduler.advanceUntilIdle()

            coVerify(exactly = 1) { signInToDriveUseCase(mockActivity) }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun signInToDrivePendingConsentUpdatesPendingConsentIntent() = runTest(testDispatcher) {
        val mockActivity = mockk<Activity>()
        val mockPendingIntent = mockk<PendingIntent>()
        coEvery { signInToDriveUseCase(mockActivity) } returns Result.failure(
            PendingConsentResolutionException(mockPendingIntent)
        )

        viewModel.uiState.test {
            awaitItem()

            viewModel.onEvent(SettingsUiEvent.SignInToDriveClicked(mockActivity))
            testScheduler.advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(mockPendingIntent, state.pendingConsentIntent)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun driveConsentResultInvokesCompleteDriveAuthUseCase() = runTest(testDispatcher) {
        val mockActivity = mockk<Activity>()
        val mockIntent = mockk<Intent>()

        viewModel.uiState.test {
            awaitItem()

            viewModel.onEvent(SettingsUiEvent.DriveConsentResult(mockActivity, mockIntent))
            testScheduler.advanceUntilIdle()

            coVerify(exactly = 1) { completeDriveAuthUseCase(mockActivity, mockIntent) }
            assertNull(viewModel.uiState.value.pendingConsentIntent)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun signOutOfDriveClickedInvokesSignOutOfDriveUseCase() = runTest(testDispatcher) {
        viewModel.uiState.test {
            awaitItem()

            viewModel.onEvent(SettingsUiEvent.SignOutOfDriveClicked)
            testScheduler.advanceUntilIdle()

            coVerify(exactly = 1) { signOutOfDriveUseCase() }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun removeDriveFolderRequestedInvokesRemoveDriveFolderUseCase() = runTest(testDispatcher) {
        viewModel.uiState.test {
            awaitItem()

            viewModel.onEvent(SettingsUiEvent.RemoveDriveFolderRequested("folder_to_remove"))
            testScheduler.advanceUntilIdle()

            coVerify(exactly = 1) { removeDriveFolderUseCase("folder_to_remove") }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun snackbarDismissedClearsSnackbarMessage() = runTest(testDispatcher) {
        val mockActivity = mockk<Activity>()
        coEvery { signInToDriveUseCase(mockActivity) } returns Result.failure(
            IllegalStateException("Google Drive is not configured in this build")
        )

        viewModel.uiState.test {
            awaitItem()

            viewModel.onEvent(SettingsUiEvent.SignInToDriveClicked(mockActivity))
            testScheduler.advanceUntilIdle()

            assertEquals("Google Drive is not configured in this build", viewModel.uiState.value.snackbarMessage)

            viewModel.onEvent(SettingsUiEvent.SnackbarDismissed)
            testScheduler.advanceUntilIdle()

            assertNull(viewModel.uiState.value.snackbarMessage)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
