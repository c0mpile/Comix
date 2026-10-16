package com.c0mpile.comix.ui.settings

import com.c0mpile.comix.domain.model.AppThemeMode
import com.c0mpile.comix.domain.model.LibraryFolder
import com.c0mpile.comix.domain.model.ReaderScaleMode
import com.c0mpile.comix.domain.model.ReadingDirection
import com.c0mpile.comix.domain.model.UserSettings

/**
 * Immutable UI state for the application settings screen.
 */
data class SettingsUiState(
    val userSettings: UserSettings = UserSettings(),
    val folders: List<LibraryFolder> = emptyList()
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
}
