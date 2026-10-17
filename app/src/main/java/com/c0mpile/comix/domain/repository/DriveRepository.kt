package com.c0mpile.comix.domain.repository

import android.app.Activity
import android.content.Intent
import com.c0mpile.comix.domain.model.DriveAccount
import com.c0mpile.comix.domain.model.DriveAuthState
import com.c0mpile.comix.domain.model.DriveBrowseItem
import com.c0mpile.comix.domain.model.DriveFolder
import com.c0mpile.comix.domain.model.ScanResult
import kotlinx.coroutines.flow.Flow

/**
 * Repository contract for Google Drive authentication, folder browsing, scanning, and incremental sync.
 */
interface DriveRepository {
    fun observeAuthState(): Flow<DriveAuthState>
    suspend fun signIn(activity: Activity): Result<DriveAccount>
    suspend fun completeAuthorizationFromIntent(
        activity: Activity,
        data: Intent?
    ): Result<DriveAccount>
    suspend fun signOut()
    fun observeDriveFolders(): Flow<List<DriveFolder>>
    fun observeDriveFoldersByLibrary(libraryId: String): Flow<List<DriveFolder>> =
        observeDriveFolders()
    suspend fun browseSubfolders(parentFolderId: String): Result<List<DriveBrowseItem>>
    suspend fun addDriveFolder(
        folderId: String,
        displayName: String,
        driveId: String? = null
    ): Result<ScanResult>
    suspend fun addDriveFolder(
        folderId: String,
        displayName: String,
        driveId: String?,
        libraryId: String
    ): Result<ScanResult> = addDriveFolder(folderId, displayName, driveId)
    suspend fun removeDriveFolder(folderId: String)
    suspend fun rescanFolder(folderId: String): Result<ScanResult>
    suspend fun rescanDriveFoldersForLibrary(libraryId: String): Result<ScanResult> =
        incrementalSync()
    suspend fun incrementalSync(): Result<ScanResult>
    suspend fun syncReadingProgress(): Result<Int> = Result.success(0)
}
