package com.filesflow.data

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import com.filesflow.features.home.FileCategorySummary
import com.filesflow.features.home.FileCategoryType
import com.filesflow.features.home.FileManagerRepository
import com.filesflow.features.home.FileOperationStatus
import com.filesflow.features.home.FileSource
import com.filesflow.features.home.FilesFlowFile
import com.filesflow.features.home.StorageOverview
import com.filesflow.features.home.formatFileMetadata
import com.filesflow.features.home.formatStorageLabel
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidFileManagerRepository(
    context: Context,
) : FileManagerRepository {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver
    private val preferences = appContext.getSharedPreferences("filesflow-storage", Context.MODE_PRIVATE)

    override suspend fun getStorageOverview(): StorageOverview = withContext(Dispatchers.IO) {
        @Suppress("DEPRECATION")
        val root = Environment.getExternalStorageDirectory()
        val stats = StatFs(root.absolutePath)
        val totalBytes = stats.blockCountLong * stats.blockSizeLong
        val freeBytes = stats.availableBlocksLong * stats.blockSizeLong
        formatStorageLabel(usedBytes = totalBytes - freeBytes, totalBytes = totalBytes)
    }

    override suspend fun getCategorySummaries(): List<FileCategorySummary> = withContext(Dispatchers.IO) {
        FileCategoryType.entries.map { type ->
            val files = if (type == FileCategoryType.Apps) {
                listInstalledApps()
            } else {
                listCategoryInternal(type, 500)
            }
            FileCategorySummary(
                type = type,
                fileCount = files.size,
                totalBytes = files.sumOf { it.sizeBytes },
            )
        }
    }

    override suspend fun getRecentFiles(limit: Int): List<FilesFlowFile> = withContext(Dispatchers.IO) {
        queryFiles(
            selection = null,
            selectionArgs = emptyArray(),
            sortOrder = "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC",
            limit = limit,
        )
    }

    override suspend fun listCategory(type: FileCategoryType): List<FilesFlowFile> = withContext(Dispatchers.IO) {
        if (type == FileCategoryType.Apps) listInstalledApps() else listCategoryInternal(type, 200)
    }

    override suspend fun searchFiles(query: String): List<FilesFlowFile> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@withContext emptyList()
        queryFiles(
            selection = "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?",
            selectionArgs = arrayOf("%$trimmed%"),
            sortOrder = "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC",
            limit = 100,
        )
    }

    override suspend fun listBrowseRoot(): List<FilesFlowFile> = withContext(Dispatchers.IO) {
        val safUri = getPersistedSafUri()
        if (safUri != null) {
            return@withContext listFolderInternal(safUri)
        }

        @Suppress("DEPRECATION")
        val root = Environment.getExternalStorageDirectory()
        if (root.canRead()) root.listFilesSafely().map { it.toDirectFileItem() } else emptyList()
    }

    override suspend fun listFolder(uri: Uri?): List<FilesFlowFile> = withContext(Dispatchers.IO) {
        if (uri == null) listBrowseRoot() else listFolderInternal(uri)
    }

    override suspend fun copyToSafFolder(file: FilesFlowFile): FileOperationStatus = withContext(Dispatchers.IO) {
        val destinationRoot = getPersistedSafDocument()
            ?: return@withContext FileOperationStatus("Choose a folder", "Select a destination folder before copying files.")
        val copied = copyIntoDocumentTree(file, destinationRoot)
        if (copied) {
            FileOperationStatus("Copied", "${file.name} was copied to ${destinationRoot.name ?: "selected folder"}.")
        } else {
            FileOperationStatus("Copy failed", "FilesFlow could not copy ${file.name}.")
        }
    }

    override suspend fun moveToSafFolder(file: FilesFlowFile): FileOperationStatus = withContext(Dispatchers.IO) {
        val destinationRoot = getPersistedSafDocument()
            ?: return@withContext FileOperationStatus("Choose a folder", "Select a destination folder before moving files.")
        val copied = copyIntoDocumentTree(file, destinationRoot)
        val deleted = if (copied) deleteInternal(file) else false
        when {
            copied && deleted -> FileOperationStatus("Moved", "${file.name} was moved to ${destinationRoot.name ?: "selected folder"}.")
            copied -> FileOperationStatus("Copied only", "${file.name} was copied, but Android did not allow deleting the original.")
            else -> FileOperationStatus("Move failed", "FilesFlow could not move ${file.name}.")
        }
    }

    override suspend fun delete(file: FilesFlowFile): FileOperationStatus = withContext(Dispatchers.IO) {
        if (deleteInternal(file)) {
            FileOperationStatus("Deleted", "${file.name} was deleted.")
        } else {
            FileOperationStatus("Delete unavailable", "Android did not allow FilesFlow to delete ${file.name}. Try SAF folder access or all-files access.")
        }
    }

    override fun persistSafFolder(uri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { resolver.takePersistableUriPermission(uri, flags) }
        preferences.edit().putString(KEY_SAF_URI, uri.toString()).apply()
    }

    override fun getPersistedSafFolderName(): String? = getPersistedSafDocument()?.name

    private fun listCategoryInternal(type: FileCategoryType, limit: Int): List<FilesFlowFile> {
        val (selection, args) = when (type) {
            FileCategoryType.Images -> "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ?" to arrayOf(MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString())
            FileCategoryType.Videos -> "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ?" to arrayOf(MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString())
            FileCategoryType.Music -> "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ?" to arrayOf(MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO.toString())
            FileCategoryType.Downloads -> downloadsSelection()
            FileCategoryType.Docs -> documentSelection()
            FileCategoryType.Apps -> return listInstalledApps()
        }
        return queryFiles(
            selection = selection,
            selectionArgs = args,
            sortOrder = "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC",
            limit = limit,
        )
    }

    private fun documentSelection(): Pair<String, Array<String>> {
        val displayName = MediaStore.Files.FileColumns.DISPLAY_NAME
        val mimeType = MediaStore.Files.FileColumns.MIME_TYPE
        return "($mimeType LIKE ? OR $mimeType LIKE ? OR $mimeType LIKE ? OR $displayName LIKE ? OR $displayName LIKE ? OR $displayName LIKE ? OR $displayName LIKE ?)" to
            arrayOf("%pdf%", "text/%", "%document%", "%.doc%", "%.xls%", "%.ppt%", "%.txt%")
    }

    private fun downloadsSelection(): Pair<String, Array<String>> {
        return "${pathColumn()} LIKE ?" to arrayOf("%Download%")
    }

    private fun queryFiles(
        selection: String?,
        selectionArgs: Array<String>,
        sortOrder: String,
        limit: Int,
    ): List<FilesFlowFile> {
        val uri = MediaStore.Files.getContentUri("external")
        val pathColumn = pathColumn()
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            pathColumn,
        )
        val results = mutableListOf<FilesFlowFile>()
        val order = "$sortOrder LIMIT $limit"

        resolver.query(uri, projection, selection, selectionArgs, order)?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
            val modifiedIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
            val mimeIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
            val mediaTypeIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
            val pathIndex = cursor.getColumnIndex(pathColumn)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idIndex)
                val name = cursor.getString(nameIndex) ?: "Unnamed file"
                val size = cursor.getLongOrZero(sizeIndex)
                val modifiedMillis = cursor.getLongOrZero(modifiedIndex) * 1000L
                val mime = cursor.getString(mimeIndex)
                val mediaType = cursor.getInt(mediaTypeIndex)
                val path = if (pathIndex >= 0 && !cursor.isNull(pathIndex)) cursor.getString(pathIndex) else null
                val contentUri = mediaContentUri(mediaType, id)

                results += FilesFlowFile(
                    id = "media-$id",
                    name = name,
                    metadata = formatFileMetadata(size, modifiedMillis),
                    uri = contentUri,
                    path = path,
                    mimeType = mime,
                    sizeBytes = size,
                    modifiedAtMillis = modifiedMillis,
                    source = FileSource.MediaStore,
                )
            }
        }

        return results
    }

    private fun mediaContentUri(mediaType: Int, id: Long): Uri {
        val baseUri = when (mediaType) {
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            else -> MediaStore.Files.getContentUri("external")
        }
        return ContentUris.withAppendedId(baseUri, id)
    }

    private fun pathColumn(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Files.FileColumns.RELATIVE_PATH
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Files.FileColumns.DATA
        }
    }

    private fun listInstalledApps(): List<FilesFlowFile> {
        return appContext.packageManager
            .getInstalledApplications(0)
            .asSequence()
            .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 }
            .sortedBy { it.loadLabel(appContext.packageManager).toString().lowercase(Locale.US) }
            .take(100)
            .map { app ->
                val label = app.loadLabel(appContext.packageManager).toString()
                FilesFlowFile(
                    id = "app-${app.packageName}",
                    name = label,
                    metadata = app.packageName,
                    uri = null,
                    path = app.sourceDir,
                    mimeType = "application/vnd.android.package-archive",
                    sizeBytes = File(app.sourceDir.orEmpty()).length().coerceAtLeast(0L),
                    modifiedAtMillis = File(app.sourceDir.orEmpty()).lastModified(),
                    source = FileSource.AppPackage,
                )
            }
            .toList()
    }

    private fun listFolderInternal(uri: Uri): List<FilesFlowFile> {
        val document = DocumentFile.fromTreeUri(appContext, uri) ?: DocumentFile.fromSingleUri(appContext, uri)
        return document?.listFiles().orEmpty()
            .sortedWith(compareByDescending<DocumentFile> { it.isDirectory }.thenBy { it.name?.lowercase(Locale.US).orEmpty() })
            .map { it.toSafFileItem() }
    }

    private fun getPersistedSafUri(): Uri? {
        return preferences.getString(KEY_SAF_URI, null)?.let(Uri::parse)
    }

    private fun getPersistedSafDocument(): DocumentFile? {
        val uri = getPersistedSafUri() ?: return null
        return DocumentFile.fromTreeUri(appContext, uri)?.takeIf { it.canWrite() }
    }

    private fun copyIntoDocumentTree(file: FilesFlowFile, destinationRoot: DocumentFile): Boolean {
        if (file.isDirectory) return false
        val mimeType = file.mimeType ?: "application/octet-stream"
        val target = destinationRoot.createFile(mimeType, file.name) ?: return false
        val input = openInputStream(file) ?: return false
        return runCatching {
            input.use { source ->
                resolver.openOutputStream(target.uri)?.use { output ->
                    source.copyTo(output)
                } ?: return false
            }
            true
        }.getOrDefault(false)
    }

    private fun openInputStream(file: FilesFlowFile) = when {
        file.uri != null -> resolver.openInputStream(file.uri)
        file.path != null -> File(file.path).takeIf { it.isFile && it.canRead() }?.inputStream()
        else -> null
    }

    private fun deleteInternal(file: FilesFlowFile): Boolean = runCatching {
        when (file.source) {
            FileSource.Saf -> file.uri?.let { DocumentFile.fromSingleUri(appContext, it)?.delete() } == true
            FileSource.DirectFile -> file.path?.let { File(it).delete() } == true
            FileSource.MediaStore -> file.uri?.let { resolver.delete(it, null, null) > 0 } == true
            FileSource.AppPackage -> false
        }
    }.getOrDefault(false)

    private fun File.listFilesSafely(): List<File> = runCatching { listFiles()?.toList().orEmpty() }.getOrDefault(emptyList())

    private fun File.toDirectFileItem(): FilesFlowFile {
        val size = if (isFile) length() else 0L
        return FilesFlowFile(
            id = "file-$absolutePath",
            name = name.ifBlank { absolutePath },
            metadata = if (isDirectory) "Folder" else formatFileMetadata(size, lastModified()),
            uri = null,
            path = absolutePath,
            mimeType = null,
            sizeBytes = size,
            modifiedAtMillis = lastModified(),
            source = FileSource.DirectFile,
            isDirectory = isDirectory,
        )
    }

    private fun DocumentFile.toSafFileItem(): FilesFlowFile {
        val size = if (isDirectory) 0L else length().coerceAtLeast(0L)
        val modified = lastModified()
        return FilesFlowFile(
            id = "saf-${uri}",
            name = name ?: "Unnamed file",
            metadata = if (isDirectory) "Folder" else formatFileMetadata(size, modified),
            uri = uri,
            path = null,
            mimeType = type,
            sizeBytes = size,
            modifiedAtMillis = modified,
            source = FileSource.Saf,
            isDirectory = isDirectory,
        )
    }

    private fun android.database.Cursor.getLongOrZero(index: Int): Long = if (isNull(index)) 0L else getLong(index)

    companion object {
        private const val KEY_SAF_URI = "saf-tree-uri"
    }
}
