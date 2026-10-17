package com.c0mpile.comix.domain.repository

import com.c0mpile.comix.domain.model.AppThemeMode
import com.c0mpile.comix.domain.model.LibrarySortOrder
import com.c0mpile.comix.domain.model.ReaderScaleMode
import com.c0mpile.comix.domain.model.ReadingDirection
import com.c0mpile.comix.domain.model.UserSettings
import kotlinx.coroutines.flow.Flow

/**
 * Repository contract for observing and updating user preferences.
 */
interface SettingsRepository {
    fun observeUserSettings(): Flow<UserSettings>
    suspend fun setThemeMode(mode: AppThemeMode)
    suspend fun setReadingDirection(direction: ReadingDirection)
    suspend fun setReaderScaleMode(scaleMode: ReaderScaleMode)
    suspend fun setKeepScreenOn(enabled: Boolean)
    suspend fun setLibrarySortOrder(sortOrder: LibrarySortOrder)
    suspend fun setActiveLibraryId(libraryId: String) {}
}
