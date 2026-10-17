package com.c0mpile.comix.data.repository

import com.c0mpile.comix.data.archive.ComicArchiveReaderFactory
import com.c0mpile.comix.data.archive.CorruptArchiveException
import com.c0mpile.comix.data.archive.EmptyArchiveException
import com.c0mpile.comix.data.local.dao.ComicDao
import com.c0mpile.comix.data.local.dao.DriveFolderDao
import com.c0mpile.comix.data.local.dao.LibraryDao
import com.c0mpile.comix.data.local.dao.LibraryFolderDao
import com.c0mpile.comix.data.local.entity.ComicEntity
import com.c0mpile.comix.data.local.entity.ComicWithProgressProjection
import com.c0mpile.comix.data.local.entity.LibraryEntity
import com.c0mpile.comix.data.local.entity.LibraryFolderEntity
import com.c0mpile.comix.data.local.entity.ReadingProgressEntity
import com.c0mpile.comix.data.source.ComicKeyHasher
import com.c0mpile.comix.data.source.ComicSource
import com.c0mpile.comix.data.source.DiscoveredComicFile
import com.c0mpile.comix.di.IoDispatcher
import com.c0mpile.comix.domain.model.BulkComicMetadataUpdate
import com.c0mpile.comix.domain.model.Comic
import com.c0mpile.comix.domain.model.ComicAvailability
import com.c0mpile.comix.domain.model.ComicError
import com.c0mpile.comix.domain.model.ComicFormat
import com.c0mpile.comix.domain.model.ComicLibrary
import com.c0mpile.comix.domain.model.ComicMetadata
import com.c0mpile.comix.domain.model.ComicSourceType
import com.c0mpile.comix.domain.model.LibraryFolder
import com.c0mpile.comix.domain.model.ReadingDirection
import com.c0mpile.comix.domain.model.ReadingProgress
import com.c0mpile.comix.domain.model.ScanResult
import com.c0mpile.comix.domain.model.SingleComicMetadataUpdate
import com.c0mpile.comix.domain.repository.LibraryRepository
import java.io.FileNotFoundException
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Repository coordinating multiple libraries, library folders, comic file discovery,
 * archive inspection, and Room database persistence off the main thread.
 */
