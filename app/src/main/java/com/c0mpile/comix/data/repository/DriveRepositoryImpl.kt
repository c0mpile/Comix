package com.c0mpile.comix.data.repository

import android.app.Activity
import android.content.Intent
import com.c0mpile.comix.data.local.dao.ComicDao
import com.c0mpile.comix.data.local.dao.DriveAccountDao
import com.c0mpile.comix.data.local.dao.DriveFolderDao
import com.c0mpile.comix.data.local.dao.ReadingProgressDao
import com.c0mpile.comix.data.local.entity.ComicEntity
import com.c0mpile.comix.data.local.entity.DriveAccountEntity
import com.c0mpile.comix.data.local.entity.DriveFolderEntity
import com.c0mpile.comix.data.local.entity.ReadingProgressEntity
import com.c0mpile.comix.data.remote.drive.DriveApiClient
import com.c0mpile.comix.data.remote.drive.DriveApiException
import com.c0mpile.comix.data.remote.drive.DriveAuthorizer
import com.c0mpile.comix.data.remote.drive.MIME_TYPE_FOLDER
import com.c0mpile.comix.data.remote.drive.MIME_TYPE_SHORTCUT
import com.c0mpile.comix.data.source.DriveSource
import com.c0mpile.comix.di.IoDispatcher
import com.c0mpile.comix.domain.model.ComicAvailability
import com.c0mpile.comix.domain.model.ComicError
import com.c0mpile.comix.domain.model.ComicFormat
import com.c0mpile.comix.domain.model.ComicLibrary
import com.c0mpile.comix.domain.model.ComicSourceType
import com.c0mpile.comix.domain.model.DriveAccount
import com.c0mpile.comix.domain.model.DriveAuthState
import com.c0mpile.comix.domain.model.DriveBrowseItem
import com.c0mpile.comix.domain.model.DriveFolder
import com.c0mpile.comix.domain.model.ScanResult
import com.c0mpile.comix.domain.repository.DriveRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Implementation of [DriveRepository] managing Google Drive account persistence,
 * folder browsing, comic metadata synchronization, and incremental change detection.
 */
