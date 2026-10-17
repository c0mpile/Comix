package com.c0mpile.comix.data.archive

import com.c0mpile.comix.data.source.RandomAccessSource
import com.c0mpile.comix.data.source.readFullyAt
import com.c0mpile.comix.di.IoDispatcher
import com.c0mpile.comix.domain.model.ComicFormat
import com.c0mpile.comix.domain.model.ComicMetadata
import com.c0mpile.comix.domain.model.ComicPage
import java.io.IOException
import java.util.zip.DataFormatException
import java.util.zip.Inflater
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * ComicArchiveReader implementation for CBZ (ZIP) archives.
 *
 * Operates exclusively through [RandomAccessSource] without sequential stream scans.
 */
class CbzArchiveReader private constructor(
    private val source: RandomAccessSource,
    private val fileName: String?,
    private val pages: List<ComicPage>,
    private val entryMetadata: List<ZipEntryInternal>,
    private val ioDispatcher: CoroutineDispatcher,
    private val comicInfoEntry: ZipEntryInternal? = null
) : ComicArchiveReader {

    override fun getPages(): List<ComicPage> = pages

    override suspend fun openPageBytes(pageIndex: Int): ByteArray = withContext(ioDispatcher) {
        if (pageIndex !in pages.indices) {
            throw IndexOutOfBoundsException("Page index $pageIndex out of bounds [0, ${pages.size})")
        }
        readEntryBytes(entryMetadata[pageIndex], MAX_UNCOMPRESSED_ENTRY_BYTES)
    }

    override suspend fun extractMetadata(): ComicMetadata? = withContext(ioDispatcher) {
        val entry = comicInfoEntry ?: return@withContext null
        if (entry.uncompressedSize <= 0L || entry.uncompressedSize > MAX_METADATA_ENTRY_BYTES) {
            return@withContext null
        }
        runCatching {
            ComicInfoXmlParser.parse(readEntryBytes(entry, MAX_METADATA_ENTRY_BYTES))
        }.getOrNull()
    }

    private suspend fun readEntryBytes(
        meta: ZipEntryInternal,
        maxUncompressedBytes: Long = MAX_UNCOMPRESSED_ENTRY_BYTES
    ): ByteArray = withContext(ioDispatcher) {
        if (meta.compressedSize < 0L || meta.uncompressedSize < 0L) {
            throw CorruptArchiveException(fileName, "Invalid entry sizes: compressed=${meta.compressedSize}, uncompressed=${meta.uncompressedSize}")
        }
        if (meta.uncompressedSize > maxUncompressedBytes) {
            throw CorruptArchiveException(fileName, "Uncompressed size ${meta.uncompressedSize} exceeds safety limit")
        }
        if (meta.localHeaderOffset < 0L || meta.localHeaderOffset + 30L > source.size()) {
            throw CorruptArchiveException(fileName, "Invalid local header offset ${meta.localHeaderOffset}")
        }

        val localHeader = ByteArray(30)
        source.readFullyAt(meta.localHeaderOffset, localHeader)
        val sig = localHeader.readIntLE(0)
        if (sig != LOCAL_FILE_HEADER_SIGNATURE) {
            throw CorruptArchiveException(
                fileName,
                "Invalid local header signature 0x${sig.toString(16)} at offset ${meta.localHeaderOffset}"
            )
        }

        val nameLength = localHeader.readShortLE(26)
        val extraLength = localHeader.readShortLE(28)
        val dataOffset = meta.localHeaderOffset + 30L + nameLength + extraLength

        if (dataOffset + meta.compressedSize > source.size()) {
            throw CorruptArchiveException(
                fileName,
                "Entry data at $dataOffset (size ${meta.compressedSize}) exceeds file size ${source.size()}"
            )
        }

        val compressedBytes = ByteArray(meta.compressedSize.toInt())
        source.readFullyAt(dataOffset, compressedBytes)

        when (meta.compressionMethod) {
            COMPRESSION_STORED -> {
                compressedBytes
            }
            COMPRESSION_DEFLATED -> {
                val targetSize = meta.uncompressedSize.toInt()
                val decompressedBytes = ByteArray(targetSize)
                val inflater = Inflater(true)
                try {
                    inflater.setInput(compressedBytes)
                    var bytesInflated = 0
                    while (!inflater.finished() && bytesInflated < targetSize) {
                        val count = inflater.inflate(decompressedBytes, bytesInflated, targetSize - bytesInflated)
                        if (count == 0) {
                            if (inflater.needsInput() || inflater.needsDictionary()) {
                                throw CorruptArchiveException(
                                    fileName,
                                    "Inflater needs more input or dictionary prematurely for ${meta.entryName}"
                                )
                            }
                            break
                        }
                        bytesInflated += count
                    }
                    if (bytesInflated != targetSize && !inflater.finished()) {
                        throw CorruptArchiveException(
                            fileName,
                            "Inflated size mismatch for ${meta.entryName}: expected $targetSize, got $bytesInflated"
                        )
                    }
                    decompressedBytes
                } catch (e: DataFormatException) {
                    throw CorruptArchiveException(
                        fileName,
                        "Decompression error for ${meta.entryName}: ${e.message}",
                        e
                    )
                } finally {
                    inflater.end()
                }
            }
            else -> {
                throw CorruptArchiveException(
                    fileName,
                    "Unsupported compression method ${meta.compressionMethod} for ${meta.entryName}"
                )
            }
        }
    }

    override fun close() {
        source.close()
    }

    companion object {
        private const val EOCD_SIGNATURE = 0x06054b50L
        private const val ZIP64_LOCATOR_SIGNATURE = 0x07064b50L
        private const val ZIP64_RECORD_SIGNATURE = 0x06064b50L
        private const val CENTRAL_DIR_SIGNATURE = 0x02014b50L
        private const val LOCAL_FILE_HEADER_SIGNATURE = 0x04034b50L

        private const val COMPRESSION_STORED = 0
        private const val COMPRESSION_DEFLATED = 8

        private const val MAX_UNCOMPRESSED_ENTRY_BYTES = 256L * 1024L * 1024L
        private const val MAX_METADATA_ENTRY_BYTES = 4L * 1024L * 1024L
        private const val MAX_CENTRAL_DIR_BYTES = 64L * 1024L * 1024L

        private val SUPPORTED_IMAGE_EXTENSIONS = setOf(
            "jpg", "jpeg", "png", "webp", "gif", "bmp"
        )

        /**
         * Parses the archive directory from [source] and returns an initialized [CbzArchiveReader].
         */
        suspend fun open(
            source: RandomAccessSource,
            fileName: String?,
            ioDispatcher: CoroutineDispatcher
        ): CbzArchiveReader = withContext(ioDispatcher) {
            val totalSize = source.size()
            if (totalSize <= 0L) {
                throw EmptyArchiveException(fileName)
            }
            if (totalSize < 22L) {
                throw CorruptArchiveException(fileName, "Archive is smaller than minimum ZIP EOCD (size: $totalSize)")
            }

            // Scan backward for EOCD record (maximum comment length 65535 + fixed EOCD 22)
            val maxScan = minOf(totalSize, 22L + 65535L).toInt()
            val scanOffset = totalSize - maxScan
            val scanBuffer = ByteArray(maxScan)
            source.readFullyAt(scanOffset, scanBuffer)

            var eocdBufferPos = -1
            for (i in maxScan - 22 downTo 0) {
                if (scanBuffer[i] == 0x50.toByte() &&
                    scanBuffer[i + 1] == 0x4b.toByte() &&
                    scanBuffer[i + 2] == 0x05.toByte() &&
                    scanBuffer[i + 3] == 0x06.toByte()
                ) {
                    eocdBufferPos = i
                    break
                }
            }

            if (eocdBufferPos < 0) {
                throw CorruptArchiveException(fileName, "End of Central Directory record not found")
            }

            val eocdAbsoluteOffset = scanOffset + eocdBufferPos
            var totalEntries = scanBuffer.readShortLE(eocdBufferPos + 10)
            var cdSize = scanBuffer.readIntLE(eocdBufferPos + 12)
            var cdOffset = scanBuffer.readIntLE(eocdBufferPos + 16)

            // Check for ZIP64 EOCD Locator and Record if 16/32-bit fields are saturated
            if (totalEntries == 0xFFFF || cdSize == 0xFFFFFFFFL || cdOffset == 0xFFFFFFFFL) {
                val locatorOffset = eocdAbsoluteOffset - 20L
                if (locatorOffset >= 0L) {
                    val locatorBuf = ByteArray(20)
                    source.readFullyAt(locatorOffset, locatorBuf)
                    val locatorSig = locatorBuf.readIntLE(0)
                    if (locatorSig == ZIP64_LOCATOR_SIGNATURE) {
                        val zip64RecordOffset = locatorBuf.readLongLE(8)
                        if (zip64RecordOffset in 0L until totalSize) {
                            val zip64RecordBuf = ByteArray(56)
                            source.readFullyAt(zip64RecordOffset, zip64RecordBuf)
                            val zip64Sig = zip64RecordBuf.readIntLE(0)
                            if (zip64Sig == ZIP64_RECORD_SIGNATURE) {
                                totalEntries = zip64RecordBuf.readLongLE(32).toInt()
                                cdSize = zip64RecordBuf.readLongLE(40)
                                cdOffset = zip64RecordBuf.readLongLE(48)
                            }
                        }
                    }
                }
            }

            if (cdOffset < 0L || cdSize < 0L || cdOffset + cdSize > totalSize) {
                throw CorruptArchiveException(
                    fileName,
                    "Invalid Central Directory bounds: offset=$cdOffset, size=$cdSize, totalSize=$totalSize"
                )
            }
            if (cdSize > MAX_CENTRAL_DIR_BYTES) {
                throw CorruptArchiveException(fileName, "Central Directory size $cdSize exceeds limit")
            }

            val cdBuffer = ByteArray(cdSize.toInt())
            source.readFullyAt(cdOffset, cdBuffer)

            var comicInfoEntry: ZipEntryInternal? = null
            val parsedEntries = mutableListOf<ZipEntryInternal>()
            var pos = 0
            while (pos + 46 <= cdBuffer.size) {
                val sig = cdBuffer.readIntLE(pos)
                if (sig != CENTRAL_DIR_SIGNATURE) {
                    break
                }

                val method = cdBuffer.readShortLE(pos + 10)
                var compressedSize = cdBuffer.readIntLE(pos + 20)
                var uncompressedSize = cdBuffer.readIntLE(pos + 24)
                val nameLength = cdBuffer.readShortLE(pos + 28)
                val extraLength = cdBuffer.readShortLE(pos + 30)
                val commentLength = cdBuffer.readShortLE(pos + 32)
                var localHeaderOffset = cdBuffer.readIntLE(pos + 42)

                val nameStart = pos + 46
                if (nameStart + nameLength > cdBuffer.size) {
                    throw CorruptArchiveException(fileName, "Central Directory entry name truncated")
                }
                val entryName = String(cdBuffer, nameStart, nameLength, Charsets.UTF_8)

                val extraStart = nameStart + nameLength
                val extraEnd = extraStart + extraLength
                if (extraEnd > cdBuffer.size) {
                    throw CorruptArchiveException(fileName, "Central Directory entry extra field truncated")
                }

                // Parse ZIP64 extra field if needed
                if (uncompressedSize == 0xFFFFFFFFL || compressedSize == 0xFFFFFFFFL || localHeaderOffset == 0xFFFFFFFFL) {
                    var extraPos = extraStart
                    while (extraPos + 4 <= extraEnd) {
                        val headerId = cdBuffer.readShortLE(extraPos)
                        val dataSize = cdBuffer.readShortLE(extraPos + 2)
                        extraPos += 4
                        if (extraPos + dataSize > extraEnd) break

                        if (headerId == 0x0001) {
                            var zip64Pos = extraPos
                            val zip64End = extraPos + dataSize
                            if (uncompressedSize == 0xFFFFFFFFL && zip64Pos + 8 <= zip64End) {
                                uncompressedSize = cdBuffer.readLongLE(zip64Pos)
                                zip64Pos += 8
                            }
                            if (compressedSize == 0xFFFFFFFFL && zip64Pos + 8 <= zip64End) {
                                compressedSize = cdBuffer.readLongLE(zip64Pos)
                                zip64Pos += 8
                            }
                            if (localHeaderOffset == 0xFFFFFFFFL && zip64Pos + 8 <= zip64End) {
                                localHeaderOffset = cdBuffer.readLongLE(zip64Pos)
                                zip64Pos += 8
                            }
                        }
                        extraPos += dataSize
                    }
                }

                pos = extraEnd + commentLength

                if (isValidImageEntry(entryName)) {
                    parsedEntries.add(
                        ZipEntryInternal(
                            entryName = entryName,
                            compressionMethod = method,
                            compressedSize = compressedSize,
                            uncompressedSize = uncompressedSize,
                            localHeaderOffset = localHeaderOffset
                        )
                    )
                } else if (comicInfoEntry == null && isComicInfoEntry(entryName)) {
                    comicInfoEntry = ZipEntryInternal(
                        entryName = entryName,
                        compressionMethod = method,
                        compressedSize = compressedSize,
                        uncompressedSize = uncompressedSize,
                        localHeaderOffset = localHeaderOffset
                    )
                }
            }

            if (parsedEntries.isEmpty()) {
                throw EmptyArchiveException(fileName)
            }

            parsedEntries.sortWith { a, b ->
                NaturalOrderComparator.compare(a.entryName, b.entryName)
            }

            val comicPages = parsedEntries.mapIndexed { index, entry ->
                ComicPage(
                    index = index,
                    entryName = entry.entryName,
                    compressedSize = entry.compressedSize,
                    uncompressedSize = entry.uncompressedSize
                )
            }

            CbzArchiveReader(
                source = source,
                fileName = fileName,
                pages = comicPages,
                entryMetadata = parsedEntries,
                ioDispatcher = ioDispatcher,
                comicInfoEntry = comicInfoEntry
            )
        }

        private fun isComicInfoEntry(entryName: String): Boolean {
            if (entryName.endsWith('/') || entryName.endsWith('\\')) {
                return false
            }
            if (entryName.startsWith("__MACOSX/") || entryName.contains("/__MACOSX/")) {
                return false
            }
            val segments = entryName.split('/', '\\')
            if (segments.any { it.startsWith('.') }) {
                return false
            }
            val simpleName = entryName.substringAfterLast('/').substringAfterLast('\\')
            return simpleName.equals("ComicInfo.xml", ignoreCase = true)
        }

        private fun isValidImageEntry(entryName: String): Boolean {
            if (entryName.endsWith('/') || entryName.endsWith('\\')) {
                return false
            }
            if (entryName.startsWith("__MACOSX/") || entryName.contains("/__MACOSX/")) {
                return false
            }
            val segments = entryName.split('/', '\\')
            if (segments.any { it.startsWith('.') }) {
                return false
            }
            val extension = entryName.substringAfterLast('.', "").lowercase()
            return extension in SUPPORTED_IMAGE_EXTENSIONS
        }
    }
}

