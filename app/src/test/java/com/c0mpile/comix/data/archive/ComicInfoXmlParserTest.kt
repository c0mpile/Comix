package com.c0mpile.comix.data.archive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for [ComicInfoXmlParser] verifying extraction of ComicRack ComicInfo.xml metadata.
 */
class ComicInfoXmlParserTest {

    @Test
    fun parseCompleteComicInfoXmlPopulatesAllFieldsAndFormattedProperties() {
        val xml = """
            <?xml version="1.0" encoding="utf-8"?>
            <ComicInfo xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:xsd="http://www.w3.org/2001/XMLSchema">
                <Title>Chapter One</Title>
                <Series>Saga</Series>
                <Number>1</Number>
                <Volume>2</Volume>
                <Summary>Two soldiers from opposite sides fall in love.</Summary>
                <Writer>Brian K. Vaughan</Writer>
                <Penciller>Fiona Staples</Penciller>
                <Publisher>Image Comics</Publisher>
                <Genre>Space Opera</Genre>
                <Year>2012</Year>
                <Month>3</Month>
                <Day>14</Day>
            </ComicInfo>
        """.trimIndent().toByteArray(Charsets.UTF_8)

        val metadata = ComicInfoXmlParser.parse(xml)

        assertNotNull(metadata)
        assertEquals("Chapter One", metadata?.title)
        assertEquals("Saga", metadata?.series)
        assertEquals("1", metadata?.number)
        assertEquals(2, metadata?.volume)
        assertEquals("Two soldiers from opposite sides fall in love.", metadata?.summary)
        assertEquals("Brian K. Vaughan", metadata?.writer)
        assertEquals("Fiona Staples", metadata?.penciller)
        assertEquals("Image Comics", metadata?.publisher)
        assertEquals("Space Opera", metadata?.genre)
        assertEquals(2012, metadata?.releaseYear)
        assertEquals(3, metadata?.releaseMonth)
        assertEquals(14, metadata?.releaseDay)
        assertEquals("Saga Vol. 2 #1", metadata?.formattedSeriesAndNumber)
        assertEquals("2012-03-14", metadata?.formattedReleaseDate)
    }

    @Test
    fun parseCaseInsensitiveTagsExtractsValues() {
        val xml = """
            <comicinfo>
                <title>Chapter 1</title>
                <SERIES>Attack on Titan</SERIES>
                <number>1</number>
                <WRITER>Hajime Isayama</WRITER>
                <penciller>Hajime Isayama</penciller>
                <PUBLISHER>Kodansha</PUBLISHER>
                <year>2009</year>
            </comicinfo>
        """.trimIndent().toByteArray(Charsets.UTF_8)

        val metadata = ComicInfoXmlParser.parse(xml)

        assertNotNull(metadata)
        assertEquals("Chapter 1", metadata?.title)
        assertEquals("Attack on Titan", metadata?.series)
        assertEquals("1", metadata?.number)
        assertEquals("Hajime Isayama", metadata?.writer)
        assertEquals("Hajime Isayama", metadata?.penciller)
        assertEquals("Kodansha", metadata?.publisher)
        assertEquals(2009, metadata?.releaseYear)
        assertEquals("Attack on Titan #1", metadata?.formattedSeriesAndNumber)
        assertEquals("2009", metadata?.formattedReleaseDate)
    }

    @Test
    fun parseEmptyAndMalformedXmlReturnsNull() {
        assertNull(ComicInfoXmlParser.parse(ByteArray(0)))
        assertNull(ComicInfoXmlParser.parse("Not XML at all".toByteArray(Charsets.UTF_8)))
        assertNull(ComicInfoXmlParser.parse("<ComicInfo><broken".toByteArray(Charsets.UTF_8)))
        assertNull(ComicInfoXmlParser.parse("<OtherDocument><Title>Valid</Title></OtherDocument>".toByteArray(Charsets.UTF_8)))
    }

    @Test
    fun parseBlankAndEmptyElementsReturnsNull() {
        val xml = """
            <ComicInfo>
                <Title>   </Title>
                <Series></Series>
                <Writer>
                </Writer>
            </ComicInfo>
        """.trimIndent().toByteArray(Charsets.UTF_8)

        val metadata = ComicInfoXmlParser.parse(xml)
        assertNull(metadata)
    }

    @Test
    fun partialDateFormatsYearAndMonthCorrectly() {
        val xmlYearMonth = """
            <ComicInfo>
                <Series>Descender</Series>
                <Year>2015</Year>
                <Month>3</Month>
            </ComicInfo>
        """.trimIndent().toByteArray(Charsets.UTF_8)

        val metadata = ComicInfoXmlParser.parse(xmlYearMonth)
        assertNotNull(metadata)
        assertEquals("2015-03", metadata?.formattedReleaseDate)

        val xmlYearOnly = """
            <ComicInfo>
                <Series>Descender</Series>
                <Year>2015</Year>
            </ComicInfo>
        """.trimIndent().toByteArray(Charsets.UTF_8)

        val metadataYear = ComicInfoXmlParser.parse(xmlYearOnly)
        assertNotNull(metadataYear)
        assertEquals("2015", metadataYear?.formattedReleaseDate)
    }
}
