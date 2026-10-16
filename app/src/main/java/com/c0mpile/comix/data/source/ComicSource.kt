package com.c0mpile.comix.data.source

import com.c0mpile.comix.domain.model.ComicFormat
import com.c0mpile.comix.domain.model.ComicSourceType

/**
 * Lightweight descriptor discovered during a folder scan before archive inspection.
 */
data class DiscoveredComicFile(
    val uri: String,
    val folderUri: String,
    val fileName: String,
    val title: String,
    val format: ComicFormat,
    val sizeBytes: Long,
    val lastModifiedEpochMillis: Long
)

/**
 * Abstraction for browsing folders, listing comic files, and opening a [RandomAccessSource].
 *
 * Implemented by LocalSource in Phase 1 and DriveSource in Phase 2.
 */
interface ComicSource {
    val sourceType: ComicSourceType

    /**
     * Verifies whether the app still holds read permission for [folderUri].
     */
    suspend fun hasPermission(folderUri: String): Boolean

    /**
     * Resolves a human-readable display name for [folderUri].
     */
    suspend fun getFolderDisplayName(folderUri: String): String

    /**
     * Recursively lists all supported comic files under [folderUri].
     */
    suspend fun listComicFiles(folderUri: String): List<DiscoveredComicFile>

    /**
     * Opens a [RandomAccessSource] for reading the comic at [uri].
     */
    suspend fun openRandomAccessSource(uri: String): RandomAccessSource
}
