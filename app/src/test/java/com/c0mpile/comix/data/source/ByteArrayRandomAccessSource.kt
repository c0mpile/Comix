package com.c0mpile.comix.data.source

import java.io.IOException
import kotlin.math.min

/**
 * In-memory [RandomAccessSource] implementation backed by a [ByteArray] for unit tests.
 *
 * @param bytes Backing byte array representing the file content.
 * @param maxBytesPerRead Maximum bytes returned by a single [readAt] invocation to test partial reads.
 */
class ByteArrayRandomAccessSource(
    private val bytes: ByteArray,
    private val maxBytesPerRead: Int = Int.MAX_VALUE
) : RandomAccessSource {

    var isClosed: Boolean = false
        private set

    override fun size(): Long {
        checkNotClosed()
        return bytes.size.toLong()
    }

    override fun readAt(
        offset: Long,
        buffer: ByteArray,
        bufferOffset: Int,
        length: Int
    ): Int {
        checkNotClosed()
        if (offset < 0L || offset >= bytes.size) {
            return -1
        }
        val available = (bytes.size - offset).toInt()
        val toRead = min(min(length, available), maxBytesPerRead)
        if (toRead <= 0) {
            return 0
        }
        System.arraycopy(bytes, offset.toInt(), buffer, bufferOffset, toRead)
        return toRead
    }

    override fun close() {
        isClosed = true
    }

    private fun checkNotClosed() {
        if (isClosed) {
            throw IOException("RandomAccessSource is already closed")
        }
    }
}
