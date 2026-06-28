package com.filesflow.features.home

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File
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
                mimeType = file.mimeType ?: mimeTypeFromName(file.name) ?: "application/octet-stream",
            )
        }

    if (shareableFiles.isEmpty()) return null

    val shareIntent = (if (shareableFiles.size == 1) {
        Intent(Intent.ACTION_SEND).apply {
            type = shareableFiles.single().mimeType
            putExtra(Intent.EXTRA_STREAM, shareableFiles.single().uri)
        }
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = commonMimeType(shareableFiles)
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(shareableFiles.map { it.uri }))
        }
    }).apply {
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    return Intent.createChooser(shareIntent, "Share files").apply {
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
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

private fun commonMimeType(files: List<ShareableFile>): String {
    val mimeTypes = files.map { it.mimeType }.distinct()
    return if (mimeTypes.size == 1) mimeTypes.single() else "*/*"
}

private fun mimeTypeFromName(name: String): String? {
    val extension = name.substringAfterLast('.', missingDelimiterValue = "")
        .lowercase(Locale.US)
        .takeIf { it.isNotBlank() }
    return extension?.let { MimeTypeMap.getSingleton().getMimeTypeFromExtension(it) }
}

private data class ShareableFile(
    val uri: Uri,
    val mimeType: String,
)
