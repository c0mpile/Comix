package com.c0mpile.comix.data.archive

import com.c0mpile.comix.domain.model.ComicMetadata
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Node

/**
 * Pure Kotlin / JVM parser for ComicRack-compatible ComicInfo.xml metadata files.
 */
object ComicInfoXmlParser {

    /**
     * Parses the given [xmlBytes] into a [ComicMetadata] instance.
     *
     * Returns null if the XML is malformed, not a ComicInfo document, or contains no usable metadata fields.
     */
    fun parse(xmlBytes: ByteArray): ComicMetadata? {
        if (xmlBytes.isEmpty()) return null
        return runCatching {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
                runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
                runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
                runCatching { isXIncludeAware = false }
                runCatching { isExpandEntityReferences = false }
            }
            val builder = factory.newDocumentBuilder()
            val doc = builder.parse(ByteArrayInputStream(xmlBytes))
            val root = doc.documentElement ?: return null
            if (!root.nodeName.equals("ComicInfo", ignoreCase = true)) {
                return null
            }

            var title: String? = null
            var series: String? = null
            var number: String? = null
            var volume: Int? = null
            var summary: String? = null
            var writer: String? = null
            var penciller: String? = null
            var publisher: String? = null
            var genre: String? = null
            var releaseYear: Int? = null
            var releaseMonth: Int? = null
            var releaseDay: Int? = null

            val childNodes = root.childNodes
            for (i in 0 until childNodes.length) {
                val node = childNodes.item(i)
                if (node.nodeType == Node.ELEMENT_NODE) {
                    val text = node.textContent?.trim()?.takeIf { it.isNotEmpty() }
                    when (node.nodeName.lowercase()) {
                        "title" -> title = text
                        "series" -> series = text
                        "number" -> number = text
                        "volume" -> volume = text?.toIntOrNull()?.takeIf { it > 0 }
                        "summary" -> summary = text
                        "writer" -> writer = text
                        "penciller" -> penciller = text
                        "publisher" -> publisher = text
                        "genre" -> genre = text
                        "year" -> releaseYear = text?.toIntOrNull()?.takeIf { it > 0 }
                        "month" -> releaseMonth = text?.toIntOrNull()?.takeIf { it in 1..12 }
                        "day" -> releaseDay = text?.toIntOrNull()?.takeIf { it in 1..31 }
                    }
                }
            }

            val metadata = ComicMetadata(
                title = title,
                series = series,
                number = number,
                volume = volume,
                summary = summary,
                writer = writer,
                penciller = penciller,
                publisher = publisher,
                genre = genre,
                releaseYear = releaseYear,
                releaseMonth = releaseMonth,
                releaseDay = releaseDay
            )

            metadata.takeIf { it.hasAnyMetadata }
        }.getOrNull()
    }
}
