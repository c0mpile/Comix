package com.c0mpile.comix.domain.model

/**
 * Summary returned after scanning one or more library folders.
 */
data class ScanResult(
    val addedOrUpdatedCount: Int,
    val removedCount: Int,
    val skippedCorruptCount: Int,
    val errors: List<ComicError>
)
