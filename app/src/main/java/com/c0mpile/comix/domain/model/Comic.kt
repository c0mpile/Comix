package com.c0mpile.comix.domain.model

/**
 * Domain representation of a comic book in the library.
 *
 * [comicKey] is a stable, content-derived identifier independent of file path or URI.
 */
data class Comic(
    val comicKey: String,
    val title: String,
    val fileName: String,
    val format: ComicFormat,
    val sourceType: ComicSourceType,
    val availability: ComicAvailability,
    val sizeBytes: Long,
    val lastModifiedEpochMillis: Long,
    val pageCount: Int,
    val localUri: String?,
    val folderUri: String?,
    val driveFileId: String? = null,
    val driveMd5: String? = null,
    val coverCachePath: String? = null,
    val progress: ReadingProgress? = null
)
