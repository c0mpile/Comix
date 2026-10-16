package com.c0mpile.comix.data.source

import java.io.Closeable
import java.io.EOFException

/**
 * Random-access byte source abstraction under [com.c0mpile.comix.data.archive.ComicArchiveReader].
 *
 * Implementations must be thread-safe for concurrent [readAt] calls.
 * Phase 1 provides LocalRandomAccessSource backed by SAF ParcelFileDescriptor + FileChannel.
 * Phase 3 adds DriveRandomAccessSource backed by HTTP Range requests and a block cache.
 */
interface RandomAccessSource : Closeable {
    /**
     * Total size of the underlying resource in bytes.
     */
    fun size(): Long

    /**
     * Reads up to [length] bytes starting at [offset] in the resource into [buffer]
     * starting at [bufferOffset].
     *
     * @return the number of bytes actually read, or -1 if [offset] is at or past [size].
     * @throws java.io.IOException if an I/O error occurs.
     */
    fun readAt(
        offset: Long,
        buffer: ByteArray,
        bufferOffset: Int = 0,
        length: Int = buffer.size - bufferOffset
    ): Int
}

/**
 * Reads exact [length] bytes at [offset], looping until the buffer slice is filled
 * or throwing [EOFException] if the resource ends prematurely.
 */
fun RandomAccessSource.readFullyAt(
    offset: Long,
    buffer: ByteArray,
    bufferOffset: Int = 0,
    length: Int = buffer.size - bufferOffset
) {
    var totalRead = 0
    while (totalRead < length) {
        val count = readAt(
            offset = offset + totalRead,
            buffer = buffer,
            bufferOffset = bufferOffset + totalRead,
            length = length - totalRead
        )
        if (count <= 0) {
            throw EOFException(
                "Unexpected EOF at offset ${offset + totalRead} (requested $length bytes, got $totalRead)"
            )
        }
        totalRead += count
    }
}

/**
 * Factory for opening a [RandomAccessSource] from a storage reference.
 */
interface RandomAccessSourceFactory {
    /**
     * Opens a [RandomAccessSource] for the given local SAF content [uri].
     */
    suspend fun openLocal(uri: String): RandomAccessSource
}
