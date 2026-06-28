package com.filesflow.features.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.filesflow.features.home.BrowseMode
import com.filesflow.features.home.CategoryFolderFilter
import com.filesflow.features.home.FileCategoryType
import com.filesflow.features.home.FilesFlowFile
import com.filesflow.features.home.favoriteFolderIdFor
import com.filesflow.features.home.isAllVisibleSelected
import com.filesflow.ui.theme.FilesFlowBackground
import com.filesflow.ui.theme.FilesFlowOnSurface
import com.filesflow.ui.theme.FilesFlowPrimary
import com.filesflow.ui.theme.FilesFlowPrimaryContainer
import com.filesflow.ui.theme.FilesFlowSecondary

@Composable
fun FileBrowserSection(
    browseMode: BrowseMode,
    files: List<FilesFlowFile>,
    isLoading: Boolean,
    categoryFolderFilters: List<CategoryFolderFilter> = emptyList(),
    selectedCategoryFolderId: String? = null,
    isSelectionMode: Boolean = false,
    selectedFileIds: Set<String> = emptySet(),
    favoriteFolderIds: Set<String> = emptySet(),
    destinationPickerActive: Boolean = false,
    needsStorageAccess: Boolean = false,
    onBackHome: () -> Unit,
    onFileClick: (FilesFlowFile) -> Unit,
    onFileLongClick: (FilesFlowFile) -> Unit,
    onMoreClick: (FilesFlowFile) -> Unit,
    onToggleFavoriteFolder: (FilesFlowFile) -> Unit = {},
    onSelectAllToggle: () -> Unit = {},
    onCategoryFolderClick: (CategoryFolderFilter) -> Unit = {},
    onRequestAccess: () -> Unit = {},
) {
    val showSelectAll = !isLoading && files.isNotEmpty() && !destinationPickerActive
    val allVisibleSelected = isAllVisibleSelected(files, selectedFileIds)
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 4.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBackHome) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back to dashboard",
                    tint = FilesFlowSecondary,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = browseTitle(browseMode),
                    color = FilesFlowOnSurface,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                )
                Text(
                    text = when {
                        destinationPickerActive -> "Open a folder, then use the button below to choose it"
                        isLoading -> "Loading files"
                        else -> "${files.size} items"
                    },
                    color = FilesFlowSecondary,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            if (showSelectAll) {
                IconButton(onClick = onSelectAllToggle) {
                    Icon(
                        imageVector = if (allVisibleSelected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                        contentDescription = if (allVisibleSelected) "Clear selection" else "Select all ${files.size} files",
                        tint = if (allVisibleSelected) FilesFlowPrimary else FilesFlowSecondary,
                    )
                }
            }
        }

        if (browseMode is BrowseMode.Category && categoryFolderFilters.isNotEmpty()) {
            CategoryFolderRibbon(
                folders = categoryFolderFilters,
                selectedFolderId = selectedCategoryFolderId,
                onFolderClick = onCategoryFolderClick,
            )
        }

        if (!isLoading && files.isEmpty()) {
            EmptyFilesCard(
                needsStorageAccess = needsStorageAccess,
                onRequestAccess = onRequestAccess,
            )
        } else if (browseMode is BrowseMode.Category && browseMode.type == FileCategoryType.Images) {
            ImageGalleryGrid(
                files = files,
                isSelectionMode = isSelectionMode,
                selectedFileIds = selectedFileIds,
                onImageClick = onFileClick,
                onImageLongClick = onFileLongClick,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                files.forEach { file ->
                    val favoriteId = favoriteFolderIdFor(file)
                    val favoriteClick = if (file.isDirectory && !destinationPickerActive) {
                        { onToggleFavoriteFolder(file) }
                    } else {
                        null
                    }
                    RecentFileRow(
                        file = file,
                        isSelectionMode = isSelectionMode,
                        isSelected = file.id in selectedFileIds,
                        isFavoriteFolder = file.isDirectory && favoriteId in favoriteFolderIds,
                        onClick = { onFileClick(file) },
                        onLongClick = { onFileLongClick(file) },
                        onMoreClick = { onMoreClick(file) },
                        onFavoriteClick = favoriteClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryFolderRibbon(
    folders: List<CategoryFolderFilter>,
    selectedFolderId: String?,
    onFolderClick: (CategoryFolderFilter) -> Unit,
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 4.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(
            items = folders,
            key = { it.id },
        ) { folder ->
            val selected = folder.id == selectedFolderId
            Text(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (selected) FilesFlowPrimaryContainer else FilesFlowBackground)
                    .clickable { onFolderClick(folder) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                text = folder.name,
                color = if (selected) FilesFlowPrimary else FilesFlowSecondary,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            )
        }
    }
}

@Composable
private fun EmptyFilesCard(
    needsStorageAccess: Boolean,
    onRequestAccess: () -> Unit,
) {
    NeumorphicSurface(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 12.dp,
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = if (needsStorageAccess) "Storage access required" else "No files found",
                color = FilesFlowOnSurface,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (needsStorageAccess) {
                    "FilesFlow needs permission to read your files before it can show anything. Tap below to open system settings, then enable Allow access to manage all files."
                } else {
                    "Grant storage access or try another category/search."
                },
                color = FilesFlowSecondary,
                style = MaterialTheme.typography.labelSmall,
            )
            if (needsStorageAccess) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onRequestAccess,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = FilesFlowPrimary,
                        contentColor = androidx.compose.ui.graphics.Color.White,
                    ),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Grant storage access",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    )
                }
            }
        }
    }
}

private fun browseTitle(browseMode: BrowseMode): String = when (browseMode) {
    BrowseMode.Home -> "Recent Files"
    is BrowseMode.Category -> browseMode.type.label
    is BrowseMode.Folder -> browseMode.displayName
    is BrowseMode.Search -> "Search: ${browseMode.query}"
}
