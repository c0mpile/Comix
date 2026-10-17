package com.c0mpile.comix.data.archive

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.c0mpile.comix.data.source.LocalRandomAccessSource
import com.c0mpile.comix.data.source.RandomAccessSource
import com.c0mpile.comix.di.IoDispatcher
import com.c0mpile.comix.domain.model.ComicFormat
import com.c0mpile.comix.domain.model.ComicPage
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * [ComicArchiveReader] implementation rendering PDF documents via Android's [PdfRenderer].
 *
 * Page rendering and lifecycle operations are thread-safe and serialized through an internal [Mutex].
 */
class PdfArchiveReader(
    private val source: RandomAccessSource,
    private val pfd: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
    private val pages: List<ComicPage>,
    private val ioDispatcher: CoroutineDispatcher,
    private val fileName: String?,
    private val spoolFile: File? = null
) : ComicArchiveReader {

    private val renderMutex = Mutex()
    private val isClosed = AtomicBoolean(false)

    override fun getPages(): List<ComicPage> = pages

    override suspend fun openPageBytes(pageIndex: Int): ByteArray = withContext(ioDispatcher) {
        if (pageIndex !in pages.indices) {
            throw IndexOutOfBoundsException(
                "Page index $pageIndex out of bounds for ${pages.size} pages"
            )
        }

        renderMutex.withLock {
            if (isClosed.get()) {
                throw IOException("Archive reader is closed")
            }

            val page = renderer.openPage(pageIndex)
            try {
                val pageWidth = page.width.coerceAtLeast(1)
                val pageHeight = page.height.coerceAtLeast(1)
                var targetWidth = pageWidth * 2
                var targetHeight = pageHeight * 2

                if (targetWidth > MAX_RENDER_WIDTH || targetHeight > MAX_RENDER_HEIGHT) {
                    val widthRatio = MAX_RENDER_WIDTH.toFloat() / targetWidth
                    val heightRatio = MAX_RENDER_HEIGHT.toFloat() / targetHeight
                    val scale = minOf(widthRatio, heightRatio)
                    targetWidth = (targetWidth * scale).toInt().coerceAtLeast(1)
                    targetHeight = (targetHeight * scale).toInt().coerceAtLeast(1)
                }

                val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                try {
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                    val outputStream = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                    outputStream.toByteArray()
                } finally {
                    bitmap.recycle()
                }
            } finally {
                page.close()
            }
        }
    }

    override fun close() {
        if (isClosed.compareAndSet(false, true)) {
            try {
                renderer.close()
            } catch (_: Exception) {}
            try {
                pfd.close()
            } catch (_: Exception) {}
            try {
                source.close()
            } catch (_: Exception) {}
            try {
                spoolFile?.delete()
            } catch (_: Exception) {}
        }
    }

    companion object {
        const val MAX_RENDER_WIDTH: Int = 2560
        const val MAX_RENDER_HEIGHT: Int = 3840
    }
}

/**
 * Factory creating [PdfArchiveReader] instances from [RandomAccessSource] inputs.
 */
