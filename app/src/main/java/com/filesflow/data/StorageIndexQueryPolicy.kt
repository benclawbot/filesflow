package com.filesflow.data

import java.util.Locale

/** Pure query rules shared by the durable storage index and JVM tests. */
internal object StorageIndexQueryPolicy {
    const val CATEGORY_RESULT_LIMIT = 1_000
    const val SEARCH_RESULT_LIMIT = 250

    fun normalizeSearchQuery(value: String): String = value.trim().lowercase(Locale.US)

    fun containsPattern(normalizedQuery: String): String = "%${escapeLike(normalizedQuery)}%"

    fun boundedLimit(requested: Int, maximum: Int): Int = requested.coerceIn(1, maximum)

    private fun escapeLike(value: String): String = buildString(value.length) {
        value.forEach { character ->
            when (character) {
                '\\', '%', '_' -> append('\\')
            }
            append(character)
        }
    }
}
