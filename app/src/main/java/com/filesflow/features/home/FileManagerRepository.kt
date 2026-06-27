package com.filesflow.features.home

import android.net.Uri

interface FileManagerRepository {
    suspend fun getStorageOverview(): StorageOverview
    suspend fun getCategorySummaries(): List<FileCategorySummary>
    suspend fun getRecentFiles(limit: Int = 20): List<FilesFlowFile>
    suspend fun listCategory(type: FileCategoryType): List<FilesFlowFile>
    suspend fun searchFiles(query: String): List<FilesFlowFile>
    suspend fun listBrowseRoot(): List<FilesFlowFile>
    suspend fun listFolder(folder: FilesFlowFile): List<FilesFlowFile>
    suspend fun copyToSafFolder(file: FilesFlowFile): FileOperationStatus
    suspend fun moveToSafFolder(file: FilesFlowFile): FileOperationStatus
    suspend fun rename(file: FilesFlowFile, newName: String): FileOperationStatus
    suspend fun delete(file: FilesFlowFile): FileOperationStatus
    fun persistSafFolder(uri: Uri)
    fun getPersistedSafFolderName(): String?
}
