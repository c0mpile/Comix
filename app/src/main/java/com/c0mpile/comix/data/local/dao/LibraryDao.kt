package com.c0mpile.comix.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.c0mpile.comix.data.local.entity.LibraryEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data access object for persisted user libraries.
 */
@Dao
interface LibraryDao {
    @Query("SELECT * FROM libraries ORDER BY is_built_in DESC, created_at_epoch_millis ASC")
    fun observeLibraries(): Flow<List<LibraryEntity>>

    @Query("SELECT * FROM libraries ORDER BY is_built_in DESC, created_at_epoch_millis ASC")
    suspend fun getLibraries(): List<LibraryEntity>

    @Query("SELECT * FROM libraries WHERE library_id = :libraryId LIMIT 1")
    suspend fun getLibrary(libraryId: String): LibraryEntity?

    @Upsert
    suspend fun upsertLibrary(library: LibraryEntity)

    @Upsert
    suspend fun upsertLibraries(libraries: List<LibraryEntity>)

    @Query("DELETE FROM libraries WHERE library_id = :libraryId")
    suspend fun deleteLibrary(libraryId: String)
}
