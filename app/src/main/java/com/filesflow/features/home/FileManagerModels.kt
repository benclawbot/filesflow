package com.filesflow.features.home

import android.net.Uri

enum class FileCategoryType(
    val label: String,
    val permissionKind: PermissionKind,
) {
    Images("Images", PermissionKind.Images),
    Videos("Videos", PermissionKind.Videos),
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
    val visibleFiles: List<FilesFlowFile> = emptyList(),
    val allCategoryFiles: List<FilesFlowFile> = emptyList(),
    val categoryFolderFilters: List<CategoryFolderFilter> = emptyList(),
    val selectedCategoryFolderId: String? = null,
    val browseMode: BrowseMode = BrowseMode.Home,
    val searchQuery: String = "",
    val selectedFile: FilesFlowFile? = null,
    val selectedFileIds: Set<String> = emptySet(),
    val destinationFolderName: String? = null,
    val operationStatus: FileOperationStatus? = null,
    val accessState: StorageAccessState = StorageAccessState(),
    val isLoading: Boolean = false,
) {
    val isSelectionMode: Boolean
        get() = selectedFileIds.isNotEmpty()
}

fun toggledSelectedFileIds(currentSelection: Set<String>, file: FilesFlowFile): Set<String> {
    return if (file.id in currentSelection) {
        currentSelection - file.id
    } else {
        currentSelection + file.id
    }
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
