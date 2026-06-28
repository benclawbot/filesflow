package com.filesflow.features.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.filesflow.ui.theme.FilesFlowAccentOrange
import com.filesflow.ui.theme.FilesFlowOnSecondaryContainer
import com.filesflow.ui.theme.FilesFlowOnSurface
import com.filesflow.ui.theme.FilesFlowPrimary
import com.filesflow.ui.theme.FilesFlowPrimaryContainer
import com.filesflow.ui.theme.FilesFlowSecondary

@Composable
fun StorageOverviewCard(
    usedPercent: Int,
    usedLabel: String,
    totalLabel: String,
    onClick: (() -> Unit)? = null,
) {
    val clampedPercent = usedPercent.coerceIn(0, 100)
    NeumorphicSurface(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        cornerRadius = 12.dp,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Column {
                    Text(
                        text = "Storage",
                        color = FilesFlowAccentOrange,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(
                        text = "Internal Storage",
                        color = FilesFlowOnSurface,
                        style = MaterialTheme.typography.headlineMedium,
                    )
                }
                Text(
                    text = "$clampedPercent%",
                    color = FilesFlowPrimary,
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.End,
                )
            }

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .neumorphicRecessed(999.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(MaterialTheme.colorScheme.background)
                    .padding(4.dp),
            ) {
                val fillWidth = maxWidth * (clampedPercent / 100f)
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(fillWidth)
                        .neumorphicRaised(999.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(FilesFlowPrimaryContainer),
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = usedLabel,
                    color = FilesFlowOnSecondaryContainer,
                    style = MaterialTheme.typography.labelSmall,
                )
                Text(
                    text = totalLabel,
                    color = FilesFlowOnSecondaryContainer,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}
