package com.c0mpile.comix.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Reading progress is stored in its own table without a CASCADE delete foreign key on `comics`
 * so that if a local comic is temporarily removed, moved between folders, or switched between
 * local SAF and Google Drive, progress is preserved by [comicKey].
 */
@Entity(tableName = "reading_progress")
data class ReadingProgressEntity(
    @PrimaryKey
    @ColumnInfo(name = "comic_key")
    val comicKey: String,

    @ColumnInfo(name = "current_page")
    val currentPage: Int,

    @ColumnInfo(name = "page_count")
    val pageCount: Int,

    @ColumnInfo(name = "is_finished")
    val isFinished: Boolean,

    @ColumnInfo(name = "updated_at_epoch_millis")
    val updatedAtEpochMillis: Long
)
