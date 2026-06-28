package com.filesflow.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import com.filesflow.features.home.FavoriteFolder
import com.filesflow.features.home.FileCategorySummary
import com.filesflow.features.home.FileCategoryType
import com.filesflow.features.home.FileManagerRepository
import com.filesflow.features.home.FileOperationStatus
import com.filesflow.features.home.FileSource
import com.filesflow.features.home.FilesFlowFile
import com.filesflow.features.home.StorageOverview
import com.filesflow.features.home.favoriteFolderIdFor
import com.filesflow.features.home.formatFileMetadata
import com.filesflow.features.home.formatStorageLabel
import com.filesflow.features.home.inferCategoryType
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

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
        val directFiles = if (canUseDirectSharedStorage()) listDirectFiles(limit = 2_000) else emptyList()
        FileCategoryType.entries.map { type ->
            val files = when {
                type == FileCategoryType.Apps -> listInstalledApps()
                directFiles.isNotEmpty() -> directFiles.filter { inferCategoryType(it.name, it.mimeType, it.path) == type }
                else -> listCategoryInternal(type, 500)
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
        when {
            type == FileCategoryType.Apps -> listInstalledApps()
            canUseDirectSharedStorage() -> listDirectFiles(limit = 500)
                .filter { inferCategoryType(it.name, it.mimeType, it.path) == type }
                .sortedByDescending { it.modifiedAtMillis }
                .take(200)
            else -> listCategoryInternal(type, 200)
        }
    }

    override suspend fun searchFiles(query: String): List<FilesFlowFile> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@withContext emptyList()
        if (canUseDirectSharedStorage()) {
            return@withContext listDirectFiles(limit = 1_000)
                .filter { it.name.contains(trimmed, ignoreCase = true) }
                .sortedByDescending { it.modifiedAtMillis }
                .take(100)
        }
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

    override suspend fun listFolder(folder: FilesFlowFile): List<FilesFlowFile> = withContext(Dispatchers.IO) {
        when (folder.source) {
            FileSource.DirectFile -> folder.path
                ?.let(::File)
                ?.takeIf { it.isDirectory && it.canRead() }
                ?.listFilesSafely()
                ?.map { it.toDirectFileItem() }
                .orEmpty()
            FileSource.Saf -> folder.uri?.let { listFolderInternal(it) }.orEmpty()
            FileSource.MediaStore,
            FileSource.AppPackage -> emptyList()
        }
    }

    override suspend fun getBrowseRootFolder(): FilesFlowFile? = withContext(Dispatchers.IO) {
        getPersistedSafDocument()?.toSafFileItem()
            ?: run {
                @Suppress("DEPRECATION")
                Environment.getExternalStorageDirectory()
                    .takeIf { it.isDirectory && it.canRead() }
                    ?.toDirectFileItem()
            }
    }

    override suspend fun copyToFolder(
        file: FilesFlowFile,
        destinationFolder: FilesFlowFile,
    ): FileOperationStatus = withContext(Dispatchers.IO) {
        if (file.isDirectory) {
            return@withContext FileOperationStatus("Copy unavailable", "Folders cannot be copied yet.")
        }
        val copied = copyIntoFolder(file, destinationFolder)
        if (copied) {
            FileOperationStatus("Copied", "${file.name} was copied to ${destinationFolder.name}.")
        } else {
            FileOperationStatus("Copy failed", "FilesFlow could not copy ${file.name} to ${destinationFolder.name}.")
        }
    }

    override suspend fun moveToFolder(
        file: FilesFlowFile,
        destinationFolder: FilesFlowFile,
    ): FileOperationStatus = withContext(Dispatchers.IO) {
        if (file.isDirectory) {
            return@withContext FileOperationStatus("Move unavailable", "Folders cannot be moved yet.")
        }
        val copied = copyIntoFolder(file, destinationFolder)
        val deleted = if (copied) deleteInternal(file) else false
        when {
            copied && deleted -> FileOperationStatus("Moved", "${file.name} was moved to ${destinationFolder.name}.")
            copied -> FileOperationStatus("Copied only", "${file.name} was copied, but Android did not allow deleting the original.")
            else -> FileOperationStatus("Move failed", "FilesFlow could not move ${file.name} to ${destinationFolder.name}.")
        }
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

    override suspend fun rename(file: FilesFlowFile, newName: String): FileOperationStatus = withContext(Dispatchers.IO) {
        val cleanedName = newName.trim()
        if (cleanedName.isBlank()) {
            return@withContext FileOperationStatus("Rename unavailable", "Enter a file name before renaming.")
        }
        if (file.source == FileSource.AppPackage) {
            return@withContext FileOperationStatus("Rename unavailable", "Installed apps cannot be renamed from FilesFlow.")
        }
        if (renameInternal(file, cleanedName)) {
            FileOperationStatus("Renamed", "${file.name} was renamed to $cleanedName.")
        } else {
            FileOperationStatus("Rename failed", "Android did not allow FilesFlow to rename ${file.name}.")
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

    override fun getFavoriteFolders(): List<FavoriteFolder> = readFavoriteFolders()

    override fun toggleFavoriteFolder(folder: FilesFlowFile): FileOperationStatus {
        if (!folder.isDirectory) {
            return FileOperationStatus("Favorite unavailable", "Only folders can be added to favorites.")
        }
        if (folder.source != FileSource.DirectFile && folder.source != FileSource.Saf) {
            return FileOperationStatus("Favorite unavailable", "This Android location cannot be saved as a favorite folder.")
        }
        val favorite = folder.toFavoriteFolder()
        val current = readFavoriteFolders().toMutableList()
        val existingIndex = current.indexOfFirst { it.id == favorite.id }
        return if (existingIndex >= 0) {
            current.removeAt(existingIndex)
            writeFavoriteFolders(current)
            FileOperationStatus("Removed favorite", "${folder.name} was removed from favorite folders.")
        } else {
            current.add(0, favorite)
            writeFavoriteFolders(current)
            FileOperationStatus("Added favorite", "${folder.name} will appear on Home and as a move/copy suggestion.")
        }
    }

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

    private fun canUseDirectSharedStorage(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()
    }

    private fun listDirectFiles(limit: Int): List<FilesFlowFile> {
        @Suppress("DEPRECATION")
        val root = Environment.getExternalStorageDirectory()
        if (!root.canRead()) return emptyList()

        val pending = ArrayDeque<File>()
        pending.add(root)
        val results = mutableListOf<FilesFlowFile>()

        while (pending.isNotEmpty() && results.size < limit) {
            val current = pending.removeFirst()
            current.listFilesSafely()
                .sortedWith(compareByDescending<File> { it.isDirectory }.thenBy { it.name.lowercase(Locale.US) })
                .forEach { child ->
                    if (child.isDirectory) {
                        pending.add(child)
                    } else if (child.isFile) {
                        results += child.toDirectFileItem()
                    }
                }
        }

        return results
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

    private fun copyIntoFolder(file: FilesFlowFile, destinationFolder: FilesFlowFile): Boolean {
        return when (destinationFolder.source) {
            FileSource.DirectFile -> destinationFolder.path
                ?.let(::File)
                ?.takeIf { it.isDirectory && it.canWrite() }
                ?.let { copyIntoDirectFolder(file, it) }
                ?: false
            FileSource.Saf -> destinationFolder.uri
                ?.let { copyIntoSafFolderUri(file, it) }
                ?: false
            FileSource.MediaStore,
            FileSource.AppPackage -> false
        }
    }

    private fun copyIntoSafFolderUri(file: FilesFlowFile, destinationUri: Uri): Boolean {
        if (file.isDirectory) return false
        val mimeType = file.mimeType ?: "application/octet-stream"
        val targetUri = runCatching {
            val parentUri = if (DocumentsContract.isTreeUri(destinationUri)) {
                val documentId = runCatching {
                    DocumentsContract.getDocumentId(destinationUri)
                }.getOrElse {
                    DocumentsContract.getTreeDocumentId(destinationUri)
                }
                DocumentsContract.buildDocumentUriUsingTree(
                    destinationUri,
                    documentId,
                )
            } else {
                destinationUri
            }
            DocumentsContract.createDocument(resolver, parentUri, mimeType, file.name)
        }.getOrNull() ?: return false
        val input = openInputStream(file) ?: return false
        return runCatching {
            input.use { source ->
                resolver.openOutputStream(targetUri)?.use { output ->
                    source.copyTo(output)
                } ?: return false
            }
            true
        }.getOrDefault(false)
    }

    private fun copyIntoDirectFolder(file: FilesFlowFile, destinationFolder: File): Boolean {
        if (file.isDirectory) return false
        val input = openInputStream(file) ?: return false
        val target = uniqueDestinationFile(destinationFolder, file.name)
        return runCatching {
            input.use { source ->
                target.outputStream().use { output ->
                    source.copyTo(output)
                }
            }
            true
        }.getOrDefault(false)
    }

    private fun uniqueDestinationFile(destinationFolder: File, fileName: String): File {
        val baseName = fileName.substringBeforeLast('.', missingDelimiterValue = fileName)
        val extension = fileName.substringAfterLast('.', missingDelimiterValue = "")
            .takeIf { it.isNotBlank() }
            ?.let { ".$it" }
            .orEmpty()
        var candidate = File(destinationFolder, fileName)
        var suffix = 1
        while (candidate.exists()) {
            candidate = File(destinationFolder, "$baseName ($suffix)$extension")
            suffix += 1
        }
        return candidate
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

    private fun renameInternal(file: FilesFlowFile, newName: String): Boolean = runCatching {
        when (file.source) {
            FileSource.Saf -> file.uri?.let { DocumentFile.fromSingleUri(appContext, it)?.renameTo(newName) } == true
            FileSource.DirectFile -> {
                val current = file.path?.let(::File) ?: return@runCatching false
                val target = File(current.parentFile, newName)
                current.renameTo(target)
            }
            FileSource.MediaStore -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.Files.FileColumns.DISPLAY_NAME, newName)
                    }
                    file.uri?.let { resolver.update(it, values, null, null) > 0 } == true
                } else {
                    val current = file.path?.let(::File) ?: return@runCatching false
                    val target = File(current.parentFile, newName)
                    current.renameTo(target)
                }
            }
            FileSource.AppPackage -> false
        }
    }.getOrDefault(false)

    private fun FilesFlowFile.toFavoriteFolder(): FavoriteFolder {
        return FavoriteFolder(
            id = favoriteFolderIdFor(this),
            name = name,
            uri = uri,
            path = path,
            source = source,
        )
    }

    private fun readFavoriteFolders(): List<FavoriteFolder> {
        val raw = preferences.getString(KEY_FAVORITE_FOLDERS, "[]").orEmpty()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val id = item.optString("id").takeIf { it.isNotBlank() } ?: continue
                    val source = runCatching { FileSource.valueOf(item.optString("source")) }.getOrNull() ?: continue
                    add(
                        FavoriteFolder(
                            id = id,
                            name = item.optString("name").takeIf { it.isNotBlank() } ?: "Favorite folder",
                            uri = item.optString("uri").takeIf { it.isNotBlank() }?.let(Uri::parse),
                            path = item.optString("path").takeIf { it.isNotBlank() },
                            source = source,
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun writeFavoriteFolders(folders: List<FavoriteFolder>) {
        val array = JSONArray()
        folders.forEach { folder ->
            array.put(
                JSONObject().apply {
                    put("id", folder.id)
                    put("name", folder.name)
                    put("uri", folder.uri?.toString().orEmpty())
                    put("path", folder.path.orEmpty())
                    put("source", folder.source.name)
                },
            )
        }
        preferences.edit().putString(KEY_FAVORITE_FOLDERS, array.toString()).apply()
    }

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
        private const val KEY_FAVORITE_FOLDERS = "favorite-folders"
    }
}
