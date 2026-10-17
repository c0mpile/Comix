package com.c0mpile.comix.data.source

import com.c0mpile.comix.data.remote.drive.DriveApiClient
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.runBlocking

/**
 * Thread-safe [RandomAccessSource] implementation that streams Google Drive files
 * on demand via HTTP Range requests and maintains both an in-memory LRU chunk cache
 * and a persistent disk chunk cache.
 */
class DriveStreamingRandomAccessSource(
    private val fileId: String,
    private val sourceSize: Long,
    private val apiClient: DriveApiClient,
    private val cacheRootDir: File,
    private val chunkSizeBytes: Int = DEFAULT_CHUNK_SIZE_BYTES
) : RandomAccessSource {

    private val isClosed = AtomicBoolean(false)
    private val memoryCacheLock = Any()
    private val chunkDir = File(cacheRootDir, sanitizeFileId(fileId))

    private val memoryCache = object : LinkedHashMap<Long, ByteArray>(MAX_MEMORY_CHUNKS, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, ByteArray>?): Boolean {
            return size > MAX_MEMORY_CHUNKS
        }
    }

    override fun size(): Long = sourceSize

    override fun readAt(
        offset: Long,
        buffer: ByteArray,
        bufferOffset: Int,
        length: Int
    ): Int {
        if (isClosed.get()) {
            throw IOException("DriveStreamingRandomAccessSource is closed")
        }
        if (offset < 0L) {
            throw IllegalArgumentException("Offset must be non-negative: $offset")
        }
        if (bufferOffset < 0 || length < 0 || bufferOffset + length > buffer.size) {
            throw IndexOutOfBoundsException(
                "Invalid buffer range: bufferOffset=$bufferOffset, length=$length, size=${buffer.size}"
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

        var bytesCopied = 0
        var currentOffset = offset
        while (bytesCopied < toRead) {
            val chunkIndex = currentOffset / chunkSizeBytes
            val offsetInChunk = (currentOffset % chunkSizeBytes).toInt()
            val chunkData = getOrLoadChunk(chunkIndex)
            val availableInChunk = chunkData.size - offsetInChunk
            val chunkBytesToCopy = minOf(toRead - bytesCopied, availableInChunk)

            System.arraycopy(chunkData, offsetInChunk, buffer, bufferOffset + bytesCopied, chunkBytesToCopy)
            bytesCopied += chunkBytesToCopy
            currentOffset += chunkBytesToCopy
        }

        return bytesCopied
    }

    private fun getOrLoadChunk(chunkIndex: Long): ByteArray {
        val chunkStart = chunkIndex * chunkSizeBytes
        val expectedChunkSize = minOf(chunkSizeBytes.toLong(), sourceSize - chunkStart).toInt()
        if (expectedChunkSize <= 0) {
            return ByteArray(0)
        }

        synchronized(memoryCacheLock) {
            val mem = memoryCache[chunkIndex]
            if (mem != null && mem.size == expectedChunkSize) {
                return mem
            }
        }

        val chunkFile = File(chunkDir, "chunk_$chunkIndex.bin")
        if (chunkFile.exists() && chunkFile.length() == expectedChunkSize.toLong()) {
            try {
                val diskBytes = chunkFile.readBytes()
                if (diskBytes.size == expectedChunkSize) {
                    synchronized(memoryCacheLock) {
                        memoryCache[chunkIndex] = diskBytes
                    }
                    return diskBytes
                }
            } catch (_: IOException) {
                // Disk read error, fall through to network download
            }
        }

        val chunkEndInclusive = minOf(chunkStart + chunkSizeBytes - 1, sourceSize - 1)
        val downloaded = try {
            runBlocking {
                apiClient.downloadRange(fileId, chunkStart, chunkEndInclusive)
            }
        } catch (e: Exception) {
            throw IOException("Failed to stream chunk $chunkIndex for $fileId", e)
        }

        if (downloaded.size != expectedChunkSize) {
            throw IOException(
                "Incomplete chunk $chunkIndex for $fileId: expected $expectedChunkSize bytes, got ${downloaded.size}"
            )
        }

        try {
            if (!chunkDir.exists()) {
                chunkDir.mkdirs()
            }
            val tempFile = File.createTempFile("chunk_${chunkIndex}_", ".tmp", chunkDir)
            tempFile.writeBytes(downloaded)
            if (!tempFile.renameTo(chunkFile)) {
                tempFile.copyTo(chunkFile, overwrite = true)
                tempFile.delete()
            }
            enforceDiskCacheLimit()
        } catch (_: IOException) {
            // Disk cache write failure is non-fatal for reading
        }

        synchronized(memoryCacheLock) {
            memoryCache[chunkIndex] = downloaded
        }
        return downloaded
    }

    private fun enforceDiskCacheLimit() {
        val files = chunkDir.listFiles() ?: return
        var totalSize = files.sumOf { it.length() }
        if (totalSize <= MAX_DISK_CACHE_BYTES) return

        val sorted = files.sortedBy { it.lastModified() }
        val targetSize = (MAX_DISK_CACHE_BYTES * 3) / 4
        for (file in sorted) {
            val len = file.length()
            if (file.delete()) {
                totalSize -= len
                if (totalSize <= targetSize) break
            }
        }
    }

    override fun close() {
        if (isClosed.compareAndSet(false, true)) {
            synchronized(memoryCacheLock) {
                memoryCache.clear()
            }
        }
    }

    companion object {
        const val DEFAULT_CHUNK_SIZE_BYTES: Int = 256 * 1024
        private const val MAX_MEMORY_CHUNKS: Int = 16
        private const val MAX_DISK_CACHE_BYTES: Long = 128L * 1024L * 1024L

        fun sanitizeFileId(id: String): String = id.replace(Regex("[^a-zA-Z0-9._-]"), "_")
    }
}
