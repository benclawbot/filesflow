package com.filesflow

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import java.io.File

private sealed interface PendingFilesFlowAction {
    data class OpenCategory(val type: FileCategoryType) : PendingFilesFlowAction
    data object BrowseRoot : PendingFilesFlowAction
    data class Search(val query: String) : PendingFilesFlowAction
}

private sealed interface SavedFilesFlowRoute {
    data object Home : SavedFilesFlowRoute
    data class Category(val type: FileCategoryType) : SavedFilesFlowRoute
    data object BrowseRoot : SavedFilesFlowRoute
    data class Folder(
        val displayName: String,
        val path: String?,
        val uri: String?,
        val source: FileSource,
    ) : SavedFilesFlowRoute
    data class Search(val query: String) : SavedFilesFlowRoute
}

@Composable
fun FilesFlowApp() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val repository = remember(context) { IndexedFileManagerRepository(context) }
    var currentAccessState by remember { mutableStateOf(currentStorageAccessState(context)) }
    var pendingAction by remember { mutableStateOf<PendingFilesFlowAction?>(null) }
    var savedRoute by rememberSaveable { mutableStateOf(encodeSavedRoute(SavedFilesFlowRoute.Home)) }
    var routeRestored by rememberSaveable { mutableStateOf(false) }
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
            is PendingFilesFlowAction.OpenCategory -> {
                savedRoute = encodeSavedRoute(SavedFilesFlowRoute.Category(action.type))
                viewModel.openCategory(action.type)
            }
            PendingFilesFlowAction.BrowseRoot -> {
                savedRoute = encodeSavedRoute(SavedFilesFlowRoute.BrowseRoot)
                viewModel.openBrowseRoot()
            }
            is PendingFilesFlowAction.Search -> {
                savedRoute = encodeSavedRoute(SavedFilesFlowRoute.Search(action.query))
                viewModel.search(action.query)
            }
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
            savedRoute = encodeSavedRoute(SavedFilesFlowRoute.Home)
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

    fun openFolderAndSaveRoute(file: FilesFlowFile) {
        if (!file.isDirectory) return
        savedRoute = encodeSavedRoute(
            SavedFilesFlowRoute.Folder(
                displayName = file.name,
                path = file.path,
                uri = file.uri?.toString(),
                source = file.source,
            ),
        )
        viewModel.openFolder(file)
    }

    LaunchedEffect(Unit) {
        refreshDashboard()
        if (!routeRestored) {
            routeRestored = true
            restoreSavedRoute(
                encodedRoute = savedRoute,
                accessState = readAccessState(),
                viewModel = viewModel,
            )
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
            onOpenFolder = ::openFolderAndSaveRoute,
            onOpenHome = {
                savedRoute = encodeSavedRoute(SavedFilesFlowRoute.Home)
            },
            onOpenFile = ::openFile,
            onPrintFile = ::printFileFromAction,
            onShareFiles = ::shareFiles,
        )
    }
}

private fun encodeSavedRoute(route: SavedFilesFlowRoute): String {
    return when (route) {
        SavedFilesFlowRoute.Home -> "home"
        is SavedFilesFlowRoute.Category -> "category|${route.type.name}"
        SavedFilesFlowRoute.BrowseRoot -> "browse-root"
        is SavedFilesFlowRoute.Search -> "search|${Uri.encode(route.query)}"
        is SavedFilesFlowRoute.Folder -> listOf(
            "folder",
            route.source.name,
            Uri.encode(route.displayName),
            Uri.encode(route.path.orEmpty()),
            Uri.encode(route.uri.orEmpty()),
        ).joinToString("|")
    }
}

private fun decodeSavedRoute(encoded: String): SavedFilesFlowRoute {
    val parts = encoded.split('|')
    return when (parts.firstOrNull()) {
        "category" -> parts.getOrNull(1)
            ?.let { runCatching { FileCategoryType.valueOf(it) }.getOrNull() }
            ?.let(SavedFilesFlowRoute::Category)
            ?: SavedFilesFlowRoute.Home
        "browse-root" -> SavedFilesFlowRoute.BrowseRoot
        "search" -> parts.getOrNull(1)
            ?.let(Uri::decode)
            ?.takeIf { it.isNotBlank() }
            ?.let(SavedFilesFlowRoute::Search)
            ?: SavedFilesFlowRoute.Home
        "folder" -> {
            val source = parts.getOrNull(1)
                ?.let { runCatching { FileSource.valueOf(it) }.getOrNull() }
                ?: return SavedFilesFlowRoute.Home
            SavedFilesFlowRoute.Folder(
                source = source,
                displayName = parts.getOrNull(2)?.let(Uri::decode).orEmpty().ifBlank { "Folder" },
                path = parts.getOrNull(3)?.let(Uri::decode)?.takeIf { it.isNotBlank() },
                uri = parts.getOrNull(4)?.let(Uri::decode)?.takeIf { it.isNotBlank() },
            )
        }
        else -> SavedFilesFlowRoute.Home
    }
}

private fun restoreSavedRoute(
    encodedRoute: String,
    accessState: StorageAccessState,
    viewModel: FilesFlowViewModel,
) {
    when (val route = decodeSavedRoute(encodedRoute)) {
        SavedFilesFlowRoute.Home -> Unit
        is SavedFilesFlowRoute.Category -> {
            if (systemAccessRequestForCategory(route.type, accessState) == SystemAccessRequest.None) {
                viewModel.openCategory(route.type)
            }
        }
        SavedFilesFlowRoute.BrowseRoot -> {
            if (systemAccessRequestForBroadFiles(accessState) == SystemAccessRequest.None) {
                viewModel.openBrowseRoot()
            }
        }
        is SavedFilesFlowRoute.Search -> {
            if (systemAccessRequestForBroadFiles(accessState) == SystemAccessRequest.None) {
                viewModel.search(route.query)
            }
        }
        is SavedFilesFlowRoute.Folder -> {
            val restorable = when (route.source) {
                FileSource.DirectFile -> accessState.hasAllFilesAccess && route.path?.let(::File)?.isDirectory == true
                FileSource.Saf -> route.uri != null
                FileSource.MediaStore,
                FileSource.AppPackage -> false
            }
            if (restorable) {
                viewModel.openFolder(
                    FilesFlowFile(
                        id = "restored-${route.uri ?: route.path}",
                        name = route.displayName,
                        metadata = "Folder",
                        uri = route.uri?.let(Uri::parse),
                        path = route.path,
                        mimeType = null,
                        sizeBytes = 0L,
                        modifiedAtMillis = 0L,
                        source = route.source,
                        isDirectory = true,
                    ),
                )
            }
        }
    }
}