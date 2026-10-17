package com.c0mpile.comix.ui.reader

import android.graphics.Bitmap
import androidx.lifecycle.SavedStateHandle
import com.c0mpile.comix.data.archive.ComicArchiveReader
import com.c0mpile.comix.data.repository.ComicDomainException
import com.c0mpile.comix.domain.model.Comic
import com.c0mpile.comix.domain.model.ComicAvailability
import com.c0mpile.comix.domain.model.ComicError
import com.c0mpile.comix.domain.model.ComicFormat
import com.c0mpile.comix.domain.model.ComicLibrary
import com.c0mpile.comix.domain.model.ComicPage
import com.c0mpile.comix.domain.model.ComicSourceType
import com.c0mpile.comix.domain.model.ReaderScaleMode
import com.c0mpile.comix.domain.model.ReadingDirection
import com.c0mpile.comix.domain.model.ReadingProgress
import com.c0mpile.comix.domain.model.UserSettings
import com.c0mpile.comix.domain.repository.ReaderRepository
import com.c0mpile.comix.domain.usecase.GetComicUseCase
import com.c0mpile.comix.domain.usecase.GetLibraryUseCase
import com.c0mpile.comix.domain.usecase.ObserveUserSettingsUseCase
import com.c0mpile.comix.domain.usecase.SaveReadingProgressUseCase
import com.c0mpile.comix.domain.usecase.UpdateReaderScaleModeUseCase
import com.c0mpile.comix.domain.usecase.UpdateReadingDirectionUseCase
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
 * Unit tests for [ReaderViewModel].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModelTest {

    private val getComicUseCase = mockk<GetComicUseCase>()
    private val saveReadingProgressUseCase = mockk<SaveReadingProgressUseCase>()
    private val readerRepository = mockk<ReaderRepository>()
    private val observeUserSettingsUseCase = mockk<ObserveUserSettingsUseCase>()
    private val updateReaderScaleModeUseCase = mockk<UpdateReaderScaleModeUseCase>()
    private val updateReadingDirectionUseCase = mockk<UpdateReadingDirectionUseCase>()
    private val getLibraryUseCase = mockk<GetLibraryUseCase>(relaxed = true)

    private val userSettingsFlow = MutableStateFlow(UserSettings())
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { observeUserSettingsUseCase() } returns userSettingsFlow
        coEvery { updateReaderScaleModeUseCase(any()) } returns Unit
        coEvery { updateReadingDirectionUseCase(any()) } returns Unit
        coEvery { getLibraryUseCase(any()) } returns null
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(comicKey: String = "comic1"): ReaderViewModel {
        val savedStateHandle = SavedStateHandle(mapOf("comicKey" to comicKey))
        return ReaderViewModel(
            savedStateHandle = savedStateHandle,
            getComicUseCase = getComicUseCase,
            saveReadingProgressUseCase = saveReadingProgressUseCase,
            readerRepository = readerRepository,
            observeUserSettingsUseCase = observeUserSettingsUseCase,
            updateReaderScaleModeUseCase = updateReaderScaleModeUseCase,
            updateReadingDirectionUseCase = updateReadingDirectionUseCase,
            getLibraryUseCase = getLibraryUseCase
        )
    }

    @Test
    fun validComicRestoresReadingProgressAndSavesInitialProgress() = runTest(testDispatcher) {
        val comic = createTestComic("comic1", "Batman", pageCount = 10)
        val mockReader = mockk<ComicArchiveReader>(relaxed = true)
        every { mockReader.getPages() } returns List(10) { ComicPage(it, "$it.png", 100L, 200L) }

        val progress = ReadingProgress("comic1", currentPage = 4, pageCount = 10, isFinished = false, updatedAtEpochMillis = 1000L)

        coEvery { getComicUseCase("comic1") } returns comic
        coEvery { readerRepository.openComicReader(comic) } returns Result.success(mockReader)
        coEvery { readerRepository.getReadingProgress("comic1") } returns progress
        coEvery { saveReadingProgressUseCase(any(), any(), any()) } returns Unit

        val viewModel = createViewModel("comic1")
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Batman", state.title)
        assertEquals(10, state.pageCount)
        assertEquals(4, state.currentPage)
        assertFalse(state.isArchiveLoading)
        assertNull(state.fatalError)

        coVerify(exactly = 1) { saveReadingProgressUseCase("comic1", 4, 10) }
    }

    @Test
    fun comicNotFoundSetsFatalErrorFileNotFoundAndShowsHud() = runTest(testDispatcher) {
        coEvery { getComicUseCase("missingKey") } returns null

        val viewModel = createViewModel("missingKey")
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isArchiveLoading)
        assertTrue(state.fatalError is ComicError.FileNotFound)
        assertEquals("missingKey", (state.fatalError as ComicError.FileNotFound).uri)
        assertTrue(state.isHudVisible)
    }

    @Test
    fun openComicReaderFailureSetsFatalErrorAndShowsHud() = runTest(testDispatcher) {
        val comic = createTestComic("comic1", "Corrupt Comic")
        coEvery { getComicUseCase("comic1") } returns comic
        coEvery {
            readerRepository.openComicReader(comic)
        } returns Result.failure(ComicDomainException(ComicError.CorruptArchive("corrupt.cbz", "Bad Central Directory")))

        val viewModel = createViewModel("comic1")
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isArchiveLoading)
        assertTrue(state.fatalError is ComicError.CorruptArchive)
        val error = state.fatalError as ComicError.CorruptArchive
        assertEquals("corrupt.cbz", error.fileName)
        assertEquals("Bad Central Directory", error.reason)
        assertTrue(state.isHudVisible)
    }

    @Test
    fun viewportSizeChangedTriggersPreloadOfAdjacentPages() = runTest(testDispatcher) {
        val comic = createTestComic("comic1", "Preload Comic", pageCount = 5)
        val mockReader = mockk<ComicArchiveReader>(relaxed = true)
        every { mockReader.getPages() } returns List(5) { ComicPage(it, "$it.png", 100L, 200L) }

        coEvery { getComicUseCase("comic1") } returns comic
        coEvery { readerRepository.openComicReader(comic) } returns Result.success(mockReader)
        coEvery { readerRepository.getReadingProgress("comic1") } returns ReadingProgress("comic1", 1, 5, false, 1000L)
        coEvery { saveReadingProgressUseCase(any(), any(), any()) } returns Unit

        val mockBitmap = mockk<Bitmap>(relaxed = true)
        coEvery {
            readerRepository.decodePageBitmap(mockReader, any(), 1080, 1920)
        } returns Result.success(mockBitmap)

        val viewModel = createViewModel("comic1")
        testScheduler.advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.currentPage)
        assertEquals(5, viewModel.uiState.value.pageCount)

        viewModel.onEvent(ReaderUiEvent.ViewportSizeChanged(1080, 1920))
        testScheduler.advanceUntilIdle()

        // Page 1 is active -> preloads 1, 2, 0
        assertTrue(viewModel.uiState.value.pageStates[1] is PageLoadState.Loaded)
        assertTrue(viewModel.uiState.value.pageStates[2] is PageLoadState.Loaded)
        assertTrue(viewModel.uiState.value.pageStates[0] is PageLoadState.Loaded)
    }

    @Test
    fun pageChangedUpdatesCurrentPageAndPersistsProgress() = runTest(testDispatcher) {
        val comic = createTestComic("comic1", "Paging Comic", pageCount = 6)
        val mockReader = mockk<ComicArchiveReader>(relaxed = true)
        every { mockReader.getPages() } returns List(6) { ComicPage(it, "$it.png", 100L, 200L) }

        coEvery { getComicUseCase("comic1") } returns comic
        coEvery { readerRepository.openComicReader(comic) } returns Result.success(mockReader)
        coEvery { readerRepository.getReadingProgress("comic1") } returns null
        coEvery { saveReadingProgressUseCase(any(), any(), any()) } returns Unit

        val mockBitmap = mockk<Bitmap>(relaxed = true)
        coEvery {
            readerRepository.decodePageBitmap(mockReader, any(), 1080, 1920)
        } returns Result.success(mockBitmap)

        val viewModel = createViewModel("comic1")
        testScheduler.advanceUntilIdle()

        viewModel.onEvent(ReaderUiEvent.ViewportSizeChanged(1080, 1920))
        testScheduler.advanceUntilIdle()

        viewModel.onEvent(ReaderUiEvent.PageChanged(3))
        testScheduler.advanceUntilIdle()

        assertEquals(3, viewModel.uiState.value.currentPage)
        coVerify { saveReadingProgressUseCase("comic1", 3, 6) }

        // Pages around 3 (3, 4, 2) should be loaded
        assertTrue(viewModel.uiState.value.pageStates[3] is PageLoadState.Loaded)
        assertTrue(viewModel.uiState.value.pageStates[4] is PageLoadState.Loaded)
        assertTrue(viewModel.uiState.value.pageStates[2] is PageLoadState.Loaded)
    }

    @Test
    fun toggleHudTogglesIsHudVisible() = runTest(testDispatcher) {
        val comic = createTestComic("comic1", "HUD Comic")
        val mockReader = mockk<ComicArchiveReader>(relaxed = true)
        every { mockReader.getPages() } returns List(5) { ComicPage(it, "$it.png", 100L, 200L) }
        coEvery { getComicUseCase("comic1") } returns comic
        coEvery { readerRepository.openComicReader(comic) } returns Result.success(mockReader)
        coEvery { readerRepository.getReadingProgress("comic1") } returns null
        coEvery { saveReadingProgressUseCase(any(), any(), any()) } returns Unit

        val viewModel = createViewModel("comic1")
        testScheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isHudVisible)

        viewModel.onEvent(ReaderUiEvent.ToggleHud)
        assertTrue(viewModel.uiState.value.isHudVisible)

        viewModel.onEvent(ReaderUiEvent.ToggleHud)
        assertFalse(viewModel.uiState.value.isHudVisible)
    }

    @Test
    fun retryPageRedecodesFailedPage() = runTest(testDispatcher) {
        val comic = createTestComic("comic1", "Retry Comic", pageCount = 3)
        val mockReader = mockk<ComicArchiveReader>(relaxed = true)
        every { mockReader.getPages() } returns List(3) { ComicPage(it, "$it.png", 100L, 200L) }

        coEvery { getComicUseCase("comic1") } returns comic
        coEvery { readerRepository.openComicReader(comic) } returns Result.success(mockReader)
        coEvery { readerRepository.getReadingProgress("comic1") } returns null
        coEvery { saveReadingProgressUseCase(any(), any(), any()) } returns Unit

        val mockBitmap = mockk<Bitmap>(relaxed = true)
        coEvery {
            readerRepository.decodePageBitmap(mockReader, any(), any(), any())
        } returns Result.success(mockBitmap)

        // Initially page 0 fails
        coEvery {
            readerRepository.decodePageBitmap(mockReader, 0, any(), any())
        } returns Result.failure(ComicDomainException(ComicError.CorruptArchive(null, "Decompression error")))

        val viewModel = createViewModel("comic1")
        testScheduler.advanceUntilIdle()

        viewModel.onEvent(ReaderUiEvent.ViewportSizeChanged(1080, 1920))
        testScheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.pageStates[0] is PageLoadState.Error)

        // Succeed upon retry
        coEvery {
            readerRepository.decodePageBitmap(mockReader, 0, any(), any())
        } returns Result.success(mockBitmap)

        viewModel.onEvent(ReaderUiEvent.RetryPage(0))
        testScheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.pageStates[0] is PageLoadState.Loaded)
    }

    @Test
    fun retryOpenArchiveReloadsComic() = runTest(testDispatcher) {
        coEvery { getComicUseCase("comic1") } returns null

        val viewModel = createViewModel("comic1")
        testScheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.fatalError is ComicError.FileNotFound)

        val comic = createTestComic("comic1", "Reloaded Comic", pageCount = 4)
        val mockReader = mockk<ComicArchiveReader>(relaxed = true)
        every { mockReader.getPages() } returns List(4) { ComicPage(it, "$it.png", 100L, 200L) }
        coEvery { getComicUseCase("comic1") } returns comic
        coEvery { readerRepository.openComicReader(comic) } returns Result.success(mockReader)
        coEvery { readerRepository.getReadingProgress("comic1") } returns null
        coEvery { saveReadingProgressUseCase(any(), any(), any()) } returns Unit

        viewModel.onEvent(ReaderUiEvent.RetryOpenArchive)
        testScheduler.advanceUntilIdle()

        assertNull(viewModel.uiState.value.fatalError)
        assertEquals(4, viewModel.uiState.value.pageCount)
        assertEquals("Reloaded Comic", viewModel.uiState.value.title)
    }

    @Test
    fun userSettingsObservationUpdatesReadingDirectionScaleModeAndKeepScreenOn() = runTest(testDispatcher) {
        val comic = createTestComic("comic1", "Settings Test Comic")
        coEvery { getComicUseCase("comic1") } returns comic
        val mockReader = mockk<ComicArchiveReader>(relaxed = true)
        coEvery { readerRepository.openComicReader(comic) } returns Result.success(mockReader)
        coEvery { readerRepository.getReadingProgress("comic1") } returns null
        coEvery { saveReadingProgressUseCase(any(), any(), any()) } returns Unit

        val viewModel = createViewModel("comic1")
        testScheduler.advanceUntilIdle()

        userSettingsFlow.value = UserSettings(
            readingDirection = ReadingDirection.RIGHT_TO_LEFT,
            readerScaleMode = ReaderScaleMode.FIT_WIDTH,
            keepScreenOn = true
        )
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(ReadingDirection.RIGHT_TO_LEFT, state.readingDirection)
        assertEquals(ReaderScaleMode.FIT_WIDTH, state.scaleMode)
        assertTrue(state.keepScreenOn)
    }

    @Test
    fun toggleScaleModeSwitchesBetweenFitScreenAndFitWidth() = runTest(testDispatcher) {
        val comic = createTestComic("comic1", "Scale Mode Comic")
        coEvery { getComicUseCase("comic1") } returns comic
        val mockReader = mockk<ComicArchiveReader>(relaxed = true)
        coEvery { readerRepository.openComicReader(comic) } returns Result.success(mockReader)
        coEvery { readerRepository.getReadingProgress("comic1") } returns null
        coEvery { saveReadingProgressUseCase(any(), any(), any()) } returns Unit

        userSettingsFlow.value = UserSettings(readerScaleMode = ReaderScaleMode.FIT_SCREEN)
        val viewModel = createViewModel("comic1")
        testScheduler.advanceUntilIdle()

        viewModel.onEvent(ReaderUiEvent.ToggleScaleMode)
        testScheduler.advanceUntilIdle()
        coVerify(exactly = 1) { updateReaderScaleModeUseCase(ReaderScaleMode.FIT_WIDTH) }

        userSettingsFlow.value = UserSettings(readerScaleMode = ReaderScaleMode.FIT_WIDTH)
        testScheduler.advanceUntilIdle()

        viewModel.onEvent(ReaderUiEvent.ToggleScaleMode)
        testScheduler.advanceUntilIdle()
        coVerify(exactly = 1) { updateReaderScaleModeUseCase(ReaderScaleMode.FIT_SCREEN) }
    }

    @Test
    fun toggleReadingDirectionSwitchesBetweenLeftToRightAndRightToLeft() = runTest(testDispatcher) {
        val comic = createTestComic("comic1", "Reading Direction Comic")
        coEvery { getComicUseCase("comic1") } returns comic
        val mockReader = mockk<ComicArchiveReader>(relaxed = true)
        coEvery { readerRepository.openComicReader(comic) } returns Result.success(mockReader)
        coEvery { readerRepository.getReadingProgress("comic1") } returns null
        coEvery { saveReadingProgressUseCase(any(), any(), any()) } returns Unit

        userSettingsFlow.value = UserSettings(readingDirection = ReadingDirection.LEFT_TO_RIGHT)
        val viewModel = createViewModel("comic1")
        testScheduler.advanceUntilIdle()

        viewModel.onEvent(ReaderUiEvent.ToggleReadingDirection)
        testScheduler.advanceUntilIdle()
        coVerify(exactly = 1) { updateReadingDirectionUseCase(ReadingDirection.RIGHT_TO_LEFT) }

        userSettingsFlow.value = UserSettings(readingDirection = ReadingDirection.RIGHT_TO_LEFT)
        testScheduler.advanceUntilIdle()

        viewModel.onEvent(ReaderUiEvent.ToggleReadingDirection)
        testScheduler.advanceUntilIdle()
        coVerify(exactly = 1) { updateReadingDirectionUseCase(ReadingDirection.LEFT_TO_RIGHT) }
    }

    @Test
    fun openingMangaLibraryComicDefaultsToRightToLeftDirection() = runTest(testDispatcher) {
        val mangaComic = createTestComic("manga1", "Naruto Vol 1", libraryId = "manga")
        coEvery { getComicUseCase("manga1") } returns mangaComic
        val mangaLibrary = ComicLibrary(
            libraryId = "manga",
            name = "Manga",
            defaultReadingDirection = ReadingDirection.RIGHT_TO_LEFT,
            createdAtEpochMillis = 1000L,
            isBuiltIn = true
        )
        coEvery { getLibraryUseCase("manga") } returns mangaLibrary
        val mockReader = mockk<ComicArchiveReader>(relaxed = true)
        coEvery { readerRepository.openComicReader(mangaComic) } returns Result.success(mockReader)
        coEvery { readerRepository.getReadingProgress("manga1") } returns null
        coEvery { saveReadingProgressUseCase(any(), any(), any()) } returns Unit

        userSettingsFlow.value = UserSettings(readingDirection = ReadingDirection.LEFT_TO_RIGHT)
        val viewModel = createViewModel("manga1")
        testScheduler.advanceUntilIdle()

        assertEquals(ReadingDirection.RIGHT_TO_LEFT, viewModel.uiState.value.readingDirection)
    }

    @Test
    fun openingComicsLibraryComicDefaultsToLeftToRightDirection() = runTest(testDispatcher) {
        val comic = createTestComic("comic1", "Batman", libraryId = "comics")
        coEvery { getComicUseCase("comic1") } returns comic
        val comicsLibrary = ComicLibrary(
            libraryId = "comics",
            name = "Comics",
            defaultReadingDirection = ReadingDirection.LEFT_TO_RIGHT,
            createdAtEpochMillis = 1000L,
            isBuiltIn = true
        )
        coEvery { getLibraryUseCase("comics") } returns comicsLibrary
        val mockReader = mockk<ComicArchiveReader>(relaxed = true)
        coEvery { readerRepository.openComicReader(comic) } returns Result.success(mockReader)
        coEvery { readerRepository.getReadingProgress("comic1") } returns null
        coEvery { saveReadingProgressUseCase(any(), any(), any()) } returns Unit

        userSettingsFlow.value = UserSettings(readingDirection = ReadingDirection.RIGHT_TO_LEFT)
        val viewModel = createViewModel("comic1")
        testScheduler.advanceUntilIdle()

        assertEquals(ReadingDirection.LEFT_TO_RIGHT, viewModel.uiState.value.readingDirection)
    }

    private fun createTestComic(
        comicKey: String,
        title: String,
        pageCount: Int = 10,
        libraryId: String = ComicLibrary.DEFAULT_COMICS_ID
    ) = Comic(
        comicKey = comicKey,
        title = title,
        fileName = "$title.cbz",
        format = ComicFormat.CBZ,
        sourceType = ComicSourceType.LOCAL_SAF,
        availability = ComicAvailability.LOCAL,
        sizeBytes = 10_000L,
        lastModifiedEpochMillis = 1000L,
        pageCount = pageCount,
        localUri = "content://tree/$title.cbz",
        folderUri = "content://tree/folder1",
        libraryId = libraryId
    )
}
