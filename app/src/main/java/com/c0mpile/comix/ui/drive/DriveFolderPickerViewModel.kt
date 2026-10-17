package com.c0mpile.comix.ui.drive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.c0mpile.comix.data.archive.NaturalOrderComparator
import com.c0mpile.comix.data.remote.drive.DriveApiException
import com.c0mpile.comix.domain.model.ComicError
import com.c0mpile.comix.domain.usecase.AddDriveFolderUseCase
import com.c0mpile.comix.domain.usecase.BrowseDriveFoldersUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel managing folder browsing, hierarchy navigation, and root folder confirmation
 * for Google Drive in the folder picker.
 */
@HiltViewModel
class DriveFolderPickerViewModel @Inject constructor(
    private val browseDriveFoldersUseCase: BrowseDriveFoldersUseCase,
    private val addDriveFolderUseCase: AddDriveFolderUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(DriveFolderPickerUiState())
    val uiState: StateFlow<DriveFolderPickerUiState> = _uiState.asStateFlow()

    init {
        loadSubfolders(ROOT_FOLDER_ID)
    }

    fun onEvent(event: DriveFolderPickerUiEvent) {
        when (event) {
            is DriveFolderPickerUiEvent.SubfolderClicked -> {
                if (_uiState.value.isLoading || _uiState.value.isAddingFolder) return
                val nextBreadcrumb = DriveBreadcrumb(
                    folderId = event.item.id,
                    displayName = event.item.name,
                    driveId = event.item.driveId
                )
                _uiState.update {
                    it.copy(
                        breadcrumbs = it.breadcrumbs + nextBreadcrumb,
                        subfolders = emptyList()
                    )
                }
                loadSubfolders(event.item.id)
            }

            is DriveFolderPickerUiEvent.NavigateUpInFolderStack -> {
                if (_uiState.value.breadcrumbs.size > 1 && !_uiState.value.isAddingFolder) {
                    val updatedBreadcrumbs = _uiState.value.breadcrumbs.dropLast(1)
                    _uiState.update {
                        it.copy(
                            breadcrumbs = updatedBreadcrumbs,
                            subfolders = emptyList()
                        )
                    }
                    loadSubfolders(updatedBreadcrumbs.last().folderId)
                }
            }

            is DriveFolderPickerUiEvent.ConfirmCurrentFolderClicked -> {
                if (_uiState.value.isLoading || _uiState.value.isAddingFolder) return
                val current = _uiState.value.currentFolder
                viewModelScope.launch {
                    _uiState.update { it.copy(isAddingFolder = true, error = null) }
                    val result = addDriveFolderUseCase(
                        folderId = current.folderId,
                        displayName = current.displayName,
                        driveId = current.driveId
                    )
                    result.onSuccess {
                        _uiState.update {
                            it.copy(
                                isAddingFolder = false,
                                folderAddedSuccess = true
                            )
                        }
                    }.onFailure { throwable ->
                        val comicError = (throwable as? DriveApiException)?.comicError
                            ?: ComicError.UnknownIoError(throwable.message)
                        _uiState.update {
                            it.copy(
                                isAddingFolder = false,
                                error = comicError
                            )
                        }
                    }
                }
            }

            is DriveFolderPickerUiEvent.RetryLoadClicked -> {
                loadSubfolders(_uiState.value.currentFolder.folderId)
            }
        }
    }

    private fun loadSubfolders(folderId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val result = browseDriveFoldersUseCase(folderId)
            result.onSuccess { items ->
                val sorted = items.sortedWith { a, b ->
                    NaturalOrderComparator.compare(a.name, b.name)
                }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        subfolders = sorted,
                        error = null
                    )
                }
            }.onFailure { throwable ->
                val comicError = (throwable as? DriveApiException)?.comicError
                    ?: ComicError.UnknownIoError(throwable.message)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = comicError
                    )
                }
            }
        }
    }

    companion object {
        const val ROOT_FOLDER_ID = "root"
    }
}
