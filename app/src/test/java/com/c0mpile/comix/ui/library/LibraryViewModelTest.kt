package com.c0mpile.comix.ui.library

import app.cash.turbine.test
import com.c0mpile.comix.domain.model.Comic
import com.c0mpile.comix.domain.model.ComicAvailability
import com.c0mpile.comix.domain.model.ComicError
import com.c0mpile.comix.domain.model.ComicFormat
import com.c0mpile.comix.domain.model.ComicSourceType
import com.c0mpile.comix.domain.model.LibraryFolder
import com.c0mpile.comix.domain.model.ScanResult
import com.c0mpile.comix.domain.usecase.AddLibraryFolderUseCase
import com.c0mpile.comix.domain.usecase.ObserveLibraryComicsUseCase
import com.c0mpile.comix.domain.usecase.ObserveLibraryFoldersUseCase
import com.c0mpile.comix.domain.usecase.RemoveLibraryFolderUseCase
import com.c0mpile.comix.domain.usecase.RescanLibraryUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.io.IOException
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
 * Unit tests for [LibraryViewModel].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {

    private val observeLibraryComicsUseCase = mockk<ObserveLibraryComicsUseCase>()
    private val observeLibraryFoldersUseCase = mockk<ObserveLibraryFoldersUseCase>()
    private val addLibraryFolderUseCase = mockk<AddLibraryFolderUseCase>()
    private val rescanLibraryUseCase = mockk<RescanLibraryUseCase>()
    private val removeLibraryFolderUseCase = mockk<RemoveLibraryFolderUseCase>()

    private val comicsFlow = MutableStateFlow<List<Comic>>(emptyList())
    private val foldersFlow = MutableStateFlow<List<LibraryFolder>>(emptyList())

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var viewModel: LibraryViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { observeLibraryComicsUseCase() } returns comicsFlow
        every { observeLibraryFoldersUseCase() } returns foldersFlow

        viewModel = LibraryViewModel(
            observeLibraryComicsUseCase = observeLibraryComicsUseCase,
            observeLibraryFoldersUseCase = observeLibraryFoldersUseCase,
            addLibraryFolderUseCase = addLibraryFolderUseCase,
            rescanLibraryUseCase = rescanLibraryUseCase,
            removeLibraryFolderUseCase = removeLibraryFolderUseCase
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialStateTransitionsToLoadedWithComicsAndRevokedFolders() = runTest(testDispatcher) {
        val testComic = createTestComic("comic1", "Amazing Spider-Man #1")
        val validFolder = createTestFolder("content://tree/f1", "Marvel", hasPermission = true)
        val revokedFolder = createTestFolder("content://tree/f2", "DC", hasPermission = false)

        comicsFlow.value = listOf(testComic)
        foldersFlow.value = listOf(validFolder, revokedFolder)

        viewModel.uiState.test {
            // Await emission until loaded
            var state = awaitItem()
            while (state.isInitialLoading) {
                state = awaitItem()
            }

            assertEquals(1, state.comics.size)
            assertEquals("Amazing Spider-Man #1", state.comics[0].title)
            assertEquals(2, state.folders.size)
            assertEquals(1, state.revokedFolders.size)
            assertEquals("content://tree/f2", state.revokedFolders[0].folderUri)
            assertFalse(state.isInitialLoading)
            assertFalse(state.isScanning)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun showEmptyNoFoldersStateIsTrueWhenFoldersAndComicsAreEmpty() = runTest(testDispatcher) {
        comicsFlow.value = emptyList()
        foldersFlow.value = emptyList()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.isInitialLoading) {
                state = awaitItem()
            }

            assertTrue(state.showEmptyNoFoldersState)
            assertFalse(state.showEmptyNoComicsState)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun showEmptyNoComicsStateIsTrueWhenFoldersNonEmptyAndComicsEmpty() = runTest(testDispatcher) {
        comicsFlow.value = emptyList()
        foldersFlow.value = listOf(createTestFolder("content://tree/f1", "Comics", hasPermission = true))

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.isInitialLoading) {
                state = awaitItem()
            }

            assertFalse(state.showEmptyNoFoldersState)
            assertTrue(state.showEmptyNoComicsState)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun folderPickedInvokesAddLibraryFolderUseCaseAndUpdatesState() = runTest(testDispatcher) {
        val scanResult = ScanResult(
            addedOrUpdatedCount = 3,
            removedCount = 0,
            skippedCorruptCount = 2,
            errors = listOf(
                ComicError.CorruptArchive("corrupt1.cbz", "Bad central directory"),
                ComicError.EmptyArchive("empty.cbz")
            )
        )
        coEvery { addLibraryFolderUseCase("content://tree/new") } returns Result.success(scanResult)

        viewModel.uiState.test {
            // Consume initial emission
            awaitItem()

            viewModel.onEvent(LibraryUiEvent.FolderPicked("content://tree/new"))

            testScheduler.advanceUntilIdle()

            coVerify(exactly = 1) { addLibraryFolderUseCase("content://tree/new") }

            val latest = viewModel.uiState.value
            assertEquals(2, latest.scanFailureCount)
            assertNull(latest.activeError)
            assertFalse(latest.isScanning)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun folderPickedSetsActiveErrorWhenNonArchiveErrorOccurs() = runTest(testDispatcher) {
        val scanResult = ScanResult(
            addedOrUpdatedCount = 0,
            removedCount = 0,
            skippedCorruptCount = 0,
            errors = listOf(ComicError.PermissionRevoked("content://tree/revoked"))
        )
        coEvery { addLibraryFolderUseCase("content://tree/revoked") } returns Result.success(scanResult)

        viewModel.uiState.test {
            awaitItem()

            viewModel.onEvent(LibraryUiEvent.FolderPicked("content://tree/revoked"))

            testScheduler.advanceUntilIdle()

            val latest = viewModel.uiState.value
            assertTrue(latest.activeError is ComicError.PermissionRevoked)
            assertEquals("content://tree/revoked", (latest.activeError as ComicError.PermissionRevoked).uri)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun folderPickedSetsActiveErrorOnUseCaseFailure() = runTest(testDispatcher) {
        coEvery {
            addLibraryFolderUseCase("content://tree/fail")
        } returns Result.failure(IOException("Failed to read tree"))

        viewModel.uiState.test {
            awaitItem()

            viewModel.onEvent(LibraryUiEvent.FolderPicked("content://tree/fail"))

            testScheduler.advanceUntilIdle()

            val latest = viewModel.uiState.value
            assertTrue(latest.activeError is ComicError.UnknownIoError)
            assertEquals("Failed to read tree", (latest.activeError as ComicError.UnknownIoError).message)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun rescanRequestedInvokesRescanLibraryUseCaseAndUpdatesState() = runTest(testDispatcher) {
        val scanResult = ScanResult(
            addedOrUpdatedCount = 4,
            removedCount = 1,
            skippedCorruptCount = 1,
            errors = listOf(ComicError.CorruptArchive("broken.cbz", "Bad signature"))
        )
        coEvery { rescanLibraryUseCase() } returns Result.success(scanResult)

        viewModel.uiState.test {
            awaitItem()

            viewModel.onEvent(LibraryUiEvent.RescanRequested)

            testScheduler.advanceUntilIdle()

            coVerify(exactly = 1) { rescanLibraryUseCase() }

            val latest = viewModel.uiState.value
            assertEquals(1, latest.scanFailureCount)
            assertNull(latest.activeError)
            assertFalse(latest.isScanning)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun removeFolderRequestedInvokesRemoveLibraryFolderUseCase() = runTest(testDispatcher) {
        coEvery { removeLibraryFolderUseCase("content://tree/remove") } returns Unit

        viewModel.onEvent(LibraryUiEvent.RemoveFolderRequested("content://tree/remove"))
        testScheduler.advanceUntilIdle()

        coVerify(exactly = 1) { removeLibraryFolderUseCase("content://tree/remove") }
    }

    @Test
    fun dismissErrorClearsActiveErrorAndScanFailureCount() = runTest(testDispatcher) {
        coEvery {
            rescanLibraryUseCase()
        } returns Result.failure(IOException("Transient IO failure"))

        viewModel.uiState.test {
            awaitItem()

            viewModel.onEvent(LibraryUiEvent.RescanRequested)
            testScheduler.advanceUntilIdle()

            val stateWithError = viewModel.uiState.value
            assertTrue(stateWithError.activeError is ComicError.UnknownIoError)

            viewModel.onEvent(LibraryUiEvent.DismissError)
            testScheduler.advanceUntilIdle()

            val stateAfterDismiss = viewModel.uiState.value
            assertNull(stateAfterDismiss.activeError)
            assertEquals(0, stateAfterDismiss.scanFailureCount)

            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun createTestComic(comicKey: String, title: String) = Comic(
        comicKey = comicKey,
        title = title,
        fileName = "$title.cbz",
        format = ComicFormat.CBZ,
        sourceType = ComicSourceType.LOCAL_SAF,
        availability = ComicAvailability.LOCAL,
        sizeBytes = 10_000L,
        lastModifiedEpochMillis = 1000L,
        pageCount = 10,
        localUri = "content://tree/$title.cbz",
        folderUri = "content://tree/f1"
    )

    private fun createTestFolder(
        uri: String,
        displayName: String,
        hasPermission: Boolean
    ) = LibraryFolder(
        folderUri = uri,
        displayName = displayName,
        addedAtEpochMillis = 1000L,
        lastScannedAtEpochMillis = 2000L,
        hasPersistedPermission = hasPermission
    )
}
