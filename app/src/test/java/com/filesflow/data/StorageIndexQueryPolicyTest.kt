package com.filesflow.data

import org.junit.Assert.assertEquals
import org.junit.Test

class StorageIndexQueryPolicyTest {
    @Test
    fun `normalizes search using stable lowercase and trimming`() {
        assertEquals("résumé 2026", StorageIndexQueryPolicy.normalizeSearchQuery("  RÉSUMÉ 2026  "))
    }

    @Test
    fun `escapes sqlite percent wildcard`() {
        assertEquals("%100\\%%", StorageIndexQueryPolicy.containsPattern("100%"))
    }

    @Test
    fun `escapes sqlite underscore wildcard`() {
        assertEquals("%draft\\_final%", StorageIndexQueryPolicy.containsPattern("draft_final"))
    }

    @Test
    fun `escapes sqlite escape character`() {
        assertEquals("%folder\\\\file%", StorageIndexQueryPolicy.containsPattern("folder\\file"))
    }

    @Test
    fun `bounds category query limits`() {
        assertEquals(1, StorageIndexQueryPolicy.boundedLimit(0, StorageIndexQueryPolicy.CATEGORY_RESULT_LIMIT))
        assertEquals(42, StorageIndexQueryPolicy.boundedLimit(42, StorageIndexQueryPolicy.CATEGORY_RESULT_LIMIT))
        assertEquals(
            StorageIndexQueryPolicy.CATEGORY_RESULT_LIMIT,
            StorageIndexQueryPolicy.boundedLimit(Int.MAX_VALUE, StorageIndexQueryPolicy.CATEGORY_RESULT_LIMIT),
        )
    }

    @Test
    fun `bounds search query limits independently`() {
        assertEquals(
            StorageIndexQueryPolicy.SEARCH_RESULT_LIMIT,
            StorageIndexQueryPolicy.boundedLimit(500, StorageIndexQueryPolicy.SEARCH_RESULT_LIMIT),
        )
    }
}
