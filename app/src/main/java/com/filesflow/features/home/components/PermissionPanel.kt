package com.filesflow.features.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FolderSpecial
import androidx.compose.material.icons.rounded.PermMedia
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.filesflow.features.home.StorageAccessState
import com.filesflow.ui.theme.FilesFlowBackground
import com.filesflow.ui.theme.FilesFlowOnSurface
import com.filesflow.ui.theme.FilesFlowPrimary
import com.filesflow.ui.theme.FilesFlowSecondary

@Composable
fun PermissionPanel(
    accessState: StorageAccessState,
    destinationFolderName: String?,
    onRequestMediaAccess: () -> Unit,
    onRequestSafFolder: () -> Unit,
    onRequestAllFilesAccess: () -> Unit,
    onBrowse: () -> Unit,
) {
    NeumorphicSurface(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 12.dp,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Storage Access",
                color = FilesFlowSecondary,
                style = MaterialTheme.typography.labelMedium,
            )
            AccessRow(
                icon = { Icon(Icons.Rounded.PermMedia, contentDescription = null, tint = FilesFlowPrimary) },
                title = "Media library",
                detail = if (accessState.hasAnyMediaAccess) "Images, videos, and audio categories can load from MediaStore." else "Allow FilesFlow to index media categories and recent files.",
                action = if (accessState.hasAnyMediaAccess) "Refresh" else "Allow",
                onAction = onRequestMediaAccess,
            )
            AccessRow(
                icon = { Icon(Icons.Rounded.FolderSpecial, contentDescription = null, tint = FilesFlowPrimary) },
                title = "Selected folder",
                detail = destinationFolderName ?: "Choose a folder for browsing plus copy and move destinations.",
                action = if (destinationFolderName == null) "Choose" else "Change",
                onAction = onRequestSafFolder,
            )
            AccessRow(
                icon = { Icon(Icons.Rounded.Security, contentDescription = null, tint = FilesFlowPrimary) },
                title = "All files access",
                detail = if (accessState.hasAllFilesAccess) "Broad file browsing is enabled for file-manager workflows." else "Optional for full shared-storage browsing on Android 11+.",
                action = if (accessState.hasAllFilesAccess) "Browse" else "Settings",
                onAction = if (accessState.hasAllFilesAccess) onBrowse else onRequestAllFilesAccess,
            )
        }
    }
}

@Composable
private fun AccessRow(
    icon: @Composable () -> Unit,
    title: String,
    detail: String,
    action: String,
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NeumorphicSurface(
            modifier = Modifier.width(48.dp),
            cornerRadius = 8.dp,
            recessed = true,
        ) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .padding(12.dp)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                icon()
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        ) {
            Text(
                text = title,
                color = FilesFlowOnSurface,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            )
            Text(
                text = detail,
                color = FilesFlowSecondary,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        OutlinedButton(
            onClick = onAction,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = FilesFlowPrimary,
                containerColor = FilesFlowBackground,
            ),
        ) {
            Text(action)
        }
    }
}
