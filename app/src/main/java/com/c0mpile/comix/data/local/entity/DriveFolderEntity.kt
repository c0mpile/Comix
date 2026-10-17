package com.c0mpile.comix.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing a user-selected Google Drive root folder in the library.
 */
@Entity(tableName = "drive_folders")
data class DriveFolderEntity(
    @PrimaryKey
    @ColumnInfo(name = "folder_id")
    val folderId: String,

    @ColumnInfo(name = "display_name")
    val displayName: String,

    @ColumnInfo(name = "added_at_epoch_millis")
    val addedAtEpochMillis: Long,

    @ColumnInfo(name = "last_scanned_at_epoch_millis")
    val lastScannedAtEpochMillis: Long? = null,

    @ColumnInfo(name = "drive_id")
    val driveId: String? = null
)
