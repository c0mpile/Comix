package com.c0mpile.comix.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.c0mpile.comix.domain.model.ComicError
import com.c0mpile.comix.domain.usecase.AddLibraryFolderUseCase
import com.c0mpile.comix.domain.usecase.ObserveLibraryComicsUseCase
import com.c0mpile.comix.domain.usecase.ObserveLibraryFoldersUseCase
import com.c0mpile.comix.domain.usecase.RemoveLibraryFolderUseCase
import com.c0mpile.comix.domain.usecase.RescanLibraryUseCase
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
 * ViewModel managing the Library screen state, folder observation, scanning, and user events.
 */
@HiltViewModel
class LibraryViewModel @Inject constructor(
    observeLibraryComicsUseCase: ObserveLibraryComicsUseCase,
    observeLibraryFoldersUseCase: ObserveLibraryFoldersUseCase,
    private val addLibraryFolderUseCase: AddLibraryFolderUseCase,
    private val rescanLibraryUseCase: RescanLibraryUseCase,
    private val removeLibraryFolderUseCase: RemoveLibraryFolderUseCase
) : ViewModel() {

    private data class ScannerState(
        val isScanning: Boolean = false,
        val activeError: ComicError? = null,
        val scanFailureCount: Int = 0
    )

    private val scannerState = MutableStateFlow(ScannerState())

    val uiState: StateFlow<LibraryUiState> = combine(
        observeLibraryComicsUseCase(),
        observeLibraryFoldersUseCase(),
        scannerState
    ) { comics, folders, scan ->
        val revoked = folders.filter { !it.hasPersistedPermission }
        LibraryUiState(
            comics = comics,
            folders = folders,
            isInitialLoading = false,
            isScanning = scan.isScanning,
            revokedFolders = revoked,
            activeError = scan.activeError,
            scanFailureCount = scan.scanFailureCount
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
        }
    }
}
