package com.filesflow.features.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FolderSpecial
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.filesflow.features.home.FileOperation
import com.filesflow.features.home.FilesFlowFile
import com.filesflow.features.home.FileSource
import com.filesflow.features.home.isPrintableFile
import com.filesflow.ui.theme.FilesFlowOnSurface
import com.filesflow.ui.theme.FilesFlowPrimary
import com.filesflow.ui.theme.FilesFlowSecondary

@Composable
fun FileActionsCard(
    modifier: Modifier = Modifier,
    file: FilesFlowFile,
    isFavoriteFolder: Boolean = false,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onPrint: () -> Unit,
    onChooseFolder: (FileOperation) -> Unit,
    onToggleFavoriteFolder: () -> Unit,
    onDismiss: () -> Unit,
) {
    var isRenaming by rememberSaveable(file.id) { mutableStateOf(false) }
    var renameValue by rememberSaveable(file.id) { mutableStateOf(file.name) }

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

            if (file.isDirectory) {
                ActionButton(
                    icon = { Icon(if (isFavoriteFolder) Icons.Rounded.Star else Icons.Rounded.StarBorder, contentDescription = null) },
                    text = if (isFavoriteFolder) "Unstar Favorite Folder" else "Star Favorite Folder",
                    onClick = onToggleFavoriteFolder,
                )
            }
            if (isPrintableFile(file)) {
                ActionButton(
                    icon = { Icon(Icons.Rounded.Print, contentDescription = null) },
                    text = "Print / Save as PDF",
                    onClick = onPrint,
                )
            }
            ActionButton(
                icon = { Icon(Icons.Rounded.FolderSpecial, contentDescription = null) },
                text = "Copy to Folder",
                onClick = { onChooseFolder(FileOperation.Copy) },
                enabled = !file.isDirectory,
            )
            ActionButton(
                icon = { Icon(Icons.Rounded.FolderSpecial, contentDescription = null) },
                text = "Move to Folder",
                onClick = { onChooseFolder(FileOperation.Move) },
                enabled = !file.isDirectory,
            )
            if (isRenaming) {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = renameValue,
                    onValueChange = { renameValue = it },
                    singleLine = true,
                    label = { Text("New name") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = FilesFlowPrimary,
                        unfocusedBorderColor = FilesFlowSecondary.copy(alpha = 0.35f),
                    ),
                )
                ActionButton(
                    icon = { Icon(Icons.Rounded.DriveFileRenameOutline, contentDescription = null) },
                    text = "Save Rename",
                    onClick = { onRename(renameValue) },
                    enabled = renameValue.isNotBlank() && renameValue != file.name && file.source != FileSource.AppPackage,
                )
            } else {
                ActionButton(
                    icon = { Icon(Icons.Rounded.DriveFileRenameOutline, contentDescription = null) },
                    text = "Rename",
                    onClick = { isRenaming = true },
                    enabled = file.source != FileSource.AppPackage,
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
