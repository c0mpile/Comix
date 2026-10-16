package com.c0mpile.comix.domain.model

/**
 * Metadata for a single page inside a comic archive.
 */
data class ComicPage(
    val index: Int,
    val entryName: String,
    val compressedSize: Long,
    val uncompressedSize: Long
)
