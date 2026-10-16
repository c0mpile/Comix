package com.c0mpile.comix.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.c0mpile.comix.data.local.entity.LibraryFolderEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data access object for persisted library root folders.
 */
@Dao
interface LibraryFolderDao {
    @Query("SELECT * FROM library_folders ORDER BY added_at_epoch_millis ASC")
    fun observeFolders(): Flow<List<LibraryFolderEntity>>

    @Query("SELECT * FROM library_folders ORDER BY added_at_epoch_millis ASC")
    suspend fun getFolders(): List<LibraryFolderEntity>

    @Upsert
    suspend fun upsertFolder(folder: LibraryFolderEntity)

    @Query("UPDATE library_folders SET last_scanned_at_epoch_millis = :scannedAt WHERE folder_uri = :folderUri")
    suspend fun updateLastScanned(folderUri: String, scannedAt: Long)

    @Query("DELETE FROM library_folders WHERE folder_uri = :folderUri")
    suspend fun deleteFolder(folderUri: String)
}
