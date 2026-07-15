package com.filesflow.features.home

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class FilesFlowViewModel(
    private val repository: FileManagerRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(FilesFlowUiState())
    val uiState: StateFlow<FilesFlowUiState> = _uiState.asStateFlow()

    fun refresh(accessState: StorageAccessState = _uiState.value.accessState) {
        _uiState.update {
            it.copy(
                isLoading = true,
                accessState = accessState,
                destinationFolderName = repository.getPersistedSafFolderName(),
                favoriteFolders = repository.getFavoriteFolders(),
            )
        }
        viewModelScope.launch {
            val storage = runCatching { repository.getStorageOverview() }.getOrElse { emptyStorageOverview }
            val categories = runCatching { repository.getCategorySummaries() }.getOrElse { emptyFileCategorySummaries() }
            val recentFiles = runCatching { repository.getRecentFiles() }.getOrDefault(emptyList())
            _uiState.update {
                it.copy(
                    storageOverview = storage,
                    categories = categories,
                    recentFiles = recentFiles,
                    favoriteFolders = repository.getFavoriteFolders(),
                    isLoading = false,
                )
            }
        }
    }

    fun updateAccessState(accessState: StorageAccessState) {
        _uiState.update {
            it.copy(
                accessState = accessState,
                destinationFolderName = repository.getPersistedSafFolderName(),
                favoriteFolders = repository.getFavoriteFolders(),
            )
        }
    }

    fun openHome() {
        _uiState.update {
            it.copy(
                browseMode = BrowseMode.Home,
                visibleFiles = emptyList(),
                allCategoryFiles = emptyList(),
                categoryFolderFilters = emptyList(),
                selectedCategoryFolderId = null,
                searchQuery = "",
                selectedFile = null,
                selectedFileIds = emptySet(),
                destinationSelection = null,
            )
        }
    }

    fun openCategory(type: FileCategoryType) {
        _uiState.update {
            it.copy(
                isLoading = true,
                browseMode = BrowseMode.Category(type),
                selectedFile = null,
                selectedFileIds = emptySet(),
                selectedCategoryFolderId = null,
                destinationSelection = null,
            )
        }
        viewModelScope.launch {
            val browserFiles = loadBrowserFiles(BrowseMode.Category(type), selectedCategoryFolderId = null)
            _uiState.update { it.withBrowserFiles(browserFiles).copy(isLoading = false) }
        }
    }

    fun openBrowseRoot() {
        _uiState.update {
            it.copy(
                isLoading = true,
                browseMode = BrowseMode.Folder(null, "Browse Files"),
                selectedFile = null,
                selectedFileIds = emptySet(),
                allCategoryFiles = emptyList(),
                categoryFolderFilters = emptyList(),
                selectedCategoryFolderId = null,
                destinationSelection = null,
            )
        }
        viewModelScope.launch {
            val browserFiles = loadBrowserFiles(BrowseMode.Folder(null, "Browse Files"), selectedCategoryFolderId = null)
            _uiState.update { it.withBrowserFiles(browserFiles).copy(isLoading = false) }
        }
    }

    fun openFavoriteFolder(folder: FavoriteFolder) {
        openFolder(folder.toFilesFlowFile())
    }

    fun openFolder(file: FilesFlowFile) {
        if (!file.isDirectory) return
        val mode = BrowseMode.Folder(
            uri = file.uri,
            displayName = file.name,
            path = file.path,
            source = file.source,
        )
        _uiState.update {
            it.copy(
                isLoading = true,
                browseMode = mode,
                selectedFile = null,
                selectedFileIds = emptySet(),
                allCategoryFiles = emptyList(),
                categoryFolderFilters = emptyList(),
                selectedCategoryFolderId = null,
            )
        }
        viewModelScope.launch {
            val browserFiles = loadBrowserFiles(mode, selectedCategoryFolderId = null)
            _uiState.update { it.withBrowserFiles(browserFiles).copy(isLoading = false) }
        }
    }

    fun showFileOpenFailed(fileName: String) {
        showStatus("Open failed", "Android could not find an app to open $fileName.")
    }

    fun showPrintFailed(fileName: String) {
        showStatus("Print unavailable", "Android could not prepare $fileName for printing.")
    }

    fun showShareFailed() {
        showStatus("Share failed", "Android could not prepare those files for sharing.")
    }

    fun search(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        if (query.isBlank()) {
            openHome()
            return
        }
        val mode = BrowseMode.Search(query)
        _uiState.update {
            it.copy(
                isLoading = true,
                browseMode = mode,
                selectedFile = null,
                selectedFileIds = emptySet(),
                allCategoryFiles = emptyList(),
                categoryFolderFilters = emptyList(),
                selectedCategoryFolderId = null,
                destinationSelection = null,
            )
        }
        viewModelScope.launch {
            val browserFiles = loadBrowserFiles(mode, selectedCategoryFolderId = null)
            _uiState.update { it.withBrowserFiles(browserFiles).copy(isLoading = false) }
        }
    }

    fun selectFile(file: FilesFlowFile) {
        _uiState.update { it.copy(selectedFile = file, selectedFileIds = emptySet()) }
    }

    fun startFileSelection(file: FilesFlowFile) {
        _uiState.update { it.copy(selectedFile = null, selectedFileIds = setOf(file.id)) }
    }

    fun toggleFileSelection(file: FilesFlowFile) {
        _uiState.update {
            it.copy(
                selectedFile = null,
                selectedFileIds = toggledSelectedFileIds(it.selectedFileIds, file),
            )
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedFileIds = emptySet()) }
    }

    fun toggleSelectAllVisible() {
        _uiState.update { state ->
            val visible = state.visibleFiles
            when {
                visible.isEmpty() -> state.copy(selectedFileIds = emptySet(), selectedFile = null)
                isAllVisibleSelected(visible, state.selectedFileIds) ->
                    state.copy(selectedFileIds = emptySet(), selectedFile = null)
                else -> state.copy(
                    selectedFileIds = selectAllVisibleFileIds(visible),
                    selectedFile = null,
                )
            }
        }
    }

    fun toggleCategoryFolder(folder: CategoryFolderFilter) {
        _uiState.update {
            val selectedId = toggledCategoryFolderSelection(it.selectedCategoryFolderId, folder.id)
            it.copy(
                selectedCategoryFolderId = selectedId,
                visibleFiles = filesForCategoryFolder(it.allCategoryFiles, selectedId),
                selectedFile = null,
                selectedFileIds = emptySet(),
            )
        }
    }

    fun toggleFavoriteFolder(folder: FilesFlowFile) {
        val status = repository.toggleFavoriteFolder(folder)
        _uiState.update {
            it.copy(
                favoriteFolders = repository.getFavoriteFolders(),
                operationStatus = status,
                selectedFile = null,
            )
        }
    }

    fun dismissActions() {
        _uiState.update { it.copy(selectedFile = null) }
    }

    fun dismissStatus() {
        _uiState.update { it.copy(operationStatus = null) }
    }

    fun showAccessRequired() {
        showStatus("Storage access needed", "Android needs file access before FilesFlow can open that location.")
    }

    fun persistSafFolder(uri: Uri) {
        repository.persistSafFolder(uri)
        _uiState.update {
            it.copy(
                destinationFolderName = repository.getPersistedSafFolderName(),
                accessState = it.accessState.copy(hasSafFolder = true),
                operationStatus = FileOperationStatus(
                    "Folder selected",
                    "FilesFlow can now copy or move files into the selected folder.",
                ),
            )
        }
    }

    fun startDestinationSelection(operation: FileOperation, file: FilesFlowFile) {
        startDestinationSelection(operation, listOf(file))
    }

    fun startDestinationSelection(operation: FileOperation, files: List<FilesFlowFile>) {
        val transferableItems = files.distinctBy { it.id }
        if (transferableItems.isEmpty()) return

        val state = _uiState.value
        val selection = DestinationSelection(
            operation = operation,
            files = transferableItems,
            returnBrowseMode = state.browseMode,
            returnSelectedCategoryFolderId = state.selectedCategoryFolderId,
        )
        _uiState.update {
            it.copy(
                isLoading = true,
                browseMode = BrowseMode.Folder(null, "Browse Files"),
                selectedFile = null,
                selectedFileIds = emptySet(),
                destinationSelection = selection,
                allCategoryFiles = emptyList(),
                categoryFolderFilters = emptyList(),
                selectedCategoryFolderId = null,
            )
        }
        viewModelScope.launch {
            val browserFiles = loadBrowserFiles(BrowseMode.Folder(null, "Browse Files"), selectedCategoryFolderId = null)
            _uiState.update { it.withBrowserFiles(browserFiles).copy(isLoading = false) }
        }
    }

    fun cancelDestinationSelection() {
        val selection = _uiState.value.destinationSelection ?: return
        _uiState.update {
            it.copy(
                isLoading = true,
                destinationSelection = null,
                selectedFile = null,
                selectedFileIds = emptySet(),
            )
        }
        viewModelScope.launch {
            restoreBrowseMode(
                mode = selection.returnBrowseMode,
                selectedCategoryFolderId = selection.returnSelectedCategoryFolderId,
            )
        }
    }

    fun confirmFavoriteDestination(folder: FavoriteFolder) {
        val selection = _uiState.value.destinationSelection ?: return
        executeDestinationSelection(selection, folder.toFilesFlowFile())
    }

    fun confirmDestinationSelection() {
        val selection = _uiState.value.destinationSelection ?: return
        val destinationMode = _uiState.value.browseMode
        _uiState.update { it.copy(isLoading = true, selectedFile = null, selectedFileIds = emptySet()) }
        viewModelScope.launch {
            val destination = destinationFolderForBrowseMode(destinationMode, repository.getBrowseRootFolder())
            val status = if (destination == null) {
                FileOperationStatus("Choose a folder", "Open a folder in Browse Files before validating the destination.")
            } else {
                runDestinationOperation(selection, destination)
            }
            restoreBrowseMode(
                mode = selection.returnBrowseMode,
                selectedCategoryFolderId = selection.returnSelectedCategoryFolderId,
                status = status,
            )
            refresh()
        }
    }

    private fun executeDestinationSelection(selection: DestinationSelection, destination: FilesFlowFile) {
        _uiState.update { it.copy(isLoading = true, selectedFile = null, selectedFileIds = emptySet()) }
        viewModelScope.launch {
            val status = runDestinationOperation(selection, destination)
            restoreBrowseMode(
                mode = selection.returnBrowseMode,
                selectedCategoryFolderId = selection.returnSelectedCategoryFolderId,
                status = status,
            )
            refresh()
        }
    }

    private suspend fun runDestinationOperation(
        selection: DestinationSelection,
        destinationFolder: FilesFlowFile,
    ): FileOperationStatus {
        val statuses = selection.files.map { file ->
            when (selection.operation) {
                FileOperation.Copy -> repository.copyToFolder(file, destinationFolder)
                FileOperation.Move -> repository.moveToFolder(file, destinationFolder)
                FileOperation.Delete -> repository.delete(file)
            }
        }
        if (selection.files.size == 1) return statuses.single()

        val expectedTitle = selection.operation.successTitle()
        val succeeded = statuses.count { it.title == expectedTitle }
        val copiedOnly = statuses.count { it.title == "Copied only" }
        val failed = statuses.size - succeeded - copiedOnly
        val itemLabel = "item".pluralized(selection.files.size)

        return when {
            failed == 0 && copiedOnly == 0 -> FileOperationStatus(
                title = expectedTitle,
                detail = "$succeeded selected $itemLabel ${selection.operation.pastTense()} to ${destinationFolder.name}.",
            )
            selection.operation == FileOperation.Move && failed == 0 -> FileOperationStatus(
                title = "Move partially completed",
                detail = "$succeeded moved; $copiedOnly copied completely but retained at the source because Android denied deletion.",
            )
            succeeded + copiedOnly > 0 -> FileOperationStatus(
                title = "Some items ${selection.operation.pastTense()}",
                detail = buildString {
                    append("${succeeded + copiedOnly} of ${selection.files.size} selected $itemLabel were transferred to ${destinationFolder.name}.")
                    if (copiedOnly > 0) append(" $copiedOnly remained at the source.")
                    append(" $failed failed.")
                },
            )
            else -> FileOperationStatus(
                title = "${selection.operation.label()} failed",
                detail = "FilesFlow could not ${selection.operation.verb()} the selected $itemLabel to ${destinationFolder.name}.",
            )
        }
    }

    fun runOperation(operation: FileOperation, file: FilesFlowFile) {
        _uiState.update { it.copy(isLoading = true, selectedFile = null, selectedFileIds = emptySet()) }
        viewModelScope.launch {
            val mode = _uiState.value.browseMode
            val selectedCategoryFolderId = _uiState.value.selectedCategoryFolderId
            val status = when (operation) {
                FileOperation.Copy -> repository.copyToSafFolder(file)
                FileOperation.Move -> repository.moveToSafFolder(file)
                FileOperation.Delete -> repository.delete(file)
            }
            val browserFiles = loadBrowserFiles(mode, selectedCategoryFolderId)
            _uiState.update { it.withBrowserFiles(browserFiles).copy(operationStatus = status, isLoading = false) }
            refresh()
        }
    }

    fun deleteSelectedFiles() {
        val state = _uiState.value
        val files = state.selectableFiles().filter { it.id in state.selectedFileIds }
        if (files.isEmpty()) return

        _uiState.update { it.copy(isLoading = true, selectedFile = null, selectedFileIds = emptySet()) }
        viewModelScope.launch {
            val mode = _uiState.value.browseMode
            val selectedCategoryFolderId = _uiState.value.selectedCategoryFolderId
            val statuses = files.map { repository.delete(it) }
            val deletedCount = statuses.count { it.title == "Deleted" }
            val itemLabel = "item".pluralized(files.size)
            val status = when {
                deletedCount == files.size -> FileOperationStatus("Deleted", "$deletedCount selected $itemLabel deleted.")
                deletedCount > 0 -> FileOperationStatus("Some items deleted", "$deletedCount of ${files.size} selected $itemLabel were deleted.")
                else -> FileOperationStatus("Delete unavailable", "Android did not allow FilesFlow to delete the selected $itemLabel.")
            }
            val browserFiles = loadBrowserFiles(mode, selectedCategoryFolderId)
            _uiState.update { it.withBrowserFiles(browserFiles).copy(operationStatus = status, isLoading = false) }
            refresh()
        }
    }

    fun renameFile(file: FilesFlowFile, newName: String) {
        _uiState.update { it.copy(isLoading = true, selectedFile = null, selectedFileIds = emptySet()) }
        viewModelScope.launch {
            val mode = _uiState.value.browseMode
            val selectedCategoryFolderId = _uiState.value.selectedCategoryFolderId
            val status = repository.rename(file, newName)
            val browserFiles = loadBrowserFiles(mode, selectedCategoryFolderId)
            _uiState.update { it.withBrowserFiles(browserFiles).copy(operationStatus = status, isLoading = false) }
            refresh()
        }
    }

    private suspend fun loadBrowserFiles(mode: BrowseMode, selectedCategoryFolderId: String?): BrowserFiles {
        val allFiles = runCatching {
            when (mode) {
                BrowseMode.Home -> emptyList()
                is BrowseMode.Category -> repository.listCategory(mode.type)
                is BrowseMode.Folder -> if (mode.uri == null && mode.path == null) {
                    repository.listBrowseRoot()
                } else {
                    repository.listFolder(
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
                        ),
                    )
                }
                is BrowseMode.Search -> repository.searchFiles(mode.query)
            }
        }.getOrDefault(emptyList())

        if (mode !is BrowseMode.Category) return BrowserFiles(visibleFiles = allFiles)

        val folders = categoryFolderFilters(allFiles)
        val activeFolderId = selectedCategoryFolderId?.takeIf { selectedId -> folders.any { it.id == selectedId } }
        return BrowserFiles(
            visibleFiles = filesForCategoryFolder(allFiles, activeFolderId),
            allCategoryFiles = allFiles,
            categoryFolderFilters = folders,
            selectedCategoryFolderId = activeFolderId,
        )
    }

    private suspend fun restoreBrowseMode(
        mode: BrowseMode,
        selectedCategoryFolderId: String?,
        status: FileOperationStatus? = null,
    ) {
        if (mode == BrowseMode.Home) {
            _uiState.update {
                it.copy(
                    browseMode = BrowseMode.Home,
                    visibleFiles = emptyList(),
                    allCategoryFiles = emptyList(),
                    categoryFolderFilters = emptyList(),
                    selectedCategoryFolderId = null,
                    searchQuery = "",
                    selectedFile = null,
                    selectedFileIds = emptySet(),
                    destinationSelection = null,
                    operationStatus = status ?: it.operationStatus,
                    isLoading = false,
                )
            }
            return
        }

        val browserFiles = loadBrowserFiles(mode, selectedCategoryFolderId)
        _uiState.update {
            it.withBrowserFiles(browserFiles).copy(
                browseMode = mode,
                searchQuery = if (mode is BrowseMode.Search) mode.query else it.searchQuery,
                selectedFile = null,
                selectedFileIds = emptySet(),
                destinationSelection = null,
                operationStatus = status ?: it.operationStatus,
                isLoading = false,
            )
        }
    }

    private fun showStatus(title: String, detail: String) {
        _uiState.update {
            it.copy(
                operationStatus = FileOperationStatus(title, detail),
                isLoading = false,
            )
        }
    }

    private fun FilesFlowUiState.withBrowserFiles(browserFiles: BrowserFiles): FilesFlowUiState {
        return copy(
            visibleFiles = browserFiles.visibleFiles,
            allCategoryFiles = browserFiles.allCategoryFiles,
            categoryFolderFilters = browserFiles.categoryFolderFilters,
            selectedCategoryFolderId = browserFiles.selectedCategoryFolderId,
        )
    }

    private fun FilesFlowUiState.selectableFiles(): List<FilesFlowFile> {
        return if (browseMode == BrowseMode.Home) recentFiles else visibleFiles
    }

    private fun String.pluralized(count: Int): String = if (count == 1) this else "${this}s"

    private fun FileOperation.label(): String = when (this) {
        FileOperation.Copy -> "Copy"
        FileOperation.Move -> "Move"
        FileOperation.Delete -> "Delete"
    }

    private fun FileOperation.verb(): String = when (this) {
        FileOperation.Copy -> "copy"
        FileOperation.Move -> "move"
        FileOperation.Delete -> "delete"
    }

    private fun FileOperation.pastTense(): String = when (this) {
        FileOperation.Copy -> "copied"
        FileOperation.Move -> "moved"
        FileOperation.Delete -> "deleted"
    }

    private fun FileOperation.successTitle(): String = when (this) {
        FileOperation.Copy -> "Copied"
        FileOperation.Move -> "Moved"
        FileOperation.Delete -> "Deleted"
    }

    private data class BrowserFiles(
        val visibleFiles: List<FilesFlowFile>,
        val allCategoryFiles: List<FilesFlowFile> = emptyList(),
        val categoryFolderFilters: List<CategoryFolderFilter> = emptyList(),
        val selectedCategoryFolderId: String? = null,
    )
}
