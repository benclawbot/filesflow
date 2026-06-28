package com.filesflow.features.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import com.filesflow.features.home.components.FileActionsCard
import com.filesflow.features.home.components.FileBrowserSection
import com.filesflow.features.home.components.CategoryGrid
import com.filesflow.features.home.components.FilesFlowTopBar
import com.filesflow.features.home.components.NeumorphicSurface
import com.filesflow.features.home.components.RecentFilesList
import com.filesflow.features.home.components.StorageOverviewCard
import com.filesflow.ui.theme.FilesFlowAccentOrange
import com.filesflow.ui.theme.FilesFlowBackground
import com.filesflow.ui.theme.FilesFlowOnSurface
import com.filesflow.ui.theme.FilesFlowPrimary
import com.filesflow.ui.theme.FilesFlowSecondary

@Composable
fun HomeDashboardScreen(
    viewModel: FilesFlowViewModel,
    onOpenCategory: (FileCategoryType) -> Unit,
    onOpenBrowseRoot: () -> Unit,
    onSearchFiles: (String) -> Unit,
    onOpenFile: (FilesFlowFile) -> Unit,
    onShareFiles: (List<FilesFlowFile>) -> Unit,
    onRequestDestinationFolder: (FileOperation, FilesFlowFile) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val isBrowserMode = uiState.browseMode != BrowseMode.Home
    val selectableFiles = if (uiState.browseMode == BrowseMode.Home) uiState.recentFiles else uiState.visibleFiles
    val selectedFiles = selectableFiles.filter { it.id in uiState.selectedFileIds }

    fun handleFileClick(file: FilesFlowFile) {
        if (uiState.isSelectionMode) {
            viewModel.toggleFileSelection(file)
        } else if (file.isDirectory) {
            viewModel.openFolder(file)
        } else {
            onOpenFile(file)
        }
    }

    BackHandler(enabled = uiState.isSelectionMode || uiState.selectedFile != null || isBrowserMode) {
        if (uiState.isSelectionMode) {
            viewModel.clearSelection()
        } else if (uiState.selectedFile != null) {
            viewModel.dismissActions()
        } else {
            viewModel.openHome()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        FilesFlowTopBar()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(FilesFlowBackground),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = FilesFlowPortraitWidthDp.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(
                        PaddingValues(
                            start = FilesFlowHorizontalPaddingDp.dp,
                            top = 24.dp,
                            end = FilesFlowHorizontalPaddingDp.dp,
                            bottom = 128.dp,
                        ),
                    ),
                    verticalArrangement = Arrangement.spacedBy(32.dp),
            ) {
                if (selectedFiles.isNotEmpty()) {
                    BatchSelectionBar(
                        selectedCount = selectedFiles.size,
                        onDelete = viewModel::deleteSelectedFiles,
                        onShare = {
                            viewModel.clearSelection()
                            onShareFiles(selectedFiles)
                        },
                        onClear = viewModel::clearSelection,
                    )
                }

                if (isBrowserMode) {
                    FileBrowserSection(
                        browseMode = uiState.browseMode,
                        files = uiState.visibleFiles,
                        isLoading = uiState.isLoading,
                        categoryFolderFilters = uiState.categoryFolderFilters,
                        selectedCategoryFolderId = uiState.selectedCategoryFolderId,
                        isSelectionMode = uiState.isSelectionMode,
                        selectedFileIds = uiState.selectedFileIds,
                        onBackHome = viewModel::openHome,
                        onFileClick = ::handleFileClick,
                        onFileLongClick = viewModel::startFileSelection,
                        onMoreClick = viewModel::selectFile,
                        onCategoryFolderClick = viewModel::toggleCategoryFolder,
                    )
                } else {
                    StorageOverviewCard(
                        usedPercent = uiState.storageOverview.usedPercent,
                        usedLabel = uiState.storageOverview.usedLabel,
                        totalLabel = uiState.storageOverview.totalLabel,
                        onClick = onOpenBrowseRoot,
                    )
                    SearchAndBrowseCard(
                        query = uiState.searchQuery,
                        onSearch = onSearchFiles,
                        onBrowse = onOpenBrowseRoot,
                        onClear = viewModel::openHome,
                    )
                    CategoryGrid(
                        summaries = uiState.categories,
                        onCategoryClick = onOpenCategory,
                    )
                    RecentFilesList(
                        files = uiState.recentFiles,
                        isSelectionMode = uiState.isSelectionMode,
                        selectedFileIds = uiState.selectedFileIds,
                        onViewAll = onOpenBrowseRoot,
                        onFileClick = ::handleFileClick,
                        onFileLongClick = viewModel::startFileSelection,
                        onMoreClick = viewModel::selectFile,
                    )
                }

                uiState.operationStatus?.let { status ->
                    OperationStatusCard(
                        status = status,
                        onDismiss = viewModel::dismissStatus,
                    )
                }
            }
        }
    }

    uiState.selectedFile?.let { file ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(FilesFlowBackground.copy(alpha = 0.88f))
                .padding(FilesFlowHorizontalPaddingDp.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            FileActionsCard(
                modifier = Modifier.widthIn(max = FilesFlowPortraitWidthDp.dp),
                file = file,
                hasDestinationFolder = uiState.destinationFolderName != null,
                onCopy = { viewModel.runOperation(FileOperation.Copy, file) },
                onMove = { viewModel.runOperation(FileOperation.Move, file) },
                onRename = { newName -> viewModel.renameFile(file, newName) },
                onDelete = { viewModel.runOperation(FileOperation.Delete, file) },
                onChooseFolder = { operation -> onRequestDestinationFolder(operation, file) },
                onDismiss = viewModel::dismissActions,
            )
        }
    }
}

