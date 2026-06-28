package com.filesflow.features.home.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.filesflow.features.home.FilesFlowFile
import com.filesflow.ui.theme.FilesFlowPrimaryContainer

private const val ImageGalleryColumns = 3
private const val ImageGalleryRows = 6
private val ImageGalleryGap = 8.dp
private val ImageGalleryCellHeight = 104.dp

val ImageGalleryVisibleSlots = ImageGalleryColumns * ImageGalleryRows

@Composable
fun ImageGalleryGrid(
    files: List<FilesFlowFile>,
    isSelectionMode: Boolean = false,
    selectedFileIds: Set<String> = emptySet(),
    onImageClick: (FilesFlowFile) -> Unit,
    onImageLongClick: (FilesFlowFile) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(ImageGalleryColumns),
        modifier = Modifier
            .fillMaxWidth()
            .height(ImageGalleryCellHeight * ImageGalleryRows + ImageGalleryGap * (ImageGalleryRows - 1)),
        contentPadding = PaddingValues(0.dp),
        horizontalArrangement = Arrangement.spacedBy(ImageGalleryGap),
        verticalArrangement = Arrangement.spacedBy(ImageGalleryGap),
    ) {
        items(
            items = files,
            key = { it.id },
        ) { file ->
            ImageGalleryTile(
                file = file,
                isSelectionMode = isSelectionMode,
                isSelected = file.id in selectedFileIds,
                onClick = { onImageClick(file) },
                onLongClick = { onImageLongClick(file) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ImageGalleryTile(
    file: FilesFlowFile,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val context = LocalContext.current
    val model = file.uri ?: file.path

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(10.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFFE7DDD2),
                        Color(0xFFB8B1A8),
                        Color(0xFFF6EFE6),
                    ),
                ),
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(model)
                .size(256)
                .crossfade(false)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .build(),
            contentDescription = file.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(FilesFlowPrimaryContainer.copy(alpha = 0.42f)),
            )
        }
        if (isSelectionMode) {
            SelectionIndicator(
                selected = isSelected,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp),
            )
        }
    }
}
