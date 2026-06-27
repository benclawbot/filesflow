package com.filesflow.features.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.filesflow.features.home.components.CategoryGrid
import com.filesflow.features.home.components.FilesFlowTopBar
import com.filesflow.features.home.components.RecentFilesList
import com.filesflow.features.home.components.StorageOverviewCard
import com.filesflow.ui.theme.FilesFlowBackground

@Composable
fun HomeDashboardScreen(
    storageOverview: StorageOverview = defaultStorageOverview,
    categories: List<FileCategory> = defaultFileCategories(),
    recentFiles: List<RecentFileItem> = defaultRecentFiles(),
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        FilesFlowTopBar()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(FilesFlowBackground),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 512.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(
                        PaddingValues(
                            start = 24.dp,
                            top = 24.dp,
                            end = 24.dp,
                            bottom = 128.dp,
                        ),
                    ),
                verticalArrangement = Arrangement.spacedBy(32.dp),
            ) {
                StorageOverviewCard(
                    usedPercent = storageOverview.usedPercent,
                    usedLabel = storageOverview.usedLabel,
                    totalLabel = storageOverview.totalLabel,
                )
                CategoryGrid(categories = categories)
                RecentFilesList(files = recentFiles)
            }
        }
    }
}
