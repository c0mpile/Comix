package com.c0mpile.comix.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.c0mpile.comix.domain.model.AppThemeMode
import com.c0mpile.comix.domain.model.LibraryFolder
import com.c0mpile.comix.domain.model.ReaderScaleMode
import com.c0mpile.comix.domain.model.ReadingDirection
import com.c0mpile.comix.domain.model.UserSettings
import com.c0mpile.comix.ui.settings.SettingsContent
import com.c0mpile.comix.ui.settings.SettingsUiEvent
import com.c0mpile.comix.ui.settings.SettingsUiState
import com.c0mpile.comix.ui.theme.ComixTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented UI tests for [SettingsContent].
 */
@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun rendersSectionsAndDispatchesThemeAndReaderSettingsEvents() {
        val capturedEvents = mutableListOf<SettingsUiEvent>()
        var backClicked = false

        val uiState = SettingsUiState(
            userSettings = UserSettings(
                themeMode = AppThemeMode.SYSTEM,
                readingDirection = ReadingDirection.LEFT_TO_RIGHT,
                readerScaleMode = ReaderScaleMode.FIT_SCREEN,
                keepScreenOn = false
            ),
            folders = emptyList()
        )

        composeTestRule.setContent {
            ComixTheme {
                SettingsContent(
                    uiState = uiState,
                    onEvent = { capturedEvents.add(it) },
                    onAddFolderClick = {},
                    onNavigateBack = { backClicked = true }
                )
            }
        }

        // Section headers
        composeTestRule.onNodeWithText("Appearance").assertIsDisplayed()
        composeTestRule.onNodeWithText("Reader").assertIsDisplayed()
        composeTestRule.onNodeWithText("Library folders").assertIsDisplayed()
        composeTestRule.onNodeWithText("No comic folders added").assertIsDisplayed()

        // Theme selection
        composeTestRule.onNodeWithText("Dark").performClick()
        assertEquals(SettingsUiEvent.ThemeModeSelected(AppThemeMode.DARK), capturedEvents.last())

        // Reading direction selection
        composeTestRule.onNodeWithText("Right to left").performClick()
        assertEquals(SettingsUiEvent.ReadingDirectionSelected(ReadingDirection.RIGHT_TO_LEFT), capturedEvents.last())

        // Scale mode selection
        composeTestRule.onNodeWithText("Fit width").performClick()
        assertEquals(SettingsUiEvent.ReaderScaleModeSelected(ReaderScaleMode.FIT_WIDTH), capturedEvents.last())

        // Keep screen on switch
        composeTestRule.onNodeWithText("Keep screen on").performClick()
        assertEquals(SettingsUiEvent.KeepScreenOnToggled(true), capturedEvents.last())

        // Navigate back
        composeTestRule.onNodeWithContentDescription("Back").performClick()
        assertTrue(backClicked)
    }

    @Test
    fun rendersFoldersWithRevokedWarningAndDispatchesFolderActions() {
        val capturedEvents = mutableListOf<SettingsUiEvent>()
        var addFolderClicked = false

        val validFolder = LibraryFolder(
            folderUri = "content://tree/comics",
            displayName = "Main Comics",
            addedAtEpochMillis = 1000L,
            lastScannedAtEpochMillis = 2000L,
            hasPersistedPermission = true
        )
        val revokedFolder = LibraryFolder(
            folderUri = "content://tree/manga",
            displayName = "Manga Shelf",
            addedAtEpochMillis = 3000L,
            lastScannedAtEpochMillis = 4000L,
            hasPersistedPermission = false
        )

        val uiState = SettingsUiState(
            userSettings = UserSettings(),
            folders = listOf(validFolder, revokedFolder)
        )

        composeTestRule.setContent {
            ComixTheme {
                SettingsContent(
                    uiState = uiState,
                    onEvent = { capturedEvents.add(it) },
                    onAddFolderClick = { addFolderClicked = true },
                    onNavigateBack = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Main Comics").assertIsDisplayed()
        composeTestRule.onNodeWithText("content://tree/comics").assertIsDisplayed()

        composeTestRule.onNodeWithText("Manga Shelf").assertIsDisplayed()
        composeTestRule.onNodeWithText("content://tree/manga").assertIsDisplayed()
        composeTestRule.onNodeWithText("Permission revoked").assertIsDisplayed()

        // Remove folder
        composeTestRule.onNodeWithContentDescription("Remove folder Manga Shelf").performClick()
        assertEquals(
            SettingsUiEvent.RemoveFolderRequested("content://tree/manga"),
            capturedEvents.last()
        )

        // Add folder
        composeTestRule.onNodeWithText("Add comic folder").performClick()
        assertTrue(addFolderClicked)
    }
}
