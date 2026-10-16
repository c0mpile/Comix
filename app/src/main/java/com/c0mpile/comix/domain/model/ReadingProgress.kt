package com.c0mpile.comix.domain.model

/**
 * Reading progress keyed by [comicKey] so progress survives file moves or source changes.
 */
data class ReadingProgress(
    val comicKey: String,
    val currentPage: Int,
    val pageCount: Int,
    val isFinished: Boolean,
    val updatedAtEpochMillis: Long
)
