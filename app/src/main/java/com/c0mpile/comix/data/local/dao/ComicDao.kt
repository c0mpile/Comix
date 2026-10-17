package com.c0mpile.comix.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.c0mpile.comix.data.local.entity.ComicEntity
import com.c0mpile.comix.data.local.entity.ComicWithProgressProjection
import kotlinx.coroutines.flow.Flow

/**
 * Data access object for comic metadata queries and updates.
 */
@Dao
interface ComicDao {
    @Transaction
    @Query("SELECT * FROM comics ORDER BY title COLLATE NOCASE ASC")
    fun observeAllComicsWithProgress(): Flow<List<ComicWithProgressProjection>>

    @Transaction
    @Query("SELECT * FROM comics WHERE library_id = :libraryId ORDER BY title COLLATE NOCASE ASC")
    fun observeComicsWithProgressByLibrary(libraryId: String): Flow<List<ComicWithProgressProjection>>

    @Transaction
    @Query("SELECT * FROM comics WHERE comic_key = :comicKey LIMIT 1")
    suspend fun getComicWithProgress(comicKey: String): ComicWithProgressProjection?

    @Query("SELECT * FROM comics WHERE folder_uri = :folderUri")
    suspend fun getComicsByFolder(folderUri: String): List<ComicEntity>

    @Query("SELECT * FROM comics WHERE folder_uri = :folderId AND source_type IN ('GOOGLE_DRIVE', 'DRIVE')")
    suspend fun getComicsByDriveFolder(folderId: String): List<ComicEntity>

    @Query("SELECT * FROM comics WHERE local_uri = :localUri LIMIT 1")
    suspend fun getComicByLocalUri(localUri: String): ComicEntity?

    @Query("SELECT * FROM comics WHERE local_sample_sha256 = :sampleHash LIMIT 1")
    suspend fun findByLocalSampleHash(sampleHash: String): ComicEntity?

    @Query("SELECT * FROM comics WHERE drive_file_id = :driveFileId LIMIT 1")
    suspend fun findByDriveFileId(driveFileId: String): ComicEntity?

    @Upsert
    suspend fun upsertComics(comics: List<ComicEntity>)

    @Upsert
    suspend fun upsertComic(comic: ComicEntity)

    @Query("UPDATE comics SET cover_cache_path = :coverPath WHERE comic_key = :comicKey")
    suspend fun updateCoverCachePath(comicKey: String, coverPath: String)

    @Query("DELETE FROM comics WHERE comic_key IN (:comicKeys)")
    suspend fun deleteByKeys(comicKeys: List<String>)

    @Query("DELETE FROM comics WHERE drive_file_id IN (:driveFileIds)")
    suspend fun deleteByDriveFileIds(driveFileIds: List<String>)

    @Query("DELETE FROM comics WHERE folder_uri = :folderUri AND drive_file_id IS NULL")
    suspend fun deleteLocalComicsInFolder(folderUri: String)

    @Query("DELETE FROM comics WHERE folder_uri = :folderId AND source_type IN ('GOOGLE_DRIVE', 'DRIVE')")
    suspend fun deleteDriveComicsInFolder(folderId: String)

    @Query("DELETE FROM comics WHERE source_type IN ('GOOGLE_DRIVE', 'DRIVE')")
    suspend fun deleteAllDriveComics()

    @Query("DELETE FROM comics WHERE library_id = :libraryId")
    suspend fun deleteComicsByLibrary(libraryId: String)
}
