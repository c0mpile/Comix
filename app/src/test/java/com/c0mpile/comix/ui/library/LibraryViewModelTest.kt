package com.c0mpile.comix.ui.library

import app.cash.turbine.test
import com.c0mpile.comix.domain.model.Comic
import com.c0mpile.comix.domain.model.ComicAvailability
import com.c0mpile.comix.domain.model.ComicError
import com.c0mpile.comix.domain.model.ComicFormat
import com.c0mpile.comix.domain.model.ComicSourceType
import com.c0mpile.comix.domain.model.LibraryFolder
import com.c0mpile.comix.domain.model.LibrarySortOrder
import com.c0mpile.comix.domain.model.ReadingProgress
import com.c0mpile.comix.domain.model.ScanResult
import com.c0mpile.comix.domain.model.UserSettings
import com.c0mpile.comix.domain.usecase.AddLibraryFolderUseCase
import com.c0mpile.comix.domain.usecase.ObserveLibraryComicsUseCase
import com.c0mpile.comix.domain.usecase.ObserveLibraryFoldersUseCase
import com.c0mpile.comix.domain.usecase.ObserveUserSettingsUseCase
import com.c0mpile.comix.domain.usecase.RemoveLibraryFolderUseCase
import com.c0mpile.comix.domain.usecase.RescanLibraryUseCase
import com.c0mpile.comix.domain.usecase.UpdateLibrarySortOrderUseCase
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
    private val observeUserSettingsUseCase = mockk<ObserveUserSettingsUseCase>()
    private val updateLibrarySortOrderUseCase = mockk<UpdateLibrarySortOrderUseCase>()

    private val comicsFlow = MutableStateFlow<List<Comic>>(emptyList())
    private val foldersFlow = MutableStateFlow<List<LibraryFolder>>(emptyList())
    private val userSettingsFlow = MutableStateFlow(UserSettings())

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var viewModel: LibraryViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { observeLibraryComicsUseCase() } returns comicsFlow
        every { observeLibraryFoldersUseCase() } returns foldersFlow
        every { observeUserSettingsUseCase() } returns userSettingsFlow
        coEvery { updateLibrarySortOrderUseCase(any()) } returns Unit

        viewModel = LibraryViewModel(
            observeLibraryComicsUseCase = observeLibraryComicsUseCase,
            observeLibraryFoldersUseCase = observeLibraryFoldersUseCase,
            addLibraryFolderUseCase = addLibraryFolderUseCase,
            rescanLibraryUseCase = rescanLibraryUseCase,
            removeLibraryFolderUseCase = removeLibraryFolderUseCase,
            observeUserSettingsUseCase = observeUserSettingsUseCase,
            updateLibrarySortOrderUseCase = updateLibrarySortOrderUseCase
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
