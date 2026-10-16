package com.c0mpile.comix.ui.library

import com.c0mpile.comix.domain.model.Comic
import com.c0mpile.comix.domain.model.ComicError
import com.c0mpile.comix.domain.model.LibraryFolder

/**
 * Immutable UI state for the Library screen.
 */
data class LibraryUiState(
    val comics: List<Comic> = emptyList(),
    val folders: List<LibraryFolder> = emptyList(),
    val isInitialLoading: Boolean = true,
    val isScanning: Boolean = false,
    val revokedFolders: List<LibraryFolder> = emptyList(),
    val activeError: ComicError? = null,
    val scanFailureCount: Int = 0
) {
    val showEmptyNoFoldersState: Boolean
        get() = !isInitialLoading && !isScanning && folders.isEmpty() && comics.isEmpty()

    val showEmptyNoComicsState: Boolean
        get() = !isInitialLoading && !isScanning && folders.isNotEmpty() && comics.isEmpty() && revokedFolders.isEmpty()
}

/**
 * User intents emitted from the Library screen.
 */
sealed interface LibraryUiEvent {
    data class FolderPicked(val treeUri: String) : LibraryUiEvent
    data object RescanRequested : LibraryUiEvent
    data class RemoveFolderRequested(val folderUri: String) : LibraryUiEvent
    data object DismissError : LibraryUiEvent
}
