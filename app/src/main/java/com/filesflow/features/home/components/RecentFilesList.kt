package com.filesflow.features.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.filesflow.features.home.RecentFileItem
import com.filesflow.ui.theme.FilesFlowPrimary
import com.filesflow.ui.theme.FilesFlowSecondary

@Composable
fun RecentFilesList(files: List<RecentFileItem>) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 4.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Recent Files",
                color = FilesFlowSecondary,
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                text = "View All",
                color = FilesFlowPrimary,
                style = MaterialTheme.typography.labelSmall,
                textDecoration = TextDecoration.Underline,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            files.forEach { file ->
                RecentFileRow(file = file)
            }
        }
    }
}
