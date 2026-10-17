package com.c0mpile.comix.domain.model

/**
 * Aggregated user preferences persisted in DataStore.
 */
data class UserSettings(
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val readingDirection: ReadingDirection = ReadingDirection.LEFT_TO_RIGHT,
    val readerScaleMode: ReaderScaleMode = ReaderScaleMode.FIT_SCREEN,
    val keepScreenOn: Boolean = true,
    val librarySortOrder: LibrarySortOrder = LibrarySortOrder.TITLE_ASC,
    val activeLibraryId: String = ComicLibrary.DEFAULT_COMICS_ID
)
