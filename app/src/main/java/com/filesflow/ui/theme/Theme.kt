package com.filesflow.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val FilesFlowColorScheme = lightColorScheme(
    primary = FilesFlowPrimary,
    onPrimary = FilesFlowBackground,
    primaryContainer = FilesFlowPrimaryContainer,
    onPrimaryContainer = FilesFlowBackground,
    secondary = FilesFlowSecondary,
    onSecondary = FilesFlowBackground,
    background = FilesFlowBackground,
    onBackground = FilesFlowOnSurface,
    surface = FilesFlowBackground,
    onSurface = FilesFlowOnSurface,
    surfaceContainerHigh = FilesFlowSurfaceContainerHigh,
    surfaceContainerHighest = FilesFlowSurfaceContainerHighest,
    onSurfaceVariant = FilesFlowOnSurfaceVariant,
)

@Composable
fun FilesFlowTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = FilesFlowColorScheme,
        typography = FilesFlowTypography,
        content = content,
    )
}
