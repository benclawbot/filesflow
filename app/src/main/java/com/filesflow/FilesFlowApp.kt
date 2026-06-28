package com.filesflow

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.filesflow.data.AndroidFileManagerRepository
import com.filesflow.features.home.FileCategoryType
import com.filesflow.features.home.FilesFlowFile
import com.filesflow.features.home.FilesFlowViewModel
import com.filesflow.features.home.HomeDashboardScreen
import com.filesflow.features.home.StorageAccessState
import com.filesflow.features.home.SystemAccessRequest
import com.filesflow.features.home.allFilesAccessIntent
import com.filesflow.features.home.currentStorageAccessState
import com.filesflow.features.home.fileOpenIntent
import com.filesflow.features.home.fileShareIntent
import com.filesflow.features.home.mediaPermissionRequest
import com.filesflow.features.home.systemAccessRequestForBroadFiles
import com.filesflow.features.home.systemAccessRequestForCategory
import com.filesflow.ui.theme.FilesFlowTheme

private sealed interface PendingFilesFlowAction {
    data class OpenCategory(val type: FileCategoryType) : PendingFilesFlowAction
    data object BrowseRoot : PendingFilesFlowAction
    data class Search(val query: String) : PendingFilesFlowAction
}

@Composable
fun FilesFlowApp() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val repository = remember(context) { AndroidFileManagerRepository(context) }
    var currentAccessState by remember { mutableStateOf(currentStorageAccessState(context)) }
    var pendingAction by remember { mutableStateOf<PendingFilesFlowAction?>(null) }
    val viewModel = viewModel<FilesFlowViewModel>(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return FilesFlowViewModel(repository) as T
            }
        },
    )

    fun readAccessState() = currentStorageAccessState(context).copy(
        hasSafFolder = repository.getPersistedSafFolderName() != null,
    )

    fun refreshDashboard() {
        val accessState = readAccessState()
        currentAccessState = accessState
        viewModel.refresh(accessState)
    }

    fun updateDashboardAccess() {
        val accessState = readAccessState()
        currentAccessState = accessState
        viewModel.updateAccessState(accessState)
    }

    fun executeAction(action: PendingFilesFlowAction) {
        when (action) {
            is PendingFilesFlowAction.OpenCategory -> viewModel.openCategory(action.type)
            PendingFilesFlowAction.BrowseRoot -> viewModel.openBrowseRoot()
            is PendingFilesFlowAction.Search -> viewModel.search(action.query)
        }
    }

    fun requiredAccessFor(action: PendingFilesFlowAction, accessState: StorageAccessState): SystemAccessRequest {
        return when (action) {
            is PendingFilesFlowAction.OpenCategory -> systemAccessRequestForCategory(action.type, accessState)
            PendingFilesFlowAction.BrowseRoot -> systemAccessRequestForBroadFiles(accessState)
            is PendingFilesFlowAction.Search -> if (action.query.isBlank()) {
                SystemAccessRequest.None
            } else {
                systemAccessRequestForBroadFiles(accessState)
            }
        }
    }

    fun resumePendingActionAfterAccess() {
        val action = pendingAction ?: return
        val accessState = readAccessState()
        currentAccessState = accessState
        viewModel.updateAccessState(accessState)
        if (requiredAccessFor(action, accessState) == SystemAccessRequest.None) {
            pendingAction = null
            executeAction(action)
        } else {
            pendingAction = null
            viewModel.showAccessRequired()
        }
    }

    val mediaPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        refreshDashboard()
        resumePendingActionAfterAccess()
    }
    val allFilesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        refreshDashboard()
        resumePendingActionAfterAccess()
    }

    fun requestSystemAccess(request: SystemAccessRequest) {
        when (request) {
            SystemAccessRequest.MediaPermissions -> mediaPermissionLauncher.launch(mediaPermissionRequest())
            SystemAccessRequest.AllFilesAccess -> runCatching {
                allFilesLauncher.launch(allFilesAccessIntent(context))
            }.onFailure {
                allFilesLauncher.launch(Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
            SystemAccessRequest.None -> Unit
        }
    }

    fun openWithAccess(action: PendingFilesFlowAction) {
        if (action is PendingFilesFlowAction.Search && action.query.isBlank()) {
            pendingAction = null
            viewModel.openHome()
            return
        }

        val accessState = readAccessState()
        currentAccessState = accessState
        val request = requiredAccessFor(action, accessState)
        if (request == SystemAccessRequest.None) {
            pendingAction = null
            executeAction(action)
        } else {
            pendingAction = action
            requestSystemAccess(request)
        }
    }

    fun openFile(file: FilesFlowFile) {
        val intent = fileOpenIntent(context, file)
        if (intent == null) {
            viewModel.showFileOpenFailed(file.name)
            return
        }
        runCatching {
            context.startActivity(intent)
        }.onFailure { error ->
            if (error is ActivityNotFoundException) {
                viewModel.showFileOpenFailed(file.name)
            } else {
                viewModel.showFileOpenFailed(file.name)
            }
        }
    }

    fun shareFiles(files: List<FilesFlowFile>) {
        val intent = fileShareIntent(context, files)
        if (intent == null) {
            viewModel.showShareFailed()
            return
        }
        runCatching {
            context.startActivity(intent)
        }.onFailure {
            viewModel.showShareFailed()
        }
    }

    LaunchedEffect(Unit) {
        refreshDashboard()
        // Auto-prompt for all-files access on first launch.
        // The user needs MANAGE_EXTERNAL_STORAGE to see any files at all;
        // without it the dashboard shows an empty list with no obvious next step.
        val initialAccess = readAccessState()
        if (!initialAccess.hasAllFilesAccess && !initialAccess.hasLegacyReadPermission) {
            // Small delay so the dashboard renders first, then the system dialog stacks on top.
            kotlinx.coroutines.delay(400)
            requestSystemAccess(SystemAccessRequest.AllFilesAccess)
        }
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                updateDashboardAccess()
                resumePendingActionAfterAccess()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    FilesFlowTheme {
        val needsStorageAccess = !currentAccessState.hasAllFilesAccess && !currentAccessState.hasLegacyReadPermission
        HomeDashboardScreen(
            viewModel = viewModel,
            needsStorageAccess = needsStorageAccess,
            onRequestStorageAccess = {
                requestSystemAccess(SystemAccessRequest.AllFilesAccess)
            },
            onOpenCategory = { type ->
                openWithAccess(PendingFilesFlowAction.OpenCategory(type))
            },
            onOpenBrowseRoot = {
                openWithAccess(PendingFilesFlowAction.BrowseRoot)
            },
            onSearchFiles = { query ->
                openWithAccess(PendingFilesFlowAction.Search(query))
            },
            onOpenFile = ::openFile,
            onShareFiles = ::shareFiles,
        )
    }
}
