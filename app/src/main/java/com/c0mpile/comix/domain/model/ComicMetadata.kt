package com.c0mpile.comix.domain.model

import java.util.Locale

/**
 * Embedded comic book metadata extracted from an archive (such as ComicInfo.xml in CBZ files).
 */
data class ComicMetadata(
    val title: String? = null,
    val series: String? = null,
    val number: String? = null,
    val volume: Int? = null,
    val summary: String? = null,
    val writer: String? = null,
    val penciller: String? = null,
    val publisher: String? = null,
    val genre: String? = null,
    val releaseYear: Int? = null,
    val releaseMonth: Int? = null,
    val releaseDay: Int? = null
) {
    /**
     * Returns true if at least one metadata field is populated.
     */
    val hasAnyMetadata: Boolean
        get() = !title.isNullOrBlank() ||
            !series.isNullOrBlank() ||
            !number.isNullOrBlank() ||
            volume != null ||
            !summary.isNullOrBlank() ||
            !writer.isNullOrBlank() ||
            !penciller.isNullOrBlank() ||
            !publisher.isNullOrBlank() ||
            !genre.isNullOrBlank() ||
            releaseYear != null

    /**
     * Formatted series and issue string (for example, "Saga #1" or "Vol. 2 #5").
     */
    val formattedSeriesAndNumber: String?
        get() {
            val parts = mutableListOf<String>()
            if (!series.isNullOrBlank()) parts.add(series)
            if (volume != null && volume > 0) parts.add("Vol. $volume")
            if (!number.isNullOrBlank()) parts.add("#$number")
            return parts.takeIf { it.isNotEmpty() }?.joinToString(" ")
        }

    /**
     * Formatted release date string (YYYY-MM-DD, YYYY-MM, or YYYY).
     */
    val formattedReleaseDate: String?
        get() {
            val year = releaseYear?.takeIf { it > 0 } ?: return null
            val month = releaseMonth?.takeIf { it in 1..12 }
            val day = releaseDay?.takeIf { it in 1..31 }
            return when {
                month != null && day != null -> String.format(Locale.US, "%04d-%02d-%02d", year, month, day)
                month != null -> String.format(Locale.US, "%04d-%02d", year, month)
                else -> year.toString()
            }
        }
}