class PdfArchiveReaderFactory(
    private val cacheDir: File,
    private val ioDispatcher: CoroutineDispatcher,
    private val pdfRendererFactory: (ParcelFileDescriptor) -> PdfRenderer = { pfd -> PdfRenderer(pfd) },
    private val pfdOpener: (File) -> ParcelFileDescriptor = { file ->
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }
) : ComicArchiveReaderFactory {

    @Inject
    constructor(
        @ApplicationContext context: Context,
        @IoDispatcher ioDispatcher: CoroutineDispatcher
    ) : this(
        cacheDir = context.cacheDir,
        ioDispatcher = ioDispatcher
    )

    constructor(
        ioDispatcher: CoroutineDispatcher,
        cacheDir: File? = null,
        pdfRendererFactory: (ParcelFileDescriptor) -> PdfRenderer = { pfd -> PdfRenderer(pfd) },
        pfdOpener: (File) -> ParcelFileDescriptor = { file ->
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        }
    ) : this(
        cacheDir = cacheDir ?: File(System.getProperty("java.io.tmpdir") ?: ".local"),
        ioDispatcher = ioDispatcher,
        pdfRendererFactory = pdfRendererFactory,
        pfdOpener = pfdOpener
    )

    override suspend fun create(
        source: RandomAccessSource,
        format: ComicFormat,
        fileName: String?
    ): ComicArchiveReader = withContext(ioDispatcher) {
        if (format != ComicFormat.PDF) {
            source.close()
            throw CorruptArchiveException(fileName, "Unsupported format for PdfArchiveReader: $format")
        }

        val totalSize = source.size()
        if (totalSize <= 0L) {
            source.close()
            throw EmptyArchiveException(fileName)
        }

        val headerLength = minOf(1024L, totalSize).toInt()
        val headerBuffer = ByteArray(headerLength)
        val bytesRead = source.readAt(0L, headerBuffer, 0, headerLength)
        if (bytesRead < PDF_HEADER_SIGNATURE.size) {
            source.close()
            throw CorruptArchiveException(fileName, "Missing PDF header signature")
        }

        if (!containsSubarray(headerBuffer, bytesRead, PDF_HEADER_SIGNATURE)) {
            source.close()
            throw CorruptArchiveException(fileName, "Missing PDF header signature")
        }

        var pfd: ParcelFileDescriptor? = null
        var spoolFile: File? = null

        try {
            if (source is LocalRandomAccessSource) {
                pfd = source.duplicateParcelFileDescriptor()
            }

            if (pfd == null) {
                if (!cacheDir.exists()) {
                    cacheDir.mkdirs()
                }
                val temp = File.createTempFile("pdf_spool_", ".pdf", cacheDir)
                spoolFile = temp

                temp.outputStream().use { out ->
                    val chunk = ByteArray(64 * 1024)
                    var offset = 0L
                    while (offset < totalSize) {
                        val toRead = minOf(chunk.size.toLong(), totalSize - offset).toInt()
                        val readCount = source.readAt(offset, chunk, 0, toRead)
                        if (readCount <= 0) break
                        out.write(chunk, 0, readCount)
                        offset += readCount
                    }
                }
                pfd = pfdOpener(temp)
            }

            val renderer = try {
                pdfRendererFactory(pfd)
            } catch (e: Exception) {
                throw CorruptArchiveException(fileName, e.message ?: "Failed to open PDF")
            }

            val count = renderer.pageCount
            if (count <= 0) {
                try {
                    renderer.close()
                } catch (_: Exception) {}
                throw EmptyArchiveException(fileName)
            }

            val pages = (0 until count).map { idx ->
                ComicPage(
                    index = idx,
                    entryName = "page_%04d.pdf".format(java.util.Locale.US, idx + 1),
                    compressedSize = 0L,
                    uncompressedSize = 0L
                )
            }

            PdfArchiveReader(
                source = source,
                pfd = pfd,
                renderer = renderer,
                pages = pages,
                ioDispatcher = ioDispatcher,
                fileName = fileName,
                spoolFile = spoolFile
            )
        } catch (e: Throwable) {
            try {
                pfd?.close()
            } catch (_: Exception) {}
            try {
                spoolFile?.delete()
            } catch (_: Exception) {}
            try {
                source.close()
            } catch (_: Exception) {}
            throw e
        }
    }

    companion object {
        private val PDF_HEADER_SIGNATURE = byteArrayOf(
            0x25.toByte(), // %
            0x50.toByte(), // P
            0x44.toByte(), // D
            0x46.toByte(), // F
            0x2D.toByte()  // -
        )

        private fun containsSubarray(source: ByteArray, length: Int, target: ByteArray): Boolean {
            if (target.isEmpty() || length < target.size) return false
            for (i in 0..length - target.size) {
                var match = true
                for (j in target.indices) {
                    if (source[i + j] != target[j]) {
                        match = false
                        break
                    }
                }
                if (match) return true
            }
            return false
        }
    }
}
