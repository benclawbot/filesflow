package com.filesflow.data

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import com.filesflow.features.home.FavoriteFolder
import com.filesflow.features.home.FileCategorySummary
import com.filesflow.features.home.FileCategoryType
import com.filesflow.features.home.FileManagerRepository
import com.filesflow.features.home.FileOperationStatus
import com.filesflow.features.home.FilesFlowFile

/**
 * Repository decorator that keeps the existing UI/storage contract while replacing capped
 * direct-storage scans with a complete durable index whenever broad file access is available.
 */
class IndexedFileManagerRepository(context: Context) : FileManagerRepository {
    private val delegate = AndroidFileManagerRepository(context)
    private val directStorageIndex = DirectStorageIndex(context)

    override suspend fun getStorageOverview() = delegate.getStorageOverview()

    override suspend fun getCategorySummaries(): List<FileCategorySummary> {
        if (!canUseDirectSharedStorage() || !directStorageIndex.ensureFresh()) {
            return delegate.getCategorySummaries()
        }

        val indexed = directStorageIndex.categorySummaries()
        val installedApps = delegate.listCategory(FileCategoryType.Apps)
        return FileCategoryType.entries.map { type ->
            when (type) {
                FileCategoryType.Apps -> FileCategorySummary(
                    type = type,
                    fileCount = installedApps.size,
                    totalBytes = installedApps.sumOf { it.sizeBytes },
                )
                else -> indexed[type] ?: FileCategorySummary(type, fileCount = 0, totalBytes = 0L)
            }
        }
    }

    override suspend fun getRecentFiles(limit: Int) = delegate.getRecentFiles(limit)

    override suspend fun listCategory(type: FileCategoryType): List<FilesFlowFile> {
        if (type == FileCategoryType.Apps || !canUseDirectSharedStorage() || !directStorageIndex.ensureFresh()) {
            return delegate.listCategory(type)
        }
        return directStorageIndex.listCategory(type)
    }

    override suspend fun searchFiles(query: String): List<FilesFlowFile> {
        if (!canUseDirectSharedStorage() || !directStorageIndex.ensureFresh()) {
            return delegate.searchFiles(query)
        }
        return directStorageIndex.search(query)
    }

    override suspend fun listBrowseRoot() = delegate.listBrowseRoot()

    override suspend fun listFolder(folder: FilesFlowFile) = delegate.listFolder(folder)

    override suspend fun getBrowseRootFolder() = delegate.getBrowseRootFolder()

    override suspend fun copyToFolder(
        file: FilesFlowFile,
        destinationFolder: FilesFlowFile,
    ): FileOperationStatus = delegate.copyToFolder(file, destinationFolder).also(::invalidateAfterMutation)

    override suspend fun moveToFolder(
        file: FilesFlowFile,
        destinationFolder: FilesFlowFile,
    ): FileOperationStatus = delegate.moveToFolder(file, destinationFolder).also(::invalidateAfterMutation)

    override suspend fun copyToSafFolder(file: FilesFlowFile): FileOperationStatus {
        return delegate.copyToSafFolder(file).also(::invalidateAfterMutation)
    }

    override suspend fun moveToSafFolder(file: FilesFlowFile): FileOperationStatus {
        return delegate.moveToSafFolder(file).also(::invalidateAfterMutation)
    }

    override suspend fun rename(file: FilesFlowFile, newName: String): FileOperationStatus {
        return delegate.rename(file, newName).also(::invalidateAfterMutation)
    }

    override suspend fun delete(file: FilesFlowFile): FileOperationStatus {
        return delegate.delete(file).also(::invalidateAfterMutation)
    }

    override fun persistSafFolder(uri: Uri) = delegate.persistSafFolder(uri)

    override fun getPersistedSafFolderName() = delegate.getPersistedSafFolderName()

    override fun getFavoriteFolders(): List<FavoriteFolder> = delegate.getFavoriteFolders()

    override fun toggleFavoriteFolder(folder: FilesFlowFile) = delegate.toggleFavoriteFolder(folder)

    private fun invalidateAfterMutation(status: FileOperationStatus) {
        val changedStorage = status.title in setOf("Copied", "Moved", "Deleted", "Renamed", "Copied only")
        if (changedStorage) directStorageIndex.invalidate()
    }

    private fun canUseDirectSharedStorage(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()
    }
}
