package com.c0mpile.comix.domain.model

/**
 * Represents a named user library (such as Comics or Manga) with its own folders,
 * comics, and default reading direction.
 */
data class ComicLibrary(
    val libraryId: String,
    val name: String,
    val defaultReadingDirection: ReadingDirection,
    val createdAtEpochMillis: Long,
    val isBuiltIn: Boolean = false
) {
    companion object {
        const val DEFAULT_COMICS_ID: String = "comics"
        const val DEFAULT_MANGA_ID: String = "manga"
        const val DEFAULT_COMICS_NAME: String = "Comics"
        const val DEFAULT_MANGA_NAME: String = "Manga"
    }
}
