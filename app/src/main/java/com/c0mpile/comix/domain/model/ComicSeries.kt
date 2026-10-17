package com.c0mpile.comix.domain.model

/**
 * Represents a grouped comic series in the library browse view.
 */
data class ComicSeries(
    val seriesKey: String,
    val name: String,
    val comics: List<Comic>,
    val coverComic: Comic
) {
    val issueCount: Int
        get() = comics.size

    val finishedCount: Int
        get() = comics.count { it.progress?.isFinished == true }

    val inProgressCount: Int
        get() = comics.count { it.progress != null && !it.progress.isFinished }
}
