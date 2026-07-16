package com.filesflow.transfer

internal data class HttpByteRange(
    val start: Long,
    val endInclusive: Long,
) {
    val length: Long get() = endInclusive - start + 1L

    companion object {
        fun parse(headerValue: String?, resourceLength: Long): HttpByteRange? {
            if (headerValue.isNullOrBlank()) return null
            if (resourceLength <= 0L) return null
            if (!headerValue.startsWith("bytes=")) return null

            val specification = headerValue.removePrefix("bytes=").trim()
            if (specification.isEmpty() || ',' in specification) return null

            val separator = specification.indexOf('-')
            if (separator < 0) return null

            val startText = specification.substring(0, separator).trim()
            val endText = specification.substring(separator + 1).trim()

            if (startText.isEmpty()) {
                val suffixLength = endText.toLongOrNull() ?: return null
                if (suffixLength <= 0L) return null
                val actualLength = suffixLength.coerceAtMost(resourceLength)
                return HttpByteRange(resourceLength - actualLength, resourceLength - 1L)
            }

            val start = startText.toLongOrNull() ?: return null
            if (start < 0L || start >= resourceLength) return null

            val requestedEnd = if (endText.isEmpty()) {
                resourceLength - 1L
            } else {
                endText.toLongOrNull() ?: return null
            }
            if (requestedEnd < start) return null

            return HttpByteRange(start, requestedEnd.coerceAtMost(resourceLength - 1L))
        }
    }
}
