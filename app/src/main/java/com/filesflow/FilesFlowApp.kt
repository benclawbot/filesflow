package com.filesflow

import androidx.compose.runtime.Composable
import com.filesflow.features.home.HomeDashboardScreen
import com.filesflow.ui.theme.FilesFlowTheme

@Composable
fun FilesFlowApp() {
    FilesFlowTheme {
        HomeDashboardScreen()
    }
}
