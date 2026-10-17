package com.c0mpile.comix.ui.library

import com.c0mpile.comix.domain.model.Comic
import com.c0mpile.comix.domain.model.ComicError
import com.c0mpile.comix.domain.model.DriveFolder
import com.c0mpile.comix.domain.model.LibraryFolder
import com.c0mpile.comix.domain.model.LibrarySortOrder

/**
 * Immutable UI state for the Library screen.
 */
data class LibraryUiState(
    val comics: List<Comic> = emptyList(),
    val folders: List<LibraryFolder> = emptyList(),
    val driveFolders: List<DriveFolder> = emptyList(),
    val isInitialLoading: Boolean = true,
    val isScanning: Boolean = false,
    val revokedFolders: List<LibraryFolder> = emptyList(),
    val activeError: ComicError? = null,
    val scanFailureCount: Int = 0,
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val sortOrder: LibrarySortOrder = LibrarySortOrder.TITLE_ASC,
    val totalComicCount: Int = comics.size
) {
    val showEmptyNoFoldersState: Boolean
        get() = !isInitialLoading && !isScanning && folders.isEmpty() && driveFolders.isEmpty() && comics.isEmpty() && totalComicCount == 0

    val showEmptyNoComicsState: Boolean
        get() = !isInitialLoading && !isScanning && (folders.isNotEmpty() || driveFolders.isNotEmpty()) && comics.isEmpty() && totalComicCount == 0 && revokedFolders.isEmpty()

    val showEmptySearchResultsState: Boolean
        get() = !isInitialLoading && !isScanning && totalComicCount > 0 && comics.isEmpty() && searchQuery.isNotBlank()
}

/**
 * User intents emitted from the Library screen.
 */
sealed interface LibraryUiEvent {
    data class FolderPicked(val treeUri: String) : LibraryUiEvent
    data object RescanRequested : LibraryUiEvent
    data class RemoveFolderRequested(val folderUri: String) : LibraryUiEvent
    data object DismissError : LibraryUiEvent
    data class SearchQueryChanged(val query: String) : LibraryUiEvent
    data class SearchActiveChanged(val active: Boolean) : LibraryUiEvent
    data class SortOrderSelected(val sortOrder: LibrarySortOrder) : LibraryUiEvent
}
