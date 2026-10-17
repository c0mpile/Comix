package com.c0mpile.comix.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.c0mpile.comix.data.local.entity.DriveAccountEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data access object for the signed-in Google Drive account metadata and incremental sync page token.
 */
@Dao
interface DriveAccountDao {
    @Query("SELECT * FROM drive_account WHERE id = ${DriveAccountEntity.SINGLETON_ID} LIMIT 1")
    fun observeAccount(): Flow<DriveAccountEntity?>

    @Query("SELECT * FROM drive_account WHERE id = ${DriveAccountEntity.SINGLETON_ID} LIMIT 1")
    suspend fun getAccount(): DriveAccountEntity?

    @Upsert
    suspend fun upsertAccount(account: DriveAccountEntity)

    @Query("UPDATE drive_account SET changes_page_token = :token WHERE id = ${DriveAccountEntity.SINGLETON_ID}")
    suspend fun updateChangesPageToken(token: String?)

    @Query("DELETE FROM drive_account")
    suspend fun clearAccount()
}
