package com.c0mpile.comix.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.c0mpile.comix.data.archive.NaturalOrderComparator
import com.c0mpile.comix.data.remote.drive.DriveApiException
import com.c0mpile.comix.domain.model.Comic
import com.c0mpile.comix.domain.model.ComicError
import com.c0mpile.comix.domain.model.ComicLibrary
import com.c0mpile.comix.domain.model.LibraryFilterTab
import com.c0mpile.comix.domain.model.LibraryLayoutMode
import com.c0mpile.comix.domain.model.LibrarySortOrder
import com.c0mpile.comix.domain.usecase.AddLibraryFolderUseCase
import com.c0mpile.comix.domain.usecase.CreateLibraryUseCase
import com.c0mpile.comix.domain.usecase.DeleteLibraryUseCase
import com.c0mpile.comix.domain.usecase.ObserveDriveFoldersUseCase
import com.c0mpile.comix.domain.usecase.ObserveLibrariesUseCase
import com.c0mpile.comix.domain.usecase.ObserveLibraryComicsUseCase
import com.c0mpile.comix.domain.usecase.ObserveLibraryFoldersUseCase
import com.c0mpile.comix.domain.usecase.ObserveUserSettingsUseCase
import com.c0mpile.comix.domain.usecase.RemoveLibraryFolderUseCase
import com.c0mpile.comix.domain.usecase.RescanDriveUseCase
import com.c0mpile.comix.domain.usecase.RescanLibraryUseCase
import com.c0mpile.comix.domain.usecase.UpdateActiveLibraryUseCase
import com.c0mpile.comix.domain.usecase.UpdateLibraryLayoutModeUseCase
import com.c0mpile.comix.domain.usecase.UpdateLibrarySortOrderUseCase
import com.c0mpile.comix.domain.usecase.UpdateLibraryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel managing the Library screen state, multiple libraries, collapsible drawer navigation,
 * filter tabs, search query filtering, sort orders, and folder scanning off the main thread.
 */
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val observeLibraryComicsUseCase: ObserveLibraryComicsUseCase,
    private val observeLibraryFoldersUseCase: ObserveLibraryFoldersUseCase,
    private val observeDriveFoldersUseCase: ObserveDriveFoldersUseCase,
    private val addLibraryFolderUseCase: AddLibraryFolderUseCase,
    private val rescanLibraryUseCase: RescanLibraryUseCase,
    private val rescanDriveUseCase: RescanDriveUseCase,
    private val removeLibraryFolderUseCase: RemoveLibraryFolderUseCase,
    private val observeUserSettingsUseCase: ObserveUserSettingsUseCase,
    private val updateLibrarySortOrderUseCase: UpdateLibrarySortOrderUseCase,
    private val observeLibrariesUseCase: ObserveLibrariesUseCase,
    private val createLibraryUseCase: CreateLibraryUseCase,
    private val updateLibraryUseCase: UpdateLibraryUseCase,
    private val deleteLibraryUseCase: DeleteLibraryUseCase,
    private val updateActiveLibraryUseCase: UpdateActiveLibraryUseCase,
    private val updateLibraryLayoutModeUseCase: UpdateLibraryLayoutModeUseCase
) : ViewModel() {

    private data class ScannerState(
        val isScanning: Boolean = false,
        val activeError: ComicError? = null,
        val scanFailureCount: Int = 0
    )

    private data class SearchAndFilterState(
        val query: String = "",
        val isActive: Boolean = false,
        val selectedFilterTab: LibraryFilterTab = LibraryFilterTab.ALL
    )

    private val scannerState = MutableStateFlow(ScannerState())
    private val searchAndFilterState = MutableStateFlow(SearchAndFilterState())
    private val selectedComicForDetails = MutableStateFlow<Comic?>(null)

    private val activeLibraryFlow = combine(
        observeLibrariesUseCase(),
        observeUserSettingsUseCase()
    ) { libraries, settings ->
        val activeId = if (libraries.isNotEmpty() && libraries.none { it.libraryId == settings.activeLibraryId }) {
            libraries.first().libraryId
        } else {
            settings.activeLibraryId
        }
        Pair(libraries, activeId)
    }.distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<LibraryUiState> = activeLibraryFlow.flatMapLatest { (libraries, activeId) ->
        combine(
            observeLibraryComicsUseCase(activeId),
            observeLibraryFoldersUseCase(activeId),
            observeDriveFoldersUseCase(activeId),
            observeUserSettingsUseCase(),
            combine(scannerState, searchAndFilterState, selectedComicForDetails, ::Triple)
        ) { rawComics, folders, driveFolders, settings, (scan, filter, detailsComic) ->
            val totalCount = rawComics.size
            val revoked = folders.filter { !it.hasPersistedPermission }

            val tabFilteredComics = when (filter.selectedFilterTab) {
                LibraryFilterTab.ALL -> rawComics
                LibraryFilterTab.IN_PROGRESS -> rawComics.filter { it.progress != null && !it.progress.isFinished }
                LibraryFilterTab.FINISHED -> rawComics.filter { it.progress?.isFinished == true }
            }

            val searchFilteredComics = if (filter.query.isBlank()) {
                tabFilteredComics
            } else {
                tabFilteredComics.filter { comic ->
                    comic.title.contains(filter.query, ignoreCase = true) ||
                            comic.fileName.contains(filter.query, ignoreCase = true) ||
                            comic.metadata?.title?.contains(filter.query, ignoreCase = true) == true ||
                            comic.metadata?.series?.contains(filter.query, ignoreCase = true) == true ||
                            comic.metadata?.writer?.contains(filter.query, ignoreCase = true) == true ||
                            comic.metadata?.publisher?.contains(filter.query, ignoreCase = true) == true ||
                            comic.metadata?.genre?.contains(filter.query, ignoreCase = true) == true
                }
            }

            val sortedComics = when (settings.librarySortOrder) {
                LibrarySortOrder.TITLE_ASC -> searchFilteredComics.sortedWith { a, b ->
                    NaturalOrderComparator.compare(a.title, b.title)
                }
                LibrarySortOrder.TITLE_DESC -> searchFilteredComics.sortedWith { a, b ->
                    NaturalOrderComparator.compare(b.title, a.title)
                }
                LibrarySortOrder.RECENTLY_READ -> searchFilteredComics.sortedWith { a, b ->
                    val timeA = a.progress?.updatedAtEpochMillis ?: 0L
                    val timeB = b.progress?.updatedAtEpochMillis ?: 0L
                    val timeCmp = timeB.compareTo(timeA)
                    if (timeCmp != 0) timeCmp else NaturalOrderComparator.compare(a.title, b.title)
                }
                LibrarySortOrder.DATE_ADDED_DESC -> searchFilteredComics.sortedWith { a, b ->
                    val timeCmp = b.lastModifiedEpochMillis.compareTo(a.lastModifiedEpochMillis)
                    if (timeCmp != 0) timeCmp else NaturalOrderComparator.compare(a.title, b.title)
                }
            }

            LibraryUiState(
                comics = sortedComics,
                folders = folders,
                driveFolders = driveFolders,
                libraries = libraries,
                activeLibraryId = activeId,
                selectedFilterTab = filter.selectedFilterTab,
                layoutMode = settings.libraryLayoutMode,
                isInitialLoading = false,
                isScanning = scan.isScanning,
                revokedFolders = revoked,
                activeError = scan.activeError,
                scanFailureCount = scan.scanFailureCount,
                searchQuery = filter.query,
                isSearchActive = filter.isActive,
                sortOrder = settings.librarySortOrder,
                totalComicCount = totalCount,
                selectedComicForDetails = detailsComic
            )
        }
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
                    val targetLibraryId = uiState.value.activeLibraryId
                    val result = addLibraryFolderUseCase(event.treeUri, targetLibraryId)
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
                    val targetLibraryId = uiState.value.activeLibraryId
                    val localResult = rescanLibraryUseCase(targetLibraryId)
                    var activeError: ComicError? = null
                    var failureCount = 0

                    localResult.onSuccess { scanResult ->
                        failureCount += scanResult.skippedCorruptCount
                        activeError = scanResult.errors.firstOrNull {
                            it !is ComicError.CorruptArchive && it !is ComicError.EmptyArchive
                        }
                    }.onFailure { throwable ->
                        activeError = ComicError.UnknownIoError(throwable.message)
                    }

                    val currentDriveFolders = uiState.value.driveFolders
                    if (currentDriveFolders.isNotEmpty()) {
                        val driveResult = rescanDriveUseCase(targetLibraryId)
                        driveResult.onSuccess { scanResult ->
                            failureCount += scanResult.skippedCorruptCount
                            if (activeError == null) {
                                activeError = scanResult.errors.firstOrNull {
                                    it !is ComicError.CorruptArchive && it !is ComicError.EmptyArchive
                                }
                            }
                        }.onFailure { throwable ->
                            if (activeError == null) {
                                activeError = (throwable as? DriveApiException)?.comicError
                                    ?: ComicError.UnknownIoError(throwable.message)
                            }
                        }
                    }

                    scannerState.update {
                        it.copy(
                            isScanning = false,
                            scanFailureCount = failureCount,
                            activeError = activeError
                        )
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
                searchAndFilterState.update { it.copy(query = event.query) }
            }

            is LibraryUiEvent.SearchActiveChanged -> {
                searchAndFilterState.update {
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

            is LibraryUiEvent.LibrarySelected -> {
                viewModelScope.launch {
                    updateActiveLibraryUseCase(event.libraryId)
                }
            }

            is LibraryUiEvent.CreateLibrarySubmitted -> {
                viewModelScope.launch {
                    val result = createLibraryUseCase(event.name, event.defaultReadingDirection)
                    result.onSuccess { created ->
                        updateActiveLibraryUseCase(created.libraryId)
                    }.onFailure { throwable ->
                        scannerState.update {
                            it.copy(activeError = ComicError.UnknownIoError(throwable.message))
                        }
                    }
                }
            }

            is LibraryUiEvent.UpdateLibrarySubmitted -> {
                viewModelScope.launch {
                    val result = updateLibraryUseCase(
                        event.libraryId,
                        event.name,
                        event.defaultReadingDirection
                    )
                    result.onFailure { throwable ->
                        scannerState.update {
                            it.copy(activeError = ComicError.UnknownIoError(throwable.message))
                        }
                    }
                }
            }

            is LibraryUiEvent.DeleteLibraryConfirmed -> {
                viewModelScope.launch {
                    val remainingTarget = uiState.value.libraries
                        .firstOrNull { it.libraryId != event.libraryId }?.libraryId
                        ?: ComicLibrary.DEFAULT_COMICS_ID
                    val result = deleteLibraryUseCase(event.libraryId)
                    result.onSuccess {
                        if (uiState.value.activeLibraryId == event.libraryId) {
                            updateActiveLibraryUseCase(remainingTarget)
                        }
                    }.onFailure { throwable ->
                        scannerState.update {
                            it.copy(activeError = ComicError.UnknownIoError(throwable.message))
                        }
                    }
                }
            }

            is LibraryUiEvent.FilterTabSelected -> {
                searchAndFilterState.update { it.copy(selectedFilterTab = event.tab) }
            }

            is LibraryUiEvent.ToggleLayoutMode -> {
                viewModelScope.launch {
                    val nextMode = if (uiState.value.layoutMode == LibraryLayoutMode.GRID) {
                        LibraryLayoutMode.LIST
                    } else {
                        LibraryLayoutMode.GRID
                    }
                    updateLibraryLayoutModeUseCase(nextMode)
                }
            }

            is LibraryUiEvent.ShowComicDetails -> {
                selectedComicForDetails.value = event.comic
            }

            is LibraryUiEvent.DismissComicDetails -> {
                selectedComicForDetails.value = null
            }
        }
    }
}
