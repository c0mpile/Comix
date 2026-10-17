package com.c0mpile.comix.domain.model

/**
 * A user-selected Google Drive root folder persisted in the library.
 */
data class DriveFolder(
    val folderId: String,
    val displayName: String,
    val addedAtEpochMillis: Long,
    val lastScannedAtEpochMillis: Long?,
    val driveId: String? = null,
    val libraryId: String = ComicLibrary.DEFAULT_COMICS_ID
)
