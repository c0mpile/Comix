package com.c0mpile.comix.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.c0mpile.comix.domain.model.DriveBrowseItem
import com.c0mpile.comix.ui.drive.DriveBreadcrumb
import com.c0mpile.comix.ui.drive.DriveFolderPickerContent
import com.c0mpile.comix.ui.drive.DriveFolderPickerUiEvent
import com.c0mpile.comix.ui.drive.DriveFolderPickerUiState
import com.c0mpile.comix.ui.theme.ComixTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented Compose UI tests for [DriveFolderPickerContent].
 */
@RunWith(AndroidJUnit4::class)
class DriveFolderPickerScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun rendersBreadcrumbsSubfoldersShortcutChipAndConfirmsCurrentFolder() {
        val capturedEvents = mutableListOf<DriveFolderPickerUiEvent>()

        val normalFolder = DriveBrowseItem(
            id = "folder-comics",
            name = "Comics Folder",
            driveId = null,
            isShortcut = false
        )
        val shortcutFolder = DriveBrowseItem(
            id = "folder-manga",
            name = "Manga Folder",
            driveId = null,
            isShortcut = true
        )

        val uiState = DriveFolderPickerUiState(
            breadcrumbs = listOf(DriveBreadcrumb("root", "My Drive")),
            subfolders = listOf(normalFolder, shortcutFolder),
            isLoading = false,
            isAddingFolder = false
        )

        composeTestRule.setContent {
            ComixTheme {
                DriveFolderPickerContent(
                    uiState = uiState,
                    onEvent = { capturedEvents.add(it) },
                    onNavigateBack = {}
                )
            }
        }

        // Top bar breadcrumb title
        composeTestRule.onAllNodesWithText("My Drive").onFirst().assertIsDisplayed()

        // Subfolder list items
        composeTestRule.onNodeWithText("Comics Folder").assertIsDisplayed()
        composeTestRule.onNodeWithText("Manga Folder").assertIsDisplayed()

        // Shortcut chip on shortcut item
        composeTestRule.onNodeWithText("Shortcut").assertIsDisplayed()

        // Bottom action button
        composeTestRule.onNodeWithText("Use this folder").assertIsDisplayed()

        // Click subfolder
        composeTestRule.onNodeWithText("Comics Folder").performClick()
        assertEquals(DriveFolderPickerUiEvent.SubfolderClicked(normalFolder), capturedEvents.last())

        // Click confirm
        composeTestRule.onNodeWithText("Use this folder").performClick()
        assertEquals(DriveFolderPickerUiEvent.ConfirmCurrentFolderClicked, capturedEvents.last())
    }
}
