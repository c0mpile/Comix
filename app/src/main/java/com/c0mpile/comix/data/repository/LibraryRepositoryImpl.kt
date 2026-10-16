package com.c0mpile.comix.data.repository

import com.c0mpile.comix.data.archive.ComicArchiveReaderFactory
import com.c0mpile.comix.data.archive.CorruptArchiveException
import com.c0mpile.comix.data.archive.EmptyArchiveException
import com.c0mpile.comix.data.local.dao.ComicDao
import com.c0mpile.comix.data.local.dao.LibraryFolderDao
import com.c0mpile.comix.data.local.entity.ComicEntity
import com.c0mpile.comix.data.local.entity.ComicWithProgressProjection
import com.c0mpile.comix.data.local.entity.LibraryFolderEntity
import com.c0mpile.comix.data.local.entity.ReadingProgressEntity
import com.c0mpile.comix.data.source.ComicKeyHasher
import com.c0mpile.comix.data.source.ComicSource
import com.c0mpile.comix.data.source.DiscoveredComicFile
import com.c0mpile.comix.di.IoDispatcher
import com.c0mpile.comix.domain.model.Comic
import com.c0mpile.comix.domain.model.ComicAvailability
import com.c0mpile.comix.domain.model.ComicError
import com.c0mpile.comix.domain.model.ComicSourceType
import com.c0mpile.comix.domain.model.LibraryFolder
import com.c0mpile.comix.domain.model.ReadingProgress
import com.c0mpile.comix.domain.model.ScanResult
import com.c0mpile.comix.domain.repository.LibraryRepository
import java.io.FileNotFoundException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Repository coordinating library folders, comic file discovery, archive inspection,
 * and Room database persistence off the main thread.
 */
