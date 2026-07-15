package com.filesflow.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.filesflow.features.home.FileSource
import com.filesflow.features.home.FilesFlowFile
import java.io.File

internal class RecursiveFolderTransfer(context: Context) {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver

    fun copy(source: FilesFlowFile, destination: FilesFlowFile): Boolean {
        if (!source.isDirectory || !destination.isDirectory) return false
        if (isUnsafeDestination(source, destination)) return false

        return when (destination.source) {
            FileSource.DirectFile -> destination.path
                ?.let(::File)
                ?.takeIf { it.isDirectory && it.canWrite() }
                ?.let { copyDirectoryToDirect(source, it) }
                ?: false
            FileSource.Saf -> destination.uri
                ?.let { DocumentFile.fromTreeUri(appContext, it) ?: DocumentFile.fromSingleUri(appContext, it) }
                ?.takeIf { it.isDirectory && it.canWrite() }
                ?.let { copyDirectoryToSaf(source, it) }
                ?: false
            FileSource.MediaStore,
            FileSource.AppPackage -> false
        }
    }

    fun deleteSource(source: FilesFlowFile): Boolean = runCatching {
        when (source.source) {
            FileSource.DirectFile -> source.path?.let(::File)?.deleteRecursively() == true
            FileSource.Saf -> source.uri?.let { DocumentFile.fromSingleUri(appContext, it)?.delete() } == true
            FileSource.MediaStore,
            FileSource.AppPackage -> false
        }
    }.getOrDefault(false)

    private fun copyDirectoryToDirect(source: FilesFlowFile, destinationRoot: File): Boolean {
        val target = uniqueDirectChild(destinationRoot, source.name, directory = true)
        if (!target.mkdirs()) return false
        val copied = copyChildren(source, DirectTarget(target))
        if (!copied) target.deleteRecursively()
        return copied
    }

    private fun copyDirectoryToSaf(source: FilesFlowFile, destinationRoot: DocumentFile): Boolean {
        val targetName = uniqueSafName(destinationRoot, source.name)
        val target = destinationRoot.createDirectory(targetName) ?: return false
        val copied = copyChildren(source, SafTarget(target))
        if (!copied) target.delete()
        return copied
    }

    private fun copyChildren(source: FilesFlowFile, target: FolderTarget): Boolean {
        val children = sourceChildren(source) ?: return false
        return children.all { child ->
            if (child.isDirectory) {
                val childTarget = target.createDirectory(child.name) ?: return@all false
                val copied = copyChildren(child, childTarget)
                if (!copied) childTarget.delete()
                copied
            } else {
                copyFile(child, target)
            }
        }
    }

    private fun copyFile(source: FilesFlowFile, target: FolderTarget): Boolean {
        val input = when {
            source.uri != null -> resolver.openInputStream(source.uri)
            source.path != null -> File(source.path).takeIf { it.isFile && it.canRead() }?.inputStream()
            else -> null
        } ?: return false
        val output = target.createFile(source.name, source.mimeType ?: "application/octet-stream") ?: return false
        return runCatching {
            input.use { sourceStream ->
                output.use { destinationStream -> sourceStream.copyTo(destinationStream) }
            }
            true
        }.getOrElse {
            target.deleteCreatedFile(source.name)
            false
        }
    }

    private fun sourceChildren(source: FilesFlowFile): List<FilesFlowFile>? = when (source.source) {
        FileSource.DirectFile -> source.path
            ?.let(::File)
            ?.takeIf { it.isDirectory && it.canRead() }
            ?.listFiles()
            ?.map { child ->
                FilesFlowFile(
                    id = "file-${child.absolutePath}",
                    name = child.name,
                    metadata = if (child.isDirectory) "Folder" else "",
                    uri = null,
                    path = child.absolutePath,
                    mimeType = null,
                    sizeBytes = if (child.isFile) child.length() else 0L,
                    modifiedAtMillis = child.lastModified(),
                    source = FileSource.DirectFile,
                    isDirectory = child.isDirectory,
                )
            }
        FileSource.Saf -> source.uri
            ?.let { DocumentFile.fromSingleUri(appContext, it) }
            ?.takeIf { it.isDirectory && it.canRead() }
            ?.listFiles()
            ?.map { child ->
                FilesFlowFile(
                    id = "saf-${child.uri}",
                    name = child.name ?: "Unnamed file",
                    metadata = if (child.isDirectory) "Folder" else "",
                    uri = child.uri,
                    path = null,
                    mimeType = child.type,
                    sizeBytes = if (child.isFile) child.length().coerceAtLeast(0L) else 0L,
                    modifiedAtMillis = child.lastModified(),
                    source = FileSource.Saf,
                    isDirectory = child.isDirectory,
                )
            }
        FileSource.MediaStore,
        FileSource.AppPackage -> null
    }

