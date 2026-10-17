package com.c0mpile.comix.ui.library

import app.cash.turbine.test
import com.c0mpile.comix.data.remote.drive.DriveApiException
import com.c0mpile.comix.domain.model.Comic
import com.c0mpile.comix.domain.model.ComicAvailability
import com.c0mpile.comix.domain.model.ComicError
import com.c0mpile.comix.domain.model.ComicFormat
import com.c0mpile.comix.domain.model.ComicLibrary
import com.c0mpile.comix.domain.model.ComicSourceType
import com.c0mpile.comix.domain.model.DriveFolder
import com.c0mpile.comix.domain.model.LibraryFilterTab
import com.c0mpile.comix.domain.model.LibraryFolder
import com.c0mpile.comix.domain.model.LibraryLayoutMode
import com.c0mpile.comix.domain.model.LibrarySortOrder
import com.c0mpile.comix.domain.model.ReadingDirection
import com.c0mpile.comix.domain.model.ReadingProgress
import com.c0mpile.comix.domain.model.ScanResult
import com.c0mpile.comix.domain.model.UserSettings
import com.c0mpile.comix.domain.usecase.AddLibraryFolderUseCase
import com.c0mpile.comix.domain.usecase.CreateLibraryUseCase
import com.c0mpile.comix.domain.usecase.DeleteLibraryUseCase
import com.c0mpile.comix.domain.usecase.ObserveDriveFoldersUseCase
import com.c0mpile.comix.domain.usecase.ObserveLibrariesUseCase
import com.c0mpile.comix.domain.usecase.ObserveLibraryComicsUseCase
import com.c0mpile.comix.domain.usecase.ObserveLibraryFoldersUseCase
import com.c0mpile.comix.domain.usecase.ObserveUserSettingsUseCase
import com.c0mpile.comix.domain.usecase.RemoveLibraryFolderUseCase
import com.c0mpile.comix.domain.usecase.RescanDriveUseCase
import com.c0mpile.comix.domain.usecase.RescanLibraryUseCase
import com.c0mpile.comix.domain.usecase.UpdateActiveLibraryUseCase
import com.c0mpile.comix.domain.usecase.UpdateLibraryLayoutModeUseCase
import com.c0mpile.comix.domain.usecase.UpdateLibrarySortOrderUseCase
import com.c0mpile.comix.domain.usecase.UpdateLibraryUseCase
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
    private val observeDriveFoldersUseCase = mockk<ObserveDriveFoldersUseCase>()
    private val addLibraryFolderUseCase = mockk<AddLibraryFolderUseCase>()
    private val rescanLibraryUseCase = mockk<RescanLibraryUseCase>()
    private val rescanDriveUseCase = mockk<RescanDriveUseCase>()
    private val removeLibraryFolderUseCase = mockk<RemoveLibraryFolderUseCase>()
    private val observeUserSettingsUseCase = mockk<ObserveUserSettingsUseCase>()
    private val updateLibrarySortOrderUseCase = mockk<UpdateLibrarySortOrderUseCase>()
    private val observeLibrariesUseCase = mockk<ObserveLibrariesUseCase>()
    private val createLibraryUseCase = mockk<CreateLibraryUseCase>()
    private val updateLibraryUseCase = mockk<UpdateLibraryUseCase>()
    private val deleteLibraryUseCase = mockk<DeleteLibraryUseCase>()
    private val updateActiveLibraryUseCase = mockk<UpdateActiveLibraryUseCase>()
    private val updateLibraryLayoutModeUseCase = mockk<UpdateLibraryLayoutModeUseCase>()

    private val comicsFlow = MutableStateFlow<List<Comic>>(emptyList())
    private val foldersFlow = MutableStateFlow<List<LibraryFolder>>(emptyList())
    private val driveFoldersFlow = MutableStateFlow<List<DriveFolder>>(emptyList())
    private val userSettingsFlow = MutableStateFlow(UserSettings())
    private val defaultLibraries = listOf(
        ComicLibrary(
            libraryId = ComicLibrary.DEFAULT_COMICS_ID,
            name = "Comics",
            defaultReadingDirection = ReadingDirection.LEFT_TO_RIGHT,
            createdAtEpochMillis = 1000L,
            isBuiltIn = true
        ),
        ComicLibrary(
            libraryId = "manga",
            name = "Manga",
            defaultReadingDirection = ReadingDirection.RIGHT_TO_LEFT,
            createdAtEpochMillis = 2000L,
            isBuiltIn = false
        )
    )
    private val librariesFlow = MutableStateFlow(defaultLibraries)

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var viewModel: LibraryViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { observeLibrariesUseCase() } returns librariesFlow
        every { observeLibraryComicsUseCase(any()) } returns comicsFlow
        every { observeLibraryFoldersUseCase(any()) } returns foldersFlow
        every { observeDriveFoldersUseCase(any()) } returns driveFoldersFlow
        every { observeUserSettingsUseCase() } returns userSettingsFlow
        coEvery { updateLibrarySortOrderUseCase(any()) } returns Unit
        coEvery { rescanLibraryUseCase(any()) } returns Result.success(ScanResult(0, 0, 0, emptyList()))
        coEvery { rescanDriveUseCase(any()) } returns Result.success(ScanResult(0, 0, 0, emptyList()))
        coEvery { updateActiveLibraryUseCase(any()) } returns Unit
        coEvery { createLibraryUseCase(any(), any()) } returns Result.success(
            ComicLibrary("lib_new", "New Lib", ReadingDirection.LEFT_TO_RIGHT, 3000L, false)
        )
        coEvery { updateLibraryUseCase(any(), any(), any()) } returns Result.success(Unit)
        coEvery { deleteLibraryUseCase(any()) } returns Result.success(Unit)
        coEvery { updateLibraryLayoutModeUseCase(any()) } returns Unit

        viewModel = LibraryViewModel(
            observeLibraryComicsUseCase = observeLibraryComicsUseCase,
            observeLibraryFoldersUseCase = observeLibraryFoldersUseCase,
            observeDriveFoldersUseCase = observeDriveFoldersUseCase,
            addLibraryFolderUseCase = addLibraryFolderUseCase,
            rescanLibraryUseCase = rescanLibraryUseCase,
            rescanDriveUseCase = rescanDriveUseCase,
            removeLibraryFolderUseCase = removeLibraryFolderUseCase,
            observeUserSettingsUseCase = observeUserSettingsUseCase,
            updateLibrarySortOrderUseCase = updateLibrarySortOrderUseCase,
            observeLibrariesUseCase = observeLibrariesUseCase,
            createLibraryUseCase = createLibraryUseCase,
            updateLibraryUseCase = updateLibraryUseCase,
            deleteLibraryUseCase = deleteLibraryUseCase,
            updateActiveLibraryUseCase = updateActiveLibraryUseCase,
            updateLibraryLayoutModeUseCase = updateLibraryLayoutModeUseCase
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
        coEvery { addLibraryFolderUseCase("content://tree/new", any()) } returns Result.success(scanResult)

        viewModel.uiState.test {
            // Consume initial emission
            awaitItem()

            viewModel.onEvent(LibraryUiEvent.FolderPicked("content://tree/new"))

            testScheduler.advanceUntilIdle()

            coVerify(exactly = 1) { addLibraryFolderUseCase("content://tree/new", ComicLibrary.DEFAULT_COMICS_ID) }

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
        coEvery { addLibraryFolderUseCase("content://tree/revoked", any()) } returns Result.success(scanResult)

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
            addLibraryFolderUseCase("content://tree/fail", any())
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
        coEvery { rescanLibraryUseCase(any()) } returns Result.success(scanResult)

        viewModel.uiState.test {
            awaitItem()

            viewModel.onEvent(LibraryUiEvent.RescanRequested)

            testScheduler.advanceUntilIdle()

            coVerify(exactly = 1) { rescanLibraryUseCase(ComicLibrary.DEFAULT_COMICS_ID) }

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
            rescanLibraryUseCase(any())
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

    @Test
    fun searchQueryFiltersComicsByTitleAndFileNameCaseInsensitively() = runTest(testDispatcher) {
        val comic1 = createTestComic("c1", "Spider-Man Issue 1", "spiderman_01.cbz")
        val comic2 = createTestComic("c2", "Batman Year One", "batman_01.cbz")
        val comic3 = createTestComic("c3", "Avengers Assemble", "spider_crossover.cbz")
        comicsFlow.value = listOf(comic1, comic2, comic3)
        foldersFlow.value = listOf(createTestFolder("content://tree/f1", "Comics", true))

        viewModel.uiState.test {
            awaitItem()
            testScheduler.advanceUntilIdle()

            viewModel.onEvent(LibraryUiEvent.SearchQueryChanged("SPIDER"))
            testScheduler.advanceUntilIdle()

            val filteredState = viewModel.uiState.value
            assertEquals(2, filteredState.comics.size)
            assertEquals(listOf("c3", "c1"), filteredState.comics.map { it.comicKey })
            assertEquals(3, filteredState.totalComicCount)
            assertEquals("SPIDER", filteredState.searchQuery)
            assertFalse(filteredState.showEmptySearchResultsState)

            viewModel.onEvent(LibraryUiEvent.SearchQueryChanged("batman"))
            testScheduler.advanceUntilIdle()

            val batmanState = viewModel.uiState.value
            assertEquals(1, batmanState.comics.size)
            assertEquals("c2", batmanState.comics.first().comicKey)
            assertEquals(3, batmanState.totalComicCount)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun searchQueryWithNoMatchesSetsShowEmptySearchResultsState() = runTest(testDispatcher) {
        val comic1 = createTestComic("c1", "Spider-Man Issue 1", "spiderman_01.cbz")
        comicsFlow.value = listOf(comic1)
        foldersFlow.value = listOf(createTestFolder("content://tree/f1", "Comics", true))

        viewModel.uiState.test {
            awaitItem()
            testScheduler.advanceUntilIdle()

            viewModel.onEvent(LibraryUiEvent.SearchQueryChanged("Superman"))
            testScheduler.advanceUntilIdle()

            val state = viewModel.uiState.value
            assertTrue(state.comics.isEmpty())
            assertEquals(1, state.totalComicCount)
            assertEquals("Superman", state.searchQuery)
            assertTrue(state.showEmptySearchResultsState)
            assertFalse(state.showEmptyNoComicsState)
            assertFalse(state.showEmptyNoFoldersState)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun searchActiveChangedToFalseResetsQueryAndActiveState() = runTest(testDispatcher) {
        val comic1 = createTestComic("c1", "Spider-Man Issue 1", "spiderman_01.cbz")
        val comic2 = createTestComic("c2", "Batman Year One", "batman_01.cbz")
        comicsFlow.value = listOf(comic1, comic2)
        foldersFlow.value = listOf(createTestFolder("content://tree/f1", "Comics", true))

        viewModel.uiState.test {
            awaitItem()
            testScheduler.advanceUntilIdle()

            viewModel.onEvent(LibraryUiEvent.SearchActiveChanged(true))
            viewModel.onEvent(LibraryUiEvent.SearchQueryChanged("batman"))
            testScheduler.advanceUntilIdle()

            val activeState = viewModel.uiState.value
            assertTrue(activeState.isSearchActive)
            assertEquals("batman", activeState.searchQuery)
            assertEquals(1, activeState.comics.size)

            viewModel.onEvent(LibraryUiEvent.SearchActiveChanged(false))
            testScheduler.advanceUntilIdle()

            val resetState = viewModel.uiState.value
            assertFalse(resetState.isSearchActive)
            assertEquals("", resetState.searchQuery)
            assertEquals(2, resetState.comics.size)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun sortOrderTitleAscendingAppliesNaturalOrder() = runTest(testDispatcher) {
        val comic1 = createTestComic("c1", "Issue 10")
        val comic2 = createTestComic("c2", "Issue 2")
        val comic3 = createTestComic("c3", "Issue 1")
        comicsFlow.value = listOf(comic1, comic2, comic3)
        userSettingsFlow.value = UserSettings(librarySortOrder = LibrarySortOrder.TITLE_ASC)

        viewModel.uiState.test {
            awaitItem()
            testScheduler.advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(listOf("Issue 1", "Issue 2", "Issue 10"), state.comics.map { it.title })

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun sortOrderTitleDescendingAppliesReverseNaturalOrder() = runTest(testDispatcher) {
        val comic1 = createTestComic("c1", "Issue 10")
        val comic2 = createTestComic("c2", "Issue 2")
        val comic3 = createTestComic("c3", "Issue 1")
        comicsFlow.value = listOf(comic1, comic2, comic3)
        userSettingsFlow.value = UserSettings(librarySortOrder = LibrarySortOrder.TITLE_DESC)

        viewModel.uiState.test {
            awaitItem()
            testScheduler.advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(listOf("Issue 10", "Issue 2", "Issue 1"), state.comics.map { it.title })

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun sortOrderRecentlyReadSortsByProgressUpdatedAtDescendingWithTitleFallback() = runTest(testDispatcher) {
        val progressOld = ReadingProgress("c1", currentPage = 1, pageCount = 10, isFinished = false, updatedAtEpochMillis = 1000L)
        val progressRecentA = ReadingProgress("c2", currentPage = 2, pageCount = 10, isFinished = false, updatedAtEpochMillis = 5000L)
        val progressRecentB = ReadingProgress("c4", currentPage = 3, pageCount = 10, isFinished = false, updatedAtEpochMillis = 5000L)

        val comic1 = createTestComic("c1", "Alpha", progress = progressOld)
        val comic2 = createTestComic("c2", "Beta", progress = progressRecentA)
        val comic3 = createTestComic("c3", "Gamma", progress = null)
        val comic4 = createTestComic("c4", "Delta", progress = progressRecentB)

        comicsFlow.value = listOf(comic1, comic2, comic3, comic4)
        userSettingsFlow.value = UserSettings(librarySortOrder = LibrarySortOrder.RECENTLY_READ)

        viewModel.uiState.test {
            awaitItem()
            testScheduler.advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(listOf("Beta", "Delta", "Alpha", "Gamma"), state.comics.map { it.title })

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun sortOrderDateAddedDescendingSortsByLastModifiedDescendingWithTitleFallback() = runTest(testDispatcher) {
        val comic1 = createTestComic("c1", "Alpha", lastModifiedEpochMillis = 1000L)
        val comic2 = createTestComic("c2", "Beta", lastModifiedEpochMillis = 5000L)
        val comic3 = createTestComic("c3", "Gamma", lastModifiedEpochMillis = 2000L)
        val comic4 = createTestComic("c4", "Delta", lastModifiedEpochMillis = 5000L)

        comicsFlow.value = listOf(comic1, comic2, comic3, comic4)
        userSettingsFlow.value = UserSettings(librarySortOrder = LibrarySortOrder.DATE_ADDED_DESC)

        viewModel.uiState.test {
            awaitItem()
            testScheduler.advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(listOf("Beta", "Delta", "Gamma", "Alpha"), state.comics.map { it.title })

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun sortOrderSelectedInvokesUpdateLibrarySortOrderUseCase() = runTest(testDispatcher) {
        viewModel.onEvent(LibraryUiEvent.SortOrderSelected(LibrarySortOrder.RECENTLY_READ))
        testScheduler.advanceUntilIdle()

        coVerify(exactly = 1) { updateLibrarySortOrderUseCase(LibrarySortOrder.RECENTLY_READ) }
    }

    @Test
    fun uiStateEmitsDriveFoldersFromUseCase() = runTest(testDispatcher) {
        val testDriveFolder = DriveFolder("drive_1", "Drive Manga", 1000L, null)
        driveFoldersFlow.value = listOf(testDriveFolder)

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.isInitialLoading) {
                state = awaitItem()
            }
            assertEquals(1, state.driveFolders.size)
            assertEquals("Drive Manga", state.driveFolders[0].displayName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun rescanRequestedWithDriveFoldersExecutesBothLocalAndDriveRescan() = runTest(testDispatcher) {
        val testDriveFolder = DriveFolder("drive_1", "Drive Manga", 1000L, null)
        driveFoldersFlow.value = listOf(testDriveFolder)
        coEvery { rescanLibraryUseCase(any()) } returns Result.success(ScanResult(addedOrUpdatedCount = 1, removedCount = 0, skippedCorruptCount = 0, errors = emptyList()))
        coEvery { rescanDriveUseCase(any()) } returns Result.success(ScanResult(addedOrUpdatedCount = 2, removedCount = 0, skippedCorruptCount = 0, errors = emptyList()))

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.isInitialLoading) {
                state = awaitItem()
            }

            viewModel.onEvent(LibraryUiEvent.RescanRequested)
            testScheduler.advanceUntilIdle()

            coVerify(exactly = 1) { rescanLibraryUseCase(ComicLibrary.DEFAULT_COMICS_ID) }
            coVerify(exactly = 1) { rescanDriveUseCase(ComicLibrary.DEFAULT_COMICS_ID) }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun rescanRequestedHandlesDriveApiErrorWithoutClearingLocalComics() = runTest(testDispatcher) {
        val testComic = createTestComic("comic1", "Spider-Man #1")
        val testDriveFolder = DriveFolder("drive_1", "Drive Manga", 1000L, null)
        comicsFlow.value = listOf(testComic)
        driveFoldersFlow.value = listOf(testDriveFolder)

        coEvery { rescanLibraryUseCase(any()) } returns Result.success(ScanResult(0, 0, 0, emptyList()))
        coEvery { rescanDriveUseCase(any()) } returns Result.failure(DriveApiException(ComicError.AuthExpired))

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.isInitialLoading) {
                state = awaitItem()
            }

            viewModel.onEvent(LibraryUiEvent.RescanRequested)
            testScheduler.advanceUntilIdle()

            val errorState = viewModel.uiState.value
            assertEquals(ComicError.AuthExpired, errorState.activeError)
            assertEquals(1, errorState.comics.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun filterTabSelectedFiltersComicsByInProgressAndFinished() = runTest(testDispatcher) {
        val unreadComic = createTestComic("c1", "Unread Comic", progress = null)
        val inProgressComic = createTestComic(
            "c2",
            "Reading Comic",
            progress = ReadingProgress("c2", currentPage = 5, pageCount = 20, isFinished = false, updatedAtEpochMillis = 1000L)
        )
        val finishedComic = createTestComic(
            "c3",
            "Finished Comic",
            progress = ReadingProgress("c3", currentPage = 20, pageCount = 20, isFinished = true, updatedAtEpochMillis = 1000L)
        )
        comicsFlow.value = listOf(unreadComic, inProgressComic, finishedComic)
        foldersFlow.value = listOf(createTestFolder("content://tree/f1", "Comics", true))

        viewModel.uiState.test {
            awaitItem()
            testScheduler.advanceUntilIdle()

            var state = viewModel.uiState.value
            assertEquals(LibraryFilterTab.ALL, state.selectedFilterTab)
            assertEquals(3, state.comics.size)
            assertFalse(state.showEmptyFilterTabState)

            // Switch to In Progress
            viewModel.onEvent(LibraryUiEvent.FilterTabSelected(LibraryFilterTab.IN_PROGRESS))
            testScheduler.advanceUntilIdle()

            state = viewModel.uiState.value
            assertEquals(LibraryFilterTab.IN_PROGRESS, state.selectedFilterTab)
            assertEquals(1, state.comics.size)
            assertEquals("c2", state.comics.first().comicKey)
            assertFalse(state.showEmptyFilterTabState)

            // Switch to Finished
            viewModel.onEvent(LibraryUiEvent.FilterTabSelected(LibraryFilterTab.FINISHED))
            testScheduler.advanceUntilIdle()

            state = viewModel.uiState.value
            assertEquals(LibraryFilterTab.FINISHED, state.selectedFilterTab)
            assertEquals(1, state.comics.size)
            assertEquals("c3", state.comics.first().comicKey)
            assertFalse(state.showEmptyFilterTabState)

            // Empty in progress tab scenario
            comicsFlow.value = listOf(unreadComic, finishedComic)
            viewModel.onEvent(LibraryUiEvent.FilterTabSelected(LibraryFilterTab.IN_PROGRESS))
            testScheduler.advanceUntilIdle()

            state = viewModel.uiState.value
            assertTrue(state.comics.isEmpty())
            assertTrue(state.showEmptyFilterTabState)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun librarySelectedUpdatesActiveLibraryId() = runTest(testDispatcher) {
        viewModel.onEvent(LibraryUiEvent.LibrarySelected("manga"))
        testScheduler.advanceUntilIdle()

        coVerify(exactly = 1) { updateActiveLibraryUseCase("manga") }
    }

    @Test
    fun createLibrarySubmittedCreatesAndSwitchesActiveLibrary() = runTest(testDispatcher) {
        val createdLibrary = ComicLibrary("lib_novel", "Light Novels", ReadingDirection.RIGHT_TO_LEFT, 5000L, false)
        coEvery { createLibraryUseCase("Light Novels", ReadingDirection.RIGHT_TO_LEFT) } returns Result.success(createdLibrary)

        viewModel.onEvent(LibraryUiEvent.CreateLibrarySubmitted("Light Novels", ReadingDirection.RIGHT_TO_LEFT))
        testScheduler.advanceUntilIdle()

        coVerify(exactly = 1) { createLibraryUseCase("Light Novels", ReadingDirection.RIGHT_TO_LEFT) }
        coVerify(exactly = 1) { updateActiveLibraryUseCase("lib_novel") }
    }

    @Test
    fun updateLibrarySubmittedInvokesUpdateLibraryUseCase() = runTest(testDispatcher) {
        viewModel.onEvent(LibraryUiEvent.UpdateLibrarySubmitted("comics", "Western Comics", ReadingDirection.LEFT_TO_RIGHT))
        testScheduler.advanceUntilIdle()

        coVerify(exactly = 1) { updateLibraryUseCase("comics", "Western Comics", ReadingDirection.LEFT_TO_RIGHT) }
    }

    @Test
    fun deleteLibraryConfirmedDeletesAndSwitchesToRemainingLibrary() = runTest(testDispatcher) {
        viewModel.uiState.test {
            awaitItem()
            testScheduler.advanceUntilIdle()

            // Active library is DEFAULT_COMICS_ID ("comics")
            viewModel.onEvent(LibraryUiEvent.DeleteLibraryConfirmed(ComicLibrary.DEFAULT_COMICS_ID))
            testScheduler.advanceUntilIdle()

            coVerify(exactly = 1) { deleteLibraryUseCase(ComicLibrary.DEFAULT_COMICS_ID) }
            coVerify(exactly = 1) { updateActiveLibraryUseCase("manga") }

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun activeLibraryIdFallsBackToFirstLibraryWhenActiveLibraryDoesNotExist() = runTest(testDispatcher) {
        userSettingsFlow.value = UserSettings(activeLibraryId = "non_existent_id")

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.isInitialLoading) {
                state = awaitItem()
            }

            assertEquals(ComicLibrary.DEFAULT_COMICS_ID, state.activeLibraryId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun toggleLayoutModeTogglesBetweenGridAndList() = runTest(testDispatcher) {
        viewModel.uiState.test {
            awaitItem()
            testScheduler.advanceUntilIdle()

            // Initial mode is GRID
            assertEquals(LibraryLayoutMode.GRID, viewModel.uiState.value.layoutMode)

            // Toggle from GRID to LIST
            viewModel.onEvent(LibraryUiEvent.ToggleLayoutMode)
            testScheduler.advanceUntilIdle()

            coVerify(exactly = 1) { updateLibraryLayoutModeUseCase(LibraryLayoutMode.LIST) }

            // Simulate settings update to LIST
            userSettingsFlow.value = UserSettings(libraryLayoutMode = LibraryLayoutMode.LIST)
            testScheduler.advanceUntilIdle()

            assertEquals(LibraryLayoutMode.LIST, viewModel.uiState.value.layoutMode)

            // Toggle from LIST to GRID
            viewModel.onEvent(LibraryUiEvent.ToggleLayoutMode)
            testScheduler.advanceUntilIdle()

            coVerify(exactly = 1) { updateLibraryLayoutModeUseCase(LibraryLayoutMode.GRID) }

            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun createTestComic(
        comicKey: String,
        title: String,
        fileName: String = "$title.cbz",
        lastModifiedEpochMillis: Long = 1000L,
        progress: ReadingProgress? = null
    ) = Comic(
        comicKey = comicKey,
        title = title,
        fileName = fileName,
        format = ComicFormat.CBZ,
        sourceType = ComicSourceType.LOCAL_SAF,
        availability = ComicAvailability.LOCAL,
        sizeBytes = 10_000L,
        lastModifiedEpochMillis = lastModifiedEpochMillis,
        pageCount = 10,
        localUri = "content://tree/$fileName",
        folderUri = "content://tree/f1",
        progress = progress
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
