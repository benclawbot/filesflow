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
import com.filesflow.features.home.FileCategory
import com.filesflow.ui.theme.FilesFlowSecondary

@Composable
fun CategoryGrid(categories: List<FileCategory>) {
    Column {
        Text(
            modifier = Modifier.padding(start = 4.dp, bottom = 16.dp),
            text = "Categories",
            color = FilesFlowSecondary,
            style = MaterialTheme.typography.labelMedium,
        )

        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            categories.chunked(3).forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    rowItems.forEach { category ->
                        CategoryButton(
                            modifier = Modifier.weight(1f),
                            category = category,
                        )
                    }
                }
            }
        }
    }
}
