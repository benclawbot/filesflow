package com.filesflow.features.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.filesflow.features.home.components.CategoryGrid
import com.filesflow.features.home.components.RecentFilesList
import com.filesflow.features.home.components.StorageOverviewCard
import com.filesflow.ui.theme.FilesFlowTheme

@Preview(
    name = "FilesFlow Home Dashboard",
    showBackground = true,
    backgroundColor = 0xFFFFF8F2,
    widthDp = FilesFlowPortraitWidthDp,
    heightDp = 852,
)
@Composable
fun HomeDashboardPreview() {
    FilesFlowTheme {
        androidx.compose.foundation.layout.Column(
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(32.dp),
            modifier = androidx.compose.ui.Modifier
                .background(com.filesflow.ui.theme.FilesFlowBackground)
                .padding(FilesFlowHorizontalPaddingDp.dp),
        ) {
            StorageOverviewCard(
                usedPercent = previewStorageOverview.usedPercent,
                usedLabel = previewStorageOverview.usedLabel,
                totalLabel = previewStorageOverview.totalLabel,
            )
            CategoryGrid(
                summaries = previewFileCategorySummaries(),
                onCategoryClick = {},
            )
            RecentFilesList(
                files = previewRecentFiles(),
                onViewAll = {},
                onFileClick = {},
                onFileLongClick = {},
                onMoreClick = {},
            )
        }
    }
}
