package com.c0mpile.comix.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.c0mpile.comix.domain.model.BulkComicMetadataUpdate
import com.c0mpile.comix.domain.model.Comic
import com.c0mpile.comix.domain.model.ComicAvailability
import com.c0mpile.comix.domain.model.ComicFormat
import com.c0mpile.comix.domain.model.ComicLibrary
import com.c0mpile.comix.domain.model.ComicMetadata
import com.c0mpile.comix.domain.model.ComicSeries
import com.c0mpile.comix.domain.model.ComicSourceType
import com.c0mpile.comix.domain.model.LibraryFilterTab
import com.c0mpile.comix.domain.model.LibraryFolder
import com.c0mpile.comix.domain.model.LibraryLayoutMode
import com.c0mpile.comix.domain.model.ReadingDirection
import com.c0mpile.comix.domain.model.ReadingProgress
import com.c0mpile.comix.domain.model.SingleComicMetadataUpdate
import com.c0mpile.comix.ui.library.LibraryScreenContent
import com.c0mpile.comix.ui.library.LibraryUiEvent
import com.c0mpile.comix.ui.library.LibraryUiState
import com.c0mpile.comix.ui.theme.ComixTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented UI tests for [LibraryScreenContent].
 */
@RunWith(AndroidJUnit4::class)
class LibraryScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun rendersEmptyNoFoldersStateAndClickInvokesAddFolder() {
        var addFolderClicked = false

        val uiState = LibraryUiState(
            isInitialLoading = false,
            folders = emptyList(),
            comics = emptyList()
        )

        composeTestRule.setContent {
            ComixTheme {
                LibraryScreenContent(
                    uiState = uiState,
                    onEvent = {},
                    onAddFolderClick = { addFolderClicked = true },
                    onOpenComic = {}
                )
            }
        }

        composeTestRule.onNodeWithText("No comic folders added").assertIsDisplayed()
        composeTestRule.onNodeWithText("Add comic folder").assertIsDisplayed()

