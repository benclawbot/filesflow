package com.filesflow.features.home

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat

fun currentStorageAccessState(context: Context): StorageAccessState {
    val hasLegacyRead = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU &&
        context.hasPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
    val hasImages = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        context.hasPermission(Manifest.permission.READ_MEDIA_IMAGES)
    val hasVideos = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        context.hasPermission(Manifest.permission.READ_MEDIA_VIDEO)
    val hasAudio = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        context.hasPermission(Manifest.permission.READ_MEDIA_AUDIO)

    return StorageAccessState(
        hasImagesPermission = hasImages,
        hasVideosPermission = hasVideos,
        hasAudioPermission = hasAudio,
        hasLegacyReadPermission = hasLegacyRead,
        hasAllFilesAccess = Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager(),
    )
}

fun mediaPermissionRequest(): Array<String> {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_AUDIO,
        )
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
}

enum class SystemAccessRequest {
    MediaPermissions,
    AllFilesAccess,
    None,
}

fun systemAccessRequestForCategory(
    type: FileCategoryType,
    accessState: StorageAccessState,
): SystemAccessRequest {
    if (type == FileCategoryType.Apps) return SystemAccessRequest.None

    return when (type.permissionKind) {
        PermissionKind.Images -> if (accessState.hasImagesPermission || accessState.hasLegacyReadPermission || accessState.hasAllFilesAccess) {
            SystemAccessRequest.None
        } else {
            SystemAccessRequest.MediaPermissions
        }
        PermissionKind.Videos -> if (accessState.hasVideosPermission || accessState.hasLegacyReadPermission || accessState.hasAllFilesAccess) {
            SystemAccessRequest.None
        } else {
            SystemAccessRequest.MediaPermissions
        }
        PermissionKind.Audio -> if (accessState.hasAudioPermission || accessState.hasLegacyReadPermission || accessState.hasAllFilesAccess) {
            SystemAccessRequest.None
        } else {
            SystemAccessRequest.MediaPermissions
        }
        PermissionKind.Files -> if (accessState.hasAllFilesAccess || accessState.hasLegacyReadPermission) {
            SystemAccessRequest.None
        } else {
            SystemAccessRequest.AllFilesAccess
        }
    }
}

fun systemAccessRequestForBroadFiles(accessState: StorageAccessState): SystemAccessRequest {
    return if (accessState.hasAllFilesAccess || accessState.hasLegacyReadPermission) {
        SystemAccessRequest.None
    } else {
        SystemAccessRequest.AllFilesAccess
    }
}

fun allFilesAccessIntent(context: Context): Intent {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
            data = Uri.parse("package:${context.packageName}")
        }
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
    }
}

private fun Context.hasPermission(permission: String): Boolean {
    return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}
