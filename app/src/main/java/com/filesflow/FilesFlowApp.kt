package com.filesflow

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.filesflow.data.IndexedFileManagerRepository
import com.filesflow.features.home.BrowseMode
import com.filesflow.features.home.FileCategoryType
import com.filesflow.features.home.FileSource
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
import com.filesflow.features.home.printFile
import com.filesflow.features.home.systemAccessRequestForBroadFiles
import com.filesflow.features.home.systemAccessRequestForCategory
import com.filesflow.ui.theme.FilesFlowTheme

private sealed interface PendingFilesFlowAction {
    data class OpenCategory(val type: FileCategoryType) : PendingFilesFlowAction
    data object BrowseRoot : PendingFilesFlowAction
    data class Search(val query: String) : PendingFilesFlowAction
}

private enum class SavedRouteKind {
    Home,
    Category,
    BrowseRoot,
    Folder,
    Search,
}

@Composable
fun FilesFlowApp() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val repository = remember(context) { IndexedFileManagerRepository(context) }
    var currentAccessState by remember { mutableStateOf(currentStorageAccessState(context)) }
    var pendingAction by remember { mutableStateOf<PendingFilesFlowAction?>(null) }
    var savedRouteKind by rememberSaveable { mutableStateOf(SavedRouteKind.Home.name) }
    var savedCategory by rememberSaveable { mutableStateOf("") }
    var savedSearchQuery by rememberSaveable { mutableStateOf("") }
    var savedFolderUri by rememberSaveable { mutableStateOf("") }
    var savedFolderPath by rememberSaveable { mutableStateOf("") }
    var savedFolderName by rememberSaveable { mutableStateOf("") }
    var savedFolderSource by rememberSaveable { mutableStateOf(FileSource.DirectFile.name) }
    var routeRestorationAttempted by rememberSaveable { mutableStateOf(false) }
    val viewModel = viewModel<FilesFlowViewModel>(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return FilesFlowViewModel(repository) as T
            }
        },
    )
    val uiState by viewModel.uiState.collectAsState()

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
        }.onFailure {
            viewModel.showFileOpenFailed(file.name)
        }
    }

    fun printFileFromAction(file: FilesFlowFile) {
        val started = printFile(context, file)
        if (!started) {
            viewModel.showPrintFailed(file.name)
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

    LaunchedEffect(uiState.browseMode, uiState.destinationSelection) {
        if (uiState.destinationSelection != null) return@LaunchedEffect
        when (val mode = uiState.browseMode) {
            BrowseMode.Home -> {
                savedRouteKind = SavedRouteKind.Home.name
                savedCategory = ""
                savedSearchQuery = ""
                savedFolderUri = ""
                savedFolderPath = ""
                savedFolderName = ""
            }
            is BrowseMode.Category -> {
                savedRouteKind = SavedRouteKind.Category.name
                savedCategory = mode.type.name
            }
            is BrowseMode.Search -> {
                savedRouteKind = SavedRouteKind.Search.name
                savedSearchQuery = mode.query
            }
            is BrowseMode.Folder -> {
                val isRoot = mode.uri == null && mode.path == null
                savedRouteKind = if (isRoot) SavedRouteKind.BrowseRoot.name else SavedRouteKind.Folder.name
                savedFolderUri = mode.uri?.toString().orEmpty()
                savedFolderPath = mode.path.orEmpty()
                savedFolderName = mode.displayName
                savedFolderSource = mode.source.name
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshDashboard()
        val initialAccess = readAccessState()
        if (!routeRestorationAttempted && uiState.browseMode == BrowseMode.Home) {
            routeRestorationAttempted = true
            when (runCatching { SavedRouteKind.valueOf(savedRouteKind) }.getOrDefault(SavedRouteKind.Home)) {
                SavedRouteKind.Home -> Unit
                SavedRouteKind.Category -> runCatching { FileCategoryType.valueOf(savedCategory) }
                    .getOrNull()
                    ?.let { openWithAccess(PendingFilesFlowAction.OpenCategory(it)) }
                SavedRouteKind.Search -> savedSearchQuery
                    .takeIf { it.isNotBlank() }
                    ?.let { openWithAccess(PendingFilesFlowAction.Search(it)) }
                SavedRouteKind.BrowseRoot -> openWithAccess(PendingFilesFlowAction.BrowseRoot)
                SavedRouteKind.Folder -> {
                    val source = runCatching { FileSource.valueOf(savedFolderSource) }.getOrDefault(FileSource.DirectFile)
                    val canRestore = source == FileSource.Saf || initialAccess.hasAllFilesAccess || initialAccess.hasLegacyReadPermission
                    if (canRestore && (savedFolderUri.isNotBlank() || savedFolderPath.isNotBlank())) {
                        viewModel.openFolder(
                            FilesFlowFile(
                                id = "restored-${savedFolderUri.ifBlank { savedFolderPath }}",
                                name = savedFolderName.ifBlank { "Folder" },
                                metadata = "Folder",
                                uri = savedFolderUri.takeIf { it.isNotBlank() }?.let(Uri::parse),
                                path = savedFolderPath.takeIf { it.isNotBlank() },
                                mimeType = null,
                                sizeBytes = 0L,
                                modifiedAtMillis = 0L,
                                source = source,
                                isDirectory = true,
                            ),
                        )
                    }
                }
            }
        }
        if (!initialAccess.hasAllFilesAccess && !initialAccess.hasLegacyReadPermission) {
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
            onPrintFile = ::printFileFromAction,
            onShareFiles = ::shareFiles,
        )
    }
}
