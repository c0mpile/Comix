package com.c0mpile.comix.data.source

import com.c0mpile.comix.data.archive.EmptyArchiveException
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Unit tests for [Sha256ComicKeyHasher].
 */
class Sha256ComicKeyHasherTest {

    private val testDispatcher = StandardTestDispatcher()
    private val hasher = Sha256ComicKeyHasher(testDispatcher)

    @Test
    fun emptySourceThrowsEmptyArchiveException() = runTest(testDispatcher) {
        val source = ByteArrayRandomAccessSource(ByteArray(0))
        try {
            hasher.computeLocalSampleHash(source)
            fail("Expected EmptyArchiveException for 0-byte source")
        } catch (expected: EmptyArchiveException) {
            // Expected
        }
    }

    @Test
    fun determinismSmallFile1024Bytes() = runTest(testDispatcher) {
        val bytes = ByteArray(1024) { (it % 251).toByte() }
        val source1 = ByteArrayRandomAccessSource(bytes)
        val source2 = ByteArrayRandomAccessSource(bytes.copyOf())

        val hash1 = hasher.computeLocalSampleHash(source1)
        val hash2 = hasher.computeLocalSampleHash(source2)

        assertEquals(hash1, hash2)
        assertEquals(64, hash1.length)
        assertTrue(hash1.matches(Regex("^[0-9a-f]{64}$")))
    }

    @Test
    fun determinismExactBoundary131072Bytes() = runTest(testDispatcher) {
        val size = 2 * ComicKeyHasher.SAMPLE_CHUNK_BYTES
        val bytes = ByteArray(size) { (it % 251).toByte() }
        val source1 = ByteArrayRandomAccessSource(bytes)
        val source2 = ByteArrayRandomAccessSource(bytes.copyOf())

        val hash1 = hasher.computeLocalSampleHash(source1)
        val hash2 = hasher.computeLocalSampleHash(source2)

        assertEquals(hash1, hash2)
        assertEquals(64, hash1.length)
        assertTrue(hash1.matches(Regex("^[0-9a-f]{64}$")))
    }

    @Test
    fun determinismLargeFile200000Bytes() = runTest(testDispatcher) {
        val bytes = ByteArray(200_000) { (it % 251).toByte() }
        val source1 = ByteArrayRandomAccessSource(bytes)
        val source2 = ByteArrayRandomAccessSource(bytes.copyOf())

        val hash1 = hasher.computeLocalSampleHash(source1)
        val hash2 = hasher.computeLocalSampleHash(source2)

        assertEquals(hash1, hash2)
        assertEquals(64, hash1.length)
        assertTrue(hash1.matches(Regex("^[0-9a-f]{64}$")))
    }

    @Test
    fun sensitivityToFileSizeChange() = runTest(testDispatcher) {
        val bytes1 = ByteArray(200_000) { (it % 251).toByte() }
        val bytes2 = ByteArray(200_001) { (it % 251).toByte() }

        val hash1 = hasher.computeLocalSampleHash(ByteArrayRandomAccessSource(bytes1))
        val hash2 = hasher.computeLocalSampleHash(ByteArrayRandomAccessSource(bytes2))

        assertNotEquals(hash1, hash2)
    }

    @Test
    fun sensitivityToFirst64KbMutation() = runTest(testDispatcher) {
        val bytes1 = ByteArray(200_000) { (it % 251).toByte() }
        val bytes2 = bytes1.copyOf()
        bytes2[100] = (bytes2[100] + 1).toByte()

        val hash1 = hasher.computeLocalSampleHash(ByteArrayRandomAccessSource(bytes1))
        val hash2 = hasher.computeLocalSampleHash(ByteArrayRandomAccessSource(bytes2))

        assertNotEquals(hash1, hash2)
    }

    @Test
    fun sensitivityToLast64KbMutation() = runTest(testDispatcher) {
        val bytes1 = ByteArray(200_000) { (it % 251).toByte() }
        val bytes2 = bytes1.copyOf()
        val tailIndex = 190_000
        bytes2[tailIndex] = (bytes2[tailIndex] + 1).toByte()

        val hash1 = hasher.computeLocalSampleHash(ByteArrayRandomAccessSource(bytes1))
        val hash2 = hasher.computeLocalSampleHash(ByteArrayRandomAccessSource(bytes2))

        assertNotEquals(hash1, hash2)
    }

    @Test
    fun insensitivityToMiddleByteChangeForLargeFiles() = runTest(testDispatcher) {
        val bytes1 = ByteArray(200_000) { (it % 251).toByte() }
        val bytes2 = bytes1.copyOf()

        // Middle region is [65536, 134464). Mutate boundaries and midpoint of this region.
        bytes2[65_536] = (bytes2[65_536] + 1).toByte()
        bytes2[100_000] = (bytes2[100_000] + 1).toByte()
        bytes2[134_463] = (bytes2[134_463] + 1).toByte()

        val hash1 = hasher.computeLocalSampleHash(ByteArrayRandomAccessSource(bytes1))
        val hash2 = hasher.computeLocalSampleHash(ByteArrayRandomAccessSource(bytes2))

        assertEquals(hash1, hash2)
    }

    @Test
    fun readFullyAtHandlesPartialChunksProperly() = runTest(testDispatcher) {
        val largeBytes = ByteArray(200_000) { (it % 251).toByte() }
        val sourceFull = ByteArrayRandomAccessSource(largeBytes)
        val sourceChunked = ByteArrayRandomAccessSource(largeBytes, maxBytesPerRead = 4096)

        val hashFull = hasher.computeLocalSampleHash(sourceFull)
        val hashChunked = hasher.computeLocalSampleHash(sourceChunked)

        assertEquals(hashFull, hashChunked)

        val smallBytes = ByteArray(10_000) { (it % 251).toByte() }
        val smallFull = ByteArrayRandomAccessSource(smallBytes)
        val smallChunked = ByteArrayRandomAccessSource(smallBytes, maxBytesPerRead = 1024)

        val smallHashFull = hasher.computeLocalSampleHash(smallFull)
        val smallHashChunked = hasher.computeLocalSampleHash(smallChunked)

        assertEquals(smallHashFull, smallHashChunked)
    }
}
