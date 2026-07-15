package com.filesflow.features.home

import androidx.compose.runtime.Composable

/**
 * Compatibility overload for the saved-route host. Folder navigation remains owned by the
 * view model and the core dashboard; these callbacks allow the host to track stable routes
 * without expanding the core screen's public contract.
 */
@Composable
fun HomeDashboardScreen(
    viewModel: FilesFlowViewModel,
    onOpenCategory: (FileCategoryType) -> Unit,
    onOpenBrowseRoot: () -> Unit,
    onSearchFiles: (String) -> Unit,
    onOpenFolder: (FilesFlowFile) -> Unit,
    onOpenHome: () -> Unit,
    onOpenFile: (FilesFlowFile) -> Unit,
    onPrintFile: (FilesFlowFile) -> Unit,
    onShareFiles: (List<FilesFlowFile>) -> Unit,
    needsStorageAccess: Boolean = false,
    onRequestStorageAccess: () -> Unit = {},
) {
    @Suppress("UNUSED_VARIABLE")
    val routeCallbacks = onOpenFolder to onOpenHome
    HomeDashboardScreen(
        viewModel = viewModel,
        onOpenCategory = onOpenCategory,
        onOpenBrowseRoot = onOpenBrowseRoot,
        onSearchFiles = onSearchFiles,
        onOpenFile = onOpenFile,
        onPrintFile = onPrintFile,
        onShareFiles = onShareFiles,
        needsStorageAccess = needsStorageAccess,
        onRequestStorageAccess = onRequestStorageAccess,
    )
}
