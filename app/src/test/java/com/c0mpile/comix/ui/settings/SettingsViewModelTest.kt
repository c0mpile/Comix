package com.c0mpile.comix.ui.settings

import app.cash.turbine.test
import com.c0mpile.comix.domain.model.AppThemeMode
import com.c0mpile.comix.domain.model.LibraryFolder
import com.c0mpile.comix.domain.model.ReaderScaleMode
import com.c0mpile.comix.domain.model.ReadingDirection
import com.c0mpile.comix.domain.model.ScanResult
import com.c0mpile.comix.domain.model.UserSettings
import com.c0mpile.comix.domain.usecase.AddLibraryFolderUseCase
import com.c0mpile.comix.domain.usecase.ObserveLibraryFoldersUseCase
import com.c0mpile.comix.domain.usecase.ObserveUserSettingsUseCase
import com.c0mpile.comix.domain.usecase.RemoveLibraryFolderUseCase
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

    private val settingsFlow = MutableStateFlow(UserSettings())
    private val foldersFlow = MutableStateFlow<List<LibraryFolder>>(emptyList())

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { observeUserSettingsUseCase() } returns settingsFlow
        every { observeLibraryFoldersUseCase() } returns foldersFlow

        coEvery { updateThemeModeUseCase(any()) } returns Unit
        coEvery { updateReadingDirectionUseCase(any()) } returns Unit
        coEvery { updateReaderScaleModeUseCase(any()) } returns Unit
        coEvery { updateKeepScreenOnUseCase(any()) } returns Unit
        coEvery { addLibraryFolderUseCase(any()) } returns Result.success(ScanResult(0, 0, 0, emptyList()))
        coEvery { removeLibraryFolderUseCase(any()) } returns Unit

        viewModel = SettingsViewModel(
            observeUserSettingsUseCase = observeUserSettingsUseCase,
            observeLibraryFoldersUseCase = observeLibraryFoldersUseCase,
            updateThemeModeUseCase = updateThemeModeUseCase,
            updateReadingDirectionUseCase = updateReadingDirectionUseCase,
            updateReaderScaleModeUseCase = updateReaderScaleModeUseCase,
            updateKeepScreenOnUseCase = updateKeepScreenOnUseCase,
            addLibraryFolderUseCase = addLibraryFolderUseCase,
            removeLibraryFolderUseCase = removeLibraryFolderUseCase
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

        settingsFlow.value = customSettings
        foldersFlow.value = customFolders

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
}
