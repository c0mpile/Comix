package com.c0mpile.comix.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.c0mpile.comix.domain.model.AppThemeMode
import com.c0mpile.comix.domain.model.ComicLibrary
import com.c0mpile.comix.domain.model.LibrarySortOrder
import com.c0mpile.comix.domain.model.ReaderScaleMode
import com.c0mpile.comix.domain.model.ReadingDirection
import com.c0mpile.comix.domain.model.UserSettings
import com.c0mpile.comix.domain.repository.SettingsRepository
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/**
 * DataStore Preferences implementation of [SettingsRepository].
 *
 * Persists and observes application theme mode, reader behavior, and library sort order.
 */
@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : SettingsRepository {

    override fun observeUserSettings(): Flow<UserSettings> =
        dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->
                mapToUserSettings(preferences)
            }

    override suspend fun setThemeMode(mode: AppThemeMode) {
        dataStore.edit { preferences ->
            preferences[KEY_THEME_MODE] = mode.name
        }
    }

    override suspend fun setReadingDirection(direction: ReadingDirection) {
        dataStore.edit { preferences ->
            preferences[KEY_READING_DIRECTION] = direction.name
        }
    }

    override suspend fun setReaderScaleMode(scaleMode: ReaderScaleMode) {
        dataStore.edit { preferences ->
            preferences[KEY_READER_SCALE_MODE] = scaleMode.name
        }
    }

    override suspend fun setKeepScreenOn(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_KEEP_SCREEN_ON] = enabled
        }
    }

    override suspend fun setLibrarySortOrder(sortOrder: LibrarySortOrder) {
        dataStore.edit { preferences ->
            preferences[KEY_LIBRARY_SORT_ORDER] = sortOrder.name
        }
    }

    override suspend fun setActiveLibraryId(libraryId: String) {
        dataStore.edit { preferences ->
            preferences[KEY_ACTIVE_LIBRARY_ID] = libraryId
        }
    }

    private fun mapToUserSettings(preferences: Preferences): UserSettings {
        val themeMode = preferences[KEY_THEME_MODE]?.let { raw ->
            AppThemeMode.entries.find { it.name == raw }
        } ?: AppThemeMode.SYSTEM

        val readingDirection = preferences[KEY_READING_DIRECTION]?.let { raw ->
            ReadingDirection.entries.find { it.name == raw }
        } ?: ReadingDirection.LEFT_TO_RIGHT

        val readerScaleMode = preferences[KEY_READER_SCALE_MODE]?.let { raw ->
            ReaderScaleMode.entries.find { it.name == raw }
        } ?: ReaderScaleMode.FIT_SCREEN

        val keepScreenOn = preferences[KEY_KEEP_SCREEN_ON] ?: true

        val librarySortOrder = preferences[KEY_LIBRARY_SORT_ORDER]?.let { raw ->
            LibrarySortOrder.entries.find { it.name == raw }
        } ?: LibrarySortOrder.TITLE_ASC

        val activeLibraryId = preferences[KEY_ACTIVE_LIBRARY_ID]?.takeIf { it.isNotBlank() }
            ?: ComicLibrary.DEFAULT_COMICS_ID

        return UserSettings(
            themeMode = themeMode,
            readingDirection = readingDirection,
            readerScaleMode = readerScaleMode,
            keepScreenOn = keepScreenOn,
            librarySortOrder = librarySortOrder,
            activeLibraryId = activeLibraryId
        )
    }

    companion object {
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_READING_DIRECTION = stringPreferencesKey("reading_direction")
        val KEY_READER_SCALE_MODE = stringPreferencesKey("reader_scale_mode")
        val KEY_KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val KEY_LIBRARY_SORT_ORDER = stringPreferencesKey("library_sort_order")
        val KEY_ACTIVE_LIBRARY_ID = stringPreferencesKey("active_library_id")
    }
}
