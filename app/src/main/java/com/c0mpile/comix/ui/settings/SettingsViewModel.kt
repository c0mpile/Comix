package com.c0mpile.comix.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.c0mpile.comix.domain.usecase.AddLibraryFolderUseCase
import com.c0mpile.comix.domain.usecase.ObserveLibraryFoldersUseCase
import com.c0mpile.comix.domain.usecase.ObserveUserSettingsUseCase
import com.c0mpile.comix.domain.usecase.RemoveLibraryFolderUseCase
import com.c0mpile.comix.domain.usecase.UpdateKeepScreenOnUseCase
import com.c0mpile.comix.domain.usecase.UpdateReaderScaleModeUseCase
import com.c0mpile.comix.domain.usecase.UpdateReadingDirectionUseCase
import com.c0mpile.comix.domain.usecase.UpdateThemeModeUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel managing user preferences and registered library folders on the Settings screen.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    observeUserSettingsUseCase: ObserveUserSettingsUseCase,
    observeLibraryFoldersUseCase: ObserveLibraryFoldersUseCase,
    private val updateThemeModeUseCase: UpdateThemeModeUseCase,
    private val updateReadingDirectionUseCase: UpdateReadingDirectionUseCase,
    private val updateReaderScaleModeUseCase: UpdateReaderScaleModeUseCase,
    private val updateKeepScreenOnUseCase: UpdateKeepScreenOnUseCase,
    private val addLibraryFolderUseCase: AddLibraryFolderUseCase,
    private val removeLibraryFolderUseCase: RemoveLibraryFolderUseCase
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        observeUserSettingsUseCase(),
        observeLibraryFoldersUseCase()
    ) { settings, folders ->
        SettingsUiState(
            userSettings = settings,
            folders = folders
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState()
    )

    fun onEvent(event: SettingsUiEvent) {
        when (event) {
            is SettingsUiEvent.ThemeModeSelected -> {
                viewModelScope.launch {
                    updateThemeModeUseCase(event.mode)
                }
            }

            is SettingsUiEvent.ReadingDirectionSelected -> {
                viewModelScope.launch {
                    updateReadingDirectionUseCase(event.direction)
                }
            }

            is SettingsUiEvent.ReaderScaleModeSelected -> {
                viewModelScope.launch {
                    updateReaderScaleModeUseCase(event.scaleMode)
                }
            }

            is SettingsUiEvent.KeepScreenOnToggled -> {
                viewModelScope.launch {
                    updateKeepScreenOnUseCase(event.enabled)
                }
            }

            is SettingsUiEvent.FolderPicked -> {
                viewModelScope.launch {
                    addLibraryFolderUseCase(event.treeUri)
                }
            }

            is SettingsUiEvent.RemoveFolderRequested -> {
                viewModelScope.launch {
                    removeLibraryFolderUseCase(event.folderUri)
                }
            }
        }
    }
}
