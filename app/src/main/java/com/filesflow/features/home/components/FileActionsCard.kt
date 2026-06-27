package com.filesflow.features.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FolderSpecial
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.filesflow.features.home.FilesFlowFile
import com.filesflow.features.home.FileSource
import com.filesflow.ui.theme.FilesFlowOnSurface
import com.filesflow.ui.theme.FilesFlowPrimary
import com.filesflow.ui.theme.FilesFlowSecondary

@Composable
fun FileActionsCard(
    modifier: Modifier = Modifier,
    file: FilesFlowFile,
    hasDestinationFolder: Boolean,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    onChooseFolder: () -> Unit,
    onDismiss: () -> Unit,
) {
    NeumorphicSurface(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = 12.dp,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RecentFileLeading(file = file)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                ) {
                    Text(
                        text = file.name,
                        color = FilesFlowOnSurface,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = file.metadata,
                        color = FilesFlowSecondary,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close actions",
                        tint = FilesFlowSecondary,
                    )
                }
            }

            if (!hasDestinationFolder) {
                ActionButton(
                    icon = { Icon(Icons.Rounded.FolderSpecial, contentDescription = null) },
                    text = "Choose Destination Folder",
                    onClick = onChooseFolder,
                )
            } else {
                ActionButton(
                    icon = { Icon(Icons.Rounded.ContentCopy, contentDescription = null) },
                    text = "Copy to Selected Folder",
                    onClick = onCopy,
                    enabled = !file.isDirectory,
                )
                ActionButton(
                    icon = { Icon(Icons.AutoMirrored.Rounded.DriveFileMove, contentDescription = null) },
                    text = "Move to Selected Folder",
                    onClick = onMove,
                    enabled = !file.isDirectory,
                )
            }
            ActionButton(
                icon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                text = "Delete",
                onClick = onDelete,
                enabled = file.source != FileSource.AppPackage,
            )
        }
    }
}

@Composable
private fun ActionButton(
    icon: @Composable () -> Unit,
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    OutlinedButton(
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        onClick = onClick,
    ) {
        androidx.compose.foundation.layout.Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon()
            Text(
                text = text,
                color = if (enabled) FilesFlowPrimary else FilesFlowSecondary,
            )
        }
    }
}
