package com.c0mpile.comix.data.local.entity

import androidx.room.Embedded
import androidx.room.Relation

/**
 * Room relational projection pairing a [ComicEntity] with its optional [ReadingProgressEntity].
 */
data class ComicWithProgressProjection(
    @Embedded
    val comic: ComicEntity,

    @Relation(
        parentColumn = "comic_key",
        entityColumn = "comic_key"
    )
    val progress: ReadingProgressEntity?
)
