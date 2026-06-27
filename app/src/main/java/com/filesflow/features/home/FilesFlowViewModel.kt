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
            val storage = runCatching { repository.getStorageOverview() }.getOrElse { previewStorageOverview }
            val categories = runCatching { repository.getCategorySummaries() }.getOrElse { previewFileCategorySummaries() }
            val recentFiles = runCatching { repository.getRecentFiles() }.getOrElse { previewRecentFiles() }
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
        _uiState.update { it.copy(browseMode = BrowseMode.Home, visibleFiles = emptyList(), searchQuery = "", selectedFile = null) }
    }

    fun openCategory(type: FileCategoryType) {
        _uiState.update { it.copy(isLoading = true, browseMode = BrowseMode.Category(type), selectedFile = null) }
        viewModelScope.launch {
            val files = runCatching { repository.listCategory(type) }.getOrDefault(emptyList())
            _uiState.update { it.copy(visibleFiles = files, isLoading = false) }
        }
    }

    fun openBrowseRoot() {
        _uiState.update { it.copy(isLoading = true, browseMode = BrowseMode.Folder(null, "Browse Files"), selectedFile = null) }
        viewModelScope.launch {
            val files = runCatching { repository.listBrowseRoot() }.getOrDefault(emptyList())
            _uiState.update { it.copy(visibleFiles = files, isLoading = false) }
        }
    }

    fun openFolder(file: FilesFlowFile) {
        if (!file.isDirectory) {
            _uiState.update { it.copy(selectedFile = file) }
            return
        }
        _uiState.update { it.copy(isLoading = true, browseMode = BrowseMode.Folder(file.uri, file.name), selectedFile = null) }
        viewModelScope.launch {
            val files = runCatching { repository.listFolder(file) }.getOrDefault(emptyList())
            _uiState.update { it.copy(visibleFiles = files, isLoading = false) }
        }
    }

    fun search(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        if (query.isBlank()) {
            openHome()
            return
        }
        _uiState.update { it.copy(isLoading = true, browseMode = BrowseMode.Search(query), selectedFile = null) }
        viewModelScope.launch {
            val files = runCatching { repository.searchFiles(query) }.getOrDefault(emptyList())
            _uiState.update { it.copy(visibleFiles = files, isLoading = false) }
        }
    }

    fun selectFile(file: FilesFlowFile) {
        _uiState.update { it.copy(selectedFile = file) }
    }

    fun dismissActions() {
        _uiState.update { it.copy(selectedFile = null) }
    }

    fun dismissStatus() {
        _uiState.update { it.copy(operationStatus = null) }
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
        openBrowseRoot()
    }

    fun runOperation(operation: FileOperation, file: FilesFlowFile) {
        _uiState.update { it.copy(isLoading = true, selectedFile = null) }
        viewModelScope.launch {
            val status = when (operation) {
                FileOperation.Copy -> repository.copyToSafFolder(file)
                FileOperation.Move -> repository.moveToSafFolder(file)
                FileOperation.Delete -> repository.delete(file)
            }
            _uiState.update { it.copy(operationStatus = status, isLoading = false) }
            refresh()
        }
    }
}
