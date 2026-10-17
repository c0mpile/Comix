package com.c0mpile.comix.data.archive

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import com.c0mpile.comix.data.local.dao.ComicDao
import com.c0mpile.comix.data.source.RandomAccessSourceFactory
import com.c0mpile.comix.di.IoDispatcher
import com.c0mpile.comix.domain.model.ComicFormat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Extracts and caches downsampled cover images from comic archives.
 */
@Singleton
class CoverExtractor @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val randomAccessSourceFactory: RandomAccessSourceFactory,
    private val archiveReaderFactory: ComicArchiveReaderFactory,
    private val comicDao: ComicDao,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {

    /**
     * Retrieves an existing cover file or extracts the first page of the comic into
     * a downsampled WebP file in the cache directory.
     */
    suspend fun getOrExtractCoverFile(
        comicKey: String,
        localUri: String?,
        existingCachePath: String? = null,
        driveFileId: String? = null,
        sizeBytes: Long = 0L
    ): File? = withContext(ioDispatcher) {
        if (!existingCachePath.isNullOrBlank()) {
            val existingFile = File(existingCachePath)
            if (existingFile.exists() && existingFile.length() > 0L) {
                return@withContext existingFile
            }
        }

        val coversDir = File(context.cacheDir, "covers")
        val safeKey = comicKey.replace(':', '_')
        val targetFile = File(coversDir, "$safeKey.webp")
        if (targetFile.exists() && targetFile.length() > 0L) {
            return@withContext targetFile
        }

        if (localUri == null && driveFileId.isNullOrBlank()) {
            return@withContext null
        }

        try {
            if (!coversDir.exists()) {
                coversDir.mkdirs()
            }

            val source = if (!localUri.isNullOrBlank()) {
                randomAccessSourceFactory.openLocal(localUri)
            } else {
                randomAccessSourceFactory.openDrive(driveFileId!!, sizeBytes)
            }
            val detectedFormat = comicDao.getComicWithProgress(comicKey)?.comic?.format
                ?: if (localUri?.endsWith(".pdf", ignoreCase = true) == true) {
                    ComicFormat.PDF
                } else {
                    val header = ByteArray(8)
                    val read = try {
                        source.readAt(0L, header, 0, 8)
                    } catch (_: Exception) {
                        -1
                    }
                    if (read >= 5 && header.take(5).toByteArray().contentEquals(PDF_SIGNATURE_BYTES)) {
                        ComicFormat.PDF
                    } else {
                        ComicFormat.CBZ
                    }
                }

            val reader = archiveReaderFactory.create(source, detectedFormat)
            val page0Bytes = reader.use { r ->
                val pages = r.getPages()
                if (pages.isEmpty()) {
                    return@withContext null
                }
                r.openPageBytes(0)
            }

            val boundsOptions = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeByteArray(page0Bytes, 0, page0Bytes.size, boundsOptions)

            val reqWidth = TARGET_COVER_WIDTH_PX
            val reqHeight = TARGET_COVER_HEIGHT_PX
            var sampleSize = 1
            val rawWidth = boundsOptions.outWidth
            val rawHeight = boundsOptions.outHeight

            if (rawHeight > reqHeight || rawWidth > reqWidth) {
                val halfHeight = rawHeight / 2
                val halfWidth = rawWidth / 2
                while ((halfHeight / sampleSize) >= reqHeight && (halfWidth / sampleSize) >= reqWidth) {
                    sampleSize *= 2
                }
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bitmap = BitmapFactory.decodeByteArray(page0Bytes, 0, page0Bytes.size, decodeOptions)
                ?: return@withContext null

            val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                @Suppress("DEPRECATION")
                Bitmap.CompressFormat.WEBP
            }

            val tempFile = File.createTempFile("cover_", ".tmp", coversDir)
            try {
                tempFile.outputStream().use { out ->
                    bitmap.compress(format, 80, out)
                }
                if (!tempFile.renameTo(targetFile)) {
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
                }
            } finally {
                bitmap.recycle()
                if (tempFile.exists()) {
                    tempFile.delete()
                }
            }

            comicDao.updateCoverCachePath(comicKey, targetFile.absolutePath)
            targetFile
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private const val TARGET_COVER_WIDTH_PX = 400
        private const val TARGET_COVER_HEIGHT_PX = 600
        private val PDF_SIGNATURE_BYTES = byteArrayOf(
            0x25.toByte(), // %
            0x50.toByte(), // P
            0x44.toByte(), // D
            0x46.toByte(), // F
            0x2D.toByte()  // -
        )
    }
}
