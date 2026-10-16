package com.c0mpile.comix.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.c0mpile.comix.domain.model.ComicAvailability
import com.c0mpile.comix.domain.model.ComicFormat
import com.c0mpile.comix.domain.model.ComicSourceType

/**
 * Room entity representing an indexed comic book in the library.
 */
@Entity(
    tableName = "comics",
    indices = [
        Index(value = ["local_uri"]),
        Index(value = ["folder_uri"]),
        Index(value = ["local_sample_sha256"]),
        Index(value = ["drive_file_id"]),
        Index(value = ["drive_md5"])
    ]
)
data class ComicEntity(
    @PrimaryKey
    @ColumnInfo(name = "comic_key")
    val comicKey: String,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "file_name")
    val fileName: String,

    @ColumnInfo(name = "format")
    val format: ComicFormat,

    @ColumnInfo(name = "source_type")
    val sourceType: ComicSourceType,

    @ColumnInfo(name = "availability")
    val availability: ComicAvailability,

    @ColumnInfo(name = "size_bytes")
    val sizeBytes: Long,

    @ColumnInfo(name = "last_modified_epoch_millis")
    val lastModifiedEpochMillis: Long,

    @ColumnInfo(name = "page_count")
    val pageCount: Int,

    @ColumnInfo(name = "local_uri")
    val localUri: String?,

    @ColumnInfo(name = "folder_uri")
    val folderUri: String?,

    @ColumnInfo(name = "local_sample_sha256")
    val localSampleSha256: String?,

    @ColumnInfo(name = "drive_file_id")
    val driveFileId: String? = null,

    @ColumnInfo(name = "drive_md5")
    val driveMd5: String? = null,

    @ColumnInfo(name = "cover_cache_path")
    val coverCachePath: String? = null
)
