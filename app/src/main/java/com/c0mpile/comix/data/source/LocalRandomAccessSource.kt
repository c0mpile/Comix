package com.c0mpile.comix.data.source

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.c0mpile.comix.data.remote.drive.DriveApiClient
import com.c0mpile.comix.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Thread-safe [RandomAccessSource] implementation backed by an Android [ParcelFileDescriptor]
 * and [FileChannel] positioned reads.
 */
class LocalRandomAccessSource(
    private val pfd: ParcelFileDescriptor?,
    private val inputStream: FileInputStream?,
    private val channel: FileChannel,
    private val sourceSize: Long,
    private val tempFile: File? = null
) : RandomAccessSource {

    private val isClosed = AtomicBoolean(false)

    override fun size(): Long = sourceSize

    override fun readAt(
        offset: Long,
        buffer: ByteArray,
        bufferOffset: Int,
        length: Int
    ): Int {
        if (isClosed.get()) {
            throw IOException("RandomAccessSource is closed")
        }
        if (offset < 0L) {
            throw IllegalArgumentException("Offset must be non-negative: $offset")
        }
        if (bufferOffset < 0 || length < 0 || bufferOffset + length > buffer.size) {
            throw IndexOutOfBoundsException(
                "Invalid buffer range: offset=$bufferOffset, length=$length, size=${buffer.size}"
            )
        }
        if (length == 0) {
            return 0
        }
        if (offset >= sourceSize) {
            return -1
        }

        val toRead = minOf(length.toLong(), sourceSize - offset).toInt()
        if (toRead <= 0) {
            return -1
        }

        val byteBuffer = ByteBuffer.wrap(buffer, bufferOffset, toRead)
        return channel.read(byteBuffer, offset)
    }

    /**
     * Duplicates the underlying seekable [ParcelFileDescriptor] for consumers that require
     * direct descriptor access (such as [android.graphics.pdf.PdfRenderer]).
     *
     * The returned descriptor is independent and must be closed by the caller.
     */
    fun duplicateParcelFileDescriptor(): ParcelFileDescriptor? {
        if (isClosed.get()) {
            throw IOException("RandomAccessSource is closed")
        }
        return when {
            pfd != null -> pfd.dup()
            tempFile != null -> ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
            else -> null
        }
    }

    override fun close() {
        if (isClosed.compareAndSet(false, true)) {
            try {
                channel.close()
            } catch (_: Exception) {}
            try {
                inputStream?.close()
            } catch (_: Exception) {}
            try {
                pfd?.close()
            } catch (_: Exception) {}
            tempFile?.delete()
        }
    }
}

/**
 * Factory that opens [LocalRandomAccessSource] instances for local SAF content URIs
 * and streaming [DriveStreamingRandomAccessSource] instances for Google Drive files.
 */
class LocalRandomAccessSourceFactory @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val driveApiClient: DriveApiClient
) : RandomAccessSourceFactory {

    constructor(
        context: Context,
        ioDispatcher: CoroutineDispatcher
    ) : this(
        context = context,
        ioDispatcher = ioDispatcher,
        driveApiClient = object : DriveApiClient {
            override suspend fun listChildren(parentId: String, pageToken: String?) = throw UnsupportedOperationException()
            override suspend fun listSubfolders(parentId: String, pageToken: String?) = throw UnsupportedOperationException()
            override suspend fun getFile(fileId: String) = throw UnsupportedOperationException()
            override suspend fun getStartPageToken() = throw UnsupportedOperationException()
            override suspend fun listChanges(pageToken: String) = throw UnsupportedOperationException()
        }
    )

    override suspend fun openDrive(
        fileId: String,
        sizeBytes: Long
    ): RandomAccessSource = withContext(ioDispatcher) {
        val resolvedSize = if (sizeBytes > 0L) {
            sizeBytes
        } else {
            driveApiClient.getFile(fileId).size ?: 0L
        }
        val cacheDir = File(context.cacheDir, "drive_chunks")
        DriveStreamingRandomAccessSource(
            fileId = fileId,
            sourceSize = resolvedSize,
            apiClient = driveApiClient,
            cacheRootDir = cacheDir
        )
    }

    override suspend fun openLocal(uri: String): RandomAccessSource = withContext(ioDispatcher) {
        val parsedUri = Uri.parse(uri)
        val pfd = context.contentResolver.openFileDescriptor(parsedUri, "r")
            ?: throw FileNotFoundException("Unable to open file descriptor for $uri")

        try {
            val inputStream = FileInputStream(pfd.fileDescriptor)
            val channel = inputStream.channel
            val channelSize = try {
                channel.size()
            } catch (_: IOException) {
                -1L
            }
            val statSize = pfd.statSize

            val determinedSize = when {
                channelSize > 0L -> channelSize
                statSize > 0L -> statSize
                channelSize == 0L && statSize == 0L -> 0L
                else -> -1L
            }

            if (determinedSize >= 0L) {
                LocalRandomAccessSource(
                    pfd = pfd,
                    inputStream = inputStream,
                    channel = channel,
                    sourceSize = determinedSize
                )
            } else {
                val temp = File.createTempFile("saf_spool_", ".tmp", context.cacheDir)
                try {
                    temp.outputStream().use { out ->
                        inputStream.copyTo(out)
                    }
                    inputStream.close()
                    pfd.close()

                    val raf = RandomAccessFile(temp, "r")
                    val spooledChannel = raf.channel
                    val spooledSize = spooledChannel.size()

                    LocalRandomAccessSource(
                        pfd = null,
                        inputStream = null,
                        channel = spooledChannel,
                        sourceSize = spooledSize,
                        tempFile = temp
                    )
                } catch (e: Exception) {
                    temp.delete()
                    throw e
                }
            }
        } catch (e: Exception) {
            try {
                pfd.close()
            } catch (closeEx: Exception) {
                e.addSuppressed(closeEx)
            }
            throw e
        }
    }
}
