package com.filesflow

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.filesflow.data.AndroidFileManagerRepository
import com.filesflow.features.home.HomeDashboardScreen
import com.filesflow.features.home.FilesFlowViewModel
import com.filesflow.features.home.allFilesAccessIntent
import com.filesflow.features.home.currentStorageAccessState
import com.filesflow.features.home.mediaPermissionRequest
import com.filesflow.ui.theme.FilesFlowTheme

@Composable
fun FilesFlowApp() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val repository = remember(context) { AndroidFileManagerRepository(context) }
    val viewModel = viewModel<FilesFlowViewModel>(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return FilesFlowViewModel(repository) as T
            }
        },
    )
    val mediaPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        val accessState = currentStorageAccessState(context).copy(
            hasSafFolder = repository.getPersistedSafFolderName() != null,
        )
        viewModel.refresh(accessState)
    }
    val safFolderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            viewModel.persistSafFolder(uri)
        }
    }
    val allFilesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        val accessState = currentStorageAccessState(context).copy(
            hasSafFolder = repository.getPersistedSafFolderName() != null,
        )
        viewModel.refresh(accessState)
    }

    LaunchedEffect(Unit) {
        val accessState = currentStorageAccessState(context).copy(
            hasSafFolder = repository.getPersistedSafFolderName() != null,
        )
        viewModel.refresh(accessState)
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.updateAccessState(
                    currentStorageAccessState(context).copy(
                        hasSafFolder = repository.getPersistedSafFolderName() != null,
                    ),
                )
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    FilesFlowTheme {
        HomeDashboardScreen(
            viewModel = viewModel,
            onRequestMediaAccess = {
                mediaPermissionLauncher.launch(mediaPermissionRequest())
            },
            onRequestSafFolder = {
                safFolderLauncher.launch(null)
            },
            onRequestAllFilesAccess = {
                runCatching {
                    allFilesLauncher.launch(allFilesAccessIntent(context))
                }.onFailure {
                    allFilesLauncher.launch(Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                }
            },
        )
    }
}
