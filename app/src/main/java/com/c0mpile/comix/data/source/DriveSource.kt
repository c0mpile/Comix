package com.c0mpile.comix.data.source

import com.c0mpile.comix.data.remote.drive.DriveApiClient
import com.c0mpile.comix.data.remote.drive.DriveAuthorizer
import com.c0mpile.comix.data.remote.drive.DriveFile
import com.c0mpile.comix.data.remote.drive.MIME_TYPE_FOLDER
import com.c0mpile.comix.data.remote.drive.MIME_TYPE_SHORTCUT
import com.c0mpile.comix.di.IoDispatcher
import com.c0mpile.comix.domain.model.ComicFormat
import com.c0mpile.comix.domain.model.ComicSourceType
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * [ComicSource] implementation for discovering and browsing comic books hosted on Google Drive.
 */
@Singleton
class DriveSource @Inject constructor(
    private val apiClient: DriveApiClient,
    private val authorizer: DriveAuthorizer,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ComicSource {

    override val sourceType: ComicSourceType = ComicSourceType.GOOGLE_DRIVE

    override suspend fun hasPermission(folderUri: String): Boolean = withContext(ioDispatcher) {
        authorizer.getAccessToken(forceRefresh = false).isSuccess
    }

    override suspend fun getFolderDisplayName(folderUri: String): String = withContext(ioDispatcher) {
        if (folderUri == ROOT_FOLDER_ID) {
            MY_DRIVE_DISPLAY_NAME
        } else {
            try {
                apiClient.getFile(folderUri).name
            } catch (_: Exception) {
                folderUri
            }
        }
    }

    override suspend fun listComicFiles(folderUri: String): List<DiscoveredComicFile> = withContext(ioDispatcher) {
        val driveFiles = scanDriveFiles(folderUri)
        driveFiles.map { file ->
            DiscoveredComicFile(
                uri = file.id,
                folderUri = folderUri,
                fileName = file.name,
                title = file.name.substringBeforeLast('.'),
                format = ComicFormat.CBZ,
                sizeBytes = file.size ?: 0L,
                lastModifiedEpochMillis = parseModifiedTime(file.modifiedTime)
            )
        }
    }

    override suspend fun openRandomAccessSource(uri: String): RandomAccessSource {
        throw UnsupportedOperationException("Streaming Drive comics is not supported in Phase 2")
    }

    /**
     * Recursively traverses [rootFolderId] using breadth-first search to find all non-trashed CBZ comic files,
     * following folder shortcuts while preventing cycles.
     */
    suspend fun scanDriveFiles(rootFolderId: String): List<DriveFile> = withContext(ioDispatcher) {
        val queue = ArrayDeque<String>()
        val visitedFolderIds = mutableSetOf<String>()
        val visitedFileIds = mutableSetOf<String>()
        val discoveredFiles = mutableListOf<DriveFile>()

        visitedFolderIds.add(rootFolderId)
        queue.add(rootFolderId)

        while (queue.isNotEmpty()) {
            val currentFolderId = queue.removeFirst()
            var pageToken: String? = null

            do {
                val fileList = apiClient.listChildren(currentFolderId, pageToken)
                for (file in fileList.files) {
                    if (file.trashed) continue

                    when (file.mimeType) {
                        MIME_TYPE_FOLDER -> {
                            if (visitedFolderIds.add(file.id)) {
                                queue.add(file.id)
                            }
                        }

                        MIME_TYPE_SHORTCUT -> {
                            val details = file.shortcutDetails
                            val targetId = details?.targetId
                            val targetMime = details?.targetMimeType

                            if (!targetId.isNullOrBlank()) {
                                if (targetMime == MIME_TYPE_FOLDER) {
                                    if (visitedFolderIds.add(targetId)) {
                                        queue.add(targetId)
                                    }
                                } else {
                                    if (visitedFileIds.add(targetId)) {
                                        try {
                                            val resolvedTarget = apiClient.getFile(targetId)
                                            if (!resolvedTarget.trashed &&
                                                isSupportedCbz(resolvedTarget.name, resolvedTarget.md5Checksum)
                                            ) {
                                                discoveredFiles.add(resolvedTarget)
                                            }
                                        } catch (_: Exception) {
                                            // Safely ignore missing or inaccessible target files
                                        }
                                    }
                                }
                            }
                        }

                        else -> {
                            if (isSupportedCbz(file.name, file.md5Checksum)) {
                                if (visitedFileIds.add(file.id)) {
                                    discoveredFiles.add(file)
                                }
                            }
                        }
                    }
                }
                pageToken = fileList.nextPageToken
            } while (!pageToken.isNullOrBlank())
        }

        discoveredFiles
    }

    companion object {
        const val ROOT_FOLDER_ID: String = "root"
        const val MY_DRIVE_DISPLAY_NAME: String = "My Drive"

        fun isSupportedCbz(name: String, md5Checksum: String?): Boolean {
            return name.endsWith(".cbz", ignoreCase = true) &&
                    !name.startsWith(".") &&
                    !md5Checksum.isNullOrBlank()
        }

        fun parseModifiedTime(modifiedTime: String?): Long {
            if (modifiedTime.isNullOrBlank()) return 0L
            return try {
                Instant.parse(modifiedTime).toEpochMilli()
            } catch (_: Exception) {
                0L
            }
        }
    }
}
