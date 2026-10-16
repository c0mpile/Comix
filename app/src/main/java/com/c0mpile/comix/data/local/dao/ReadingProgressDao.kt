package com.c0mpile.comix.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.c0mpile.comix.data.local.entity.ReadingProgressEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data access object for per-comic reading progress.
 */
@Dao
interface ReadingProgressDao {
    @Query("SELECT * FROM reading_progress WHERE comic_key = :comicKey LIMIT 1")
    fun observeProgress(comicKey: String): Flow<ReadingProgressEntity?>

    @Query("SELECT * FROM reading_progress WHERE comic_key = :comicKey LIMIT 1")
    suspend fun getProgress(comicKey: String): ReadingProgressEntity?

    @Upsert
    suspend fun upsertProgress(progress: ReadingProgressEntity)
}
