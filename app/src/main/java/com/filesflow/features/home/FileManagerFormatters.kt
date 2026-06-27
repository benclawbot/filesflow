package com.filesflow.features.home

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

fun formatBytes(bytes: Long): String {
    val safeBytes = max(0L, bytes)
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var unitIndex = 0
    var value = safeBytes.toDouble()
    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex += 1
    }

    return when {
        unitIndex == 0 -> "${safeBytes} B"
        value >= 100 -> "${value.toInt()} ${units[unitIndex]}"
        else -> String.format(Locale.US, "%.1f %s", value, units[unitIndex])
    }
}

fun formatStorageLabel(usedBytes: Long, totalBytes: Long): StorageOverview {
    val percent = if (totalBytes <= 0L) 0 else ((usedBytes * 100) / totalBytes).toInt().coerceIn(0, 100)
    return StorageOverview(
        usedPercent = percent,
        usedLabel = "${formatBytes(usedBytes)} Used",
        totalLabel = "${formatBytes(totalBytes)} Total",
    )
}

fun formatFileMetadata(sizeBytes: Long, modifiedAtMillis: Long): String {
    val size = formatBytes(sizeBytes)
    val modified = when {
        modifiedAtMillis <= 0L -> "Unknown"
        else -> SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date(modifiedAtMillis))
    }
    return "$size - $modified"
}

fun inferCategoryType(name: String, mimeType: String?, path: String? = null): FileCategoryType {
    val lowerName = name.lowercase(Locale.US)
    val lowerPath = path?.lowercase(Locale.US).orEmpty()
    val lowerMime = mimeType?.lowercase(Locale.US).orEmpty()

    return when {
        lowerMime.startsWith("image/") ||
            lowerName.endsWith(".jpg") ||
            lowerName.endsWith(".jpeg") ||
            lowerName.endsWith(".png") ||
            lowerName.endsWith(".gif") ||
            lowerName.endsWith(".webp") ||
            lowerName.endsWith(".heic") -> FileCategoryType.Images
        lowerMime.startsWith("video/") ||
            lowerName.endsWith(".mp4") ||
            lowerName.endsWith(".mkv") ||
            lowerName.endsWith(".mov") ||
            lowerName.endsWith(".webm") ||
            lowerName.endsWith(".avi") -> FileCategoryType.Videos
        lowerMime.startsWith("audio/") ||
            lowerName.endsWith(".mp3") ||
            lowerName.endsWith(".m4a") ||
            lowerName.endsWith(".flac") ||
            lowerName.endsWith(".wav") ||
            lowerName.endsWith(".ogg") -> FileCategoryType.Music
        lowerPath.contains("download") -> FileCategoryType.Downloads
        lowerName.endsWith(".apk") || lowerMime == "application/vnd.android.package-archive" -> FileCategoryType.Apps
        lowerMime.contains("pdf") ||
            lowerMime.startsWith("text/") ||
            lowerMime.contains("document") ||
            lowerMime.contains("presentation") ||
            lowerMime.contains("spreadsheet") ||
            lowerName.endsWith(".doc") ||
            lowerName.endsWith(".docx") ||
            lowerName.endsWith(".pdf") ||
            lowerName.endsWith(".txt") ||
            lowerName.endsWith(".xls") ||
            lowerName.endsWith(".xlsx") ||
            lowerName.endsWith(".ppt") ||
            lowerName.endsWith(".pptx") -> FileCategoryType.Docs
        else -> FileCategoryType.Docs
    }
}
