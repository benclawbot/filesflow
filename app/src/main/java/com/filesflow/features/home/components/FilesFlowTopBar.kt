package com.filesflow.features.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.filesflow.features.home.FilesFlowAppName
import com.filesflow.features.home.FilesFlowHorizontalPaddingDp
import com.filesflow.features.home.FilesFlowPortraitWidthDp
import com.filesflow.features.home.FilesFlowTopBarContentHeightDp
import com.filesflow.ui.theme.FilesFlowBackground
import com.filesflow.ui.theme.FilesFlowPrimary

@Composable
fun FilesFlowTopBar() {
    val statusBarTopPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(statusBarTopPadding + FilesFlowTopBarContentHeightDp.dp)
            .drawBehind {
                drawRect(
                    color = Color(0x0D1C1C18),
                    topLeft = Offset(4.dp.toPx(), size.height - 4.dp.toPx()),
                    size = size.copy(height = 8.dp.toPx()),
                )
                drawRect(
                    color = Color.White,
                    topLeft = Offset(-4.dp.toPx(), size.height - 12.dp.toPx()),
                    size = size.copy(height = 8.dp.toPx()),
                )
            }
            .background(FilesFlowBackground),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = FilesFlowPortraitWidthDp.dp)
                .padding(
                    start = FilesFlowHorizontalPaddingDp.dp,
                    top = statusBarTopPadding + 8.dp,
                    end = FilesFlowHorizontalPaddingDp.dp,
                    bottom = 8.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = {}) {
                Icon(
                    imageVector = Icons.Rounded.Menu,
                    contentDescription = "Open FilesFlow menu",
                    tint = FilesFlowPrimary,
                )
            }

            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = FilesFlowAppName,
                    color = FilesFlowPrimary,
                    style = MaterialTheme.typography.headlineMedium,
                )
            }

            IconButton(onClick = {}) {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = "Search FilesFlow",
                    tint = FilesFlowPrimary,
                )
            }
        }
    }
}