private data class ZipEntryInternal(
    val entryName: String,
    val compressionMethod: Int,
    val compressedSize: Long,
    val uncompressedSize: Long,
    val localHeaderOffset: Long
)

/**
 * Factory for creating [CbzArchiveReader] instances.
 */
class CbzArchiveReaderFactory @Inject constructor(
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ComicArchiveReaderFactory {

    override suspend fun create(
        source: RandomAccessSource,
        format: ComicFormat,
        fileName: String?
    ): ComicArchiveReader = withContext(ioDispatcher) {
        if (format != ComicFormat.CBZ) {
            source.close()
            throw IllegalArgumentException("Unsupported archive format: $format")
        }
        try {
            CbzArchiveReader.open(source, fileName, ioDispatcher)
        } catch (e: Throwable) {
            try {
                source.close()
            } catch (closeEx: Exception) {
                e.addSuppressed(closeEx)
            }
            throw e
        }
    }
}

private fun ByteArray.readShortLE(offset: Int): Int {
    return (this[offset].toInt() and 0xFF) or
            ((this[offset + 1].toInt() and 0xFF) shl 8)
}

private fun ByteArray.readIntLE(offset: Int): Long {
    return (this[offset].toLong() and 0xFFL) or
            ((this[offset + 1].toLong() and 0xFFL) shl 8) or
            ((this[offset + 2].toLong() and 0xFFL) shl 16) or
            ((this[offset + 3].toLong() and 0xFFL) shl 24)
}

private fun ByteArray.readLongLE(offset: Int): Long {
    var result = 0L
    for (i in 0 until 8) {
        result = result or ((this[offset + i].toLong() and 0xFFL) shl (i * 8))
    }
    return result
}