    private fun isUnsafeDestination(source: FilesFlowFile, destination: FilesFlowFile): Boolean {
        if (source.source == FileSource.DirectFile && destination.source == FileSource.DirectFile) {
            val sourcePath = source.path?.let(::File)?.canonicalFile ?: return true
            val destinationPath = destination.path?.let(::File)?.canonicalFile ?: return true
            return destinationPath == sourcePath || destinationPath.path.startsWith(sourcePath.path + File.separator)
        }
        if (source.source == FileSource.Saf && destination.source == FileSource.Saf) {
            val sourceUri = source.uri ?: return true
            val destinationUri = destination.uri ?: return true
            val sourceId = documentId(sourceUri) ?: return sourceUri == destinationUri
            val destinationId = documentId(destinationUri) ?: return sourceUri == destinationUri
            return destinationId == sourceId || destinationId.startsWith("$sourceId/")
        }
        return false
    }

    private fun documentId(uri: Uri): String? = runCatching {
        DocumentsContract.getDocumentId(uri)
    }.recoverCatching {
        DocumentsContract.getTreeDocumentId(uri)
    }.getOrNull()

    private sealed interface FolderTarget {
        fun createDirectory(name: String): FolderTarget?
        fun createFile(name: String, mimeType: String): java.io.OutputStream?
        fun deleteCreatedFile(name: String)
        fun delete()
    }

    private inner class DirectTarget(private val directory: File) : FolderTarget {
        override fun createDirectory(name: String): FolderTarget? {
            val child = uniqueDirectChild(directory, name, directory = true)
            return child.takeIf { it.mkdirs() }?.let(::DirectTarget)
        }

        override fun createFile(name: String, mimeType: String): java.io.OutputStream? {
            return runCatching { uniqueDirectChild(directory, name, directory = false).outputStream() }.getOrNull()
        }

        override fun deleteCreatedFile(name: String) = Unit
        override fun delete() { directory.deleteRecursively() }
    }

    private inner class SafTarget(private val directory: DocumentFile) : FolderTarget {
        override fun createDirectory(name: String): FolderTarget? {
            return directory.createDirectory(uniqueSafName(directory, name))?.let(::SafTarget)
        }

        override fun createFile(name: String, mimeType: String): java.io.OutputStream? {
            val child = directory.createFile(mimeType, uniqueSafName(directory, name)) ?: return null
            return resolver.openOutputStream(child.uri)
        }

        override fun deleteCreatedFile(name: String) {
            directory.findFile(name)?.delete()
        }

        override fun delete() { directory.delete() }
    }

    private fun uniqueDirectChild(parent: File, requestedName: String, directory: Boolean): File {
        val parts = TransferNamePolicy.parts(requestedName, preserveExtension = !directory)
        var candidate = File(parent, requestedName)
        var suffix = 1
        while (candidate.exists()) {
            candidate = File(parent, TransferNamePolicy.withSuffix(parts, suffix++))
        }
        return candidate
    }

    private fun uniqueSafName(parent: DocumentFile, requestedName: String): String {
        val existing = parent.listFiles().mapNotNull { it.name }.toHashSet()
        val parts = TransferNamePolicy.parts(requestedName, preserveExtension = true)
        var candidate = requestedName
        var suffix = 1
        while (candidate in existing) candidate = TransferNamePolicy.withSuffix(parts, suffix++)
        return candidate
    }
}

internal object TransferNamePolicy {
    data class NameParts(val base: String, val extension: String)

    fun parts(name: String, preserveExtension: Boolean): NameParts {
        if (!preserveExtension) return NameParts(name, "")
        val separator = name.lastIndexOf('.')
        return if (separator > 0 && separator < name.lastIndex) {
            NameParts(name.substring(0, separator), name.substring(separator))
        } else {
            NameParts(name, "")
        }
    }

    fun withSuffix(parts: NameParts, suffix: Int): String = "${parts.base} ($suffix)${parts.extension}"
}
