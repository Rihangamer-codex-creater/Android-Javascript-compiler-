package com.example

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
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
import com.example.ui.libraries.LibrariesScreen
import com.example.ui.settings.SettingsScreen
import com.example.ui.terminal.ConsoleTerminal
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: IdeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme(darkTheme = viewModel.isDarkMode) {
                MainLayout(viewModel)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.voiceAssistant.shutdown()
    }
}

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
    var selectedFolderForNewFile by remember { mutableStateOf("") }

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
        contract = ActivityResultContracts.CreateDocument("text/plain"),
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

    // Share / Export file contents helper
    val exportFile: (ProgramFile) -> Unit = { file ->
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, file.content)
            putExtra(Intent.EXTRA_TITLE, file.name)
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, "Export ${file.name} to My Files")
        context.startActivity(shareIntent)
    }

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
                        val suggestion = active?.name ?: "untitled.js"
                        createDocumentLauncher.launch(suggestion)
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
                        }
                    )
                    HorizontalDivider(
                        color = if (viewModel.isDarkMode) Color(0xFF49454F) else Color(0xFFE2E8F0),
                        thickness = 1.dp
                    )
                }
            },
            bottomBar = {
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
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
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
                                        onToggleAutoTerminate = { viewModel.toggleAutoTerminate() }
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
                                onToggleAutoTerminate = { viewModel.toggleAutoTerminate() }
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
}
