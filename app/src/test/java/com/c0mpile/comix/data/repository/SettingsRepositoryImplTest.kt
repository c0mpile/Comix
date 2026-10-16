package com.c0mpile.comix.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import app.cash.turbine.test
import com.c0mpile.comix.domain.model.AppThemeMode
import com.c0mpile.comix.domain.model.LibrarySortOrder
import com.c0mpile.comix.domain.model.ReaderScaleMode
import com.c0mpile.comix.domain.model.ReadingDirection
import com.c0mpile.comix.domain.model.UserSettings
import io.mockk.every
import io.mockk.mockk
import java.io.IOException
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Unit tests for [SettingsRepositoryImpl] verifying DataStore persistence,
 * default settings emission, and exception fallback.
 */
class SettingsRepositoryImplTest {

    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    /**
     * Creates an isolated [SettingsRepositoryImpl] backed by a unique temporary DataStore file.
     */
    private fun TestScope.createTestRepository(
        fileName: String = "test_settings_${System.nanoTime()}.preferences_pb"
    ): Pair<SettingsRepositoryImpl, DataStore<Preferences>> {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { tempFolder.newFile(fileName) }
        )
        return SettingsRepositoryImpl(dataStore) to dataStore
    }

    @Test
    fun initialObservationEmitsDefaultUserSettings() = runTest {
        val (repository, _) = createTestRepository()

        repository.observeUserSettings().test {
            val initial = awaitItem()
            val expected = UserSettings(
                themeMode = AppThemeMode.SYSTEM,
                readingDirection = ReadingDirection.LEFT_TO_RIGHT,
                readerScaleMode = ReaderScaleMode.FIT_SCREEN,
                keepScreenOn = true,
                librarySortOrder = LibrarySortOrder.TITLE_ASC
            )
            assertEquals(expected, initial)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun setThemeModePersistsAndEmitsUpdatedThemeMode() = runTest {
        val (repository, _) = createTestRepository()

        repository.observeUserSettings().test {
            assertEquals(AppThemeMode.SYSTEM, awaitItem().themeMode)

            repository.setThemeMode(AppThemeMode.DARK)
            assertEquals(AppThemeMode.DARK, awaitItem().themeMode)

            repository.setThemeMode(AppThemeMode.LIGHT)
            assertEquals(AppThemeMode.LIGHT, awaitItem().themeMode)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun setReadingDirectionPersistsAndEmitsUpdatedDirection() = runTest {
        val (repository, _) = createTestRepository()

        repository.observeUserSettings().test {
            assertEquals(ReadingDirection.LEFT_TO_RIGHT, awaitItem().readingDirection)

            repository.setReadingDirection(ReadingDirection.RIGHT_TO_LEFT)
            assertEquals(ReadingDirection.RIGHT_TO_LEFT, awaitItem().readingDirection)

            repository.setReadingDirection(ReadingDirection.LEFT_TO_RIGHT)
            assertEquals(ReadingDirection.LEFT_TO_RIGHT, awaitItem().readingDirection)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun setReaderScaleModePersistsAndEmitsUpdatedScaleMode() = runTest {
        val (repository, _) = createTestRepository()

        repository.observeUserSettings().test {
            assertEquals(ReaderScaleMode.FIT_SCREEN, awaitItem().readerScaleMode)

            repository.setReaderScaleMode(ReaderScaleMode.FIT_WIDTH)
            assertEquals(ReaderScaleMode.FIT_WIDTH, awaitItem().readerScaleMode)

            repository.setReaderScaleMode(ReaderScaleMode.FIT_SCREEN)
            assertEquals(ReaderScaleMode.FIT_SCREEN, awaitItem().readerScaleMode)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun setKeepScreenOnPersistsAndEmitsUpdatedBoolean() = runTest {
        val (repository, _) = createTestRepository()

        repository.observeUserSettings().test {
            assertEquals(true, awaitItem().keepScreenOn)

            repository.setKeepScreenOn(false)
            assertEquals(false, awaitItem().keepScreenOn)

            repository.setKeepScreenOn(true)
            assertEquals(true, awaitItem().keepScreenOn)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun setLibrarySortOrderPersistsAndEmitsUpdatedSortOrder() = runTest {
        val (repository, _) = createTestRepository()

        repository.observeUserSettings().test {
            assertEquals(LibrarySortOrder.TITLE_ASC, awaitItem().librarySortOrder)

            repository.setLibrarySortOrder(LibrarySortOrder.RECENTLY_READ)
            assertEquals(LibrarySortOrder.RECENTLY_READ, awaitItem().librarySortOrder)

            repository.setLibrarySortOrder(LibrarySortOrder.DATE_ADDED_DESC)
            assertEquals(LibrarySortOrder.DATE_ADDED_DESC, awaitItem().librarySortOrder)

            repository.setLibrarySortOrder(LibrarySortOrder.TITLE_DESC)
            assertEquals(LibrarySortOrder.TITLE_DESC, awaitItem().librarySortOrder)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun unrecognizedEnumStringInPreferencesFallsBackToDefaultUserSettings() = runTest {
        val (repository, dataStore) = createTestRepository()

        dataStore.edit { preferences ->
            preferences[SettingsRepositoryImpl.KEY_THEME_MODE] = "INVALID_THEME"
            preferences[SettingsRepositoryImpl.KEY_READING_DIRECTION] = "INVALID_DIRECTION"
            preferences[SettingsRepositoryImpl.KEY_READER_SCALE_MODE] = "INVALID_SCALE"
            preferences[SettingsRepositoryImpl.KEY_LIBRARY_SORT_ORDER] = "INVALID_SORT"
        }

        repository.observeUserSettings().test {
            val settings = awaitItem()
            assertEquals(AppThemeMode.SYSTEM, settings.themeMode)
            assertEquals(ReadingDirection.LEFT_TO_RIGHT, settings.readingDirection)
            assertEquals(ReaderScaleMode.FIT_SCREEN, settings.readerScaleMode)
            assertEquals(LibrarySortOrder.TITLE_ASC, settings.librarySortOrder)
            assertEquals(true, settings.keepScreenOn)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun ioExceptionFromDataStoreFallsBackToDefaultUserSettings() = runTest {
        val mockDataStore = mockk<DataStore<Preferences>>()
        every { mockDataStore.data } returns flow {
            throw IOException("Simulated disk read error")
        }

        val repository = SettingsRepositoryImpl(mockDataStore)

        repository.observeUserSettings().test {
            val settings = awaitItem()
            assertEquals(UserSettings(), settings)
            awaitComplete()
        }
    }

    @Test
    fun nonIoExceptionFromDataStoreIsRethrown() = runTest {
        val mockDataStore = mockk<DataStore<Preferences>>()
        every { mockDataStore.data } returns flow {
            throw IllegalStateException("Fatal preference corruption")
        }

        val repository = SettingsRepositoryImpl(mockDataStore)

        repository.observeUserSettings().test {
            val error = awaitError()
            assertTrue(error is IllegalStateException)
            assertEquals("Fatal preference corruption", error.message)
        }
    }
}