        composeTestRule.onNodeWithText("Add comic folder").performClick()
        assertTrue(addFolderClicked)
    }

    @Test
    fun rendersComicGridItemsWithTitlesAndBadgesAndClickOpensComic() {
        var openedComicKey: String? = null

        val comic = Comic(
            comicKey = "sm_issue_1",
            title = "Spider-Man #1",
            fileName = "spiderman_1.cbz",
            format = ComicFormat.CBZ,
            sourceType = ComicSourceType.LOCAL_SAF,
            availability = ComicAvailability.LOCAL,
            sizeBytes = 25_000_000L,
            lastModifiedEpochMillis = 1000L,
            pageCount = 10,
            localUri = "content://tree/spiderman_1.cbz",
            folderUri = "content://tree/comics",
            progress = ReadingProgress(
                comicKey = "sm_issue_1",
                currentPage = 2,
                pageCount = 10,
                isFinished = false,
                updatedAtEpochMillis = 2000L
            )
        )

        val folder = LibraryFolder(
            folderUri = "content://tree/comics",
            displayName = "Comics Root",
            addedAtEpochMillis = 500L,
            lastScannedAtEpochMillis = 1000L,
            hasPersistedPermission = true
        )

        val uiState = LibraryUiState(
            isInitialLoading = false,
            folders = listOf(folder),
            comics = listOf(comic)
        )

        composeTestRule.setContent {
            ComixTheme {
                LibraryScreenContent(
                    uiState = uiState,
                    onEvent = {},
                    onAddFolderClick = {},
                    onOpenComic = { openedComicKey = it }
                )
            }
        }

        composeTestRule.onNodeWithText("Spider-Man #1").assertIsDisplayed()
        composeTestRule.onNodeWithText("Page 3 of 10").assertIsDisplayed()

        composeTestRule.onNodeWithText("Spider-Man #1").performClick()
        assertEquals("sm_issue_1", openedComicKey)
    }

    @Test
    fun rendersRevokedFoldersBannerWhenRevokedFoldersPresent() {
        var addFolderClicked = false
        var removedFolderUri: String? = null

        val revokedFolder = LibraryFolder(
            folderUri = "content://tree/revoked_folder",
            displayName = "Marvel",
            addedAtEpochMillis = 1000L,
            lastScannedAtEpochMillis = 2000L,
            hasPersistedPermission = false
        )

        val uiState = LibraryUiState(
            isInitialLoading = false,
            folders = listOf(revokedFolder),
            comics = emptyList(),
            revokedFolders = listOf(revokedFolder)
        )

        composeTestRule.setContent {
            ComixTheme {
                LibraryScreenContent(
                    uiState = uiState,
                    onEvent = { event ->
                        if (event is LibraryUiEvent.RemoveFolderRequested) {
                            removedFolderUri = event.folderUri
                        }
                    },
                    onAddFolderClick = { addFolderClicked = true },
                    onOpenComic = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Folder access revoked").assertIsDisplayed()
        composeTestRule.onNodeWithText("Re-authorize").assertIsDisplayed()
        composeTestRule.onNodeWithText("Remove").assertIsDisplayed()

        composeTestRule.onNodeWithText("Re-authorize").performClick()
        assertTrue(addFolderClicked)

        composeTestRule.onNodeWithText("Remove").performClick()
        assertEquals("content://tree/revoked_folder", removedFolderUri)
    }

    @Test
    fun rendersEmptySearchResultsStateAndClearSearchDispatchesReset() {
        var dispatchedEvent: LibraryUiEvent? = null

        val uiState = LibraryUiState(
            isInitialLoading = false,
            searchQuery = "Unknown Hero",
            isSearchActive = true,
            totalComicCount = 5,
            comics = emptyList()
        )

        composeTestRule.setContent {
            ComixTheme {
                LibraryScreenContent(
                    uiState = uiState,
                    onEvent = { dispatchedEvent = it },
                    onAddFolderClick = {},
                    onOpenComic = {}
                )
            }
        }

        composeTestRule.onNodeWithText("No matching comics").assertIsDisplayed()
        composeTestRule.onNodeWithText("Clear search").assertIsDisplayed()

        composeTestRule.onNodeWithText("Clear search").performClick()
        assertEquals(LibraryUiEvent.SearchQueryChanged(""), dispatchedEvent)
    }

    @Test
    fun searchIconInTopBarDispatchesSearchActiveChanged() {
        var dispatchedEvent: LibraryUiEvent? = null

        val uiState = LibraryUiState(
            isInitialLoading = false,
            isSearchActive = false
        )

        composeTestRule.setContent {
            ComixTheme {
                LibraryScreenContent(
                    uiState = uiState,
                    onEvent = { dispatchedEvent = it },
                    onAddFolderClick = {},
                    onOpenComic = {}
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Search").performClick()
        assertEquals(LibraryUiEvent.SearchActiveChanged(true), dispatchedEvent)
    }

    @Test
    fun remoteDriveComicShowsInDriveBadgeAndClickInvokesOpenComicForStreamedReading() {
        var openedComicKey: String? = null

        val remoteComic = Comic(
            comicKey = "md5:abcd1234efgh5678",
            title = "Remote Comic #1",
            fileName = "RemoteComic1.cbz",
            format = ComicFormat.CBZ,
            sourceType = ComicSourceType.GOOGLE_DRIVE,
            availability = ComicAvailability.REMOTE,
            sizeBytes = 10_000_000L,
            lastModifiedEpochMillis = 1000L,
            pageCount = 0,
            localUri = null,
            folderUri = "drive-folder-1",
            driveFileId = "df-101",
            driveMd5 = "abcd1234efgh5678"
        )

        val uiState = LibraryUiState(
            isInitialLoading = false,
            totalComicCount = 1,
            comics = listOf(remoteComic)
        )

        composeTestRule.setContent {
            ComixTheme {
                LibraryScreenContent(
                    uiState = uiState,
                    onEvent = {},
                    onAddFolderClick = {},
                    onOpenComic = { openedComicKey = it }
                )
            }
        }

        composeTestRule.onNodeWithText("Remote Comic #1").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("In Drive").onFirst().assertIsDisplayed()

        composeTestRule.onNodeWithText("Remote Comic #1").performClick()

        assertEquals("md5:abcd1234efgh5678", openedComicKey)
    }

    @Test
    fun navigationDrawerOpensAndSelectingLibraryDispatchesLibrarySelected() {
        var selectedLibraryId: String? = null

        val libraries = listOf(
            ComicLibrary(
                libraryId = "comics",
                name = "Comics",
                defaultReadingDirection = ReadingDirection.LEFT_TO_RIGHT,
                createdAtEpochMillis = 0L,
                isBuiltIn = true
            ),
            ComicLibrary(
                libraryId = "manga",
                name = "Manga",
                defaultReadingDirection = ReadingDirection.RIGHT_TO_LEFT,
                createdAtEpochMillis = 1L,
                isBuiltIn = true
            )
        )

        val uiState = LibraryUiState(
            isInitialLoading = false,
            libraries = libraries,
            activeLibraryId = "comics"
        )

        composeTestRule.setContent {
            ComixTheme {
                LibraryScreenContent(
                    uiState = uiState,
                    onEvent = { event ->
                        if (event is LibraryUiEvent.LibrarySelected) {
                            selectedLibraryId = event.libraryId
                        }
                    },
                    onAddFolderClick = {},
                    onOpenComic = {}
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Open navigation menu").performClick()

        composeTestRule.onNodeWithText("Libraries").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Comics").onFirst().assertIsDisplayed()
        composeTestRule.onNodeWithText("Manga").assertIsDisplayed()
        composeTestRule.onNodeWithText("LTR").assertDoesNotExist()
        composeTestRule.onNodeWithText("RTL").assertDoesNotExist()
        composeTestRule.onNodeWithText("Create library").assertIsDisplayed()

        composeTestRule.onNodeWithText("Manga").performClick()
        assertEquals("manga", selectedLibraryId)
    }

    @Test
    fun filteringSectionRendersSearchBarAndFilterTabsAndDispatchesEvents() {
        var lastDispatchedEvent: LibraryUiEvent? = null

        val folder = LibraryFolder(
            folderUri = "content://tree/comics",
            displayName = "Comics Root",
            addedAtEpochMillis = 500L,
            lastScannedAtEpochMillis = 1000L,
            hasPersistedPermission = true
        )

        val comic1 = Comic(
            comicKey = "c1",
            title = "Spider-Man #1",
            fileName = "spiderman_1.cbz",
            format = ComicFormat.CBZ,
            sourceType = ComicSourceType.LOCAL_SAF,
            availability = ComicAvailability.LOCAL,
            sizeBytes = 10_000_000L,
            lastModifiedEpochMillis = 1000L,
            pageCount = 10,
            localUri = "content://tree/spiderman_1.cbz",
            folderUri = "content://tree/comics"
        )

        val comic2 = Comic(
            comicKey = "c2",
            title = "Spider-Man #2",
            fileName = "spiderman_2.cbz",
            format = ComicFormat.CBZ,
            sourceType = ComicSourceType.LOCAL_SAF,
            availability = ComicAvailability.LOCAL,
            sizeBytes = 10_000_000L,
            lastModifiedEpochMillis = 1000L,
            pageCount = 10,
            localUri = "content://tree/spiderman_2.cbz",
            folderUri = "content://tree/comics"
        )

        val uiState = LibraryUiState(
            isInitialLoading = false,
            folders = listOf(folder),
            comics = listOf(comic1, comic2),
            totalComicCount = 2,
            selectedFilterTab = LibraryFilterTab.ALL
        )

        composeTestRule.setContent {
            ComixTheme {
                LibraryScreenContent(
                    uiState = uiState,
                    onEvent = { lastDispatchedEvent = it },
                    onAddFolderClick = {},
                    onOpenComic = {}
                )
            }
        }

        composeTestRule.onNodeWithText("All").assertIsDisplayed()
        composeTestRule.onNodeWithText("Series").assertIsDisplayed()
        composeTestRule.onNodeWithText("In Progress").assertIsDisplayed()
        composeTestRule.onNodeWithText("Finished").assertIsDisplayed()
        composeTestRule.onNodeWithText("Search comics", substring = true).assertIsDisplayed()

        composeTestRule.onNodeWithText("Series").performClick()
        assertEquals(LibraryUiEvent.FilterTabSelected(LibraryFilterTab.SERIES), lastDispatchedEvent)

        composeTestRule.onNodeWithText("In Progress").performClick()
        assertEquals(LibraryUiEvent.FilterTabSelected(LibraryFilterTab.IN_PROGRESS), lastDispatchedEvent)

        composeTestRule.onNodeWithText("Finished").performClick()
        assertEquals(LibraryUiEvent.FilterTabSelected(LibraryFilterTab.FINISHED), lastDispatchedEvent)

        composeTestRule.onNodeWithText("Search comics", substring = true).performTextInput("Spider")
        assertEquals(LibraryUiEvent.SearchQueryChanged("Spider"), lastDispatchedEvent)
    }

    @Test
    fun emptyFilterTabStateShowsMessageAndShowAllComicsResetsTab() {
        var lastDispatchedEvent: LibraryUiEvent? = null

        val folder = LibraryFolder(
            folderUri = "content://tree/comics",
            displayName = "Comics Root",
            addedAtEpochMillis = 500L,
            lastScannedAtEpochMillis = 1000L,
            hasPersistedPermission = true
        )

        val uiState = LibraryUiState(
            isInitialLoading = false,
            selectedFilterTab = LibraryFilterTab.IN_PROGRESS,
            totalComicCount = 3,
            comics = emptyList(),
            folders = listOf(folder)
        )

        composeTestRule.setContent {
            ComixTheme {
                LibraryScreenContent(
                    uiState = uiState,
                    onEvent = { lastDispatchedEvent = it },
                    onAddFolderClick = {},
                    onOpenComic = {}
                )
            }
        }

        composeTestRule.onNodeWithText("No comics in progress").assertIsDisplayed()
        composeTestRule.onNodeWithText("Show all comics").assertIsDisplayed()

        composeTestRule.onNodeWithText("Show all comics").performClick()
        assertEquals(LibraryUiEvent.FilterTabSelected(LibraryFilterTab.ALL), lastDispatchedEvent)
    }

    @Test
    fun toggleLayoutModeDispatchesEventAndRendersListViewComics() {
        var lastDispatchedEvent: LibraryUiEvent? = null
        var openedComicKey: String? = null

        val comic = Comic(
            comicKey = "sm_issue_1",
            title = "Spider-Man #1",
            fileName = "spiderman_1.cbz",
            format = ComicFormat.CBZ,
            sourceType = ComicSourceType.LOCAL_SAF,
            availability = ComicAvailability.LOCAL,
            sizeBytes = 25_000_000L,
            lastModifiedEpochMillis = 1000L,
            pageCount = 10,
            localUri = "content://tree/spiderman_1.cbz",
            folderUri = "content://tree/comics",
            progress = ReadingProgress(
                comicKey = "sm_issue_1",
                currentPage = 2,
                pageCount = 10,
                isFinished = false,
                updatedAtEpochMillis = 2000L
            )
        )

        val folder = LibraryFolder(
            folderUri = "content://tree/comics",
            displayName = "Comics Root",
            addedAtEpochMillis = 500L,
            lastScannedAtEpochMillis = 1000L,
            hasPersistedPermission = true
        )

        val uiStateHolder = androidx.compose.runtime.mutableStateOf(
            LibraryUiState(
                isInitialLoading = false,
                folders = listOf(folder),
                comics = listOf(comic),
                layoutMode = LibraryLayoutMode.GRID
            )
        )

        composeTestRule.setContent {
            ComixTheme {
                LibraryScreenContent(
                    uiState = uiStateHolder.value,
                    onEvent = { event ->
                        lastDispatchedEvent = event
                        if (event is LibraryUiEvent.ToggleLayoutMode) {
                            uiStateHolder.value = uiStateHolder.value.copy(layoutMode = LibraryLayoutMode.LIST)
                        }
                    },
                    onAddFolderClick = {},
                    onOpenComic = { openedComicKey = it }
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Switch to list view").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Switch to list view").performClick()
        assertEquals(LibraryUiEvent.ToggleLayoutMode, lastDispatchedEvent)

        composeTestRule.onNodeWithContentDescription("Switch to grid view").assertIsDisplayed()

        composeTestRule.onNodeWithText("Spider-Man #1").assertIsDisplayed()
        composeTestRule.onNodeWithText("spiderman_1.cbz").assertIsDisplayed()
        composeTestRule.onNodeWithText("Page 3 of 10").assertIsDisplayed()

        composeTestRule.onNodeWithText("Spider-Man #1").performClick()
        assertEquals("sm_issue_1", openedComicKey)
    }

    @Test
    fun comicInfoButtonClickDispatchesShowComicDetailsAndRendersDetailsDialog() {
        var lastDispatchedEvent: LibraryUiEvent? = null

        val metadata = ComicMetadata(
            title = "Chapter 1: The Beginning",
            series = "Saga",
            number = "1",
            volume = 1,
            summary = "Two soldiers from opposite sides fall in love.",
            writer = "Brian K. Vaughan",
            penciller = "Fiona Staples",
            publisher = "Image Comics",
            genre = "Sci-Fi",
            releaseYear = 2012,
            releaseMonth = 3,
            releaseDay = 14
        )

        val comic = Comic(
            comicKey = "saga_1",
            title = "Saga #1",
            fileName = "saga_01.cbz",
            format = ComicFormat.CBZ,
            sourceType = ComicSourceType.LOCAL_SAF,
            availability = ComicAvailability.LOCAL,
            sizeBytes = 25_000_000L,
            lastModifiedEpochMillis = 1000L,
            pageCount = 44,
            localUri = "content://tree/saga_01.cbz",
            folderUri = "content://tree/comics",
            metadata = metadata
        )

        val folder = LibraryFolder(
            folderUri = "content://tree/comics",
            displayName = "Comics Root",
            addedAtEpochMillis = 500L,
            lastScannedAtEpochMillis = 1000L,
            hasPersistedPermission = true
        )

        val uiStateHolder = androidx.compose.runtime.mutableStateOf(
            LibraryUiState(
                isInitialLoading = false,
                folders = listOf(folder),
                comics = listOf(comic),
                totalComicCount = 1,
                selectedComicForDetails = null
            )
        )

        composeTestRule.setContent {
            ComixTheme {
                LibraryScreenContent(
                    uiState = uiStateHolder.value,
                    onEvent = { event ->
                        lastDispatchedEvent = event
                        if (event is LibraryUiEvent.ShowComicDetails) {
                            uiStateHolder.value = uiStateHolder.value.copy(selectedComicForDetails = event.comic)
                        } else if (event is LibraryUiEvent.DismissComicDetails) {
                            uiStateHolder.value = uiStateHolder.value.copy(selectedComicForDetails = null)
                        }
                    },
                    onAddFolderClick = {},
                    onOpenComic = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Saga #1").assertIsDisplayed()

        composeTestRule.onNodeWithContentDescription("Comic info for Saga #1").performClick()
        assertEquals(LibraryUiEvent.ShowComicDetails(comic), lastDispatchedEvent)

        composeTestRule.onAllNodesWithText("Series").onFirst().assertIsDisplayed()
        composeTestRule.onNodeWithText("Writer").assertIsDisplayed()
        composeTestRule.onNodeWithText("Brian K. Vaughan").assertIsDisplayed()
        composeTestRule.onNodeWithText("Publisher").assertIsDisplayed()
        composeTestRule.onNodeWithText("Image Comics").assertIsDisplayed()
        composeTestRule.onNodeWithText("Description").assertIsDisplayed()
        composeTestRule.onNodeWithText("Two soldiers from opposite sides fall in love.").assertIsDisplayed()

        composeTestRule.onNodeWithText("Close").assertIsDisplayed()
        composeTestRule.onNodeWithText("Close").performClick()
        assertEquals(LibraryUiEvent.DismissComicDetails, lastDispatchedEvent)

        composeTestRule.onNodeWithText("Two soldiers from opposite sides fall in love.").assertDoesNotExist()
    }

    @Test
    fun seriesTabRendersSeriesCardsAndDrillsDownIntoSeriesIssues() {
        var lastDispatchedEvent: LibraryUiEvent? = null
        var openedComicKey: String? = null

        val folder = LibraryFolder(
            folderUri = "content://tree/comics",
            displayName = "Comics Root",
            addedAtEpochMillis = 500L,
            lastScannedAtEpochMillis = 1000L,
            hasPersistedPermission = true
        )

        val comic1 = Comic(
            comicKey = "c1",
            title = "Spider-Man #1",
            fileName = "spiderman_1.cbz",
            format = ComicFormat.CBZ,
            sourceType = ComicSourceType.LOCAL_SAF,
            availability = ComicAvailability.LOCAL,
            sizeBytes = 10_000_000L,
            lastModifiedEpochMillis = 1000L,
            pageCount = 10,
            localUri = "content://tree/spiderman_1.cbz",
            folderUri = "content://tree/comics"
        )

        val comic2 = Comic(
            comicKey = "c2",
            title = "Spider-Man #2",
            fileName = "spiderman_2.cbz",
            format = ComicFormat.CBZ,
            sourceType = ComicSourceType.LOCAL_SAF,
            availability = ComicAvailability.LOCAL,
            sizeBytes = 10_000_000L,
            lastModifiedEpochMillis = 2000L,
            pageCount = 12,
            localUri = "content://tree/spiderman_2.cbz",
            folderUri = "content://tree/comics"
        )

        val series = ComicSeries(
            seriesKey = "spider-man",
            name = "Spider-Man",
            comics = listOf(comic1, comic2),
            coverComic = comic1
        )

        val uiStateHolder = androidx.compose.runtime.mutableStateOf(
            LibraryUiState(
                isInitialLoading = false,
                folders = listOf(folder),
                comics = listOf(comic1, comic2),
                seriesList = listOf(series),
                totalComicCount = 2,
                selectedFilterTab = LibraryFilterTab.SERIES,
                selectedSeriesKey = null
            )
        )

        composeTestRule.setContent {
            ComixTheme {
                LibraryScreenContent(
                    uiState = uiStateHolder.value,
                    onEvent = { event ->
                        lastDispatchedEvent = event
                        if (event is LibraryUiEvent.SeriesSelected) {
                            uiStateHolder.value = uiStateHolder.value.copy(selectedSeriesKey = event.seriesKey)
                        } else if (event is LibraryUiEvent.ClearSelectedSeries) {
                            uiStateHolder.value = uiStateHolder.value.copy(selectedSeriesKey = null)
                        }
                    },
                    onAddFolderClick = {},
                    onOpenComic = { openedComicKey = it }
                )
            }
        }

        // Verify Series card is rendered
        composeTestRule.onNodeWithText("Spider-Man").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("2 issues").onFirst().assertIsDisplayed()

        // Click series card to drill down
        composeTestRule.onNodeWithText("Spider-Man").performClick()
        assertEquals(LibraryUiEvent.SeriesSelected("spider-man"), lastDispatchedEvent)

        // Verify drill-down header and issues
        composeTestRule.onNodeWithContentDescription("Back to all series").assertIsDisplayed()
        composeTestRule.onNodeWithText("Spider-Man #1").assertIsDisplayed()
        composeTestRule.onNodeWithText("Spider-Man #2").assertIsDisplayed()

        // Clicking a comic issue opens it
        composeTestRule.onNodeWithText("Spider-Man #1").performClick()
        assertEquals("c1", openedComicKey)

        // Click back button to return to series grid
        composeTestRule.onNodeWithContentDescription("Back to all series").performClick()
        assertEquals(LibraryUiEvent.ClearSelectedSeries, lastDispatchedEvent)

        // Verify we are back to series cards
        composeTestRule.onNodeWithText("Spider-Man").assertIsDisplayed()
    }

    @Test
    fun comicDetailsDialogRendersSeparateSeriesAndIssueFields() {
        val metadata = ComicMetadata(
            title = "Chapter 1: The Beginning",
            series = "Saga",
            number = "1",
            volume = 1,
            summary = "Two soldiers from opposite sides fall in love.",
            writer = "Brian K. Vaughan",
            penciller = "Fiona Staples",
            publisher = "Image Comics"
        )

        val comic = Comic(
            comicKey = "saga_1",
            title = "Saga #1",
            fileName = "saga_01.cbz",
            format = ComicFormat.CBZ,
            sourceType = ComicSourceType.LOCAL_SAF,
            availability = ComicAvailability.LOCAL,
            sizeBytes = 25_000_000L,
            lastModifiedEpochMillis = 1000L,
            pageCount = 44,
            localUri = "content://tree/saga_01.cbz",
            folderUri = "content://tree/comics",
            metadata = metadata
        )

        val folder = LibraryFolder(
            folderUri = "content://tree/comics",
            displayName = "Comics Root",
            addedAtEpochMillis = 500L,
            lastScannedAtEpochMillis = 1000L,
            hasPersistedPermission = true
        )

        val uiState = LibraryUiState(
            isInitialLoading = false,
            folders = listOf(folder),
            comics = listOf(comic),
            totalComicCount = 1,
            selectedComicForDetails = comic
        )

        composeTestRule.setContent {
            ComixTheme {
                LibraryScreenContent(
                    uiState = uiState,
                    onEvent = {},
                    onAddFolderClick = {},
                    onOpenComic = {}
                )
            }
        }

        // Details dialog title is metadata title
        composeTestRule.onNodeWithText("Chapter 1: The Beginning").assertIsDisplayed()

        // Separate Series and Issue rows exist
        composeTestRule.onAllNodesWithText("Series").onFirst().assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Saga").onFirst().assertIsDisplayed()
        composeTestRule.onNodeWithText("Issue").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("1").onFirst().assertIsDisplayed()

        // Confirm there is only one "Saga #1" in the hierarchy (the grid card behind the dialog)
        composeTestRule.onAllNodesWithText("Saga #1").assertCountEquals(1)
    }

    @Test
    fun comicDetailsDialogEditButtonDispatchesOpenMetadataEditorForComic() {
        var lastDispatchedEvent: LibraryUiEvent? = null

        val comic = Comic(
            comicKey = "saga_1",
            title = "Saga #1",
            fileName = "saga_01.cbz",
            format = ComicFormat.CBZ,
            sourceType = ComicSourceType.LOCAL_SAF,
            availability = ComicAvailability.LOCAL,
            sizeBytes = 25_000_000L,
            lastModifiedEpochMillis = 1000L,
            pageCount = 44,
            localUri = "content://tree/saga_01.cbz",
            folderUri = "content://tree/comics"
        )

        val folder = LibraryFolder(
            folderUri = "content://tree/comics",
            displayName = "Comics Root",
            addedAtEpochMillis = 500L,
            lastScannedAtEpochMillis = 1000L,
            hasPersistedPermission = true
        )

        val uiState = LibraryUiState(
            isInitialLoading = false,
            folders = listOf(folder),
            comics = listOf(comic),
            totalComicCount = 1,
            selectedComicForDetails = comic
        )

        composeTestRule.setContent {
            ComixTheme {
                LibraryScreenContent(
                    uiState = uiState,
                    onEvent = { lastDispatchedEvent = it },
                    onAddFolderClick = {},
                    onOpenComic = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Edit").assertIsDisplayed().performClick()
        assertEquals(LibraryUiEvent.OpenMetadataEditorForComic(comic), lastDispatchedEvent)
    }

    @Test
    fun singleMetadataEditorRendersAllFieldsAndSaveDispatchesSaveSingleMetadata() {
        var lastDispatchedEvent: LibraryUiEvent? = null

        val metadata = ComicMetadata(
            title = "Awakening",
            series = "Monstress",
            number = "1",
            volume = 1,
            releaseYear = 2015,
            writer = "Marjorie Liu",
            penciller = "Sana Takeda",
            publisher = "Image Comics",
            summary = "A teenage girl survives a cataclysmic war."
        )

        val comic = Comic(
            comicKey = "monstress_1",
            title = "Monstress #1",
            fileName = "monstress_01.cbz",
            format = ComicFormat.CBZ,
            sourceType = ComicSourceType.LOCAL_SAF,
            availability = ComicAvailability.LOCAL,
            sizeBytes = 25_000_000L,
            lastModifiedEpochMillis = 1000L,
            pageCount = 30,
            localUri = "content://tree/monstress_01.cbz",
            folderUri = "content://tree/comics",
            metadata = metadata
        )

        val uiState = LibraryUiState(
            isInitialLoading = false,
            comics = listOf(comic),
            totalComicCount = 1,
            editingMetadataComics = listOf(comic)
        )

        composeTestRule.setContent {
            ComixTheme {
                LibraryScreenContent(
                    uiState = uiState,
                    onEvent = { lastDispatchedEvent = it },
                    onAddFolderClick = {},
                    onOpenComic = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Edit issue metadata").assertIsDisplayed()
        composeTestRule.onNodeWithTag("metadata_field_series").assertIsDisplayed()
        composeTestRule.onNodeWithTag("metadata_field_title").assertIsDisplayed()
        composeTestRule.onNodeWithTag("metadata_field_issue").assertIsDisplayed()
        composeTestRule.onNodeWithTag("metadata_field_year").assertIsDisplayed()
        composeTestRule.onNodeWithTag("metadata_field_writer").assertIsDisplayed()
        composeTestRule.onNodeWithTag("metadata_field_penciller").assertIsDisplayed()
        composeTestRule.onNodeWithTag("metadata_field_publisher").assertIsDisplayed()
        composeTestRule.onNodeWithTag("metadata_field_description").assertIsDisplayed()

        // Edit Series
        composeTestRule.onNodeWithTag("metadata_field_series").performTextReplacement("Monstress Edited")

        // Click Save
        composeTestRule.onNodeWithText("Save").performClick()

        assertTrue(lastDispatchedEvent is LibraryUiEvent.SaveSingleMetadata)
        val update = (lastDispatchedEvent as LibraryUiEvent.SaveSingleMetadata).update
        assertEquals("monstress_1", update.comicKey)
        assertEquals("Monstress Edited", update.series)
        assertEquals("Awakening", update.title)
        assertEquals("1", update.number)
        assertEquals(2015, update.releaseYear)
        assertEquals("Marjorie Liu", update.writer)
        assertEquals("Sana Takeda", update.penciller)
        assertEquals("Image Comics", update.publisher)
        assertEquals("A teenage girl survives a cataclysmic war.", update.summary)
    }

    @Test
    fun bulkMetadataEditorRendersSharedFieldsAndSaveDispatchesSaveBulkMetadata() {
        var lastDispatchedEvent: LibraryUiEvent? = null

        val comic1 = Comic(
            comicKey = "c1",
            title = "Comic 1",
            fileName = "c1.cbz",
            format = ComicFormat.CBZ,
            sourceType = ComicSourceType.LOCAL_SAF,
            availability = ComicAvailability.LOCAL,
            sizeBytes = 10_000_000L,
            lastModifiedEpochMillis = 1000L,
            pageCount = 20,
            localUri = "content://tree/c1.cbz",
            folderUri = "content://tree/comics",
            metadata = ComicMetadata(series = "Shared Series", writer = "Writer 1")
        )

        val comic2 = Comic(
            comicKey = "c2",
            title = "Comic 2",
            fileName = "c2.cbz",
            format = ComicFormat.CBZ,
            sourceType = ComicSourceType.LOCAL_SAF,
            availability = ComicAvailability.LOCAL,
            sizeBytes = 10_000_000L,
            lastModifiedEpochMillis = 1000L,
            pageCount = 20,
            localUri = "content://tree/c2.cbz",
            folderUri = "content://tree/comics",
            metadata = ComicMetadata(series = "Shared Series", writer = "Writer 2")
        )

        val uiState = LibraryUiState(
            isInitialLoading = false,
            comics = listOf(comic1, comic2),
            totalComicCount = 2,
            editingMetadataComics = listOf(comic1, comic2)
        )

        composeTestRule.setContent {
            ComixTheme {
                LibraryScreenContent(
                    uiState = uiState,
                    onEvent = { lastDispatchedEvent = it },
                    onAddFolderClick = {},
                    onOpenComic = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Edit 2 issues").assertIsDisplayed()
        composeTestRule.onNodeWithTag("metadata_field_series").assertIsDisplayed()
        composeTestRule.onNodeWithTag("metadata_field_year").assertIsDisplayed()
        composeTestRule.onNodeWithTag("metadata_field_writer").assertIsDisplayed()
        composeTestRule.onNodeWithTag("metadata_field_penciller").assertIsDisplayed()
        composeTestRule.onNodeWithTag("metadata_field_publisher").assertIsDisplayed()

        // Disallowed bulk fields must not exist
        composeTestRule.onNodeWithTag("metadata_field_title").assertDoesNotExist()
        composeTestRule.onNodeWithTag("metadata_field_issue").assertDoesNotExist()
        composeTestRule.onNodeWithTag("metadata_field_description").assertDoesNotExist()

        // Edit Writer field
        composeTestRule.onNodeWithTag("metadata_field_writer").performTextReplacement("Common Writer")

        // Click Save
        composeTestRule.onNodeWithText("Save").performClick()

        assertTrue(lastDispatchedEvent is LibraryUiEvent.SaveBulkMetadata)
        val update = (lastDispatchedEvent as LibraryUiEvent.SaveBulkMetadata).update
        assertEquals(listOf("c1", "c2"), update.comicKeys)
        assertEquals("Common Writer", update.writer)
        assertTrue(update.updateWriter)
        assertFalse(update.updateSeries)
        assertFalse(update.updateReleaseYear)
        assertFalse(update.updatePenciller)
        assertFalse(update.updatePublisher)
    }

    @Test
    fun selectionModeRendersContextualBarAndHandlesActionsAndLongPressEntersSelectionMode() {
        var lastDispatchedEvent: LibraryUiEvent? = null

        val comic = Comic(
            comicKey = "sm_issue_1",
            title = "Spider-Man #1",
            fileName = "spiderman_1.cbz",
            format = ComicFormat.CBZ,
            sourceType = ComicSourceType.LOCAL_SAF,
            availability = ComicAvailability.LOCAL,
            sizeBytes = 25_000_000L,
            lastModifiedEpochMillis = 1000L,
            pageCount = 10,
            localUri = "content://tree/spiderman_1.cbz",
            folderUri = "content://tree/comics"
        )

        val folder = LibraryFolder(
            folderUri = "content://tree/comics",
            displayName = "Comics Root",
            addedAtEpochMillis = 500L,
            lastScannedAtEpochMillis = 1000L,
            hasPersistedPermission = true
        )

        val uiStateHolder = androidx.compose.runtime.mutableStateOf(
            LibraryUiState(
                isInitialLoading = false,
                folders = listOf(folder),
                comics = listOf(comic),
                totalComicCount = 1,
                selectedComicKeys = emptySet()
            )
        )

        composeTestRule.setContent {
            ComixTheme {
                LibraryScreenContent(
                    uiState = uiStateHolder.value,
                    onEvent = { event ->
                        lastDispatchedEvent = event
                        if (event is LibraryUiEvent.EnterSelectionMode) {
                            uiStateHolder.value = uiStateHolder.value.copy(
                                selectedComicKeys = setOf(event.comicKey)
                            )
                        } else if (event is LibraryUiEvent.ClearSelection) {
                            uiStateHolder.value = uiStateHolder.value.copy(
                                selectedComicKeys = emptySet()
                            )
                        }
                    },
                    onAddFolderClick = {},
                    onOpenComic = {}
                )
            }
        }

        // 1. Long-press on comic card enters selection mode
        composeTestRule.onNodeWithText("Spider-Man #1").performTouchInput { longClick() }
        assertEquals(LibraryUiEvent.EnterSelectionMode("sm_issue_1"), lastDispatchedEvent)

        // 2. Selection bar controls are displayed
        composeTestRule.onNodeWithText("1 selected").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Select all").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Edit metadata").assertIsDisplayed()

        // 3. Click "Edit metadata"
        composeTestRule.onNodeWithContentDescription("Edit metadata").performClick()
        assertEquals(LibraryUiEvent.OpenMetadataEditorForSelection, lastDispatchedEvent)

        // 4. Click "Clear selection"
        composeTestRule.onNodeWithContentDescription("Clear selection").performClick()
        assertEquals(LibraryUiEvent.ClearSelection, lastDispatchedEvent)

        // Contextual bar should be gone
        composeTestRule.onNodeWithText("1 selected").assertDoesNotExist()
    }
}