@Singleton
class LibraryRepositoryImpl @Inject constructor(
    private val comicDao: ComicDao,
    private val libraryFolderDao: LibraryFolderDao,
    private val comicSource: ComicSource,
    private val comicKeyHasher: ComicKeyHasher,
    private val archiveReaderFactory: ComicArchiveReaderFactory,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val libraryDao: LibraryDao,
    private val driveFolderDao: DriveFolderDao
) : LibraryRepository {

    override fun observeLibraries(): Flow<List<ComicLibrary>> =
        libraryDao.observeLibraries()
            .map { entities ->
                if (entities.isEmpty()) {
                    ensureDefaultLibrariesSeeded()
                    libraryDao.getLibraries().map { it.toDomain() }
                } else {
                    entities.map { it.toDomain() }
                }
            }
            .flowOn(ioDispatcher)

    override suspend fun getLibrary(libraryId: String): ComicLibrary? =
        withContext(ioDispatcher) {
            ensureDefaultLibrariesSeeded()
            libraryDao.getLibrary(libraryId)?.toDomain()
        }

    override suspend fun createLibrary(
        name: String,
        defaultReadingDirection: ReadingDirection
    ): Result<ComicLibrary> = withContext(ioDispatcher) {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Library name cannot be blank"))
        }

        val slug = trimmedName.lowercase()
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .take(20)
        val uniqueSuffix = UUID.randomUUID().toString().replace("-", "").take(8)
        val libraryId = if (slug.isNotBlank()) "${slug}_$uniqueSuffix" else uniqueSuffix

        val entity = LibraryEntity(
            libraryId = libraryId,
            name = trimmedName,
            defaultReadingDirection = defaultReadingDirection,
            createdAtEpochMillis = System.currentTimeMillis(),
            isBuiltIn = false
        )
        libraryDao.upsertLibrary(entity)
        Result.success(entity.toDomain())
    }

    override suspend fun updateLibrary(
        libraryId: String,
        name: String,
        defaultReadingDirection: ReadingDirection
    ): Result<Unit> = withContext(ioDispatcher) {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Library name cannot be blank"))
        }

        val existing = libraryDao.getLibrary(libraryId)
            ?: return@withContext Result.failure(IllegalArgumentException("Library not found: $libraryId"))

        val updated = existing.copy(
            name = trimmedName,
            defaultReadingDirection = defaultReadingDirection
        )
        libraryDao.upsertLibrary(updated)
        Result.success(Unit)
    }

    override suspend fun deleteLibrary(libraryId: String): Result<Unit> = withContext(ioDispatcher) {
        val allLibraries = libraryDao.getLibraries()
        if (allLibraries.size <= 1) {
            return@withContext Result.failure(IllegalStateException("Cannot delete the last remaining library"))
        }

        comicDao.deleteComicsByLibrary(libraryId)
        libraryFolderDao.deleteFoldersByLibrary(libraryId)
        driveFolderDao.deleteFoldersByLibrary(libraryId)
        libraryDao.deleteLibrary(libraryId)
        Result.success(Unit)
    }

    override fun observeComics(): Flow<List<Comic>> =
        comicDao.observeAllComicsWithProgress()
            .map { list -> list.map { it.toDomain() } }
            .flowOn(ioDispatcher)

    override fun observeComicsByLibrary(libraryId: String): Flow<List<Comic>> =
        comicDao.observeComicsWithProgressByLibrary(libraryId)
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
                        hasPersistedPermission = comicSource.hasPermission(entity.folderUri),
                        libraryId = entity.libraryId
                    )
                }
            }
            .flowOn(ioDispatcher)

    override fun observeFoldersByLibrary(libraryId: String): Flow<List<LibraryFolder>> =
        libraryFolderDao.observeFoldersByLibrary(libraryId)
            .map { entities ->
                entities.map { entity ->
                    LibraryFolder(
                        folderUri = entity.folderUri,
                        displayName = entity.displayName,
                        addedAtEpochMillis = entity.addedAtEpochMillis,
                        lastScannedAtEpochMillis = entity.lastScannedAtEpochMillis,
                        hasPersistedPermission = comicSource.hasPermission(entity.folderUri),
                        libraryId = entity.libraryId
                    )
                }
            }
            .flowOn(ioDispatcher)

    override suspend fun addFolderAndScan(treeUri: String): Result<ScanResult> =
        addFolderAndScan(treeUri, ComicLibrary.DEFAULT_COMICS_ID)

    override suspend fun addFolderAndScan(treeUri: String, libraryId: String): Result<ScanResult> =
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
                lastScannedAtEpochMillis = existing?.lastScannedAtEpochMillis,
                libraryId = libraryId
            )
            libraryFolderDao.upsertFolder(folderEntity)

            val scanResult = scanSingleFolder(treeUri, libraryId)
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
                val singleResult = scanSingleFolder(folder.folderUri, folder.libraryId)
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

    override suspend fun rescanLibraryFolders(libraryId: String): Result<ScanResult> =
        withContext(ioDispatcher) {
            val folders = libraryFolderDao.getFoldersByLibrary(libraryId)
            var totalAddedOrUpdated = 0
            var totalRemoved = 0
            var totalSkippedCorrupt = 0
            val allErrors = mutableListOf<ComicError>()

            for (folder in folders) {
                val singleResult = scanSingleFolder(folder.folderUri, folder.libraryId)
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

    override suspend fun updateComicMetadata(update: SingleComicMetadataUpdate): Result<Unit> =
        withContext(ioDispatcher) {
            val entity = comicDao.getComicByKey(update.comicKey)
                ?: return@withContext Result.failure(NoSuchElementException("Comic not found: ${update.comicKey}"))

            val trimmedTitle = update.title?.trim()
            val newTitle = if (!trimmedTitle.isNullOrBlank()) trimmedTitle else entity.title

            val updatedEntity = entity.copy(
                title = newTitle,
                metaSeries = update.series?.trim()?.ifBlank { null },
                metaTitle = trimmedTitle?.ifBlank { null },
                metaNumber = update.number?.trim()?.ifBlank { null },
                metaReleaseYear = update.releaseYear,
                metaWriter = update.writer?.trim()?.ifBlank { null },
                metaPenciller = update.penciller?.trim()?.ifBlank { null },
                metaPublisher = update.publisher?.trim()?.ifBlank { null },
                metaSummary = update.summary?.trim()?.ifBlank { null }
            )

            comicDao.upsertComic(updatedEntity)
            Result.success(Unit)
        }

    override suspend fun bulkUpdateComicMetadata(update: BulkComicMetadataUpdate): Result<Unit> =
        withContext(ioDispatcher) {
            if (update.comicKeys.isEmpty()) {
                return@withContext Result.success(Unit)
            }
            val entities = comicDao.getComicsByKeys(update.comicKeys)
            if (entities.isEmpty()) {
                return@withContext Result.success(Unit)
            }

            val updatedEntities = entities.map { entity ->
                entity.copy(
                    metaSeries = if (update.updateSeries) update.series?.trim()?.ifBlank { null } else entity.metaSeries,
                    metaReleaseYear = if (update.updateReleaseYear) update.releaseYear else entity.metaReleaseYear,
                    metaWriter = if (update.updateWriter) update.writer?.trim()?.ifBlank { null } else entity.metaWriter,
                    metaPenciller = if (update.updatePenciller) update.penciller?.trim()?.ifBlank { null } else entity.metaPenciller,
                    metaPublisher = if (update.updatePublisher) update.publisher?.trim()?.ifBlank { null } else entity.metaPublisher
                )
            }

            comicDao.upsertComics(updatedEntities)
            Result.success(Unit)
        }

    private suspend fun ensureDefaultLibrariesSeeded() {
        if (libraryDao.getLibraries().isEmpty()) {
            val now = System.currentTimeMillis()
            libraryDao.upsertLibraries(
                listOf(
                    LibraryEntity(
                        libraryId = ComicLibrary.DEFAULT_COMICS_ID,
                        name = "Comics",
                        defaultReadingDirection = ReadingDirection.LEFT_TO_RIGHT,
                        createdAtEpochMillis = now,
                        isBuiltIn = true
                    ),
                    LibraryEntity(
                        libraryId = ComicLibrary.DEFAULT_MANGA_ID,
                        name = "Manga",
                        defaultReadingDirection = ReadingDirection.RIGHT_TO_LEFT,
                        createdAtEpochMillis = now + 1,
                        isBuiltIn = true
                    )
                )
            )
        }
    }

    private suspend fun scanSingleFolder(folderUri: String, libraryId: String): ScanResult {
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

            val (pageCount, metadata) = try {
                val archiveSource = comicSource.openRandomAccessSource(file.uri)
                val reader = archiveReaderFactory.create(archiveSource, file.format, file.fileName)
                reader.use {
                    Pair(it.getPages().size, it.extractMetadata())
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
                coverCachePath = coverCachePath,
                libraryId = libraryId,
                metaTitle = metadata?.title,
                metaSeries = metadata?.series,
                metaNumber = metadata?.number,
                metaVolume = metadata?.volume,
                metaSummary = metadata?.summary,
                metaWriter = metadata?.writer,
                metaPenciller = metadata?.penciller,
                metaPublisher = metadata?.publisher,
                metaGenre = metadata?.genre,
                metaReleaseYear = metadata?.releaseYear,
                metaReleaseMonth = metadata?.releaseMonth,
                metaReleaseDay = metadata?.releaseDay
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

    private fun ComicWithProgressProjection.toDomain(): Comic {
        val metadata = ComicMetadata(
            title = comic.metaTitle,
            series = comic.metaSeries,
            number = comic.metaNumber,
            volume = comic.metaVolume,
            summary = comic.metaSummary,
            writer = comic.metaWriter,
            penciller = comic.metaPenciller,
            publisher = comic.metaPublisher,
            genre = comic.metaGenre,
            releaseYear = comic.metaReleaseYear,
            releaseMonth = comic.metaReleaseMonth,
            releaseDay = comic.metaReleaseDay
        ).takeIf { it.hasAnyMetadata }

        return Comic(
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
            progress = progress?.toDomain(),
            libraryId = comic.libraryId,
            metadata = metadata
        )
    }

    private fun ReadingProgressEntity.toDomain(): ReadingProgress = ReadingProgress(
        comicKey = comicKey,
        currentPage = currentPage,
        pageCount = pageCount,
        isFinished = isFinished,
        updatedAtEpochMillis = updatedAtEpochMillis
    )

    private fun LibraryEntity.toDomain(): ComicLibrary = ComicLibrary(
        libraryId = libraryId,
        name = name,
        defaultReadingDirection = defaultReadingDirection,
        createdAtEpochMillis = createdAtEpochMillis,
        isBuiltIn = isBuiltIn
    )
}
