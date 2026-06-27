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
