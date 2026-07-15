package com.filesflow.transfer

import android.content.Context
import com.filesflow.features.home.FilesFlowFile
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Small, dependency-free HTTP sender for trusted local networks.
 *
 * Sessions use a cryptographically random path token, expire automatically, serve only explicit
 * files selected by the user, and never expose directory traversal or arbitrary filesystem paths.
 */
class LanTransferServer(context: Context) : AutoCloseable {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val running = AtomicBoolean(false)
    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null
    private var expiresAtMillis: Long = 0L
    private var token: String = ""
    private var items: List<FilesFlowFile> = emptyList()

    data class SharedItem(
        val name: String,
        val sizeBytes: Long,
        val url: String,
    )

    data class Session(
        val expiresAtMillis: Long,
        val port: Int,
        val addresses: List<String>,
        val items: List<SharedItem>,
    )

    @Synchronized
    fun start(
        files: List<FilesFlowFile>,
        lifetimeMillis: Long = LanTransferProtocol.DEFAULT_SESSION_MILLIS,
    ): Session {
        closeActiveSession()
        val shareable = files.filter { !it.isDirectory && (it.uri != null || it.path != null) }.distinctBy { it.id }
        require(shareable.isNotEmpty()) { "At least one readable file is required" }
        require(lifetimeMillis in 30_000L..60 * 60 * 1000L) { "Session lifetime must be between 30 seconds and 1 hour" }

        val socket = ServerSocket(0).apply { reuseAddress = true }
        serverSocket = socket
        token = LanTransferProtocol.newToken()
        items = shareable
        expiresAtMillis = System.currentTimeMillis() + lifetimeMillis
        running.set(true)
        acceptJob = scope.launch { acceptLoop(socket) }

        val addresses = localIpv4Addresses().map { "http://$it:${socket.localPort}" }
        val baseAddresses = addresses.ifEmpty { listOf("http://127.0.0.1:${socket.localPort}") }
        return Session(
            expiresAtMillis = expiresAtMillis,
            port = socket.localPort,
            addresses = baseAddresses,
            items = shareable.mapIndexed { index, file ->
                SharedItem(
                    name = file.name,
                    sizeBytes = file.sizeBytes,
                    url = baseAddresses.first() + LanTransferProtocol.itemPath(token, index),
                )
            },
        )
    }

    override fun close() {
        closeActiveSession()
        scope.cancel()
    }

    @Synchronized
    fun stop() = closeActiveSession()

    private fun closeActiveSession() {
        running.set(false)
        runCatching { serverSocket?.close() }
        acceptJob?.cancel()
        acceptJob = null
        serverSocket = null
        items = emptyList()
        token = ""
        expiresAtMillis = 0L
    }

    private suspend fun acceptLoop(socket: ServerSocket) {
        while (scope.isActive && running.get()) {
            if (System.currentTimeMillis() >= expiresAtMillis) {
                stop()
                break
            }
            val client = runCatching { socket.accept() }.getOrNull() ?: break
            scope.launch { handle(client) }
        }
    }

    private fun handle(socket: Socket) {
        socket.use { client ->
            client.soTimeout = 15_000
            val input = BufferedInputStream(client.getInputStream())
            val output = BufferedOutputStream(client.getOutputStream())
            val requestLine = readRequestLine(input) ?: return
            val parts = requestLine.split(' ')
            if (parts.size < 2 || parts[0] != "GET") {
                writeText(output, 405, "Method Not Allowed")
                return
            }
            if (!running.get() || System.currentTimeMillis() >= expiresAtMillis) {
                writeText(output, 410, "Transfer session expired")
                return
            }
            val index = LanTransferProtocol.isValidRequestPath(parts[1], token, items.size)
            if (index == null) {
                writeText(output, 404, "Not Found")
                return
            }
            val file = items[index]
            val stream = openInput(file)
            if (stream == null) {
                writeText(output, 404, "File unavailable")
                return
            }
            stream.use { source ->
                val length = contentLength(file)
                if (length < 0L) {
                    writeText(output, 500, "Unable to determine file size")
                    return
                }
                val safeName = LanTransferProtocol.safeHeaderFileName(file.name)
                val encodedName = LanTransferProtocol.encodedFileName(file.name)
                val headers = buildString {
                    append("HTTP/1.1 200 OK\r\n")
                    append("Content-Type: ${file.mimeType ?: "application/octet-stream"}\r\n")
                    append("Content-Length: $length\r\n")
                    append("Content-Disposition: attachment; filename=\"$safeName\"; filename*=UTF-8''$encodedName\r\n")
                    append("Cache-Control: no-store\r\n")
                    append("X-Content-Type-Options: nosniff\r\n")
                    append("Connection: close\r\n\r\n")
                }
                output.write(headers.toByteArray(StandardCharsets.US_ASCII))
                source.copyTo(output)
                output.flush()
            }
        }
    }

    private fun readRequestLine(input: BufferedInputStream): String? {
        val bytes = ArrayList<Byte>()
        while (bytes.size < LanTransferProtocol.MAX_REQUEST_LINE_BYTES) {
            val value = input.read()
            if (value == -1) return null
            if (value == '\n'.code) break
            if (value != '\r'.code) bytes += value.toByte()
        }
        if (bytes.size >= LanTransferProtocol.MAX_REQUEST_LINE_BYTES) return null
        return bytes.toByteArray().toString(StandardCharsets.US_ASCII)
    }

    private fun openInput(file: FilesFlowFile) = runCatching {
        when {
            file.uri != null -> appContext.contentResolver.openInputStream(file.uri)
            file.path != null -> File(file.path).takeIf { it.isFile && it.canRead() }?.inputStream()
            else -> null
        }
    }.getOrNull()

    private fun contentLength(file: FilesFlowFile): Long {
        if (file.sizeBytes > 0L) return file.sizeBytes
        return when {
            file.path != null -> File(file.path).takeIf { it.isFile }?.length() ?: -1L
            file.uri != null -> appContext.contentResolver.openAssetFileDescriptor(file.uri, "r")?.use { it.length } ?: -1L
            else -> -1L
        }
    }

    private fun writeText(output: BufferedOutputStream, status: Int, message: String) {
        val body = message.toByteArray(StandardCharsets.UTF_8)
        val reason = when (status) {
            404 -> "Not Found"
            405 -> "Method Not Allowed"
            410 -> "Gone"
            else -> "Internal Server Error"
        }
        output.write(
            "HTTP/1.1 $status $reason\r\nContent-Type: text/plain; charset=utf-8\r\nContent-Length: ${body.size}\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n"
                .toByteArray(StandardCharsets.US_ASCII),
        )
        output.write(body)
        output.flush()
    }

    private fun localIpv4Addresses(): List<String> {
        val interfaces = runCatching { Collections.list(NetworkInterface.getNetworkInterfaces()) }.getOrDefault(emptyList())
        return interfaces
            .filter { runCatching { it.isUp && !it.isLoopback }.getOrDefault(false) }
            .flatMap { Collections.list(it.inetAddresses) }
            .filterIsInstance<Inet4Address>()
            .filter { !it.isLoopbackAddress && !it.isLinkLocalAddress }
            .mapNotNull { it.hostAddress }
            .distinct()
            .sorted()
    }
}
