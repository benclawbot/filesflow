package com.filesflow.features.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.filesflow.features.home.FileCategorySummary
import com.filesflow.features.home.FileCategoryType
import com.filesflow.ui.theme.FilesFlowSecondary

@Composable
fun CategoryGrid(
    summaries: List<FileCategorySummary>,
    onCategoryClick: (FileCategoryType) -> Unit,
) {
    Column {
        Text(
            modifier = Modifier.padding(start = 4.dp, bottom = 16.dp),
            text = "Categories",
            color = FilesFlowSecondary,
            style = MaterialTheme.typography.labelMedium,
        )

        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            summaries.chunked(3).forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    rowItems.forEach { summary ->
                        CategoryButton(
                            modifier = Modifier.weight(1f),
                            summary = summary,
                            onClick = { onCategoryClick(summary.type) },
                        )
                    }
                }
            }
        }
    }
}
