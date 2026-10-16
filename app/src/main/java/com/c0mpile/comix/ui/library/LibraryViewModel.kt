package com.c0mpile.comix.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.c0mpile.comix.data.archive.NaturalOrderComparator
import com.c0mpile.comix.domain.model.ComicError
import com.c0mpile.comix.domain.model.LibrarySortOrder
import com.c0mpile.comix.domain.usecase.AddLibraryFolderUseCase
import com.c0mpile.comix.domain.usecase.ObserveLibraryComicsUseCase
import com.c0mpile.comix.domain.usecase.ObserveLibraryFoldersUseCase
import com.c0mpile.comix.domain.usecase.ObserveUserSettingsUseCase
import com.c0mpile.comix.domain.usecase.RemoveLibraryFolderUseCase
import com.c0mpile.comix.domain.usecase.RescanLibraryUseCase
import com.c0mpile.comix.domain.usecase.UpdateLibrarySortOrderUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel managing the Library screen state, search query filtering, sort orders,
 * folder observation, scanning, and user events.
 */
@HiltViewModel
class LibraryViewModel @Inject constructor(
    observeLibraryComicsUseCase: ObserveLibraryComicsUseCase,
    observeLibraryFoldersUseCase: ObserveLibraryFoldersUseCase,
    private val addLibraryFolderUseCase: AddLibraryFolderUseCase,
    private val rescanLibraryUseCase: RescanLibraryUseCase,
    private val removeLibraryFolderUseCase: RemoveLibraryFolderUseCase,
    private val observeUserSettingsUseCase: ObserveUserSettingsUseCase,
    private val updateLibrarySortOrderUseCase: UpdateLibrarySortOrderUseCase
) : ViewModel() {

    private data class ScannerState(
        val isScanning: Boolean = false,
        val activeError: ComicError? = null,
        val scanFailureCount: Int = 0
    )

    private data class SearchState(
        val query: String = "",
        val isActive: Boolean = false
    )

    private val scannerState = MutableStateFlow(ScannerState())
    private val searchState = MutableStateFlow(SearchState())

    val uiState: StateFlow<LibraryUiState> = combine(
        observeLibraryComicsUseCase(),
        observeLibraryFoldersUseCase(),
        observeUserSettingsUseCase(),
        scannerState,
        searchState
    ) { rawComics, folders, settings, scan, search ->
        val totalCount = rawComics.size
        val revoked = folders.filter { !it.hasPersistedPermission }

        val filteredComics = if (search.query.isBlank()) {
            rawComics
        } else {
            rawComics.filter { comic ->
                comic.title.contains(search.query, ignoreCase = true) ||
                        comic.fileName.contains(search.query, ignoreCase = true)
            }
        }

        val sortedComics = when (settings.librarySortOrder) {
            LibrarySortOrder.TITLE_ASC -> filteredComics.sortedWith { a, b ->
                NaturalOrderComparator.compare(a.title, b.title)
            }
            LibrarySortOrder.TITLE_DESC -> filteredComics.sortedWith { a, b ->
                NaturalOrderComparator.compare(b.title, a.title)
            }
            LibrarySortOrder.RECENTLY_READ -> filteredComics.sortedWith { a, b ->
                val timeA = a.progress?.updatedAtEpochMillis ?: 0L
                val timeB = b.progress?.updatedAtEpochMillis ?: 0L
                val timeCmp = timeB.compareTo(timeA)
                if (timeCmp != 0) timeCmp else NaturalOrderComparator.compare(a.title, b.title)
            }
            LibrarySortOrder.DATE_ADDED_DESC -> filteredComics.sortedWith { a, b ->
                val timeCmp = b.lastModifiedEpochMillis.compareTo(a.lastModifiedEpochMillis)
                if (timeCmp != 0) timeCmp else NaturalOrderComparator.compare(a.title, b.title)
            }
        }

        LibraryUiState(
            comics = sortedComics,
            folders = folders,
            isInitialLoading = false,
            isScanning = scan.isScanning,
            revokedFolders = revoked,
            activeError = scan.activeError,
            scanFailureCount = scan.scanFailureCount,
            searchQuery = search.query,
            isSearchActive = search.isActive,
            sortOrder = settings.librarySortOrder,
            totalComicCount = totalCount
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LibraryUiState(isInitialLoading = true)
    )

    fun onEvent(event: LibraryUiEvent) {
        when (event) {
            is LibraryUiEvent.FolderPicked -> {
                viewModelScope.launch {
                    scannerState.update {
                        it.copy(isScanning = true, activeError = null, scanFailureCount = 0)
                    }
                    val result = addLibraryFolderUseCase(event.treeUri)
                    result.onSuccess { scanResult ->
                        val nonArchiveError = scanResult.errors.firstOrNull {
                            it !is ComicError.CorruptArchive && it !is ComicError.EmptyArchive
                        }
                        scannerState.update {
                            it.copy(
                                isScanning = false,
                                scanFailureCount = scanResult.skippedCorruptCount,
                                activeError = nonArchiveError
                            )
                        }
                    }.onFailure { throwable ->
                        scannerState.update {
                            it.copy(
                                isScanning = false,
                                activeError = ComicError.UnknownIoError(throwable.message)
                            )
                        }
                    }
                }
            }

            is LibraryUiEvent.RescanRequested -> {
                viewModelScope.launch {
                    scannerState.update {
                        it.copy(isScanning = true, activeError = null, scanFailureCount = 0)
                    }
                    val result = rescanLibraryUseCase()
                    result.onSuccess { scanResult ->
                        val nonArchiveError = scanResult.errors.firstOrNull {
                            it !is ComicError.CorruptArchive && it !is ComicError.EmptyArchive
                        }
                        scannerState.update {
                            it.copy(
                                isScanning = false,
                                scanFailureCount = scanResult.skippedCorruptCount,
                                activeError = nonArchiveError
                            )
                        }
                    }.onFailure { throwable ->
                        scannerState.update {
                            it.copy(
                                isScanning = false,
                                activeError = ComicError.UnknownIoError(throwable.message)
                            )
                        }
                    }
                }
            }

            is LibraryUiEvent.RemoveFolderRequested -> {
                viewModelScope.launch {
                    removeLibraryFolderUseCase(event.folderUri)
                }
            }

            is LibraryUiEvent.DismissError -> {
                scannerState.update {
                    it.copy(activeError = null, scanFailureCount = 0)
                }
            }

            is LibraryUiEvent.SearchQueryChanged -> {
                searchState.update { it.copy(query = event.query) }
            }

            is LibraryUiEvent.SearchActiveChanged -> {
                searchState.update {
                    it.copy(
                        isActive = event.active,
                        query = if (!event.active) "" else it.query
                    )
                }
            }

            is LibraryUiEvent.SortOrderSelected -> {
                viewModelScope.launch {
                    updateLibrarySortOrderUseCase(event.sortOrder)
                }
            }
        }
    }
}
