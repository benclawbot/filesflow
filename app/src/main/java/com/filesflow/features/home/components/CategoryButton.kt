package com.filesflow.features.home.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import com.filesflow.features.home.FileCategory
import com.filesflow.ui.theme.FilesFlowOnSurface

@Composable
fun CategoryButton(
    category: FileCategory,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        label = "category press scale",
    )

    NeumorphicSurface(
        modifier = modifier
            .scale(scale)
            .fillMaxWidth()
            .aspectRatio(1f)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {},
            ),
        cornerRadius = 12.dp,
        recessed = isPressed,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .size(32.dp),
                imageVector = category.icon,
                contentDescription = category.label,
                tint = FilesFlowOnSurface,
            )
            Text(
                text = category.label,
                color = FilesFlowOnSurface,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}
