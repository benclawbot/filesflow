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
    val browseMode: BrowseMode = BrowseMode.Home,
    val searchQuery: String = "",
    val selectedFile: FilesFlowFile? = null,
    val destinationFolderName: String? = null,
    val operationStatus: FileOperationStatus? = null,
    val accessState: StorageAccessState = StorageAccessState(),
    val isLoading: Boolean = false,
)
