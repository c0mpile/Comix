package com.c0mpile.comix.data.archive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [NaturalOrderComparator].
 */
class NaturalOrderComparatorTest {

    @Test
    fun naturalAlphanumericOrderingForPages() {
        assertTrue(NaturalOrderComparator.compare("page2.jpg", "page10.jpg") < 0)
        assertTrue(NaturalOrderComparator.compare("page10.jpg", "page2.jpg") > 0)
        assertEquals(0, NaturalOrderComparator.compare("page2.jpg", "page2.jpg"))

        val input = listOf("page10.jpg", "page2.jpg", "page1.jpg", "page20.jpg")
        val expected = listOf("page1.jpg", "page2.jpg", "page10.jpg", "page20.jpg")
        assertEquals(expected, input.sortedWith(NaturalOrderComparator))
    }

    @Test
    fun naturalOrderingWithLeadingZeroes() {
        val input = listOf("10.png", "01.png", "02.png")
        val expected = listOf("01.png", "02.png", "10.png")
        assertEquals(expected, input.sortedWith(NaturalOrderComparator))
    }

    @Test
    fun leadingZeroesTieBreakingIsDeterministic() {
        assertTrue(NaturalOrderComparator.compare("1.jpg", "01.jpg") < 0)
        assertTrue(NaturalOrderComparator.compare("01.jpg", "001.jpg") < 0)
        assertTrue(NaturalOrderComparator.compare("1.jpg", "001.jpg") < 0)

        val input = listOf("001.jpg", "1.jpg", "01.jpg")
        val expected = listOf("1.jpg", "01.jpg", "001.jpg")
        assertEquals(expected, input.sortedWith(NaturalOrderComparator))
    }

    @Test
    fun nestedPathsOrdering() {
        val input = listOf("ch2/page1.jpg", "ch1/page10.jpg", "ch1/page2.jpg")
        val expected = listOf("ch1/page2.jpg", "ch1/page10.jpg", "ch2/page1.jpg")
        assertEquals(expected, input.sortedWith(NaturalOrderComparator))

        val multiLevelInput = listOf("vol2/ch1/01.jpg", "vol1/ch10/01.jpg", "vol1/ch2/03.jpg")
        val multiLevelExpected = listOf("vol1/ch2/03.jpg", "vol1/ch10/01.jpg", "vol2/ch1/01.jpg")
        assertEquals(multiLevelExpected, multiLevelInput.sortedWith(NaturalOrderComparator))
    }

    @Test
    fun caseInsensitiveComparisonWithDeterministicTieBreaking() {
        assertTrue(NaturalOrderComparator.compare("PAGE2.jpg", "page10.jpg") < 0)
        assertTrue(NaturalOrderComparator.compare("page2.jpg", "PAGE10.jpg") < 0)
        assertTrue(NaturalOrderComparator.compare("PAGE1.jpg", "page2.jpg") < 0)

        // Tie-breaker: uppercase 'P' (ASCII 80) precedes lowercase 'p' (ASCII 112)
        assertTrue(NaturalOrderComparator.compare("Page1.jpg", "page1.jpg") < 0)
        assertTrue(NaturalOrderComparator.compare("page1.jpg", "Page1.jpg") > 0)
    }

    @Test
    fun handlesNullAndIdenticalStrings() {
        assertEquals(0, NaturalOrderComparator.compare(null, null))
        assertTrue(NaturalOrderComparator.compare(null, "page.jpg") < 0)
        assertTrue(NaturalOrderComparator.compare("page.jpg", null) > 0)
        assertEquals(0, NaturalOrderComparator.compare("page.jpg", "page.jpg"))
    }
}
