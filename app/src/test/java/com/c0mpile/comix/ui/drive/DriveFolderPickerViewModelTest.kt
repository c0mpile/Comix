package com.c0mpile.comix.ui.drive

import app.cash.turbine.test
import com.c0mpile.comix.data.remote.drive.DriveApiException
import com.c0mpile.comix.domain.model.ComicError
import com.c0mpile.comix.domain.model.DriveBrowseItem
import com.c0mpile.comix.domain.model.ScanResult
import com.c0mpile.comix.domain.usecase.AddDriveFolderUseCase
import com.c0mpile.comix.domain.usecase.BrowseDriveFoldersUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
 * Unit tests for [DriveFolderPickerViewModel].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DriveFolderPickerViewModelTest {

    private val browseDriveFoldersUseCase = mockk<BrowseDriveFoldersUseCase>()
    private val addDriveFolderUseCase = mockk<AddDriveFolderUseCase>()
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var viewModel: DriveFolderPickerViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { browseDriveFoldersUseCase("root") } returns Result.success(
            listOf(
                DriveBrowseItem("f1", "Comics"),
                DriveBrowseItem("f2", "Manga")
            )
        )
        coEvery { addDriveFolderUseCase(any(), any(), any()) } returns Result.success(
            ScanResult(10, 0, 0, emptyList())
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initLoadsSubfoldersForRoot() = runTest(testDispatcher) {
        viewModel = DriveFolderPickerViewModel(
            browseDriveFoldersUseCase = browseDriveFoldersUseCase,
            addDriveFolderUseCase = addDriveFolderUseCase
        )

        viewModel.uiState.test {
            testScheduler.advanceUntilIdle()
            val state = viewModel.uiState.value
            assertFalse(state.isLoading)
            assertNull(state.error)
            assertEquals(1, state.breadcrumbs.size)
            assertEquals("root", state.currentFolder.folderId)
            assertEquals(2, state.subfolders.size)
            assertEquals("Comics", state.subfolders[0].name)
            assertEquals("Manga", state.subfolders[1].name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun subfolderClickedPushesBreadcrumbAndLoadsSubfolders() = runTest(testDispatcher) {
        coEvery { browseDriveFoldersUseCase("f1") } returns Result.success(
            listOf(DriveBrowseItem("sub1", "Marvel"))
        )

        viewModel = DriveFolderPickerViewModel(
            browseDriveFoldersUseCase = browseDriveFoldersUseCase,
            addDriveFolderUseCase = addDriveFolderUseCase
        )
        testScheduler.advanceUntilIdle()

        viewModel.onEvent(DriveFolderPickerUiEvent.SubfolderClicked(DriveBrowseItem("f1", "Comics")))
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.breadcrumbs.size)
        assertEquals("f1", state.currentFolder.folderId)
        assertEquals("Comics", state.currentFolder.displayName)
        assertEquals(1, state.subfolders.size)
        assertEquals("Marvel", state.subfolders[0].name)
    }

    @Test
    fun navigateUpInFolderStackPopsBreadcrumbAndReloadsParent() = runTest(testDispatcher) {
        coEvery { browseDriveFoldersUseCase("f1") } returns Result.success(
            listOf(DriveBrowseItem("sub1", "Marvel"))
        )

        viewModel = DriveFolderPickerViewModel(
            browseDriveFoldersUseCase = browseDriveFoldersUseCase,
            addDriveFolderUseCase = addDriveFolderUseCase
        )
        testScheduler.advanceUntilIdle()

        viewModel.onEvent(DriveFolderPickerUiEvent.SubfolderClicked(DriveBrowseItem("f1", "Comics")))
        testScheduler.advanceUntilIdle()
        assertEquals(2, viewModel.uiState.value.breadcrumbs.size)

        viewModel.onEvent(DriveFolderPickerUiEvent.NavigateUpInFolderStack)
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.breadcrumbs.size)
        assertEquals("root", state.currentFolder.folderId)
        assertEquals(2, state.subfolders.size)
    }

    @Test
    fun confirmCurrentFolderClickedCallsAddDriveFolderUseCaseAndSetsSuccess() = runTest(testDispatcher) {
        viewModel = DriveFolderPickerViewModel(
            browseDriveFoldersUseCase = browseDriveFoldersUseCase,
            addDriveFolderUseCase = addDriveFolderUseCase
        )
        testScheduler.advanceUntilIdle()

        viewModel.onEvent(DriveFolderPickerUiEvent.ConfirmCurrentFolderClicked)
        testScheduler.advanceUntilIdle()

        coVerify(exactly = 1) {
            addDriveFolderUseCase(
                folderId = "root",
                displayName = "My Drive",
                driveId = null
            )
        }
        val state = viewModel.uiState.value
        assertFalse(state.isAddingFolder)
        assertTrue(state.folderAddedSuccess)
        assertNull(state.error)
    }

    @Test
    fun confirmCurrentFolderClickedSetsErrorOnFailure() = runTest(testDispatcher) {
        coEvery { addDriveFolderUseCase(any(), any(), any()) } returns Result.failure(
            DriveApiException(ComicError.AuthExpired)
        )

        viewModel = DriveFolderPickerViewModel(
            browseDriveFoldersUseCase = browseDriveFoldersUseCase,
            addDriveFolderUseCase = addDriveFolderUseCase
        )
        testScheduler.advanceUntilIdle()

        viewModel.onEvent(DriveFolderPickerUiEvent.ConfirmCurrentFolderClicked)
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isAddingFolder)
        assertFalse(state.folderAddedSuccess)
        assertEquals(ComicError.AuthExpired, state.error)
    }

    @Test
    fun retryLoadClickedReloadsSubfolders() = runTest(testDispatcher) {
        coEvery { browseDriveFoldersUseCase("root") } returns Result.failure(
            DriveApiException(ComicError.NetworkUnavailable)
        )

        viewModel = DriveFolderPickerViewModel(
            browseDriveFoldersUseCase = browseDriveFoldersUseCase,
            addDriveFolderUseCase = addDriveFolderUseCase
        )
        testScheduler.advanceUntilIdle()
        assertEquals(ComicError.NetworkUnavailable, viewModel.uiState.value.error)

        coEvery { browseDriveFoldersUseCase("root") } returns Result.success(
            listOf(DriveBrowseItem("f1", "Comics"))
        )
        viewModel.onEvent(DriveFolderPickerUiEvent.RetryLoadClicked)
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.error)
        assertEquals(1, state.subfolders.size)
        assertEquals("Comics", state.subfolders[0].name)
    }
}
