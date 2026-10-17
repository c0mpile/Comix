package com.c0mpile.comix.ui

import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.c0mpile.comix.domain.model.ComicError
import com.c0mpile.comix.domain.model.ReaderScaleMode
import com.c0mpile.comix.domain.model.ReadingDirection
import com.c0mpile.comix.ui.reader.PageLoadState
import com.c0mpile.comix.ui.reader.ReaderScreenContent
import com.c0mpile.comix.ui.reader.ReaderUiEvent
import com.c0mpile.comix.ui.reader.ReaderUiState
import com.c0mpile.comix.ui.theme.ComixTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented UI tests for [ReaderScreenContent].
 */
@RunWith(AndroidJUnit4::class)
class ReaderScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun rendersPageIndicatorAndBitmapAndHudWhenVisible() {
        var backClicked = false
        val testBitmap = Bitmap.createBitmap(200, 300, Bitmap.Config.ARGB_8888)

        val uiState = ReaderUiState(
            comicKey = "comic1",
            title = "Action Comics #1",
            pageCount = 3,
            currentPage = 0,
            isArchiveLoading = false,
            isHudVisible = true,
            pageStates = mapOf(0 to PageLoadState.Loaded(testBitmap))
        )

        composeTestRule.setContent {
            ComixTheme {
                ReaderScreenContent(
                    uiState = uiState,
                    onEvent = {},
                    onNavigateBack = { backClicked = true }
                )
            }
        }

        composeTestRule.onNodeWithText("Action Comics #1").assertIsDisplayed()
        composeTestRule.onNodeWithText("1 / 3").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Page 1 of 3").assertIsDisplayed()

        composeTestRule.onNodeWithContentDescription("Back").performClick()
        assertTrue(backClicked)
    }

    @Test
    fun rendersHudWithScaleModeToggleAndDispatchesEvents() {
        val capturedEvents = mutableListOf<ReaderUiEvent>()
        val testBitmap = Bitmap.createBitmap(200, 300, Bitmap.Config.ARGB_8888)

        val uiState = ReaderUiState(
            comicKey = "comic1",
            title = "Action Comics #1",
            pageCount = 3,
            currentPage = 0,
            isArchiveLoading = false,
            isHudVisible = true,
            readingDirection = ReadingDirection.LEFT_TO_RIGHT,
            scaleMode = ReaderScaleMode.FIT_SCREEN,
            pageStates = mapOf(0 to PageLoadState.Loaded(testBitmap))
        )

        composeTestRule.setContent {
            ComixTheme {
                ReaderScreenContent(
                    uiState = uiState,
                    onEvent = { capturedEvents.add(it) },
                    onNavigateBack = {}
                )
            }
        }

        composeTestRule.onNodeWithText("LTR").assertDoesNotExist()
        composeTestRule.onNodeWithText("RTL").assertDoesNotExist()
        composeTestRule.onNodeWithText("Fit Screen").assertIsDisplayed()

        composeTestRule.onNodeWithText("Fit Screen").performClick()
        assertEquals(ReaderUiEvent.ToggleScaleMode, capturedEvents.last())
    }

    @Test
    fun rendersFullScreenErrorStateWhenFatalErrorPresent() {
        var backClicked = false
        var retryDispatched = false

        val uiState = ReaderUiState(
            comicKey = "bad_comic",
            isArchiveLoading = false,
            fatalError = ComicError.CorruptArchive("bad.cbz", "Corrupt header")
        )

        composeTestRule.setContent {
            ComixTheme {
                ReaderScreenContent(
                    uiState = uiState,
                    onEvent = { event ->
                        if (event is ReaderUiEvent.RetryOpenArchive) {
                            retryDispatched = true
                        }
                    },
                    onNavigateBack = { backClicked = true }
                )
            }
        }

        composeTestRule.onNodeWithText("Corrupt archive").assertIsDisplayed()
        composeTestRule.onNodeWithText("Corrupt header").assertIsDisplayed()

        composeTestRule.onNodeWithText("Back to Library").performClick()
        assertTrue(backClicked)

        composeTestRule.onNodeWithText("Retry").performClick()
        assertTrue(retryDispatched)
    }

    @Test
    fun rendersInlinePageErrorWithRetryPageButton() {
        var retriedPageIndex: Int? = null

        val uiState = ReaderUiState(
            comicKey = "comic1",
            title = "Action Comics #1",
            pageCount = 3,
            currentPage = 0,
            isArchiveLoading = false,
            pageStates = mapOf(
                0 to PageLoadState.Error(ComicError.CorruptArchive(null, "Bad page"))
            )
        )

        composeTestRule.setContent {
            ComixTheme {
                ReaderScreenContent(
                    uiState = uiState,
                    onEvent = { event ->
                        if (event is ReaderUiEvent.RetryPage) {
                            retriedPageIndex = event.pageIndex
                        }
                    },
                    onNavigateBack = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Failed to load page 1").assertIsDisplayed()
        composeTestRule.onNodeWithText("Bad page").assertIsDisplayed()
        composeTestRule.onNodeWithText("Retry page").assertIsDisplayed()

        composeTestRule.onNodeWithText("Retry page").performClick()
        assertEquals(0, retriedPageIndex)
    }
}
