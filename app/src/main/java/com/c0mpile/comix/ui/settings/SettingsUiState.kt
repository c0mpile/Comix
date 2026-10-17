package com.c0mpile.comix.ui.settings

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import com.c0mpile.comix.domain.model.AppThemeMode
import com.c0mpile.comix.domain.model.DriveAuthState
import com.c0mpile.comix.domain.model.DriveFolder
import com.c0mpile.comix.domain.model.LibraryFolder
import com.c0mpile.comix.domain.model.ReaderScaleMode
import com.c0mpile.comix.domain.model.ReadingDirection
import com.c0mpile.comix.domain.model.UserSettings

/**
 * Immutable UI state for the application settings screen.
 */
data class SettingsUiState(
    val userSettings: UserSettings = UserSettings(),
    val folders: List<LibraryFolder> = emptyList(),
    val driveAuthState: DriveAuthState = DriveAuthState.SignedOut,
    val driveFolders: List<DriveFolder> = emptyList(),
    val isDriveBusy: Boolean = false,
    val snackbarMessage: String? = null,
    val pendingConsentIntent: PendingIntent? = null
)

/**
 * User actions emitted from the settings screen.
 */
sealed interface SettingsUiEvent {
    data class ThemeModeSelected(val mode: AppThemeMode) : SettingsUiEvent
    data class ReadingDirectionSelected(val direction: ReadingDirection) : SettingsUiEvent
    data class ReaderScaleModeSelected(val scaleMode: ReaderScaleMode) : SettingsUiEvent
    data class KeepScreenOnToggled(val enabled: Boolean) : SettingsUiEvent
    data class FolderPicked(val treeUri: String) : SettingsUiEvent
    data class RemoveFolderRequested(val folderUri: String) : SettingsUiEvent

    data class SignInToDriveClicked(val activity: Activity) : SettingsUiEvent
    data class DriveConsentResult(val activity: Activity, val data: Intent?) : SettingsUiEvent
    data object SignOutOfDriveClicked : SettingsUiEvent
    data class RemoveDriveFolderRequested(val folderId: String) : SettingsUiEvent
    data object SnackbarDismissed : SettingsUiEvent
}
