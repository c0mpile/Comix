package com.c0mpile.comix.ui.drive

import com.c0mpile.comix.domain.model.ComicError
import com.c0mpile.comix.domain.model.DriveBrowseItem

/**
 * A folder navigation entry in the Google Drive folder hierarchy breadcrumb trail.
 */
data class DriveBreadcrumb(
    val folderId: String,
    val displayName: String,
    val driveId: String? = null
)

/**
 * Immutable UI state for the Google Drive folder picker screen.
 */
data class DriveFolderPickerUiState(
    val breadcrumbs: List<DriveBreadcrumb> = listOf(DriveBreadcrumb("root", "My Drive")),
    val subfolders: List<DriveBrowseItem> = emptyList(),
    val isLoading: Boolean = true,
    val isAddingFolder: Boolean = false,
    val error: ComicError? = null,
    val folderAddedSuccess: Boolean = false
) {
    val currentFolder: DriveBreadcrumb
        get() = breadcrumbs.last()
}

/**
 * User actions emitted from the Google Drive folder picker screen.
 */
sealed interface DriveFolderPickerUiEvent {
    data class SubfolderClicked(val item: DriveBrowseItem) : DriveFolderPickerUiEvent
    data object NavigateUpInFolderStack : DriveFolderPickerUiEvent
    data object ConfirmCurrentFolderClicked : DriveFolderPickerUiEvent
    data object RetryLoadClicked : DriveFolderPickerUiEvent
}
