package com.filesflow.transfer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.filesflow.features.home.FileSource
import com.filesflow.features.home.FilesFlowFile
import com.filesflow.ui.theme.FilesFlowTheme
import java.util.concurrent.TimeUnit

class LanTransferActivity : ComponentActivity() {
    private var server: LanTransferServer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val files = readFiles(intent)
        setContent {
            FilesFlowTheme {
                var session by remember { mutableStateOf<LanTransferServer.Session?>(null) }
                var error by remember { mutableStateOf<String?>(null) }
                val transferServer = remember { LanTransferServer(this) }
                server = transferServer

                DisposableEffect(Unit) {
                    runCatching { transferServer.start(files) }
                        .onSuccess { session = it }
                        .onFailure { error = it.localizedMessage ?: "Unable to start local transfer" }
                    onDispose { transferServer.close() }
                }

                Surface(modifier = Modifier.fillMaxSize()) {
                    TransferScreen(
                        session = session,
                        error = error,
                        onCopy = { copyText(sessionText(it)) },
                        onShare = { shareText(sessionText(it)) },
                        onStop = {
                            transferServer.stop()
                            finish()
                        },
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        server?.close()
        server = null
        super.onDestroy()
    }

    private fun copyText(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("FilesFlow LAN transfer", text))
    }

    private fun shareText(text: String) {
        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                },
                "Share transfer link",
            ),
        )
    }

    private fun sessionText(session: LanTransferServer.Session): String = buildString {
        appendLine("FilesFlow local transfer")
        appendLine("Open this link while both devices are on the same Wi-Fi network:")
        appendLine(session.landingUrl)
        append("The link expires in 10 minutes.")
    }

    private fun readFiles(intent: Intent): List<FilesFlowFile> {
        val uris = intent.getStringArrayListExtra(EXTRA_URIS).orEmpty()
        val names = intent.getStringArrayListExtra(EXTRA_NAMES).orEmpty()
        val mimeTypes = intent.getStringArrayListExtra(EXTRA_MIME_TYPES).orEmpty()
        val sizes = intent.getLongArrayExtra(EXTRA_SIZES) ?: LongArray(0)
        return uris.mapIndexedNotNull { index, value ->
            val uri = runCatching { Uri.parse(value) }.getOrNull() ?: return@mapIndexedNotNull null
            FilesFlowFile(
                id = "lan-$index-$value",
                name = names.getOrNull(index).orEmpty().ifBlank { "download-$index" },
                metadata = "LAN transfer",
                uri = uri,
                path = null,
                mimeType = mimeTypes.getOrNull(index)?.takeIf { it.isNotBlank() },
                sizeBytes = sizes.getOrElse(index) { 0L },
                modifiedAtMillis = 0L,
                source = FileSource.Saf,
                isDirectory = false,
            )
        }
    }

    companion object {
        const val EXTRA_URIS = "com.filesflow.transfer.URIS"
        const val EXTRA_NAMES = "com.filesflow.transfer.NAMES"
        const val EXTRA_MIME_TYPES = "com.filesflow.transfer.MIME_TYPES"
        const val EXTRA_SIZES = "com.filesflow.transfer.SIZES"
    }
}

@androidx.compose.runtime.Composable
private fun TransferScreen(
    session: LanTransferServer.Session?,
    error: String?,
    onCopy: (LanTransferServer.Session) -> Unit,
    onShare: (LanTransferServer.Session) -> Unit,
    onStop: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("Send with FilesFlow", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Keep this screen open and connect the receiving device to the same Wi-Fi network.")

        when {
            error != null -> Text(error, color = MaterialTheme.colorScheme.error)
            session == null -> Text("Starting secure local transfer…")
            else -> {
                val minutes = TimeUnit.MILLISECONDS.toMinutes((session.expiresAtMillis - System.currentTimeMillis()).coerceAtLeast(0L))
                Text("${session.items.size} file${if (session.items.size == 1) "" else "s"} available for about $minutes minutes.")
                Text("Open this single link on the receiving device:", fontWeight = FontWeight.SemiBold)
                Text(session.landingUrl, style = MaterialTheme.typography.bodySmall)
                session.items.forEach { item ->
                    Text(item.name, fontWeight = FontWeight.SemiBold)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(modifier = Modifier.weight(1f), onClick = { onCopy(session) }) { Text("Copy link") }
                    Button(modifier = Modifier.weight(1f), onClick = { onShare(session) }) { Text("Share link") }
                }
            }
        }

        OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onStop) {
            Text(if (session == null && error == null) "Cancel" else "Stop transfer")
        }
    }
}
