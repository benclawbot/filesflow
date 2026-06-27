package com.filesflow.features.home

import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.runtime.Composable
import com.filesflow.ui.theme.FilesFlowTheme

@Preview(
    name = "FilesFlow Home Dashboard",
    showBackground = true,
    backgroundColor = 0xFFFFF8F2,
    widthDp = 393,
    heightDp = 852,
)
@Composable
fun HomeDashboardPreview() {
    FilesFlowTheme {
        HomeDashboardScreen()
    }
}
