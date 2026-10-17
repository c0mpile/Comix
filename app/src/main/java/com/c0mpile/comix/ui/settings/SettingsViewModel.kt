package com.c0mpile.comix.ui.settings

import android.app.PendingIntent
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.c0mpile.comix.data.remote.drive.PendingConsentResolutionException
import com.c0mpile.comix.domain.usecase.AddLibraryFolderUseCase
import com.c0mpile.comix.domain.usecase.CompleteDriveAuthUseCase
import com.c0mpile.comix.domain.usecase.ObserveDriveAuthStateUseCase
import com.c0mpile.comix.domain.usecase.ObserveDriveFoldersUseCase
import com.c0mpile.comix.domain.usecase.ObserveLibraryFoldersUseCase
import com.c0mpile.comix.domain.usecase.ObserveUserSettingsUseCase
import com.c0mpile.comix.domain.usecase.RemoveDriveFolderUseCase
import com.c0mpile.comix.domain.usecase.RemoveLibraryFolderUseCase
import com.c0mpile.comix.domain.usecase.SignInToDriveUseCase
import com.c0mpile.comix.domain.usecase.SignOutOfDriveUseCase
import com.c0mpile.comix.domain.usecase.UpdateKeepScreenOnUseCase
import com.c0mpile.comix.domain.usecase.UpdateReaderScaleModeUseCase
import com.c0mpile.comix.domain.usecase.UpdateReadingDirectionUseCase
import com.c0mpile.comix.domain.usecase.UpdateThemeModeUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel managing user preferences, registered library folders, and Google Drive account
 * integration on the Settings screen.
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
    private val removeLibraryFolderUseCase: RemoveLibraryFolderUseCase,
    observeDriveAuthStateUseCase: ObserveDriveAuthStateUseCase,
    observeDriveFoldersUseCase: ObserveDriveFoldersUseCase,
    private val signInToDriveUseCase: SignInToDriveUseCase,
    private val completeDriveAuthUseCase: CompleteDriveAuthUseCase,
    private val signOutOfDriveUseCase: SignOutOfDriveUseCase,
    private val removeDriveFolderUseCase: RemoveDriveFolderUseCase
) : ViewModel() {

    private val isDriveBusy = MutableStateFlow(false)
    private val snackbarMessage = MutableStateFlow<String?>(null)
    private val pendingConsentIntent = MutableStateFlow<PendingIntent?>(null)

    val uiState: StateFlow<SettingsUiState> = combine(
        observeUserSettingsUseCase(),
        observeLibraryFoldersUseCase(),
        observeDriveAuthStateUseCase(),
        observeDriveFoldersUseCase(),
        combine(isDriveBusy, snackbarMessage, pendingConsentIntent) { isBusy, snackbar, pendingConsent ->
            Triple(isBusy, snackbar, pendingConsent)
        }
    ) { settings, folders, driveAuth, driveFolders, (isBusy, snackbar, pendingConsent) ->
        SettingsUiState(
            userSettings = settings,
            folders = folders,
            driveAuthState = driveAuth,
            driveFolders = driveFolders,
            isDriveBusy = isBusy,
            snackbarMessage = snackbar,
            pendingConsentIntent = pendingConsent
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
                    addLibraryFolderUseCase(event.treeUri, uiState.value.userSettings.activeLibraryId)
                }
            }

            is SettingsUiEvent.RemoveFolderRequested -> {
                viewModelScope.launch {
                    removeLibraryFolderUseCase(event.folderUri)
                }
            }

            is SettingsUiEvent.SignInToDriveClicked -> {
                viewModelScope.launch {
                    isDriveBusy.value = true
                    val result = signInToDriveUseCase(event.activity)
                    isDriveBusy.value = false
                    result.onFailure { error ->
                        if (error is PendingConsentResolutionException) {
                            pendingConsentIntent.value = error.pendingIntent
                        } else if (error !is GetCredentialCancellationException) {
                            val message = error.message
                            if (message == "Google Drive is not configured in this build") {
                                snackbarMessage.value = message
                            } else {
                                snackbarMessage.value = "Failed to sign in to Google Drive."
                            }
                        }
                    }
                }
            }

            is SettingsUiEvent.DriveConsentResult -> {
                pendingConsentIntent.value = null
                if (event.data != null) {
                    viewModelScope.launch {
                        isDriveBusy.value = true
                        val result = completeDriveAuthUseCase(event.activity, event.data)
                        isDriveBusy.value = false
                        result.onFailure { error ->
                            if (error is PendingConsentResolutionException) {
                                pendingConsentIntent.value = error.pendingIntent
                            } else {
                                snackbarMessage.value = "Failed to sign in to Google Drive."
                            }
                        }
                    }
                }
            }

            is SettingsUiEvent.SignOutOfDriveClicked -> {
                viewModelScope.launch {
                    signOutOfDriveUseCase()
                }
            }

            is SettingsUiEvent.RemoveDriveFolderRequested -> {
                viewModelScope.launch {
                    removeDriveFolderUseCase(event.folderId)
                }
            }

            is SettingsUiEvent.SnackbarDismissed -> {
                snackbarMessage.value = null
            }
        }
    }
}
