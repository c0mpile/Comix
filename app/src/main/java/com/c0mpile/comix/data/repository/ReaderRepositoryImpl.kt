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
import com.c0mpile.comix.data.source.ComicSource
import com.c0mpile.comix.di.IoDispatcher
import com.c0mpile.comix.domain.model.Comic
import com.c0mpile.comix.domain.model.ComicError
import com.c0mpile.comix.domain.model.ReadingProgress
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
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ReaderRepository {

    override suspend fun getComic(comicKey: String): Comic? = withContext(ioDispatcher) {
        comicDao.getComicWithProgress(comicKey)?.toDomain()
    }

    override suspend fun openComicReader(comic: Comic): Result<ComicArchiveReader> =
        withContext(ioDispatcher) {
            val localUri = comic.localUri
            if (localUri.isNullOrBlank()) {
                return@withContext Result.failure(
                    ComicDomainException(ComicError.FileNotFound("No local URI for comic ${comic.comicKey}"))
                )
            }

            if (comic.folderUri != null && !comicSource.hasPermission(comic.folderUri)) {
                return@withContext Result.failure(
                    ComicDomainException(ComicError.PermissionRevoked(comic.folderUri))
                )
            }

            try {
                val source = comicSource.openRandomAccessSource(localUri)
                val reader = archiveReaderFactory.create(source, comic.format, comic.fileName)
                Result.success(reader)
            } catch (e: SecurityException) {
                Result.failure(ComicDomainException(ComicError.PermissionRevoked(localUri), e))
            } catch (e: FileNotFoundException) {
                Result.failure(ComicDomainException(ComicError.FileNotFound(localUri), e))
            } catch (e: EmptyArchiveException) {
                Result.failure(ComicDomainException(ComicError.EmptyArchive(comic.fileName), e))
            } catch (e: CorruptArchiveException) {
                Result.failure(ComicDomainException(ComicError.CorruptArchive(comic.fileName, e.reason), e))
            } catch (e: OutOfMemoryError) {
                Result.failure(ComicDomainException(ComicError.OutOfMemory(null), e))
            } catch (e: Exception) {
                Result.failure(ComicDomainException(ComicError.UnknownIoError(e.message), e))
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
