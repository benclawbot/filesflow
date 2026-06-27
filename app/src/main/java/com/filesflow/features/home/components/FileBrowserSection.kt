package com.filesflow.features.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.filesflow.features.home.BrowseMode
import com.filesflow.features.home.FilesFlowFile
import com.filesflow.ui.theme.FilesFlowOnSurface
import com.filesflow.ui.theme.FilesFlowSecondary

@Composable
fun FileBrowserSection(
    browseMode: BrowseMode,
    files: List<FilesFlowFile>,
    isLoading: Boolean,
    onBackHome: () -> Unit,
    onFileClick: (FilesFlowFile) -> Unit,
    onFileLongClick: (FilesFlowFile) -> Unit,
    onMoreClick: (FilesFlowFile) -> Unit,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 4.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBackHome) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back to dashboard",
                    tint = FilesFlowSecondary,
                )
            }
            Column {
                Text(
                    text = browseTitle(browseMode),
                    color = FilesFlowOnSurface,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                )
                Text(
                    text = if (isLoading) "Loading files" else "${files.size} items",
                    color = FilesFlowSecondary,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }

        if (!isLoading && files.isEmpty()) {
            EmptyFilesCard()
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                files.forEach { file ->
                    RecentFileRow(
                        file = file,
                        onClick = { onFileClick(file) },
                        onLongClick = { onFileLongClick(file) },
                        onMoreClick = { onMoreClick(file) },
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyFilesCard() {
    NeumorphicSurface(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 12.dp,
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "No files found",
                color = FilesFlowOnSurface,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            )
            Text(
                text = "Grant storage access or try another category/search.",
                color = FilesFlowSecondary,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

private fun browseTitle(browseMode: BrowseMode): String = when (browseMode) {
    BrowseMode.Home -> "Recent Files"
    is BrowseMode.Category -> browseMode.type.label
    is BrowseMode.Folder -> browseMode.displayName
    is BrowseMode.Search -> "Search: ${browseMode.query}"
}
