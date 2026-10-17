package com.c0mpile.comix.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.c0mpile.comix.domain.model.ComicLibrary

/**
 * Room entity representing a user-selected SAF root folder in the library.
 */
@Entity(
    tableName = "library_folders",
    indices = [Index(value = ["library_id"])]
)
data class LibraryFolderEntity(
    @PrimaryKey
    @ColumnInfo(name = "folder_uri")
    val folderUri: String,

    @ColumnInfo(name = "display_name")
    val displayName: String,

    @ColumnInfo(name = "added_at_epoch_millis")
    val addedAtEpochMillis: Long,

    @ColumnInfo(name = "last_scanned_at_epoch_millis")
    val lastScannedAtEpochMillis: Long?,

    @ColumnInfo(name = "library_id", defaultValue = "'comics'")
    val libraryId: String = ComicLibrary.DEFAULT_COMICS_ID
)
