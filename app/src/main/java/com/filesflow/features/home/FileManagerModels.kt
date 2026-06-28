package com.filesflow.features.home

import android.net.Uri

enum class FileCategoryType(
    val label: String,
    val permissionKind: PermissionKind,
) {
    Images("Images", PermissionKind.Images),
    Videos("Videos", PermissionKind.Files),
    Docs("Docs", PermissionKind.Files),
    Downloads("Downloads", PermissionKind.Files),
    Music("Music", PermissionKind.Audio),
    Apps("Apps", PermissionKind.Files),
}

enum class PermissionKind {
    Images,
    Videos,
    Audio,
    Files,
}

enum class FileSource {
    MediaStore,
    Saf,
    DirectFile,
    AppPackage,
}

enum class FileOperation {
    Copy,
    Move,
    Delete,
}

data class CategoryFolderFilter(
    val id: String,
    val name: String,
)

data class FavoriteFolder(
    val id: String,
    val name: String,
    val uri: Uri?,
    val path: String?,
    val source: FileSource,
)

data class DestinationSelection(
    val operation: FileOperation,
    val files: List<FilesFlowFile>,
    val returnBrowseMode: BrowseMode,
    val returnSelectedCategoryFolderId: String? = null,
) {
    val primaryFile: FilesFlowFile?
        get() = files.firstOrNull()
}

sealed interface BrowseMode {
    data object Home : BrowseMode
    data class Category(val type: FileCategoryType) : BrowseMode
    data class Folder(
        val uri: Uri?,
        val displayName: String,
        val path: String? = null,
        val source: FileSource = FileSource.DirectFile,
    ) : BrowseMode
    data class Search(val query: String) : BrowseMode
}

data class FileCategorySummary(
    val type: FileCategoryType,
    val fileCount: Int,
    val totalBytes: Long,
)

data class FilesFlowFile(
    val id: String,
    val name: String,
    val metadata: String,
    val uri: Uri?,
    val path: String?,
    val mimeType: String?,
    val sizeBytes: Long,
    val modifiedAtMillis: Long,
    val source: FileSource,
    val isDirectory: Boolean = false,
)

data class FileOperationStatus(
    val title: String,
    val detail: String,
)

data class StorageAccessState(
    val hasImagesPermission: Boolean = false,
    val hasVideosPermission: Boolean = false,
    val hasAudioPermission: Boolean = false,
    val hasLegacyReadPermission: Boolean = false,
    val hasAllFilesAccess: Boolean = false,
    val hasSafFolder: Boolean = false,
) {
    val hasAnyMediaAccess: Boolean
        get() = hasImagesPermission || hasVideosPermission || hasAudioPermission || hasLegacyReadPermission
}

data class FilesFlowUiState(
    val storageOverview: StorageOverview = emptyStorageOverview,
    val categories: List<FileCategorySummary> = emptyFileCategorySummaries(),
    val recentFiles: List<FilesFlowFile> = emptyList(),
    val favoriteFolders: List<FavoriteFolder> = emptyList(),
    val visibleFiles: List<FilesFlowFile> = emptyList(),
    val allCategoryFiles: List<FilesFlowFile> = emptyList(),
    val categoryFolderFilters: List<CategoryFolderFilter> = emptyList(),
    val selectedCategoryFolderId: String? = null,
    val browseMode: BrowseMode = BrowseMode.Home,
    val searchQuery: String = "",
    val selectedFile: FilesFlowFile? = null,
    val selectedFileIds: Set<String> = emptySet(),
    val destinationSelection: DestinationSelection? = null,
    val destinationFolderName: String? = null,
    val operationStatus: FileOperationStatus? = null,
    val accessState: StorageAccessState = StorageAccessState(),
    val isLoading: Boolean = false,
) {
    val isSelectionMode: Boolean
        get() = selectedFileIds.isNotEmpty()

    val favoriteFolderIds: Set<String>
        get() = favoriteFolders.map { it.id }.toSet()
}

