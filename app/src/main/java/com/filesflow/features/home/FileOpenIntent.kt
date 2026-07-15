package com.filesflow.features.home

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import androidx.print.PrintHelper
import com.filesflow.transfer.LanTransferActivity
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

fun fileOpenIntent(context: Context, file: FilesFlowFile): Intent? {
    if (file.isDirectory) return null
    val uri = openableUri(context, file) ?: return null
    val mimeType = file.mimeType ?: mimeTypeFromName(file.name) ?: "application/octet-stream"

    return Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mimeType)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}

fun fileShareIntent(context: Context, files: List<FilesFlowFile>): Intent? {
    val shareableFiles = files
        .filterNot { it.isDirectory }
        .mapNotNull { file ->
            val uri = openableUri(context, file) ?: return@mapNotNull null
            ShareableFile(
                uri = uri,
                name = file.name,
                mimeType = file.mimeType ?: mimeTypeFromName(file.name) ?: "application/octet-stream",
                sizeBytes = file.sizeBytes,
            )
        }
        .distinctBy { it.uri }

    if (shareableFiles.isEmpty()) return null

    return Intent(context, LanTransferActivity::class.java).apply {
        putStringArrayListExtra(LanTransferActivity.EXTRA_URIS, ArrayList(shareableFiles.map { it.uri.toString() }))
        putStringArrayListExtra(LanTransferActivity.EXTRA_NAMES, ArrayList(shareableFiles.map { it.name }))
        putStringArrayListExtra(LanTransferActivity.EXTRA_MIME_TYPES, ArrayList(shareableFiles.map { it.mimeType }))
        putExtra(LanTransferActivity.EXTRA_SIZES, shareableFiles.map { it.sizeBytes }.toLongArray())
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        shareableFiles.forEach { clipFile ->
            clipData = (clipData ?: android.content.ClipData.newUri(context.contentResolver, clipFile.name, clipFile.uri)).also { data ->
                if (data.itemCount == 0 || data.getItemAt(0).uri != clipFile.uri) {
                    data.addItem(android.content.ClipData.Item(clipFile.uri))
                }
            }
        }
    }
}

fun isPrintableFile(file: FilesFlowFile): Boolean {
    if (file.isDirectory) return false
    val mimeType = file.mimeType ?: mimeTypeFromName(file.name).orEmpty()
    return mimeType.startsWith("image/") || mimeType.contains("pdf") || file.name.endsWith(".pdf", ignoreCase = true)
}

fun printFile(context: Context, file: FilesFlowFile): Boolean {
    if (!isPrintableFile(file)) return false
    val uri = openableUri(context, file) ?: return false
    val mimeType = file.mimeType ?: mimeTypeFromName(file.name) ?: "application/octet-stream"

    return runCatching {
        when {
            mimeType.startsWith("image/") -> {
                PrintHelper(context).apply {
                    scaleMode = PrintHelper.SCALE_MODE_FIT
                }.printBitmap(file.name, uri)
                true
            }
            mimeType.contains("pdf") || file.name.endsWith(".pdf", ignoreCase = true) -> {
                val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return@runCatching false
                printManager.print(
                    file.name,
                    PdfPassthroughPrintAdapter(context, uri, file.name),
                    PrintAttributes.Builder().build(),
                )
                true
            }
            else -> false
        }
    }.getOrDefault(false)
}

private fun openableUri(context: Context, file: FilesFlowFile): Uri? {
    file.uri?.let { return it }
    val path = file.path ?: return null
    val diskFile = File(path).takeIf { it.exists() && it.isFile } ?: return null
    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        diskFile,
    )
}

private fun mimeTypeFromName(name: String): String? {
    val extension = name.substringAfterLast('.', missingDelimiterValue = "")
        .lowercase(Locale.US)
        .takeIf { it.isNotBlank() }
    return extension?.let { MimeTypeMap.getSingleton().getMimeTypeFromExtension(it) }
}

private class PdfPassthroughPrintAdapter(
    private val context: Context,
    private val uri: Uri,
    private val jobName: String,
) : PrintDocumentAdapter() {
    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes?,
        cancellationSignal: CancellationSignal?,
        callback: LayoutResultCallback,
        extras: Bundle?,
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback.onLayoutCancelled()
            return
        }

        val info = PrintDocumentInfo.Builder(jobName)
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .build()
        callback.onLayoutFinished(info, true)
    }

    override fun onWrite(
        pages: Array<out PageRange>?,
        destination: ParcelFileDescriptor,
        cancellationSignal: CancellationSignal?,
        callback: WriteResultCallback,
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback.onWriteCancelled()
            return
        }

        runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destination.fileDescriptor).use { output ->
                    input.copyTo(output)
                }
            } ?: error("Cannot open file")
        }.onSuccess {
            if (cancellationSignal?.isCanceled == true) {
                callback.onWriteCancelled()
            } else {
                callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
            }
        }.onFailure { error ->
            callback.onWriteFailed(error.localizedMessage ?: "Print failed")
        }
    }
}

private data class ShareableFile(
    val uri: Uri,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
)
