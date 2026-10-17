package com.c0mpile.comix.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity storing the single signed-in Google Drive account and its incremental `changes` page token.
 *
 * OAuth access and refresh tokens are never stored in this table.
 */
@Entity(tableName = "drive_account")
data class DriveAccountEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Int = SINGLETON_ID,

    @ColumnInfo(name = "email")
    val email: String,

    @ColumnInfo(name = "signed_in_at_epoch_millis")
    val signedInAtEpochMillis: Long,

    @ColumnInfo(name = "changes_page_token")
    val changesPageToken: String? = null
) {
    companion object {
        const val SINGLETON_ID: Int = 1
    }
}
