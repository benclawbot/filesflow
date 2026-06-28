package com.filesflow.features.home.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.filesflow.features.home.FilesFlowFile
import com.filesflow.ui.theme.FilesFlowBackground
import com.filesflow.ui.theme.FilesFlowOnSurface
import com.filesflow.ui.theme.FilesFlowSecondary

/**
 * Full-screen swipeable image viewer. Opens on top of the dashboard so the user can
 * flick left/right through every picture in the list without leaving the app.
 */
@Composable
fun ImageViewer(
    files: List<FilesFlowFile>,
    initialIndex: Int,
    onDismiss: () -> Unit,
) {
    BackHandler(enabled = files.isNotEmpty()) {
        onDismiss()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(FilesFlowBackground),
    ) {
        if (files.isEmpty()) {
            onDismiss()
            return
        }

        val pagerState = rememberPagerState(
            initialPage = initialIndex.coerceIn(0, files.lastIndex),
        ) { files.size }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(0.dp),
            pageSpacing = 0.dp,
            key = { files[it].id },
        ) { pageIndex ->
            ImageViewerPage(file = files[pageIndex])
        }

        ViewerOverlay(
            currentIndex = pagerState.currentPage,
            totalCount = files.size,
            fileName = files.getOrNull(pagerState.currentPage)?.name.orEmpty(),
            onDismiss = onDismiss,
        )
    }
}

@Composable
private fun ImageViewerPage(file: FilesFlowFile) {
    val context = LocalContext.current
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(file.uri ?: file.path)
                .crossfade(false)
                .build(),
            contentDescription = file.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun ViewerOverlay(
    currentIndex: Int,
    totalCount: Int,
    fileName: String,
    onDismiss: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 24.dp),
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .widthIn(max = 560.dp)
                .background(Color.Black.copy(alpha = 0.42f))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                modifier = Modifier.weight(1f),
                text = fileName,
                color = FilesFlowOnSurface,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (totalCount > 1) {
                Text(
                    modifier = Modifier.padding(start = 12.dp, end = 4.dp),
                    text = "${currentIndex + 1} / $totalCount",
                    color = FilesFlowSecondary,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Close image viewer",
                    tint = FilesFlowOnSurface,
                )
            }
        }

        if (totalCount > 1) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                repeat(totalCount) { index ->
                    val tint = if (index == currentIndex) FilesFlowOnSurface else FilesFlowSecondary.copy(alpha = 0.55f)
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(tint),
                    )
                }
            }
        }
    }
}
