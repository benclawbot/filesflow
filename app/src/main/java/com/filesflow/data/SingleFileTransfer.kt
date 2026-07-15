package com.filesflow.data

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import com.filesflow.features.home.FileSource
import com.filesflow.features.home.FilesFlowFile
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Copies one file into direct storage or a SAF directory with the same collision and cleanup
 * guarantees as recursive folder transfers.
 */
internal class SingleFileTransfer(context: Context) {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver

    suspend fun copy(source: FilesFlowFile, destination: FilesFlowFile): Boolean = withContext(Dispatchers.IO) {
        if (source.isDirectory || !destination.isDirectory) return@withContext false
        when (destination.source) {
            FileSource.DirectFile -> destination.path
                ?.let(::File)
                ?.takeIf { it.isDirectory && it.canWrite() }
                ?.let { copyToDirect(source, it) }
                ?: false
            FileSource.Saf -> destination.uri
                ?.let { DocumentFile.fromTreeUri(appContext, it) ?: DocumentFile.fromSingleUri(appContext, it) }
                ?.takeIf { it.isDirectory && it.canWrite() }
                ?.let { copyToSaf(source, it) }
                ?: false
            FileSource.MediaStore,
            FileSource.AppPackage -> false
        }
    }

    suspend fun deleteSource(source: FilesFlowFile): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            when (source.source) {
                FileSource.DirectFile -> source.path?.let(::File)?.takeIf { it.isFile }?.delete() == true
                FileSource.Saf -> source.uri?.let { DocumentFile.fromSingleUri(appContext, it)?.delete() } == true
                FileSource.MediaStore -> source.uri?.let { resolver.delete(it, null, null) > 0 } == true
                FileSource.AppPackage -> false
            }
        }.getOrDefault(false)
    }

    private fun copyToDirect(source: FilesFlowFile, destination: File): Boolean {
        val target = uniqueDirectFile(destination, source.name)
        val input = openInput(source) ?: return false
        return runCatching {
            input.use { sourceStream ->
                target.outputStream().use { destinationStream -> sourceStream.copyTo(destinationStream) }
            }
            true
        }.getOrElse {
            target.delete()
            false
        }
    }

    private fun copyToSaf(source: FilesFlowFile, destination: DocumentFile): Boolean {
        val targetName = uniqueSafFileName(destination, source.name)
        val target = destination.createFile(source.mimeType ?: "application/octet-stream", targetName) ?: return false
        val input = openInput(source)
        if (input == null) {
            target.delete()
            return false
        }
        return runCatching {
            input.use { sourceStream ->
                resolver.openOutputStream(target.uri, "w")?.use { destinationStream ->
                    sourceStream.copyTo(destinationStream)
                } ?: error("Unable to open SAF destination")
            }
            true
        }.getOrElse {
            target.delete()
            false
        }
    }

    private fun openInput(source: FilesFlowFile) = when {
        source.uri != null -> resolver.openInputStream(source.uri)
        source.path != null -> File(source.path).takeIf { it.isFile && it.canRead() }?.inputStream()
        else -> null
    }

    private fun uniqueDirectFile(parent: File, requestedName: String): File {
        val parts = TransferNamePolicy.parts(requestedName, preserveExtension = true)
        var candidate = File(parent, requestedName)
        var suffix = 1
        while (candidate.exists()) candidate = File(parent, TransferNamePolicy.withSuffix(parts, suffix++))
        return candidate
    }

    private fun uniqueSafFileName(parent: DocumentFile, requestedName: String): String {
        val existing = parent.listFiles().mapNotNull { it.name }.toHashSet()
        val parts = TransferNamePolicy.parts(requestedName, preserveExtension = true)
        var candidate = requestedName
        var suffix = 1
        while (candidate in existing) candidate = TransferNamePolicy.withSuffix(parts, suffix++)
        return candidate
    }
}