fun toggledSelectedFileIds(currentSelection: Set<String>, file: FilesFlowFile): Set<String> {
    return if (file.id in currentSelection) {
        currentSelection - file.id
    } else {
        currentSelection + file.id
    }
}

/**
 * Returns the id-set that selects every file in [visibleFiles]. Use this for the
 * category topbar "select all" toggle so the selection always matches what the
 * user currently sees (respecting any active category-folder filter).
 */
fun selectAllVisibleFileIds(visibleFiles: List<FilesFlowFile>): Set<String> {
    return visibleFiles.map { it.id }.toSet()
}

/**
 * True when every file in [visibleFiles] is currently in [selectedFileIds] AND
 * there is at least one visible file. Drives the topbar select-all toggle's
 * checked/unchecked state.
 */
fun isAllVisibleSelected(visibleFiles: List<FilesFlowFile>, selectedFileIds: Set<String>): Boolean {
    if (visibleFiles.isEmpty()) return false
    return visibleFiles.all { it.id in selectedFileIds }
}

fun toggledCategoryFolderSelection(currentFolderId: String?, clickedFolderId: String): String? {
    return if (currentFolderId == clickedFolderId) null else clickedFolderId
}

fun categoryFolderFilters(files: List<FilesFlowFile>): List<CategoryFolderFilter> {
    return files
        .mapNotNull { file -> file.categoryFolderId()?.let { id -> CategoryFolderFilter(id = id, name = id.substringAfterLast('/')) } }
        .distinctBy { it.id }
        .sortedBy { it.name.lowercase() }
}

fun filesForCategoryFolder(files: List<FilesFlowFile>, selectedFolderId: String?): List<FilesFlowFile> {
    if (selectedFolderId == null) return files
    return files.filter { it.categoryFolderId() == selectedFolderId }
}

fun destinationFolderForBrowseMode(mode: BrowseMode, browseRootFolder: FilesFlowFile?): FilesFlowFile? {
    return when (mode) {
        is BrowseMode.Folder -> if (mode.uri == null && mode.path == null) {
            browseRootFolder
        } else {
            FilesFlowFile(
                id = "folder-${mode.uri ?: mode.path}",
                name = mode.displayName,
                metadata = "Folder",
                uri = mode.uri,
                path = mode.path,
                mimeType = null,
                sizeBytes = 0L,
                modifiedAtMillis = 0L,
                source = mode.source,
                isDirectory = true,
            )
        }
        BrowseMode.Home,
        is BrowseMode.Category,
        is BrowseMode.Search -> null
    }
}

fun favoriteFolderIdFor(file: FilesFlowFile): String {
    return when (file.source) {
        FileSource.Saf -> "saf:${file.uri}"
        FileSource.DirectFile -> "file:${file.path}"
        FileSource.MediaStore -> "media:${file.uri ?: file.id}"
        FileSource.AppPackage -> "app:${file.id}"
    }
}

fun FavoriteFolder.toFilesFlowFile(): FilesFlowFile {
    return FilesFlowFile(
        id = id,
        name = name,
        metadata = "Favorite folder",
        uri = uri,
        path = path,
        mimeType = null,
        sizeBytes = 0L,
        modifiedAtMillis = 0L,
        source = source,
        isDirectory = true,
    )
}

private fun FilesFlowFile.categoryFolderId(): String? {
    val rawPath = path?.replace('\\', '/')?.trim().orEmpty()
    if (rawPath.isBlank()) return null

    val normalized = rawPath.trimEnd('/')
    if (normalized.isBlank()) return null

    val parentPath = when {
        source == FileSource.MediaStore && rawPath.endsWith("/") -> normalized
        source == FileSource.DirectFile || source == FileSource.MediaStore -> normalized.substringBeforeLast(
            delimiter = "/",
            missingDelimiterValue = "",
        )
        else -> ""
    }

    return parentPath
        .trim('/')
        .takeIf { it.isNotBlank() }
}
