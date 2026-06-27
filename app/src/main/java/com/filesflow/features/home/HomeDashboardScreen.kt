package com.filesflow.features.home

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
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.filesflow.features.home.components.FileActionsCard
import com.filesflow.features.home.components.FileBrowserSection
import com.filesflow.features.home.components.CategoryGrid
import com.filesflow.features.home.components.FilesFlowTopBar
import com.filesflow.features.home.components.NeumorphicSurface
import com.filesflow.features.home.components.PermissionPanel
import com.filesflow.features.home.components.RecentFilesList
import com.filesflow.features.home.components.StorageOverviewCard
import com.filesflow.ui.theme.FilesFlowBackground
import com.filesflow.ui.theme.FilesFlowOnSurface
import com.filesflow.ui.theme.FilesFlowPrimary
import com.filesflow.ui.theme.FilesFlowSecondary

@Composable
fun HomeDashboardScreen(
    viewModel: FilesFlowViewModel,
    onRequestMediaAccess: () -> Unit,
    onRequestSafFolder: () -> Unit,
    onRequestAllFilesAccess: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()

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
                StorageOverviewCard(
                    usedPercent = uiState.storageOverview.usedPercent,
                    usedLabel = uiState.storageOverview.usedLabel,
                    totalLabel = uiState.storageOverview.totalLabel,
                )
                PermissionPanel(
                    accessState = uiState.accessState,
                    destinationFolderName = uiState.destinationFolderName,
                    onRequestMediaAccess = onRequestMediaAccess,
                    onRequestSafFolder = onRequestSafFolder,
                    onRequestAllFilesAccess = onRequestAllFilesAccess,
                    onBrowse = viewModel::openBrowseRoot,
                )
                SearchAndBrowseCard(
                    query = uiState.searchQuery,
                    onQueryChange = viewModel::search,
                    onBrowse = viewModel::openBrowseRoot,
                    onClear = viewModel::openHome,
                )
                CategoryGrid(
                    summaries = uiState.categories,
                    onCategoryClick = viewModel::openCategory,
                )

                when (uiState.browseMode) {
                    BrowseMode.Home -> RecentFilesList(
                        files = uiState.recentFiles,
                        onViewAll = viewModel::openBrowseRoot,
                        onFileClick = viewModel::openFolder,
                        onMoreClick = viewModel::selectFile,
                    )
                    else -> FileBrowserSection(
                        browseMode = uiState.browseMode,
                        files = uiState.visibleFiles,
                        isLoading = uiState.isLoading,
                        onBackHome = viewModel::openHome,
                        onFileClick = viewModel::openFolder,
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
                onDelete = { viewModel.runOperation(FileOperation.Delete, file) },
                onChooseFolder = onRequestSafFolder,
                onDismiss = viewModel::dismissActions,
            )
        }
    }
}

@Composable
private fun SearchAndBrowseCard(
    query: String,
    onQueryChange: (String) -> Unit,
    onBrowse: () -> Unit,
    onClear: () -> Unit,
) {
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
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        tint = FilesFlowSecondary,
                    )
                },
                trailingIcon = {
                    if (query.isNotBlank()) {
                        IconButton(onClick = onClear) {
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
) {
    Column(modifier = modifier) {
        androidx.compose.material3.Text(
            text = title,
            color = FilesFlowOnSurface,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
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
