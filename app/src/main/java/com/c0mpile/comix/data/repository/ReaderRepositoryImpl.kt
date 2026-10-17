package com.c0mpile.comix.data.repository

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.c0mpile.comix.data.archive.ComicArchiveReader
import com.c0mpile.comix.data.archive.ComicArchiveReaderFactory
import com.c0mpile.comix.data.archive.CorruptArchiveException
import com.c0mpile.comix.data.archive.EmptyArchiveException
import com.c0mpile.comix.data.local.dao.ComicDao
import com.c0mpile.comix.data.local.dao.ReadingProgressDao
import com.c0mpile.comix.data.local.entity.ComicWithProgressProjection
import com.c0mpile.comix.data.local.entity.ReadingProgressEntity
import com.c0mpile.comix.data.remote.drive.DriveApiException
import com.c0mpile.comix.data.source.ComicSource
import com.c0mpile.comix.data.source.RandomAccessSource
import com.c0mpile.comix.data.source.RandomAccessSourceFactory
import com.c0mpile.comix.di.IoDispatcher
import com.c0mpile.comix.domain.model.Comic
import com.c0mpile.comix.domain.model.ComicError
import com.c0mpile.comix.domain.model.ComicMetadata
import com.c0mpile.comix.domain.model.DriveAccount
import com.c0mpile.comix.domain.model.DriveAuthState
import com.c0mpile.comix.domain.model.DriveBrowseItem
import com.c0mpile.comix.domain.model.DriveFolder
import com.c0mpile.comix.domain.model.ReadingProgress
import com.c0mpile.comix.domain.model.ScanResult
import com.c0mpile.comix.domain.repository.DriveRepository
import com.c0mpile.comix.domain.repository.ReaderRepository
import java.io.FileNotFoundException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Exception wrapper carrying a typed [ComicError] when reader repository operations fail.
 */
class ComicDomainException(
    val comicError: ComicError,
    cause: Throwable? = null
) : IOException(comicError.toString(), cause)

/**
 * Repository coordinating comic opening, downsampled bitmap decoding, and reading progress.
 */
