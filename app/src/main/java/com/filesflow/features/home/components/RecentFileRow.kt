package com.filesflow.features.home.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.VideoFile
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.combinedClickable
import com.filesflow.features.home.FileSource
import com.filesflow.features.home.FilesFlowFile
import com.filesflow.ui.theme.FilesFlowOnSurface
import com.filesflow.ui.theme.FilesFlowOnSurfaceVariant
import com.filesflow.ui.theme.FilesFlowSecondary
import com.filesflow.ui.theme.FilesFlowSurfaceContainerHigh

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecentFileRow(
    file: FilesFlowFile,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMoreClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 1.01f else 1f,
        label = "recent file press scale",
    )

    NeumorphicSurface(
        modifier = Modifier
            .scale(scale)
            .fillMaxWidth()
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        cornerRadius = 12.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RecentFileLeading(file = file)
            Spacer(modifier = Modifier.width(16.dp))
            Column(
                modifier = Modifier.weight(1f),
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
            IconButton(onClick = onMoreClick) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = "More options for ${file.name}",
                    tint = FilesFlowSecondary,
                )
            }
        }
    }
}

@Composable
fun RecentFileLeading(file: FilesFlowFile) {
    if (file.mimeType?.startsWith("image/") == true && !file.isDirectory) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFFE7DDD2),
                            Color(0xFFB8B1A8),
                            Color(0xFFF6EFE6),
                        ),
                    ),
                )
                .drawBehind {
                    drawLine(
                        color = Color.White.copy(alpha = 0.55f),
                        start = Offset(size.width * 0.12f, size.height * 0.78f),
                        end = Offset(size.width * 0.86f, size.height * 0.18f),
                        strokeWidth = 3.dp.toPx(),
                    )
                },
        )
        return
    }

    NeumorphicSurface(
        modifier = Modifier.size(48.dp),
        cornerRadius = 8.dp,
        recessed = true,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(FilesFlowSurfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = when {
                    file.isDirectory -> Icons.Rounded.Folder
                    file.source == FileSource.AppPackage -> Icons.Rounded.Android
                    file.mimeType?.startsWith("video/") == true -> Icons.Rounded.VideoFile
                    file.mimeType?.startsWith("audio/") == true -> Icons.Rounded.AudioFile
                    file.mimeType?.contains("pdf") == true -> Icons.Rounded.PictureAsPdf
                    file.mimeType?.startsWith("image/") == true -> Icons.Rounded.Image
                    file.mimeType?.contains("document") == true -> Icons.AutoMirrored.Rounded.Article
                    else -> Icons.Rounded.Description
                },
                contentDescription = null,
                tint = FilesFlowOnSurfaceVariant,
            )
        }
    }
}
