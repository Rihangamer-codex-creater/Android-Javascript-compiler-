package com.example.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.ProgramFile
import com.example.ui.IdeViewModel
import com.example.ui.browser.BrowserWindow
import com.example.ui.components.CreateFileDialog
import com.example.ui.components.CreateFolderDialog
import com.example.ui.components.SaveFileAsDialog
import com.example.ui.editor.JsEditor
import com.example.ui.explorer.ProjectDrawerContent
import com.example.ui.explorer.FileManagerScreen
import com.example.ui.libraries.LibrariesScreen
import com.example.ui.settings.SettingsScreen
import com.example.ui.terminal.ConsoleTerminal
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainLayout(viewModel: IdeViewModel) {
    val context = LocalContext.current
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // Database / State flows
    val files by viewModel.files.collectAsStateWithLifecycle()
    val currentFile by viewModel.currentFile.collectAsStateWithLifecycle()
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val libraries by viewModel.libraries.collectAsStateWithLifecycle()
    val runBundle by viewModel.runCodeEvent.collectAsStateWithLifecycle()

    // Dialog trigger states
    var showCreateDialog by remember { mutableStateOf(false) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var showSaveAsDialog by remember { mutableStateOf(false) }
    var showGuide by remember { mutableStateOf(false) }
    var selectedFolderForNewFile by remember { mutableStateOf("") }
    var showFileManager by remember { mutableStateOf(false) }

    // SAF (Storage Access Framework) System file selectors
    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null) {
                viewModel.openExternalFile(context, uri)
            }
        }
    )

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = DynamicCreateDocument(),
        onResult = { uri ->
            if (uri != null) {
                viewModel.saveCurrentAsExternal(context, uri)
            }
        }
    )

    // Screen configuration check for Android 16 split-layout
    val configuration = LocalConfiguration.current
    val isWideScreen = configuration.screenWidthDp >= 600

    // Visual theme colors matching Professional Polish Theme
    val darkMainColor = Color(0xFF1C1B1F)
    val lightMainColor = Color(0xFFFAFAFA)
    val headerBgColor = if (viewModel.isDarkMode) darkMainColor else lightMainColor
    val textColor = if (viewModel.isDarkMode) Color(0xFFE6E1E5) else Color(0xFF1C1B1F)
    val activePillColor = if (viewModel.isDarkMode) Color(0xFF4A4458) else Color(0xFFE8DEF8)
    val bottomBarBgColor = if (viewModel.isDarkMode) Color(0xFF25232A) else Color(0xFFF1F5F9)

    // Share / Export file contents helper - shares as a REAL file with its original extension!
    val exportFile: (ProgramFile) -> Unit = { file ->
        try {
            val exportDir = java.io.File(context.cacheDir, "exports").apply { mkdirs() }
            val tempFile = java.io.File(exportDir, file.name)
            tempFile.writeText(file.content)

            val authority = "${context.packageName}.fileprovider"
            val uri = androidx.core.content.FileProvider.getUriForFile(context, authority, tempFile)

            val mime = com.example.ui.files.ExternalFileHelper.detectMimeType(file.name)
            val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_STREAM, uri)
                type = mime
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val shareIntent = Intent.createChooser(sendIntent, "Export ${file.name}")
            context.startActivity(shareIntent)
        } catch (e: Exception) {
            Log.e("MainLayout", "Failed to export file", e)
            android.widget.Toast.makeText(context, "Export failed: ${e.localizedMessage}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    if (showFileManager) {
        FileManagerScreen(
            viewModel = viewModel,
            files = files,
            onClose = { showFileManager = false }
        )
    } else {
        ModalNavigationDrawer(
            drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = if (viewModel.isDarkMode) Color(0xFF111318) else Color(0xFFFFFFFF),
                modifier = Modifier.width(320.dp)
            ) {
                ProjectDrawerContent(
                    viewModel = viewModel,
                    files = files,
                    currentFile = currentFile,
                    onOpenExternalFileTriggered = {
                        scope.launch { drawerState.close() }
                        openDocumentLauncher.launch(arrayOf("text/plain", "application/javascript", "text/html", "text/css", "*/*"))
                    },
                    onSaveAsExternalTriggered = {
                        scope.launch { drawerState.close() }
                        val active = currentFile
                        val suggestionName = active?.name ?: "untitled.js"
                        val mime = com.example.ui.files.ExternalFileHelper.detectMimeType(suggestionName)
                        createDocumentLauncher.launch(DynamicCreateDocument.Input(mime, suggestionName))
                    },
                    onSaveToPublicDocumentsTriggered = {
                        scope.launch { drawerState.close() }
                        viewModel.saveToLocalPublicDocuments(context)
                    },
                    onNewFileTriggered = { folder ->
                        selectedFolderForNewFile = folder
                        showCreateDialog = true
                        scope.launch { drawerState.close() }
                    },
                    onNewFolderTriggered = {
                        showCreateFolderDialog = true
                        scope.launch { drawerState.close() }
                    },
                    onSaveAsLocalTriggered = {
                        showSaveAsDialog = true
                        scope.launch { drawerState.close() }
                    },
                    onOpenFileManagerTriggered = {
                        showFileManager = true
                        scope.launch { drawerState.close() }
                    },
                    onDismissDrawer = {
                        scope.launch { drawerState.close() }
                    }
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                Column {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = headerBgColor),
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, contentDescription = "Menu", tint = textColor)
                            }
                        },
                        title = {
                            Column {
                                Text(
                                    text = currentFile?.name ?: "Workspace",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textColor,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = if (currentFile?.externalUri != null) "Linked to Device File (Auto-save)" else "Commute coding safe sandbox",
                                    fontSize = 10.sp,
                                    color = textColor.copy(alpha = 0.5f)
                                )
                            }
                        },
                        actions = {
                            // Play / Pause execution buttons
                            if (viewModel.isCodeRunning) {
                                IconButton(onClick = { viewModel.stopCode() }) {
                                    Icon(Icons.Default.Stop, contentDescription = "Stop", tint = MaterialTheme.colorScheme.error)
                                }
                            } else {
                                IconButton(onClick = { viewModel.runCode() }) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = "Run", tint = Color(0xFF4CAF50))
                                }
                            }

                            // Share / Export Action
                            IconButton(
                                onClick = {
                                    currentFile?.let { exportFile(it) }
                                }
                            ) {
                                Icon(Icons.Default.Share, contentDescription = "Export File", tint = textColor)
                            }

                            // Advanced File Manager Action
                            IconButton(onClick = { showFileManager = true }) {
                                Icon(Icons.Default.Folder, contentDescription = "File Manager", tint = textColor)
                            }

                            // Interactive User Guide Action
                            IconButton(onClick = { showGuide = true }) {
                                Icon(Icons.Default.Help, contentDescription = "User Guide", tint = textColor)
                            }
                        }
                    )
                    HorizontalDivider(
                        color = if (viewModel.isDarkMode) Color(0xFF49454F) else Color(0xFFE2E8F0),
                        thickness = 1.dp
                    )
                }
            },
            bottomBar = {
                val isKeyboardVisible = WindowInsets.ime.asPaddingValues().calculateBottomPadding() > 0.dp
                if (!isKeyboardVisible) {
                    NavigationBar(containerColor = bottomBarBgColor) {
                        val items = listOf(
                            Triple(0, "Editor", Icons.Default.Code),
                            Triple(1, "Browser", Icons.Default.Language),
                            Triple(2, "Terminal", Icons.Default.Terminal),
                            Triple(3, "Libraries", Icons.Default.Inventory),
                            Triple(4, "Settings", Icons.Default.Settings)
                        )
                        items.forEach { (index, label, icon) ->
                            NavigationBarItem(
                                selected = viewModel.selectedTab == index,
                                onClick = { viewModel.selectedTab = index },
                                icon = { Icon(icon, contentDescription = label) },
                                label = { Text(label, fontSize = 11.sp) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = if (viewModel.isDarkMode) Color(0xFFE8DEF8) else Color(0xFF21133D),
                                    selectedTextColor = if (viewModel.isDarkMode) Color(0xFFE6E1E5) else Color(0xFF21133D),
                                    indicatorColor = activePillColor,
                                    unselectedIconColor = if (viewModel.isDarkMode) Color(0xFFCAC4D0) else Color(0xFF625B71),
                                    unselectedTextColor = if (viewModel.isDarkMode) Color(0xFFCAC4D0) else Color(0xFF625B71)
                                )
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Background Headless BrowserWindow to ensure JavaScript runs even when terminal/settings tabs are selected
                val isBrowserTabActive = if (isWideScreen) {
                    viewModel.selectedTab == 0 || viewModel.selectedTab == 1
                } else {
                    viewModel.selectedTab == 1
                }
                
                if (!isBrowserTabActive) {
                    Box(modifier = Modifier.size(1.dp).background(Color.Transparent)) {
                        BrowserWindow(
                            codeBundle = runBundle,
                            isDarkMode = viewModel.isDarkMode,
                            downloadedLibraries = libraries,
                            allFiles = files,
                            onJsConsoleLog = { type, msg ->
                                viewModel.addLog(type, msg)
                            },
                            onAutoTerminate = {
                                viewModel.autoTerminateCode()
                            },
                            onOpenExternalBrowser = null
                        )
                    }
                }
                if (isWideScreen) {
                    // Split screen for large/wide displays (Android 16 optimization)
                    Row(modifier = Modifier.fillMaxSize()) {
                        // Left Column: Workspace Code Editor (No small terminal under it!)
                        Column(
                            modifier = Modifier
                                .weight(1.2f)
                                .fillMaxHeight()
                        ) {
                            JsEditor(
                                fileId = currentFile?.id ?: 0,
                                code = viewModel.editorCode,
                                onCodeChanged = { viewModel.updateEditorCode(it) },
                                isDarkMode = viewModel.isDarkMode,
                                isAutocompleteEnabled = viewModel.isAutocompleteEnabled,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        VerticalDivider(
                            color = if (viewModel.isDarkMode) Color(0xFF49454F) else Color(0xFFE2E8F0),
                            thickness = 1.dp
                        )

                        // Right Column: Active Tab Content (or Preview if coding)
                        Box(
                            modifier = Modifier
                                .weight(0.8f)
                                .fillMaxHeight()
                        ) {
                            when (viewModel.selectedTab) {
                                0, 1 -> {
                                    // Live web preview beside editor
                                    BrowserWindow(
                                        codeBundle = runBundle,
                                        isDarkMode = viewModel.isDarkMode,
                                        downloadedLibraries = libraries,
                                        allFiles = files,
                                        onJsConsoleLog = { type, msg ->
                                            viewModel.addLog(type, msg)
                                        },
                                        onAutoTerminate = {
                                            viewModel.autoTerminateCode()
                                        },
                                        onOpenExternalBrowser = { viewModel.openInExternalBrowser(context) }
                                    )
                                }
                                2 -> {
                                    // Dedicated separate Terminal Page
                                    ConsoleTerminal(
                                        logs = logs,
                                        onClearLogs = { viewModel.clearLogs() },
                                        isDarkMode = viewModel.isDarkMode,
                                        isAutoTerminateEnabled = viewModel.isAutoTerminateEnabled,
                                        onToggleAutoTerminate = { viewModel.toggleAutoTerminate() },
                                        terminalsList = viewModel.terminals,
                                        activeTerminalId = viewModel.activeTerminalId,
                                        onSelectTerminal = { viewModel.activeTerminalId = it },
                                        onCreateTerminal = { viewModel.createTerminal() },
                                        onDeleteTerminal = { viewModel.deleteTerminal(it) },
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                3 -> {
                                    LibrariesScreen(
                                        libraries = libraries,
                                        downloadingName = viewModel.downloadingLibraryName,
                                        downloadError = viewModel.libraryDownloadError,
                                        onDownload = { viewModel.downloadLibrary(it) },
                                        onRemoveCache = { viewModel.removeCachedLibrary(it) },
                                        onAddLibrary = { name, url ->
                                            viewModel.addLibrary(name, url)
                                        },
                                        isDarkMode = viewModel.isDarkMode,
                                        searchQuery = viewModel.searchLibraryQuery,
                                        onSearchQueryChange = { viewModel.searchLibraryQuery = it },
                                        onSearchOnline = { viewModel.searchLibrariesOnline(it) },
                                        searchResults = viewModel.searchLibraryResults,
                                        isSearchingOnline = viewModel.isLibrarySearching,
                                        searchError = viewModel.librarySearchError
                                    )
                                }
                                 4 -> {
                                    SettingsScreen(
                                        isDarkMode = viewModel.isDarkMode,
                                        onToggleDarkMode = { viewModel.toggleDarkMode() },
                                        isJsExecutionEnabled = viewModel.isJsExecutionEnabled,
                                        onToggleJsExecution = { viewModel.toggleJsExecution() },
                                        isAutocompleteEnabled = viewModel.isAutocompleteEnabled,
                                        onToggleAutocomplete = { viewModel.toggleAutocomplete() },
                                        isVoiceSupportEnabled = viewModel.isVoiceSupportEnabled,
                                        onToggleVoiceSupport = { viewModel.toggleVoiceSupport() },
                                        isAutoTerminateEnabled = viewModel.isAutoTerminateEnabled,
                                        onToggleAutoTerminate = { viewModel.toggleAutoTerminate() },
                                        onOpenGuide = { showGuide = true }
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // Mobile-first single pane tab layout
                    when (viewModel.selectedTab) {
                        0 -> {
                            JsEditor(
                                fileId = currentFile?.id ?: 0,
                                code = viewModel.editorCode,
                                onCodeChanged = { viewModel.updateEditorCode(it) },
                                isDarkMode = viewModel.isDarkMode,
                                isAutocompleteEnabled = viewModel.isAutocompleteEnabled,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        1 -> {
                            BrowserWindow(
                                codeBundle = runBundle,
                                isDarkMode = viewModel.isDarkMode,
                                downloadedLibraries = libraries,
                                allFiles = files,
                                onJsConsoleLog = { type, msg ->
                                    viewModel.addLog(type, msg)
                                },
                                onAutoTerminate = {
                                    viewModel.autoTerminateCode()
                                },
                                onOpenExternalBrowser = { viewModel.openInExternalBrowser(context) }
                            )
                        }
                        2 -> {
                            // Dedicated separate Terminal Page on mobile
                            ConsoleTerminal(
                                logs = logs,
                                onClearLogs = { viewModel.clearLogs() },
                                isDarkMode = viewModel.isDarkMode,
                                isAutoTerminateEnabled = viewModel.isAutoTerminateEnabled,
                                onToggleAutoTerminate = { viewModel.toggleAutoTerminate() },
                                terminalsList = viewModel.terminals,
                                activeTerminalId = viewModel.activeTerminalId,
                                onSelectTerminal = { viewModel.activeTerminalId = it },
                                onCreateTerminal = { viewModel.createTerminal() },
                                onDeleteTerminal = { viewModel.deleteTerminal(it) },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        3 -> {
                            LibrariesScreen(
                                libraries = libraries,
                                downloadingName = viewModel.downloadingLibraryName,
                                downloadError = viewModel.libraryDownloadError,
                                onDownload = { viewModel.downloadLibrary(it) },
                                onRemoveCache = { viewModel.removeCachedLibrary(it) },
                                onAddLibrary = { name, url ->
                                    viewModel.addLibrary(name, url)
                                },
                                isDarkMode = viewModel.isDarkMode,
                                searchQuery = viewModel.searchLibraryQuery,
                                onSearchQueryChange = { viewModel.searchLibraryQuery = it },
                                onSearchOnline = { viewModel.searchLibrariesOnline(it) },
                                searchResults = viewModel.searchLibraryResults,
                                isSearchingOnline = viewModel.isLibrarySearching,
                                searchError = viewModel.librarySearchError
                            )
                        }
                        4 -> {
                            SettingsScreen(
                                isDarkMode = viewModel.isDarkMode,
                                onToggleDarkMode = { viewModel.toggleDarkMode() },
                                isJsExecutionEnabled = viewModel.isJsExecutionEnabled,
                                onToggleJsExecution = { viewModel.toggleJsExecution() },
                                isAutocompleteEnabled = viewModel.isAutocompleteEnabled,
                                onToggleAutocomplete = { viewModel.toggleAutocomplete() },
                                isVoiceSupportEnabled = viewModel.isVoiceSupportEnabled,
                                onToggleVoiceSupport = { viewModel.toggleVoiceSupport() },
                                isAutoTerminateEnabled = viewModel.isAutoTerminateEnabled,
                                onToggleAutoTerminate = { viewModel.toggleAutoTerminate() },
                                onOpenGuide = { showGuide = true }
                            )
                        }
                    }
                }
            }
        }
    }

    // Dialog components
    if (showCreateDialog) {
        CreateFileDialog(
            selectedFolder = selectedFolderForNewFile,
            onDismiss = { showCreateDialog = false },
            viewModel = viewModel
        )
    }

    if (showCreateFolderDialog) {
        CreateFolderDialog(
            onDismiss = { showCreateFolderDialog = false },
            viewModel = viewModel
        )
    }

    if (showSaveAsDialog) {
        SaveFileAsDialog(
            onDismiss = { showSaveAsDialog = false },
            viewModel = viewModel
        )
    }

    if (showGuide) {
        com.example.ui.components.UserGuideDialog(
            viewModel = viewModel,
            onDismiss = { showGuide = false }
        )
    }

    if (!viewModel.isStoragePermissionGranted) {
        StoragePermissionDialog(viewModel = viewModel)
    }
    }
}

@Composable
fun StoragePermissionDialog(viewModel: IdeViewModel) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = { /* Force selection */ },
        icon = {
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
        },
        title = {
            Text(
                text = "Storage Permission Required",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = if (viewModel.isDarkMode) Color.White else Color.Black
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "To open, edit, create, and save files (.html, .js, .txt, .css) directly on your device storage, CodeX Editor requires standard Storage Access permissions.",
                    fontSize = 14.sp,
                    color = if (viewModel.isDarkMode) Color(0xFFCAC4D0) else Color(0xFF49454F)
                )
                Text(
                    text = "🔒 Privacy Shield Protection:\nNone of your personal files, private keys, or source codes are processed outside your device. Everything runs 100% locally and offline. Your privacy is fully secured.",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    viewModel.requestStoragePermission(context)
                }
            ) {
                Text("Grant Permission")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    (context as? android.app.Activity)?.finish()
                }
            ) {
                Text("Close App")
            }
        }
    )
}

/**
 * Custom ActivityResultContract that accepts dynamic MIME type and filename suggestion.
 */
class DynamicCreateDocument : ActivityResultContract<DynamicCreateDocument.Input, Uri?>() {
    data class Input(val mimeType: String, val title: String)

    override fun createIntent(context: Context, input: Input): Intent {
        return Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType(input.mimeType)
            .putExtra(Intent.EXTRA_TITLE, input.title)
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
        return if (intent == null || resultCode != android.app.Activity.RESULT_OK) null else intent.data
    }
}