@Singleton
class ReaderRepositoryImpl @Inject constructor(
    private val comicDao: ComicDao,
    private val readingProgressDao: ReadingProgressDao,
    private val comicSource: ComicSource,
    private val archiveReaderFactory: ComicArchiveReaderFactory,
    private val randomAccessSourceFactory: RandomAccessSourceFactory,
    private val driveRepository: DriveRepository,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ReaderRepository {

    constructor(
        comicDao: ComicDao,
        readingProgressDao: ReadingProgressDao,
        comicSource: ComicSource,
        archiveReaderFactory: ComicArchiveReaderFactory,
        ioDispatcher: CoroutineDispatcher
    ) : this(
        comicDao = comicDao,
        readingProgressDao = readingProgressDao,
        comicSource = comicSource,
        archiveReaderFactory = archiveReaderFactory,
        randomAccessSourceFactory = object : RandomAccessSourceFactory {
            override suspend fun openLocal(uri: String): RandomAccessSource = comicSource.openRandomAccessSource(uri)
        },
        driveRepository = object : DriveRepository {
            override fun observeAuthState() = kotlinx.coroutines.flow.emptyFlow<DriveAuthState>()
            override suspend fun signIn(activity: android.app.Activity) = Result.failure<DriveAccount>(UnsupportedOperationException())
            override suspend fun completeAuthorizationFromIntent(activity: android.app.Activity, data: android.content.Intent?) = Result.failure<DriveAccount>(UnsupportedOperationException())
            override suspend fun signOut() {}
            override fun observeDriveFolders() = kotlinx.coroutines.flow.emptyFlow<List<DriveFolder>>()
            override fun observeDriveFoldersByLibrary(libraryId: String) = kotlinx.coroutines.flow.emptyFlow<List<DriveFolder>>()
            override suspend fun browseSubfolders(parentFolderId: String) = Result.success(emptyList<DriveBrowseItem>())
            override suspend fun addDriveFolder(folderId: String, displayName: String, driveId: String?): Result<ScanResult> = Result.success(ScanResult(0, 0, 0, emptyList()))
            override suspend fun removeDriveFolder(folderId: String) {}
            override suspend fun rescanFolder(folderId: String) = Result.success(ScanResult(0, 0, 0, emptyList()))
            override suspend fun incrementalSync() = Result.success(ScanResult(0, 0, 0, emptyList()))
        },
        ioDispatcher = ioDispatcher
    )

    override suspend fun getComic(comicKey: String): Comic? = withContext(ioDispatcher) {
        comicDao.getComicWithProgress(comicKey)?.toDomain()
    }

    override suspend fun openComicReader(comic: Comic): Result<ComicArchiveReader> =
        withContext(ioDispatcher) {
            val localUri = comic.localUri
            val driveFileId = comic.driveFileId

            if (localUri.isNullOrBlank() && driveFileId.isNullOrBlank()) {
                return@withContext Result.failure(
                    ComicDomainException(ComicError.FileNotFound("No local URI or Drive file ID for comic ${comic.comicKey}"))
                )
            }

            if (!localUri.isNullOrBlank() && comic.folderUri != null && !comicSource.hasPermission(comic.folderUri)) {
                return@withContext Result.failure(
                    ComicDomainException(ComicError.PermissionRevoked(comic.folderUri))
                )
            }

            try {
                val source = if (!localUri.isNullOrBlank()) {
                    comicSource.openRandomAccessSource(localUri)
                } else {
                    randomAccessSourceFactory.openDrive(driveFileId!!, comic.sizeBytes)
                }
                val reader = archiveReaderFactory.create(source, comic.format, comic.fileName)

                if (localUri.isNullOrBlank() && !driveFileId.isNullOrBlank()) {
                    if (comic.pageCount <= 0 || comic.metadata == null) {
                        try {
                            val pages = reader.getPages()
                            val pageCount = pages.size
                            val metadata = reader.extractMetadata()
                            val existing = comicDao.getComicWithProgress(comic.comicKey)?.comic
                            if (existing != null) {
                                val updated = existing.copy(
                                    pageCount = if (existing.pageCount <= 0) pageCount else existing.pageCount,
                                    metaTitle = existing.metaTitle ?: metadata?.title,
                                    metaSeries = existing.metaSeries ?: metadata?.series,
                                    metaNumber = existing.metaNumber ?: metadata?.number,
                                    metaVolume = existing.metaVolume ?: metadata?.volume,
                                    metaSummary = existing.metaSummary ?: metadata?.summary,
                                    metaWriter = existing.metaWriter ?: metadata?.writer,
                                    metaPenciller = existing.metaPenciller ?: metadata?.penciller,
                                    metaPublisher = existing.metaPublisher ?: metadata?.publisher,
                                    metaGenre = existing.metaGenre ?: metadata?.genre,
                                    metaReleaseYear = existing.metaReleaseYear ?: metadata?.releaseYear,
                                    metaReleaseMonth = existing.metaReleaseMonth ?: metadata?.releaseMonth,
                                    metaReleaseDay = existing.metaReleaseDay ?: metadata?.releaseDay
                                )
                                comicDao.upsertComic(updated)
                            }
                        } catch (_: Exception) {
                            // Non-fatal if metadata persistence fails
                        }
                    }
                }

                Result.success(reader)
            } catch (e: SecurityException) {
                Result.failure(ComicDomainException(ComicError.PermissionRevoked(localUri ?: driveFileId ?: ""), e))
            } catch (e: FileNotFoundException) {
                Result.failure(ComicDomainException(ComicError.FileNotFound(localUri ?: driveFileId ?: ""), e))
            } catch (e: EmptyArchiveException) {
                Result.failure(ComicDomainException(ComicError.EmptyArchive(comic.fileName), e))
            } catch (e: CorruptArchiveException) {
                Result.failure(ComicDomainException(ComicError.CorruptArchive(comic.fileName, e.reason), e))
            } catch (e: OutOfMemoryError) {
                Result.failure(ComicDomainException(ComicError.OutOfMemory(null), e))
            } catch (e: DriveApiException) {
                Result.failure(ComicDomainException(e.comicError, e))
            } catch (e: Exception) {
                val driveEx = findDriveApiException(e)
                if (driveEx != null) {
                    Result.failure(ComicDomainException(driveEx.comicError, driveEx))
                } else {
                    Result.failure(ComicDomainException(ComicError.UnknownIoError(e.message), e))
                }
            }
        }

    override suspend fun decodePageBitmap(
        reader: ComicArchiveReader,
        pageIndex: Int,
        targetWidthPx: Int,
        targetHeightPx: Int
    ): Result<Bitmap> = withContext(ioDispatcher) {
        val pageBytes: ByteArray = try {
            reader.openPageBytes(pageIndex)
        } catch (e: OutOfMemoryError) {
            return@withContext Result.failure(
                ComicDomainException(ComicError.OutOfMemory(pageIndex), e)
            )
        } catch (e: CorruptArchiveException) {
            return@withContext Result.failure(
                ComicDomainException(ComicError.CorruptArchive(null, e.reason), e)
            )
        } catch (e: Exception) {
            return@withContext Result.failure(
                ComicDomainException(ComicError.UnknownIoError(e.message), e)
            )
        }

        val boundsOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeByteArray(pageBytes, 0, pageBytes.size, boundsOptions)

        val rawWidth = boundsOptions.outWidth
        val rawHeight = boundsOptions.outHeight
        if (rawWidth <= 0 || rawHeight <= 0) {
            return@withContext Result.failure(
                ComicDomainException(
                    ComicError.CorruptArchive(null, "Unable to decode image bounds for page $pageIndex")
                )
            )
        }

        val reqWidth = minOf(targetWidthPx.coerceAtLeast(1) * 2, 2560).coerceAtLeast(1080)
        val reqHeight = minOf(targetHeightPx.coerceAtLeast(1) * 2, 3840).coerceAtLeast(1920)

        var sampleSize = 1
        while ((rawWidth / sampleSize) > 4096 || (rawHeight / sampleSize) > 4096) {
            sampleSize *= 2
        }
        while ((rawWidth / (sampleSize * 2)) >= reqWidth && (rawHeight / (sampleSize * 2)) >= reqHeight) {
            sampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        try {
            val bitmap = BitmapFactory.decodeByteArray(pageBytes, 0, pageBytes.size, decodeOptions)
            if (bitmap != null) {
                Result.success(bitmap)
            } else {
                Result.failure(
                    ComicDomainException(
                        ComicError.CorruptArchive(null, "Bitmap decoding returned null for page $pageIndex")
                    )
                )
            }
        } catch (oom: OutOfMemoryError) {
            try {
                val fallbackOptions = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize * 2
                    inPreferredConfig = Bitmap.Config.RGB_565
                }
                val fallbackBitmap = BitmapFactory.decodeByteArray(pageBytes, 0, pageBytes.size, fallbackOptions)
                if (fallbackBitmap != null) {
                    Result.success(fallbackBitmap)
                } else {
                    Result.failure(ComicDomainException(ComicError.OutOfMemory(pageIndex), oom))
                }
            } catch (fallbackOom: OutOfMemoryError) {
                Result.failure(ComicDomainException(ComicError.OutOfMemory(pageIndex), fallbackOom))
            }
        } catch (e: Exception) {
            Result.failure(ComicDomainException(ComicError.UnknownIoError(e.message), e))
        }
    }

    override suspend fun getReadingProgress(comicKey: String): ReadingProgress? =
        withContext(ioDispatcher) {
            readingProgressDao.getProgress(comicKey)?.toDomain()
        }

    override suspend fun saveReadingProgress(
        comicKey: String,
        currentPage: Int,
        pageCount: Int
    ): Unit = withContext(ioDispatcher) {
        if (pageCount <= 0) return@withContext

        val clampedPage = currentPage.coerceIn(0, pageCount - 1)
        val isFinished = clampedPage >= pageCount - 1
        val entity = ReadingProgressEntity(
            comicKey = comicKey,
            currentPage = clampedPage,
            pageCount = pageCount,
            isFinished = isFinished,
            updatedAtEpochMillis = System.currentTimeMillis()
        )
        readingProgressDao.upsertProgress(entity)
        runCatching { driveRepository.syncReadingProgress() }
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

    private fun findDriveApiException(throwable: Throwable?): DriveApiException? {
        var current = throwable
        while (current != null) {
            if (current is DriveApiException) return current
            current = current.cause
        }
        return null
    }
}
