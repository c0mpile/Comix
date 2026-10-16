package com.c0mpile.comix.data.source

/**
 * Computes a deterministic content-based identifier from a [RandomAccessSource]
 * using the 8-byte big-endian file size plus the first 64 KB and last 64 KB of content.
 */
interface ComicKeyHasher {
    suspend fun computeLocalSampleHash(source: RandomAccessSource): String

    companion object {
        const val SAMPLE_CHUNK_BYTES: Int = 64 * 1024
    }
}
