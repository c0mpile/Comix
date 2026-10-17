package com.c0mpile.comix.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.c0mpile.comix.data.local.entity.DriveFolderEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data access object for persisted Google Drive root folders.
 */
@Dao
interface DriveFolderDao {
    @Query("SELECT * FROM drive_folders ORDER BY added_at_epoch_millis ASC")
    fun observeAllFolders(): Flow<List<DriveFolderEntity>>

    @Query("SELECT * FROM drive_folders WHERE library_id = :libraryId ORDER BY added_at_epoch_millis ASC")
    fun observeFoldersByLibrary(libraryId: String): Flow<List<DriveFolderEntity>>

    @Query("SELECT * FROM drive_folders ORDER BY added_at_epoch_millis ASC")
    suspend fun getAllFolders(): List<DriveFolderEntity>

    @Query("SELECT * FROM drive_folders WHERE library_id = :libraryId ORDER BY added_at_epoch_millis ASC")
    suspend fun getFoldersByLibrary(libraryId: String): List<DriveFolderEntity>

    @Query("SELECT * FROM drive_folders WHERE folder_id = :folderId LIMIT 1")
    suspend fun getFolder(folderId: String): DriveFolderEntity?

    @Upsert
    suspend fun upsertFolder(folder: DriveFolderEntity)

    @Query("UPDATE drive_folders SET last_scanned_at_epoch_millis = :timestamp WHERE folder_id = :folderId")
    suspend fun updateLastScanned(folderId: String, timestamp: Long)

    @Query("DELETE FROM drive_folders WHERE folder_id = :folderId")
    suspend fun deleteFolder(folderId: String)

    @Query("DELETE FROM drive_folders WHERE library_id = :libraryId")
    suspend fun deleteFoldersByLibrary(libraryId: String)

    @Query("DELETE FROM drive_folders")
    suspend fun deleteAllFolders()
}
