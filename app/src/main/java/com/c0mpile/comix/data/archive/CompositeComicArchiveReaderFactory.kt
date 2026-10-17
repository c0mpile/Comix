package com.c0mpile.comix.data.archive

import com.c0mpile.comix.data.source.RandomAccessSource
import com.c0mpile.comix.domain.model.ComicFormat
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Composite [ComicArchiveReaderFactory] that delegates to format-specific reader factories.
 */
@Singleton
class CompositeComicArchiveReaderFactory @Inject constructor(
    private val cbzFactory: CbzArchiveReaderFactory,
    private val pdfFactory: PdfArchiveReaderFactory
) : ComicArchiveReaderFactory {

    override suspend fun create(
        source: RandomAccessSource,
        format: ComicFormat,
        fileName: String?
    ): ComicArchiveReader {
        return when (format) {
            ComicFormat.CBZ -> cbzFactory.create(source, format, fileName)
            ComicFormat.PDF -> pdfFactory.create(source, format, fileName)
            else -> {
                source.close()
                throw CorruptArchiveException(fileName, "Unsupported comic format: $format")
            }
        }
    }
}
