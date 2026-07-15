package com.filesflow.transfer

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.SecureRandom

internal object LanTransferProtocol {
    const val DEFAULT_SESSION_MILLIS = 10 * 60 * 1000L
    const val MAX_REQUEST_LINE_BYTES = 8 * 1024
    private const val TOKEN_BYTES = 24
    private val secureRandom = SecureRandom()

    fun newToken(): String {
        val bytes = ByteArray(TOKEN_BYTES).also(secureRandom::nextBytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun landingPath(token: String): String = "/t/$token"

    fun itemPath(token: String, index: Int): String = "/t/$token/$index"

    fun encodedFileName(name: String): String = URLEncoder.encode(name, StandardCharsets.UTF_8.name())
        .replace("+", "%20")

    fun isLandingRequestPath(path: String, token: String): Boolean {
        val requestPath = path.substringBefore('?').removeSuffix("/")
        return requestPath == landingPath(token)
    }

    fun isValidRequestPath(path: String, token: String, itemCount: Int): Int? {
        val prefix = "/t/$token/"
        if (!path.startsWith(prefix)) return null
        val index = path.removePrefix(prefix).substringBefore('?').toIntOrNull() ?: return null
        return index.takeIf { it in 0 until itemCount }
    }

    fun safeHeaderFileName(name: String): String = name
        .replace("\r", "_")
        .replace("\n", "_")
        .replace("\"", "'")
        .ifBlank { "download" }
}
