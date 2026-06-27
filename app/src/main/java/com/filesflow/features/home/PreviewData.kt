package com.filesflow.features.home

val previewStorageOverview = StorageOverview(
    usedPercent = 78,
    usedLabel = "92.4 GB Used",
    totalLabel = "128 GB Total",
)

val emptyStorageOverview = StorageOverview(
    usedPercent = 0,
    usedLabel = "0 B Used",
    totalLabel = "0 B Total",
)

fun emptyFileCategorySummaries(): List<FileCategorySummary> = FileCategoryType.entries.map { type ->
    FileCategorySummary(
        type = type,
        fileCount = 0,
        totalBytes = 0L,
    )
}

fun previewFileCategorySummaries(): List<FileCategorySummary> = FileCategoryType.entries.mapIndexed { index, type ->
    FileCategorySummary(
        type = type,
        fileCount = listOf(128, 24, 56, 18, 204, 9)[index],
        totalBytes = listOf(4_800_000_000L, 12_400_000_000L, 820_000_000L, 2_100_000_000L, 8_600_000_000L, 940_000_000L)[index],
    )
}

fun previewRecentFiles(): List<FilesFlowFile> = listOf(
    FilesFlowFile(
        id = "preview-quarterly-report",
        name = "Quarterly Report",
        metadata = "2.4 MB - 2h ago",
        uri = null,
        path = null,
        mimeType = "application/pdf",
        sizeBytes = 2_400_000L,
        modifiedAtMillis = 0L,
        source = FileSource.MediaStore,
    ),
    FilesFlowFile(
        id = "preview-img",
        name = "IMG_8421.jpg",
        metadata = "4.8 MB - 5h ago",
        uri = null,
        path = null,
        mimeType = "image/jpeg",
        sizeBytes = 4_800_000L,
        modifiedAtMillis = 0L,
        source = FileSource.MediaStore,
    ),
    FilesFlowFile(
        id = "preview-brand-guidelines",
        name = "Brand_Guidelines_v2.pdf",
        metadata = "15.2 MB - Yesterday",
        uri = null,
        path = null,
        mimeType = "application/pdf",
        sizeBytes = 15_200_000L,
        modifiedAtMillis = 0L,
        source = FileSource.MediaStore,
    ),
    FilesFlowFile(
        id = "preview-assets",
        name = "Project_Assets_Archive",
        metadata = "245 MB - Oct 12",
        uri = null,
        path = null,
        mimeType = "application/zip",
        sizeBytes = 245_000_000L,
        modifiedAtMillis = 0L,
        source = FileSource.MediaStore,
    ),
)
