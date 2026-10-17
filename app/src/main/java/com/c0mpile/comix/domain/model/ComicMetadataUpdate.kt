package com.c0mpile.comix.domain.model

/** Payload for updating metadata on a single comic issue. */
data class SingleComicMetadataUpdate(
    val comicKey: String,
    val series: String?,
    val title: String?,
    val number: String?,
    val releaseYear: Int?,
    val writer: String?,
    val penciller: String?,
    val publisher: String?,
    val summary: String?
)

/** Payload for bulk-updating metadata across multiple selected issues. Each update* flag says whether the field should be applied. */
data class BulkComicMetadataUpdate(
    val comicKeys: List<String>,
    val series: String? = null,
    val updateSeries: Boolean = false,
    val releaseYear: Int? = null,
    val updateReleaseYear: Boolean = false,
    val writer: String? = null,
    val updateWriter: Boolean = false,
    val penciller: String? = null,
    val updatePenciller: Boolean = false,
    val publisher: String? = null,
    val updatePublisher: Boolean = false
)
