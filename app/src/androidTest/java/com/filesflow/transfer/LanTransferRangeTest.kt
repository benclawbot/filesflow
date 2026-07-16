package com.filesflow.transfer

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.filesflow.features.home.FileSource
import com.filesflow.features.home.FilesFlowFile
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LanTransferRangeTest {
    @Test
    fun serverReturnsExactPartialContentAndRejectsInvalidRange() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "lan-range-test.txt").apply {
            writeBytes("0123456789".toByteArray(Charsets.US_ASCII))
        }
        val sharedFile = FilesFlowFile(
            id = "range-test",
            name = file.name,
            metadata = "LAN range test",
            uri = null,
            path = file.absolutePath,
            mimeType = "text/plain",
            sizeBytes = file.length(),
            modifiedAtMillis = file.lastModified(),
            source = FileSource.Direct,
            isDirectory = false,
        )

        LanTransferServer(context).use { server ->
            val session = server.start(listOf(sharedFile))
            val advertisedUri = URI(session.items.single().url)
            val path = advertisedUri.rawPath

            val partial = request(session.port, path, "Range: bytes=2-5\r\n")
            assertTrue(partial.headers, partial.headers.startsWith("HTTP/1.1 206 Partial Content\r\n"))
            assertTrue(partial.headers, partial.headers.contains("Content-Range: bytes 2-5/10\r\n"))
            assertTrue(partial.headers, partial.headers.contains("Content-Length: 4\r\n"))
            assertTrue(partial.headers, partial.headers.contains("Accept-Ranges: bytes\r\n"))
            assertArrayEquals("2345".toByteArray(Charsets.US_ASCII), partial.body)

            val suffix = request(session.port, path, "Range: bytes=-3\r\n")
            assertTrue(suffix.headers, suffix.headers.contains("Content-Range: bytes 7-9/10\r\n"))
            assertArrayEquals("789".toByteArray(Charsets.US_ASCII), suffix.body)

            val invalid = request(session.port, path, "Range: bytes=20-30\r\n")
            assertTrue(invalid.headers, invalid.headers.startsWith("HTTP/1.1 416 Range Not Satisfiable\r\n"))
            assertTrue(invalid.headers, invalid.headers.contains("Content-Range: bytes */10\r\n"))
            assertEquals(0, invalid.body.size)
        }
    }

    private fun request(port: Int, path: String, additionalHeaders: String): Response {
        val rawResponse = Socket().use { socket ->
            socket.connect(InetSocketAddress("127.0.0.1", port), 5_000)
            socket.soTimeout = 5_000
            socket.getOutputStream().apply {
                write(
                    buildString {
                        append("GET $path HTTP/1.1\r\n")
                        append("Host: 127.0.0.1:$port\r\n")
                        append(additionalHeaders)
                        append("Connection: close\r\n\r\n")
                    }.toByteArray(Charsets.US_ASCII),
                )
                flush()
            }
            ByteArrayOutputStream().use { buffer ->
                socket.getInputStream().copyTo(buffer)
                buffer.toByteArray()
            }
        }
        val separator = rawResponse.indexOfSequence(HEADER_SEPARATOR)
        check(separator >= 0) { "HTTP response did not contain a header separator: ${rawResponse.toString(Charsets.US_ASCII)}" }
        return Response(
            headers = rawResponse.copyOfRange(0, separator + HEADER_SEPARATOR.size).toString(Charsets.US_ASCII),
            body = rawResponse.copyOfRange(separator + HEADER_SEPARATOR.size, rawResponse.size),
        )
    }

    private fun ByteArray.indexOfSequence(sequence: ByteArray): Int {
        if (sequence.isEmpty() || sequence.size > size) return -1
        for (start in 0..(size - sequence.size)) {
            var matches = true
            for (offset in sequence.indices) {
                if (this[start + offset] != sequence[offset]) {
                    matches = false
                    break
                }
            }
            if (matches) return start
        }
        return -1
    }

    private data class Response(
        val headers: String,
        val body: ByteArray,
    )

    private companion object {
        val HEADER_SEPARATOR = "\r\n\r\n".toByteArray(Charsets.US_ASCII)
    }
}
