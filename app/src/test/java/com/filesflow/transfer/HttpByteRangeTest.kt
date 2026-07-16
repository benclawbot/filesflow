package com.filesflow.transfer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HttpByteRangeTest {
    @Test
    fun parsesClosedOpenAndSuffixRanges() {
        assertEquals(HttpByteRange(2, 5), HttpByteRange.parse("bytes=2-5", 10))
        assertEquals(HttpByteRange(7, 9), HttpByteRange.parse("bytes=7-", 10))
        assertEquals(HttpByteRange(6, 9), HttpByteRange.parse("bytes=-4", 10))
    }

    @Test
    fun clampsEndAndSuffixToResourceLength() {
        assertEquals(HttpByteRange(2, 9), HttpByteRange.parse("bytes=2-99", 10))
        assertEquals(HttpByteRange(0, 9), HttpByteRange.parse("bytes=-99", 10))
    }

    @Test
    fun rejectsMalformedMultipartAndUnsatisfiableRanges() {
        assertNull(HttpByteRange.parse("items=0-1", 10))
        assertNull(HttpByteRange.parse("bytes=0-1,4-5", 10))
        assertNull(HttpByteRange.parse("bytes=10-", 10))
        assertNull(HttpByteRange.parse("bytes=5-2", 10))
        assertNull(HttpByteRange.parse("bytes=-0", 10))
        assertNull(HttpByteRange.parse("bytes=abc-def", 10))
    }

    @Test
    fun missingRangeMeansFullResponse() {
        assertNull(HttpByteRange.parse(null, 10))
        assertNull(HttpByteRange.parse("", 10))
    }
}
