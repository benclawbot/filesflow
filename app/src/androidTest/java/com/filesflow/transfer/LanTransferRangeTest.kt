package com.filesflow.transfer

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.filesflow.features.home.FileSource
import com.filesflow.features.home.FilesFlowFile
import java.io.File
import java.net.Socket
import java.net.URI
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
            writeText("0123456789")
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
            val testUri = URI("http", null, "127.0.0.1", session.port, advertisedUri.rawPath, null, null)

            val partial = request(testUri, "Range: bytes=2-5\r\n")
            assertTrue(partial.startsWith("HTTP/1.1 206 Partial Content\r\n"))
            assertTrue(partial.contains("Content-Range: bytes 2-5/10\r\n"))
            assertTrue(partial.contains("Content-Length: 4\r\n"))
            assertTrue(partial.contains("Accept-Ranges: bytes\r\n"))
            assertEquals("2345", partial.substringAfter("\r\n\r\n"))

            val suffix = request(testUri, "Range: bytes=-3\r\n")
            assertTrue(suffix.contains("Content-Range: bytes 7-9/10\r\n"))
            assertEquals("789", suffix.substringAfter("\r\n\r\n"))

            val invalid = request(testUri, "Range: bytes=20-30\r\n")
            assertTrue(invalid.startsWith("HTTP/1.1 416 Range Not Satisfiable\r\n"))
            assertTrue(invalid.contains("Content-Range: bytes */10\r\n"))
            assertEquals("", invalid.substringAfter("\r\n\r\n"))
        }
    }

    private fun request(uri: URI, additionalHeaders: String): String =
        Socket(uri.host, uri.port).use { socket ->
            socket.soTimeout = 5_000
            socket.getOutputStream().bufferedWriter(Charsets.US_ASCII).apply {
                write("GET ${uri.rawPath} HTTP/1.1\r\n")
                write("Host: ${uri.host}:${uri.port}\r\n")
                write(additionalHeaders)
                write("Connection: close\r\n\r\n")
                flush()
            }
            socket.getInputStream().readBytes().toString(Charsets.UTF_8)
        }
}
