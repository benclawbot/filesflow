package com.filesflow.features.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.filesflow.ui.theme.FilesFlowBackground

fun Modifier.neumorphicRaised(cornerRadius: Dp = 12.dp): Modifier = drawBehind {
    val radius = cornerRadius.toPx()
    val darkOffset = 4.dp.toPx()
    val lightOffset = -4.dp.toPx()
    drawRoundRect(
        color = Color(0x141C1C18),
        topLeft = Offset(darkOffset, darkOffset),
        size = size,
        cornerRadius = CornerRadius(radius, radius),
    )
    drawRoundRect(
        color = Color.White,
        topLeft = Offset(lightOffset, lightOffset),
        size = size,
        cornerRadius = CornerRadius(radius, radius),
    )
}

fun Modifier.neumorphicRecessed(cornerRadius: Dp = 12.dp): Modifier = drawBehind {
    val radius = cornerRadius.toPx()
    val stroke = 2.dp.toPx()
    drawRoundRect(
        color = Color(0x0D1C1C18),
        topLeft = Offset(stroke, stroke),
        size = Size(size.width - stroke, size.height - stroke),
        cornerRadius = CornerRadius(radius, radius),
        style = Stroke(width = stroke),
    )
    drawRoundRect(
        color = Color.White,
        topLeft = Offset(-stroke, -stroke),
        size = Size(size.width + stroke, size.height + stroke),
        cornerRadius = CornerRadius(radius, radius),
        style = Stroke(width = stroke),
    )
}

@Composable
fun NeumorphicSurface(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 12.dp,
    recessed: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(cornerRadius)
    val shadowModifier = if (recessed) {
        Modifier.neumorphicRecessed(cornerRadius)
    } else {
        Modifier.neumorphicRaised(cornerRadius)
    }

    Box(
        modifier = modifier
            .then(shadowModifier)
            .clip(shape)
            .background(FilesFlowBackground, shape)
            .padding(0.dp),
        content = content,
    )
}
