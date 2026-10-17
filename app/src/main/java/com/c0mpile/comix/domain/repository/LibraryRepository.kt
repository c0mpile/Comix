package com.c0mpile.comix.domain.repository

import com.c0mpile.comix.domain.model.Comic
import com.c0mpile.comix.domain.model.ComicLibrary
import com.c0mpile.comix.domain.model.LibraryFolder
import com.c0mpile.comix.domain.model.ReadingDirection
import com.c0mpile.comix.domain.model.ScanResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Repository contract for managing library folders, scanning comic archives, and observing library state.
 */
interface LibraryRepository {
    fun observeLibraries(): Flow<List<ComicLibrary>> = flowOf(emptyList())
    suspend fun getLibrary(libraryId: String): ComicLibrary? = null
    suspend fun createLibrary(
        name: String,
        defaultReadingDirection: ReadingDirection
    ): Result<ComicLibrary> = Result.failure(UnsupportedOperationException())
    suspend fun updateLibrary(
        libraryId: String,
        name: String,
        defaultReadingDirection: ReadingDirection
    ): Result<Unit> = Result.failure(UnsupportedOperationException())
    suspend fun deleteLibrary(libraryId: String): Result<Unit> =
        Result.failure(UnsupportedOperationException())
    fun observeComics(): Flow<List<Comic>>
    fun observeComicsByLibrary(libraryId: String): Flow<List<Comic>> = observeComics()
    fun observeFolders(): Flow<List<LibraryFolder>>
    fun observeFoldersByLibrary(libraryId: String): Flow<List<LibraryFolder>> = observeFolders()
    suspend fun addFolderAndScan(treeUri: String): Result<ScanResult>
    suspend fun addFolderAndScan(treeUri: String, libraryId: String): Result<ScanResult> =
        addFolderAndScan(treeUri)
    suspend fun rescanAllFolders(): Result<ScanResult>
    suspend fun rescanLibraryFolders(libraryId: String): Result<ScanResult> = rescanAllFolders()
    suspend fun removeFolder(folderUri: String)
}
