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
                    isLoading = false,
                )
            }
        }
    }

    fun updateAccessState(accessState: StorageAccessState) {
        _uiState.update { it.copy(accessState = accessState, destinationFolderName = repository.getPersistedSafFolderName()) }
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

    fun openFolder(file: FilesFlowFile) {
        if (!file.isDirectory) {
            return
        }
        _uiState.update {
            it.copy(
                isLoading = true,
                browseMode = BrowseMode.Folder(
                    uri = file.uri,
                    displayName = file.name,
                    path = file.path,
                    source = file.source,
                ),
                selectedFile = null,
                selectedFileIds = emptySet(),
                allCategoryFiles = emptyList(),
                categoryFolderFilters = emptyList(),
                selectedCategoryFolderId = null,
            )
        }
        viewModelScope.launch {
            val browserFiles = loadBrowserFiles(
                mode = BrowseMode.Folder(
                    uri = file.uri,
                    displayName = file.name,
                    path = file.path,
                    source = file.source,
                ),
                selectedCategoryFolderId = null,
            )
            _uiState.update { it.withBrowserFiles(browserFiles).copy(isLoading = false) }
        }
    }

    fun showFileOpenFailed(fileName: String) {
        _uiState.update {
            it.copy(
                operationStatus = FileOperationStatus(
                    title = "Open failed",
                    detail = "Android could not find an app to open $fileName.",
                ),
                isLoading = false,
            )
        }
    }

    fun showShareFailed() {
        _uiState.update {
            it.copy(
                operationStatus = FileOperationStatus(
                    title = "Share failed",
                    detail = "Android could not prepare those files for sharing.",
                ),
                isLoading = false,
            )
        }
    }

    fun search(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        if (query.isBlank()) {
            openHome()
            return
        }
        _uiState.update {
            it.copy(
                isLoading = true,
                browseMode = BrowseMode.Search(query),
                selectedFile = null,
                selectedFileIds = emptySet(),
                allCategoryFiles = emptyList(),
                categoryFolderFilters = emptyList(),
                selectedCategoryFolderId = null,
                destinationSelection = null,
            )
        }
        viewModelScope.launch {
            val browserFiles = loadBrowserFiles(BrowseMode.Search(query), selectedCategoryFolderId = null)
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

    /**
     * Toggle every visible file in the current category view into/out of the
     * multi-selection. Visible-files-aware so it respects any active
     * category-folder filter. If nothing is selected, all visible files are
     * added; if everything visible is already selected, the selection is
     * cleared.
     */
    fun toggleSelectAllVisible() {
        _uiState.update { state ->
            val visible = state.visibleFiles
            when {
                visible.isEmpty() -> state.copy(selectedFileIds = emptySet(), selectedFile = null)
                isAllVisibleSelected(visible, state.selectedFileIds) ->
                    state.copy(selectedFileIds = emptySet(), selectedFile = null)
                else ->
                    state.copy(
                        selectedFileIds = selectAllVisibleFileIds(visible),
                        selectedFile = null,
                    )
            }
        }
    }

    fun toggleCategoryFolder(folder: CategoryFolderFilter) {
        _uiState.update {
            val nextSelectedFolderId = toggledCategoryFolderSelection(it.selectedCategoryFolderId, folder.id)
            it.copy(
                selectedCategoryFolderId = nextSelectedFolderId,
                visibleFiles = filesForCategoryFolder(it.allCategoryFiles, nextSelectedFolderId),
                selectedFile = null,
                selectedFileIds = emptySet(),
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
        _uiState.update {
            it.copy(
                operationStatus = FileOperationStatus(
                    title = "Storage access needed",
                    detail = "Android needs file access before FilesFlow can open that location.",
                ),
                isLoading = false,
            )
        }
    }

    fun persistSafFolder(uri: Uri) {
        repository.persistSafFolder(uri)
        _uiState.update {
            it.copy(
                destinationFolderName = repository.getPersistedSafFolderName(),
                accessState = it.accessState.copy(hasSafFolder = true),
                operationStatus = FileOperationStatus("Folder selected", "FilesFlow can now copy or move files into the selected folder."),
            )
        }
    }

    fun startDestinationSelection(operation: FileOperation, file: FilesFlowFile) {
        startDestinationSelection(operation, listOf(file))
    }

    fun startDestinationSelection(operation: FileOperation, files: List<FilesFlowFile>) {
        val movableFiles = files.filterNot { it.isDirectory }
        if (movableFiles.isEmpty()) return

        val state = _uiState.value
        val selection = DestinationSelection(
            operation = operation,
            files = movableFiles,
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
        _uiState.update { it.copy(isLoading = true, destinationSelection = null, selectedFile = null, selectedFileIds = emptySet()) }
        viewModelScope.launch {
            restoreBrowseMode(
                mode = selection.returnBrowseMode,
                selectedCategoryFolderId = selection.returnSelectedCategoryFolderId,
            )
        }
    }

    fun confirmDestinationSelection() {
        val selection = _uiState.value.destinationSelection ?: return
        val destinationMode = _uiState.value.browseMode
        _uiState.update { it.copy(isLoading = true, selectedFile = null, selectedFileIds = emptySet()) }
        viewModelScope.launch {
            val destinationFolder = destinationFolderForBrowseMode(destinationMode, repository.getBrowseRootFolder())
            val status = if (destinationFolder == null) {
                FileOperationStatus("Choose a folder", "Open a folder in Browse Files before validating the destination.")
            } else {
                runDestinationOperation(selection, destinationFolder)
            }
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
        val successTitle = when (selection.operation) {
            FileOperation.Copy -> "Copied"
            FileOperation.Move -> "Moved"
            FileOperation.Delete -> "Deleted"
        }
        val successCount = statuses.count { it.title == successTitle }
        val fileLabel = "file".pluralized(selection.files.size)
        return when {
            selection.files.size == 1 -> statuses.single()
            successCount == selection.files.size -> FileOperationStatus(
                title = successTitle,
                detail = "$successCount selected $fileLabel ${selection.operation.pastTense()} to ${destinationFolder.name}.",
            )
            successCount > 0 -> FileOperationStatus(
                title = "Some files ${selection.operation.pastTense()}",
                detail = "$successCount of ${selection.files.size} selected $fileLabel were ${selection.operation.pastTense()} to ${destinationFolder.name}.",
            )
            else -> FileOperationStatus(
                title = "${selection.operation.label()} failed",
                detail = "FilesFlow could not ${selection.operation.verb()} the selected $fileLabel to ${destinationFolder.name}.",
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
            val status = when {
                deletedCount == files.size -> FileOperationStatus("Deleted", "$deletedCount selected ${"file".pluralized(deletedCount)} deleted.")
                deletedCount > 0 -> FileOperationStatus("Some files deleted", "$deletedCount of ${files.size} selected files were deleted.")
                else -> FileOperationStatus("Delete unavailable", "Android did not allow FilesFlow to delete the selected files.")
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

        if (mode !is BrowseMode.Category) {
            return BrowserFiles(visibleFiles = allFiles)
        }

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

    private fun String.pluralized(count: Int): String {
        return if (count == 1) this else "${this}s"
    }

    private fun FileOperation.label(): String {
        return when (this) {
            FileOperation.Copy -> "Copy"
            FileOperation.Move -> "Move"
            FileOperation.Delete -> "Delete"
        }
    }

    private fun FileOperation.verb(): String {
        return when (this) {
            FileOperation.Copy -> "copy"
            FileOperation.Move -> "move"
            FileOperation.Delete -> "delete"
        }
    }

    private fun FileOperation.pastTense(): String {
        return when (this) {
            FileOperation.Copy -> "copied"
            FileOperation.Move -> "moved"
            FileOperation.Delete -> "deleted"
        }
    }

    private data class BrowserFiles(
        val visibleFiles: List<FilesFlowFile>,
        val allCategoryFiles: List<FilesFlowFile> = emptyList(),
        val categoryFolderFilters: List<CategoryFolderFilter> = emptyList(),
        val selectedCategoryFolderId: String? = null,
    )
}