@Composable
private fun SearchAndBrowseCard(
    query: String,
    onSearch: (String) -> Unit,
    onBrowse: () -> Unit,
    onClear: () -> Unit,
) {
    var draftQuery by remember { mutableStateOf(query) }

    LaunchedEffect(query) {
        draftQuery = query
    }

    NeumorphicSurface(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 12.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextHeader(
                    modifier = Modifier.weight(1f),
                    title = "Find Files",
                    titleColor = FilesFlowAccentOrange,
                    subtitle = "Search by name or browse granted storage",
                )
                IconButton(onClick = onBrowse) {
                    Icon(
                        imageVector = Icons.Rounded.FolderOpen,
                        contentDescription = "Browse files",
                        tint = FilesFlowPrimary,
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = draftQuery,
                onValueChange = { draftQuery = it },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = { onSearch(draftQuery) },
                ),
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        tint = FilesFlowSecondary,
                    )
                },
                trailingIcon = {
                    if (draftQuery.isNotBlank()) {
                        IconButton(
                            onClick = {
                                draftQuery = ""
                                onClear()
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Clear search",
                                tint = FilesFlowSecondary,
                            )
                        }
                    }
                },
                placeholder = { androidx.compose.material3.Text("Search FilesFlow") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = FilesFlowPrimary,
                    unfocusedBorderColor = FilesFlowSecondary.copy(alpha = 0.28f),
                    focusedContainerColor = FilesFlowBackground,
                    unfocusedContainerColor = FilesFlowBackground,
                ),
            )
        }
    }
}

@Composable
private fun BatchSelectionBar(
    selectedCount: Int,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    onClear: () -> Unit,
) {
    NeumorphicSurface(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 12.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 10.dp, end = 8.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                modifier = Modifier.weight(1f),
                text = "$selectedCount selected",
                color = FilesFlowOnSurface,
                style = MaterialTheme.typography.labelMedium,
            )
            IconButton(onClick = onShare) {
                Icon(
                    imageVector = Icons.Rounded.Share,
                    contentDescription = "Share selected files",
                    tint = FilesFlowPrimary,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = "Delete selected files",
                    tint = FilesFlowPrimary,
                )
            }
            IconButton(onClick = onClear) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Clear selection",
                    tint = FilesFlowSecondary,
                )
            }
        }
    }
}

@Composable
private fun OperationStatusCard(
    status: FileOperationStatus,
    onDismiss: () -> Unit,
) {
    NeumorphicSurface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onDismiss),
        cornerRadius = 12.dp,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextHeader(
                modifier = Modifier.weight(1f),
                title = status.title,
                subtitle = status.detail,
            )
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = FilesFlowPrimary,
                    contentColor = FilesFlowBackground,
                ),
            ) {
                androidx.compose.material3.Text("OK")
            }
        }
    }
}

@Composable
private fun TextHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    titleColor: androidx.compose.ui.graphics.Color = FilesFlowOnSurface,
) {
    Column(modifier = modifier) {
        androidx.compose.material3.Text(
            text = title,
            color = titleColor,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        androidx.compose.material3.Text(
            text = subtitle,
            color = FilesFlowSecondary,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
