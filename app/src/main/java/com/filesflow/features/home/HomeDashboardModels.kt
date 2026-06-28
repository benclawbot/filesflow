package com.filesflow.features.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.ui.graphics.vector.ImageVector

const val FilesFlowAppName = "FilesFlow"

data class StorageOverview(
    val usedPercent: Int,
    val usedLabel: String,
    val totalLabel: String,
)

data class FileCategory(
    val label: String,
    val icon: ImageVector,
)

data class RecentFileItem(
    val name: String,
    val metadata: String,
    val icon: ImageVector,
)

val defaultStorageOverview = StorageOverview(
    usedPercent = 78,
    usedLabel = "92.4 GB Used",
    totalLabel = "128 GB Total",
)

fun FileCategoryType.toFileCategory(): FileCategory = when (this) {
    FileCategoryType.Images -> FileCategory(label, Icons.Rounded.Image)
    FileCategoryType.Videos -> FileCategory(label, Icons.Rounded.VideoLibrary)
    FileCategoryType.Docs -> FileCategory(label, Icons.Rounded.Description)
    FileCategoryType.Downloads -> FileCategory(label, Icons.Rounded.Download)
    FileCategoryType.Music -> FileCategory(label, Icons.Rounded.MusicNote)
    FileCategoryType.Apps -> FileCategory(label, Icons.Rounded.Apps)
}

fun defaultFileCategories(): List<FileCategory> = listOf(
    FileCategoryType.Images.toFileCategory(),
    FileCategoryType.Videos.toFileCategory(),
    FileCategoryType.Docs.toFileCategory(),
    FileCategoryType.Downloads.toFileCategory(),
    FileCategoryType.Music.toFileCategory(),
    FileCategoryType.Apps.toFileCategory(),
)

fun defaultRecentFiles(): List<RecentFileItem> = listOf(
    RecentFileItem("Quarterly Report", "2.4 MB - 2h ago", Icons.AutoMirrored.Rounded.Article),
    RecentFileItem("IMG_8421.jpg", "4.8 MB - 5h ago", Icons.Rounded.Image),
    RecentFileItem("Brand_Guidelines_v2.pdf", "15.2 MB - Yesterday", Icons.Rounded.PictureAsPdf),
    RecentFileItem("Project_Assets_Archive", "245 MB - Oct 12", Icons.Rounded.FolderZip),
)
