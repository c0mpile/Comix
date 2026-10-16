package com.c0mpile.comix.domain.repository

import com.c0mpile.comix.domain.model.Comic
import com.c0mpile.comix.domain.model.LibraryFolder
import com.c0mpile.comix.domain.model.ScanResult
import kotlinx.coroutines.flow.Flow

/**
 * Repository contract for managing library folders, scanning comic archives, and observing library state.
 */
interface LibraryRepository {
    fun observeComics(): Flow<List<Comic>>
    fun observeFolders(): Flow<List<LibraryFolder>>
    suspend fun addFolderAndScan(treeUri: String): Result<ScanResult>
    suspend fun rescanAllFolders(): Result<ScanResult>
    suspend fun removeFolder(folderUri: String)
}
