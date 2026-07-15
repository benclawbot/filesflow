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
    private val folderTransfer = RecursiveFolderTransfer(context)

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
    ): FileOperationStatus {
        val status = if (file.isDirectory) {
            copyDirectory(file, destinationFolder)
        } else {
            delegate.copyToFolder(file, destinationFolder)
        }
        invalidateAfterMutation(status)
        return status
    }

    override suspend fun moveToFolder(
        file: FilesFlowFile,
        destinationFolder: FilesFlowFile,
    ): FileOperationStatus {
        val status = if (file.isDirectory) {
            moveDirectory(file, destinationFolder)
        } else {
            delegate.moveToFolder(file, destinationFolder)
        }
        invalidateAfterMutation(status)
        return status
    }

    override suspend fun copyToSafFolder(file: FilesFlowFile): FileOperationStatus {
        val status = if (file.isDirectory) {
            val destination = delegate.getBrowseRootFolder()
                ?: return FileOperationStatus("Choose a folder", "Select a destination folder before copying folders.")
            copyDirectory(file, destination)
        } else {
            delegate.copyToSafFolder(file)
        }
        invalidateAfterMutation(status)
        return status
    }

    override suspend fun moveToSafFolder(file: FilesFlowFile): FileOperationStatus {
        val status = if (file.isDirectory) {
            val destination = delegate.getBrowseRootFolder()
                ?: return FileOperationStatus("Choose a folder", "Select a destination folder before moving folders.")
            moveDirectory(file, destination)
        } else {
            delegate.moveToSafFolder(file)
        }
        invalidateAfterMutation(status)
        return status
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

    private fun copyDirectory(source: FilesFlowFile, destination: FilesFlowFile): FileOperationStatus {
        return if (folderTransfer.copy(source, destination)) {
            FileOperationStatus("Copied", "${source.name} and its contents were copied to ${destination.name}.")
        } else {
            FileOperationStatus(
                "Copy failed",
                "FilesFlow could not copy ${source.name}. The destination may be inside the source folder, inaccessible, or out of space.",
            )
        }
    }

    private fun moveDirectory(source: FilesFlowFile, destination: FilesFlowFile): FileOperationStatus {
        if (!folderTransfer.copy(source, destination)) {
            return FileOperationStatus(
                "Move failed",
                "FilesFlow could not copy ${source.name} safely, so the original folder was left unchanged.",
            )
        }
        return if (folderTransfer.deleteSource(source)) {
            FileOperationStatus("Moved", "${source.name} and its contents were moved to ${destination.name}.")
        } else {
            FileOperationStatus(
                "Copied only",
                "${source.name} was copied completely, but Android did not allow deleting the original folder.",
            )
        }
    }

    private fun invalidateAfterMutation(status: FileOperationStatus) {
        val changedStorage = status.title in setOf("Copied", "Moved", "Deleted", "Renamed", "Copied only")
        if (changedStorage) directStorageIndex.invalidate()
    }

    private fun canUseDirectSharedStorage(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()
    }
}
