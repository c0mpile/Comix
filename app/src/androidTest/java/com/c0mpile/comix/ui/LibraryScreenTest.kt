package com.c0mpile.comix.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.c0mpile.comix.domain.model.Comic
import com.c0mpile.comix.domain.model.ComicAvailability
import com.c0mpile.comix.domain.model.ComicFormat
import com.c0mpile.comix.domain.model.ComicSourceType
import com.c0mpile.comix.domain.model.LibraryFolder
import com.c0mpile.comix.domain.model.ReadingProgress
import com.c0mpile.comix.ui.library.LibraryScreenContent
import com.c0mpile.comix.ui.library.LibraryUiEvent
import com.c0mpile.comix.ui.library.LibraryUiState
import com.c0mpile.comix.ui.theme.ComixTheme
import org.junit.Assert.assertEquals
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
}
