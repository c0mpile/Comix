package com.c0mpile.comix.data.source

import com.c0mpile.comix.data.archive.EmptyArchiveException
import com.c0mpile.comix.di.IoDispatcher
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Computes a deterministic content-based identifier from a [RandomAccessSource]
 * using the 8-byte big-endian file size plus the first 64 KB and last 64 KB of content.
 */
class Sha256ComicKeyHasher @Inject constructor(
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ComicKeyHasher {

    override suspend fun computeLocalSampleHash(source: RandomAccessSource): String =
        withContext(ioDispatcher) {
            val size = source.size()
            if (size <= 0L) {
                throw EmptyArchiveException()
            }

            val digest = MessageDigest.getInstance("SHA-256")

            val sizeBytes = ByteBuffer.allocate(8)
                .order(ByteOrder.BIG_ENDIAN)
                .putLong(size)
                .array()
            digest.update(sizeBytes)

            val chunkSize = ComicKeyHasher.SAMPLE_CHUNK_BYTES
            val maxSmallSize = 2L * chunkSize

            if (size <= maxSmallSize) {
                val fullBuffer = ByteArray(size.toInt())
                source.readFullyAt(offset = 0L, buffer = fullBuffer)
                digest.update(fullBuffer)
            } else {
                val headBuffer = ByteArray(chunkSize)
                source.readFullyAt(offset = 0L, buffer = headBuffer)
                digest.update(headBuffer)

                val tailBuffer = ByteArray(chunkSize)
                val tailOffset = size - chunkSize
                source.readFullyAt(offset = tailOffset, buffer = tailBuffer)
                digest.update(tailBuffer)
            }

            digest.digest().joinToString("") { "%02x".format(it) }
        }
}