@Singleton
class DriveRepositoryImpl @Inject constructor(
    private val authorizer: DriveAuthorizer,
    private val apiClient: DriveApiClient,
    private val driveSource: DriveSource,
    private val driveAccountDao: DriveAccountDao,
    private val driveFolderDao: DriveFolderDao,
    private val comicDao: ComicDao,
    private val readingProgressDao: ReadingProgressDao,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : DriveRepository {

    constructor(
        authorizer: DriveAuthorizer,
        apiClient: DriveApiClient,
        driveSource: DriveSource,
        driveAccountDao: DriveAccountDao,
        driveFolderDao: DriveFolderDao,
        comicDao: ComicDao,
        ioDispatcher: CoroutineDispatcher
    ) : this(
        authorizer = authorizer,
        apiClient = apiClient,
        driveSource = driveSource,
        driveAccountDao = driveAccountDao,
        driveFolderDao = driveFolderDao,
        comicDao = comicDao,
        readingProgressDao = object : ReadingProgressDao {
            override fun observeProgress(comicKey: String): kotlinx.coroutines.flow.Flow<ReadingProgressEntity?> =
                kotlinx.coroutines.flow.emptyFlow()
            override suspend fun getProgress(comicKey: String): ReadingProgressEntity? = null
            override suspend fun getAllProgress(): List<ReadingProgressEntity> = emptyList()
            override suspend fun upsertProgress(progress: ReadingProgressEntity) {}
            override suspend fun upsertAllProgress(progressList: List<ReadingProgressEntity>) {}
        },
        ioDispatcher = ioDispatcher
    )

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    override fun observeAuthState(): Flow<DriveAuthState> =
        authorizer.observeAuthState()

    override suspend fun signIn(activity: Activity): Result<DriveAccount> = withContext(ioDispatcher) {
        val result = authorizer.signIn(activity)
        result.onSuccess { account ->
            driveAccountDao.upsertAccount(
                DriveAccountEntity(
                    email = account.email,
                    signedInAtEpochMillis = account.signedInAtEpochMillis
                )
            )
            runCatching { syncReadingProgress() }
        }
    }

    override suspend fun completeAuthorizationFromIntent(
        activity: Activity,
        data: Intent?
    ): Result<DriveAccount> = withContext(ioDispatcher) {
        val result = authorizer.completeAuthorizationFromIntent(activity, data)
        result.onSuccess { account ->
            driveAccountDao.upsertAccount(
                DriveAccountEntity(
                    email = account.email,
                    signedInAtEpochMillis = account.signedInAtEpochMillis
                )
            )
            runCatching { syncReadingProgress() }
        }
    }

    override suspend fun signOut() = withContext(ioDispatcher) {
        authorizer.signOut()
        comicDao.deleteAllDriveComics()
        driveFolderDao.deleteAllFolders()
        driveAccountDao.clearAccount()
    }

    override fun observeDriveFolders(): Flow<List<DriveFolder>> =
        driveFolderDao.observeAllFolders().map { entities ->
            entities.map { entity ->
                DriveFolder(
                    folderId = entity.folderId,
                    displayName = entity.displayName,
                    addedAtEpochMillis = entity.addedAtEpochMillis,
                    lastScannedAtEpochMillis = entity.lastScannedAtEpochMillis,
                    driveId = entity.driveId,
                    libraryId = entity.libraryId
                )
            }
        }.flowOn(ioDispatcher)

    override fun observeDriveFoldersByLibrary(libraryId: String): Flow<List<DriveFolder>> =
        driveFolderDao.observeFoldersByLibrary(libraryId).map { entities ->
            entities.map { entity ->
                DriveFolder(
                    folderId = entity.folderId,
                    displayName = entity.displayName,
                    addedAtEpochMillis = entity.addedAtEpochMillis,
                    lastScannedAtEpochMillis = entity.lastScannedAtEpochMillis,
                    driveId = entity.driveId,
                    libraryId = entity.libraryId
                )
            }
        }.flowOn(ioDispatcher)

    override suspend fun browseSubfolders(parentFolderId: String): Result<List<DriveBrowseItem>> =
        withContext(ioDispatcher) {
            try {
                val items = mutableListOf<DriveBrowseItem>()
                var pageToken: String? = null

                do {
                    val list = apiClient.listSubfolders(parentFolderId, pageToken)
                    for (file in list.files) {
                        if (file.trashed) continue
                        when (file.mimeType) {
                            MIME_TYPE_FOLDER -> {
                                items.add(
                                    DriveBrowseItem(
                                        id = file.id,
                                        name = file.name,
                                        driveId = file.driveId,
                                        isShortcut = false
                                    )
                                )
                            }

                            MIME_TYPE_SHORTCUT -> {
                                val details = file.shortcutDetails
                                val targetId = details?.targetId
                                val targetMime = details?.targetMimeType
                                if (targetMime == MIME_TYPE_FOLDER && !targetId.isNullOrBlank()) {
                                    items.add(
                                        DriveBrowseItem(
                                            id = targetId,
                                            name = file.name,
                                            driveId = file.driveId,
                                            isShortcut = true
                                        )
                                    )
                                }
                            }
                        }
                    }
                    pageToken = list.nextPageToken
                } while (!pageToken.isNullOrBlank())

                Result.success(items)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun addDriveFolder(
        folderId: String,
        displayName: String,
        driveId: String?
    ): Result<ScanResult> = addDriveFolder(folderId, displayName, driveId, ComicLibrary.DEFAULT_COMICS_ID)

    override suspend fun addDriveFolder(
        folderId: String,
        displayName: String,
        driveId: String?,
        libraryId: String
    ): Result<ScanResult> = withContext(ioDispatcher) {
        try {
            val now = System.currentTimeMillis()
            driveFolderDao.upsertFolder(
                DriveFolderEntity(
                    folderId = folderId,
                    displayName = displayName,
                    addedAtEpochMillis = now,
                    driveId = driveId,
                    libraryId = libraryId
                )
            )

            val currentAccount = driveAccountDao.getAccount()
            if (currentAccount?.changesPageToken.isNullOrBlank()) {
                try {
                    val startToken = apiClient.getStartPageToken()
                    driveAccountDao.updateChangesPageToken(startToken)
                } catch (_: Exception) {
                    // Start token will be refreshed on next sync
                }
            }

            rescanFolder(folderId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun removeDriveFolder(folderId: String) = withContext(ioDispatcher) {
        comicDao.deleteDriveComicsInFolder(folderId)
        driveFolderDao.deleteFolder(folderId)
    }

    override suspend fun rescanFolder(folderId: String): Result<ScanResult> = withContext(ioDispatcher) {
        try {
            val folderEntity = driveFolderDao.getFolder(folderId)
            val folderLibraryId = folderEntity?.libraryId ?: ComicLibrary.DEFAULT_COMICS_ID
            val driveFiles = driveSource.scanDriveFiles(folderId)
            val existing = comicDao.getComicsByDriveFolder(folderId)
            val existingByKey = existing.associateBy { it.comicKey }
            val existingByFileId = existing.mapNotNull { comic ->
                comic.driveFileId?.let { id -> id to comic }
            }.toMap()

            val entitiesToUpsert = mutableListOf<ComicEntity>()
            var addedCount = 0
            var updatedCount = 0

            for (file in driveFiles) {
                val md5 = file.md5Checksum?.lowercase() ?: continue
                val comicKey = "md5:$md5"
                val existingComic = existingByKey[comicKey] ?: existingByFileId[file.id]

                val format = DriveSource.detectSupportedFormat(file.name, file.md5Checksum) ?: ComicFormat.CBZ
                val entity = ComicEntity(
                    comicKey = comicKey,
                    title = existingComic?.title ?: file.name.substringBeforeLast('.'),
                    fileName = file.name,
                    format = format,
                    sourceType = ComicSourceType.GOOGLE_DRIVE,
                    availability = existingComic?.availability ?: ComicAvailability.REMOTE,
                    sizeBytes = file.size ?: 0L,
                    lastModifiedEpochMillis = DriveSource.parseModifiedTime(file.modifiedTime),
                    pageCount = existingComic?.pageCount ?: 0,
                    localUri = existingComic?.localUri,
                    folderUri = folderId,
                    localSampleSha256 = existingComic?.localSampleSha256,
                    driveFileId = file.id,
                    driveMd5 = md5,
                    coverCachePath = existingComic?.coverCachePath,
                    libraryId = folderLibraryId,
                    metaTitle = existingComic?.metaTitle,
                    metaSeries = existingComic?.metaSeries,
                    metaNumber = existingComic?.metaNumber,
                    metaVolume = existingComic?.metaVolume,
                    metaSummary = existingComic?.metaSummary,
                    metaWriter = existingComic?.metaWriter,
                    metaPenciller = existingComic?.metaPenciller,
                    metaPublisher = existingComic?.metaPublisher,
                    metaGenre = existingComic?.metaGenre,
                    metaReleaseYear = existingComic?.metaReleaseYear,
                    metaReleaseMonth = existingComic?.metaReleaseMonth,
                    metaReleaseDay = existingComic?.metaReleaseDay
                )

                if (existingComic == null) {
                    addedCount++
                } else {
                    updatedCount++
                }
                entitiesToUpsert.add(entity)
            }

            val discoveredKeys = entitiesToUpsert.map { it.comicKey }.toSet()
            val staleKeys = existingByKey.keys - discoveredKeys
            if (staleKeys.isNotEmpty()) {
                comicDao.deleteByKeys(staleKeys.toList())
            }

            if (entitiesToUpsert.isNotEmpty()) {
                comicDao.upsertComics(entitiesToUpsert)
            }

            driveFolderDao.updateLastScanned(folderId, System.currentTimeMillis())
            runCatching { syncReadingProgress() }

            Result.success(
                ScanResult(
                    addedOrUpdatedCount = addedCount + updatedCount,
                    removedCount = staleKeys.size,
                    skippedCorruptCount = 0,
                    errors = emptyList()
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun rescanDriveFoldersForLibrary(libraryId: String): Result<ScanResult> =
        withContext(ioDispatcher) {
            val folders = driveFolderDao.getFoldersByLibrary(libraryId)
            var totalAddedOrUpdated = 0
            var totalRemoved = 0
            var totalSkippedCorrupt = 0
            val allErrors = mutableListOf<ComicError>()

            for (folder in folders) {
                val singleResult = rescanFolder(folder.folderId)
                singleResult.onSuccess { result ->
                    totalAddedOrUpdated += result.addedOrUpdatedCount
                    totalRemoved += result.removedCount
                    totalSkippedCorrupt += result.skippedCorruptCount
                    allErrors.addAll(result.errors)
                }.onFailure { e ->
                    allErrors.add(ComicError.UnknownIoError(e.message))
                }
            }

            Result.success(
                ScanResult(
                    addedOrUpdatedCount = totalAddedOrUpdated,
                    removedCount = totalRemoved,
                    skippedCorruptCount = totalSkippedCorrupt,
                    errors = allErrors
                )
            )
        }

    override suspend fun incrementalSync(): Result<ScanResult> = withContext(ioDispatcher) {
        try {
            val registeredFolders = driveFolderDao.getAllFolders()
            if (registeredFolders.isEmpty()) {
                return@withContext Result.success(ScanResult(0, 0, 0, emptyList()))
            }

            val account = driveAccountDao.getAccount()
            val token = account?.changesPageToken

            if (token.isNullOrBlank()) {
                return@withContext fullRescanAllFolders(registeredFolders)
            }

            val removedFileIds = mutableListOf<String>()
            var hasRelevantChange = false
            var currentToken: String? = token
            var newStartPageToken: String? = null

            try {
                while (currentToken != null) {
                    val changeList = apiClient.listChanges(currentToken)
                    for (change in changeList.changes) {
                        val file = change.file
                        if (change.removed || file?.trashed == true) {
                            removedFileIds.add(change.fileId)
                        } else if (file != null) {
                            if (file.mimeType == MIME_TYPE_FOLDER ||
                                file.mimeType == MIME_TYPE_SHORTCUT ||
                                file.name.endsWith(".cbz", ignoreCase = true) ||
                                file.name.endsWith(".pdf", ignoreCase = true)
                            ) {
                                hasRelevantChange = true
                            }
                        }
                    }

                    if (!changeList.newStartPageToken.isNullOrBlank()) {
                        newStartPageToken = changeList.newStartPageToken
                        break
                    }
                    if (changeList.nextPageToken.isNullOrBlank()) {
                        break
                    }
                    currentToken = changeList.nextPageToken
                }
            } catch (e: com.c0mpile.comix.data.remote.drive.DriveApiException) {
                val err = e.comicError
                if (err is com.c0mpile.comix.domain.model.ComicError.DriveApiError &&
                    err.code in setOf(400, 404, 410)
                ) {
                    return@withContext fullRescanAllFolders(registeredFolders)
                }
                throw e
            }

            if (removedFileIds.isNotEmpty()) {
                comicDao.deleteByDriveFileIds(removedFileIds)
            }

            val result = if (hasRelevantChange) {
                var aggregatedAddedOrUpdated = 0
                var aggregatedRemoved = removedFileIds.size
                for (folder in registeredFolders) {
                    val scan = rescanFolder(folder.folderId).getOrThrow()
                    aggregatedAddedOrUpdated += scan.addedOrUpdatedCount
                    aggregatedRemoved += scan.removedCount
                }
                ScanResult(
                    addedOrUpdatedCount = aggregatedAddedOrUpdated,
                    removedCount = aggregatedRemoved,
                    skippedCorruptCount = 0,
                    errors = emptyList()
                )
            } else {
                ScanResult(
                    addedOrUpdatedCount = 0,
                    removedCount = removedFileIds.size,
                    skippedCorruptCount = 0,
                    errors = emptyList()
                )
            }

            if (!newStartPageToken.isNullOrBlank()) {
                driveAccountDao.updateChangesPageToken(newStartPageToken)
            }

            runCatching { syncReadingProgress() }

            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun fullRescanAllFolders(
        folders: List<DriveFolderEntity>
    ): Result<ScanResult> {
        val startToken = try {
            apiClient.getStartPageToken()
        } catch (_: Exception) {
            null
        }

        var totalAddedOrUpdated = 0
        var totalRemoved = 0
        for (folder in folders) {
            val scan = rescanFolder(folder.folderId).getOrThrow()
            totalAddedOrUpdated += scan.addedOrUpdatedCount
            totalRemoved += scan.removedCount
        }

        if (startToken != null) {
            driveAccountDao.updateChangesPageToken(startToken)
        }

        return Result.success(
            ScanResult(
                addedOrUpdatedCount = totalAddedOrUpdated,
                removedCount = totalRemoved,
                skippedCorruptCount = 0,
                errors = emptyList()
            )
        )
    }

    override suspend fun syncReadingProgress(): Result<Int> = withContext(ioDispatcher) {
        if (driveAccountDao.getAccount() == null) {
            return@withContext Result.success(0)
        }

        try {
            val localList = readingProgressDao.getAllProgress()
            val remoteJson = apiClient.readProgressSyncPayload()

            val remotePayload = if (!remoteJson.isNullOrBlank()) {
                runCatching {
                    json.decodeFromString<SyncedReadingProgressPayload>(remoteJson)
                }.getOrNull()
            } else {
                null
            }

            val localMap = localList.associateBy { it.comicKey }
            val remoteMap = (remotePayload?.entries ?: emptyList()).associateBy { it.comicKey }
            val allKeys = localMap.keys + remoteMap.keys

            val toUpsertLocal = mutableListOf<ReadingProgressEntity>()
            val mergedRemoteEntries = mutableListOf<SyncedReadingProgressEntry>()
            var remoteNeedsUpdate = false

            for (key in allKeys) {
                val local = localMap[key]
                val remote = remoteMap[key]

                when {
                    local == null && remote != null -> {
                        toUpsertLocal.add(
                            ReadingProgressEntity(
                                comicKey = remote.comicKey,
                                currentPage = remote.currentPage,
                                pageCount = remote.pageCount,
                                isFinished = remote.isFinished,
                                updatedAtEpochMillis = remote.updatedAtEpochMillis
                            )
                        )
                        mergedRemoteEntries.add(remote)
                    }

                    local != null && remote == null -> {
                        mergedRemoteEntries.add(
                            SyncedReadingProgressEntry(
                                comicKey = local.comicKey,
                                currentPage = local.currentPage,
                                pageCount = local.pageCount,
                                isFinished = local.isFinished,
                                updatedAtEpochMillis = local.updatedAtEpochMillis
                            )
                        )
                        remoteNeedsUpdate = true
                    }

                    local != null && remote != null -> {
                        if (remote.updatedAtEpochMillis > local.updatedAtEpochMillis) {
                            toUpsertLocal.add(
                                ReadingProgressEntity(
                                    comicKey = remote.comicKey,
                                    currentPage = remote.currentPage,
                                    pageCount = remote.pageCount,
                                    isFinished = remote.isFinished,
                                    updatedAtEpochMillis = remote.updatedAtEpochMillis
                                )
                            )
                            mergedRemoteEntries.add(remote)
                        } else if (local.updatedAtEpochMillis > remote.updatedAtEpochMillis) {
                            mergedRemoteEntries.add(
                                SyncedReadingProgressEntry(
                                    comicKey = local.comicKey,
                                    currentPage = local.currentPage,
                                    pageCount = local.pageCount,
                                    isFinished = local.isFinished,
                                    updatedAtEpochMillis = local.updatedAtEpochMillis
                                )
                            )
                            remoteNeedsUpdate = true
                        } else {
                            mergedRemoteEntries.add(remote)
                        }
                    }
                }
            }

            if (toUpsertLocal.isNotEmpty()) {
                readingProgressDao.upsertAllProgress(toUpsertLocal)
            }

            if (remoteNeedsUpdate || (remotePayload == null && mergedRemoteEntries.isNotEmpty())) {
                val newPayload = SyncedReadingProgressPayload(version = 1, entries = mergedRemoteEntries)
                val newJson = json.encodeToString(newPayload)
                apiClient.writeProgressSyncPayload(newJson)
            }

            Result.success(toUpsertLocal.size)
        } catch (e: DriveApiException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

@Serializable
data class SyncedReadingProgressEntry(
    val comicKey: String,
    val currentPage: Int,
    val pageCount: Int,
    val isFinished: Boolean,
    val updatedAtEpochMillis: Long
)

@Serializable
data class SyncedReadingProgressPayload(
    val version: Int = 1,
    val entries: List<SyncedReadingProgressEntry> = emptyList()
)
