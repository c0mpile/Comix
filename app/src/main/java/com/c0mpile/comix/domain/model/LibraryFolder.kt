package com.c0mpile.comix.domain.model

/**
 * A user-selected SAF root folder persisted in the library.
 */
data class LibraryFolder(
    val folderUri: String,
    val displayName: String,
    val addedAtEpochMillis: Long,
    val lastScannedAtEpochMillis: Long?,
    val hasPersistedPermission: Boolean
)
