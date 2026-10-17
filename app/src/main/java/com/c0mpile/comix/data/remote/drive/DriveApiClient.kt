package com.c0mpile.comix.data.remote.drive

/**
 * Client contract for the Google Drive REST v3 API.
 */
interface DriveApiClient {
    /**
     * Lists non-trashed children (folders, shortcuts, and comic files) under [parentId].
     */
    suspend fun listChildren(parentId: String, pageToken: String? = null): DriveFileList

    /**
     * Lists non-trashed subfolders and folder shortcuts under [parentId].
     */
    suspend fun listSubfolders(parentId: String, pageToken: String? = null): DriveFileList

    /**
     * Fetches metadata for a single Google Drive file or folder by [fileId].
     */
    suspend fun getFile(fileId: String): DriveFile

    /**
     * Fetches the starting page token for future `changes.list` incremental sync calls.
     */
    suspend fun getStartPageToken(): String

    /**
     * Lists file and folder changes starting from [pageToken].
     */
    suspend fun listChanges(pageToken: String): DriveChangeList
}
