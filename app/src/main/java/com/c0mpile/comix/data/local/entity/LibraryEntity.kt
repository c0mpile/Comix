package com.c0mpile.comix.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.c0mpile.comix.domain.model.ReadingDirection

/**
 * Room entity representing a named library (such as Comics or Manga).
 */
@Entity(tableName = "libraries")
data class LibraryEntity(
    @PrimaryKey
    @ColumnInfo(name = "library_id")
    val libraryId: String,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "default_reading_direction")
    val defaultReadingDirection: ReadingDirection,

    @ColumnInfo(name = "created_at_epoch_millis")
    val createdAtEpochMillis: Long,

    @ColumnInfo(name = "is_built_in")
    val isBuiltIn: Boolean = false
)