@Singleton
class LibraryRepositoryImpl @Inject constructor(
    private val comicDao: ComicDao,
    private val libraryFolderDao: LibraryFolderDao,
    private val comicSource: ComicSource,
    private val comicKeyHasher: ComicKeyHasher,
    private val archiveReaderFactory: ComicArchiveReaderFactory,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : LibraryRepository {

    override fun observeComics(): Flow<List<Comic>> =
        comicDao.observeAllComicsWithProgress()
            .map { list -> list.map { it.toDomain() } }
            .flowOn(ioDispatcher)

    override fun observeFolders(): Flow<List<LibraryFolder>> =
        libraryFolderDao.observeFolders()
            .map { entities ->
                entities.map { entity ->
                    LibraryFolder(
                        folderUri = entity.folderUri,
                        displayName = entity.displayName,
                        addedAtEpochMillis = entity.addedAtEpochMillis,
                        lastScannedAtEpochMillis = entity.lastScannedAtEpochMillis,
                        hasPersistedPermission = comicSource.hasPermission(entity.folderUri)
                    )
                }
            }
            .flowOn(ioDispatcher)

    override suspend fun addFolderAndScan(treeUri: String): Result<ScanResult> =
        withContext(ioDispatcher) {
            val hasPermission = comicSource.hasPermission(treeUri)
            if (!hasPermission) {
                return@withContext Result.success(
                    ScanResult(
                        addedOrUpdatedCount = 0,
                        removedCount = 0,
                        skippedCorruptCount = 0,
                        errors = listOf(ComicError.PermissionRevoked(treeUri))
                    )
                )
            }

            val existingFolders = libraryFolderDao.getFolders()
            val existing = existingFolders.find { it.folderUri == treeUri }
            val now = System.currentTimeMillis()
            val displayName = comicSource.getFolderDisplayName(treeUri)

            val folderEntity = LibraryFolderEntity(
                folderUri = treeUri,
                displayName = displayName,
                addedAtEpochMillis = existing?.addedAtEpochMillis ?: now,
                lastScannedAtEpochMillis = existing?.lastScannedAtEpochMillis
            )
            libraryFolderDao.upsertFolder(folderEntity)

            val scanResult = scanSingleFolder(treeUri)
            Result.success(scanResult)
        }

    override suspend fun rescanAllFolders(): Result<ScanResult> =
        withContext(ioDispatcher) {
            val folders = libraryFolderDao.getFolders()
            var totalAddedOrUpdated = 0
            var totalRemoved = 0
            var totalSkippedCorrupt = 0
            val allErrors = mutableListOf<ComicError>()

            for (folder in folders) {
                val singleResult = scanSingleFolder(folder.folderUri)
                totalAddedOrUpdated += singleResult.addedOrUpdatedCount
                totalRemoved += singleResult.removedCount
                totalSkippedCorrupt += singleResult.skippedCorruptCount
                allErrors.addAll(singleResult.errors)
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

    override suspend fun removeFolder(folderUri: String): Unit =
        withContext(ioDispatcher) {
            comicDao.deleteLocalComicsInFolder(folderUri)
            libraryFolderDao.deleteFolder(folderUri)
        }

    private suspend fun scanSingleFolder(folderUri: String): ScanResult {
        if (!comicSource.hasPermission(folderUri)) {
            return ScanResult(
                addedOrUpdatedCount = 0,
                removedCount = 0,
                skippedCorruptCount = 0,
                errors = listOf(ComicError.PermissionRevoked(folderUri))
            )
        }

        val existingComics = comicDao.getComicsByFolder(folderUri)
        val existingByUri = existingComics.associateBy { it.localUri }

        val discoveredFiles: List<DiscoveredComicFile> = try {
            comicSource.listComicFiles(folderUri)
        } catch (_: SecurityException) {
            return ScanResult(
                addedOrUpdatedCount = 0,
                removedCount = 0,
                skippedCorruptCount = 0,
                errors = listOf(ComicError.PermissionRevoked(folderUri))
            )
        } catch (e: IOException) {
            return ScanResult(
                addedOrUpdatedCount = 0,
                removedCount = 0,
                skippedCorruptCount = 0,
                errors = listOf(ComicError.UnknownIoError(e.message))
            )
        }

        val seenComicKeys = mutableSetOf<String>()
        var addedOrUpdated = 0
        var skippedCorrupt = 0
        val errors = mutableListOf<ComicError>()

        for (file in discoveredFiles) {
            val existing = existingByUri[file.uri]
            if (existing != null &&
                existing.sizeBytes == file.sizeBytes &&
                existing.lastModifiedEpochMillis == file.lastModifiedEpochMillis
            ) {
                seenComicKeys.add(existing.comicKey)
                continue
            }

            val sampleHash = try {
                comicSource.openRandomAccessSource(file.uri).use { source ->
                    comicKeyHasher.computeLocalSampleHash(source)
                }
            } catch (e: CorruptArchiveException) {
                skippedCorrupt++
                errors.add(ComicError.CorruptArchive(file.fileName, e.reason))
                continue
            } catch (e: EmptyArchiveException) {
                skippedCorrupt++
                errors.add(ComicError.EmptyArchive(file.fileName))
                continue
            } catch (_: FileNotFoundException) {
                skippedCorrupt++
                errors.add(ComicError.FileNotFound(file.uri))
                continue
            } catch (_: SecurityException) {
                skippedCorrupt++
                errors.add(ComicError.PermissionRevoked(file.uri))
                continue
            } catch (e: IOException) {
                skippedCorrupt++
                errors.add(ComicError.UnknownIoError(e.message))
                continue
            }

            val pageCount = try {
                val archiveSource = comicSource.openRandomAccessSource(file.uri)
                val reader = archiveReaderFactory.create(archiveSource, file.format, file.fileName)
                reader.use { it.getPages().size }
            } catch (e: CorruptArchiveException) {
                skippedCorrupt++
                errors.add(ComicError.CorruptArchive(file.fileName, e.reason))
                continue
            } catch (e: EmptyArchiveException) {
                skippedCorrupt++
                errors.add(ComicError.EmptyArchive(file.fileName))
                continue
            } catch (_: FileNotFoundException) {
                skippedCorrupt++
                errors.add(ComicError.FileNotFound(file.uri))
                continue
            } catch (_: SecurityException) {
                skippedCorrupt++
                errors.add(ComicError.PermissionRevoked(file.uri))
                continue
            } catch (e: IOException) {
                skippedCorrupt++
                errors.add(ComicError.UnknownIoError(e.message))
                continue
            }

            val existingByHash = comicDao.findByLocalSampleHash(sampleHash)
            val comicKey = existingByHash?.comicKey ?: sampleHash
            val coverCachePath = existingByHash?.coverCachePath ?: existing?.coverCachePath

            val entity = ComicEntity(
                comicKey = comicKey,
                title = file.title,
                fileName = file.fileName,
                format = file.format,
                sourceType = ComicSourceType.LOCAL_SAF,
                availability = ComicAvailability.LOCAL,
                sizeBytes = file.sizeBytes,
                lastModifiedEpochMillis = file.lastModifiedEpochMillis,
                pageCount = pageCount,
                localUri = file.uri,
                folderUri = folderUri,
                localSampleSha256 = sampleHash,
                driveFileId = existingByHash?.driveFileId,
                driveMd5 = existingByHash?.driveMd5,
                coverCachePath = coverCachePath
            )

            comicDao.upsertComic(entity)
            addedOrUpdated++
            seenComicKeys.add(comicKey)
        }

        val keysToDelete = existingComics
            .filter { it.driveFileId == null && it.comicKey !in seenComicKeys }
            .map { it.comicKey }
        if (keysToDelete.isNotEmpty()) {
            comicDao.deleteByKeys(keysToDelete)
        }
        val removedCount = keysToDelete.size

        libraryFolderDao.updateLastScanned(folderUri, System.currentTimeMillis())

        return ScanResult(
            addedOrUpdatedCount = addedOrUpdated,
            removedCount = removedCount,
            skippedCorruptCount = skippedCorrupt,
            errors = errors
        )
    }

    private fun ComicWithProgressProjection.toDomain(): Comic = Comic(
        comicKey = comic.comicKey,
        title = comic.title,
        fileName = comic.fileName,
        format = comic.format,
        sourceType = comic.sourceType,
        availability = comic.availability,
        sizeBytes = comic.sizeBytes,
        lastModifiedEpochMillis = comic.lastModifiedEpochMillis,
        pageCount = comic.pageCount,
        localUri = comic.localUri,
        folderUri = comic.folderUri,
        driveFileId = comic.driveFileId,
        driveMd5 = comic.driveMd5,
        coverCachePath = comic.coverCachePath,
        progress = progress?.toDomain()
    )

    private fun ReadingProgressEntity.toDomain(): ReadingProgress = ReadingProgress(
        comicKey = comicKey,
        currentPage = currentPage,
        pageCount = pageCount,
        isFinished = isFinished,
        updatedAtEpochMillis = updatedAtEpochMillis
    )
}
