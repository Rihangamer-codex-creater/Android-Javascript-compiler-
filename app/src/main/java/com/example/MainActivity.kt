package com.example

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.example.ui.ai.AiAssistScreen
import com.example.ui.browser.BrowserWindow
import com.example.ui.editor.JsEditor
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

    // Dialog state for creating a new file
    var showCreateDialog by remember { mutableStateOf(false) }
    var newFileName by remember { mutableStateOf("") }
    var newFileLang by remember { mutableStateOf("javascript") }

    // Folder states
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var selectedFolderForNewFile by remember { mutableStateOf("") }

    // Save As states
    var showSaveAsDialog by remember { mutableStateOf(false) }
    var saveAsNewName by remember { mutableStateOf("") }
    var saveAsFolderName by remember { mutableStateOf("") }

    // Screen configuration check for Android 16 split-layout
    val configuration = LocalConfiguration.current
    val isWideScreen = configuration.screenWidthDp >= 600

    // Visual theme colors matching Professional Polish Theme
    val darkMainColor = Color(0xFF1C1B1F)
    val lightMainColor = Color(0xFFF1F5F9)
    val headerBgColor = if (viewModel.isDarkMode) darkMainColor else lightMainColor
    val textColor = if (viewModel.isDarkMode) Color(0xFFE6E1E5) else Color(0xFF1C1B1F)
    val activePillColor = if (viewModel.isDarkMode) Color(0xFF4A4458) else Color(0xFFE8DEF8)
    val bottomBarBgColor = if (viewModel.isDarkMode) Color(0xFF25232A) else Color(0xFFF1F5F9)

    // Export Intent helper
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
                modifier = Modifier.width(300.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "📁 Project File Explorer",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = textColor,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    ) {
                        Button(
                            onClick = { 
                                selectedFolderForNewFile = ""
                                showCreateDialog = true 
                            },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "New File", modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("New File", fontSize = 10.sp)
                        }
                        Button(
                            onClick = { showCreateFolderDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.CreateNewFolder, contentDescription = "New Folder", modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("Folder", fontSize = 10.sp)
                        }
                        Button(
                            onClick = { 
                                currentFile?.let {
                                    saveAsNewName = it.name.substringBeforeLast(".")
                                    saveAsFolderName = it.folder
                                    showSaveAsDialog = true
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = "Save As", modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("Save As", fontSize = 10.sp)
                        }
                    }

                    HorizontalDivider(color = textColor.copy(alpha = 0.1f), thickness = 1.dp)

                    // Files selection list grouped by folder!
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                    ) {
                        val grouped = files.groupBy { it.folder }
                        
                        grouped.forEach { (folderName, folderFiles) ->
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = if (folderName.isEmpty()) Icons.Default.FolderOpen else Icons.Default.Folder,
                                            contentDescription = "Folder Icon",
                                            tint = if (folderName.isEmpty()) Color(0xFF62B6CB) else Color(0xFFFFB703),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (folderName.isEmpty()) "Workspace (Root)" else folderName,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = textColor.copy(alpha = 0.8f)
                                        )
                                    }
                                    
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        // Add file directly inside this folder
                                        IconButton(
                                            onClick = {
                                                selectedFolderForNewFile = folderName
                                                showCreateDialog = true
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Add,
                                                contentDescription = "Add File to Folder",
                                                tint = textColor.copy(alpha = 0.6f),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }

                                        // Delete whole folder button
                                        if (folderName.isNotEmpty()) {
                                            IconButton(
                                                onClick = { viewModel.deleteFolder(folderName) },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Delete Folder",
                                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            
                            items(folderFiles) { file ->
                                val isSelected = file.id == currentFile?.id
                                val fileBgColor = if (isSelected) activePillColor else Color.Transparent

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 16.dp) // Nested Indentation
                                        .background(fileBgColor, shape = MaterialTheme.shapes.small)
                                        .clickable {
                                            viewModel.selectFile(file.id)
                                            scope.launch { drawerState.close() }
                                        }
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = when (file.language) {
                                                "html" -> Icons.Default.Language
                                                "css" -> Icons.Default.Css
                                                else -> Icons.Default.Code
                                            },
                                            contentDescription = "File Icon",
                                            tint = when (file.language) {
                                                "html" -> Color(0xFFE34F26)
                                                "css" -> Color(0xFF264DE4)
                                                else -> Color(0xFFF7DF1E)
                                            },
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = file.name,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 13.sp,
                                            color = textColor
                                        )
                                    }

                                    // Delete button (do not allow deleting if it's the last file)
                                    if (files.size > 1) {
                                        IconButton(
                                            onClick = { viewModel.deleteFile(file) },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "Delete",
                                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    Text(
                        text = "JavaScript IDE v1.0",
                        fontSize = 11.sp,
                        color = textColor.copy(alpha = 0.4f),
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
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
                                    text = "Commute coding safe sandbox",
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

                            // Share / Save to Files action
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
                        Triple(4, "AI Coach", Icons.Default.AutoAwesome),
                        Triple(5, "Settings", Icons.Default.Settings)
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
                        // Left Column: Workspace Code Editor + Collapsible terminal
                        Column(
                            modifier = Modifier
                                .weight(1.2f)
                                .fillMaxHeight()
                        ) {
                            JsEditor(
                                code = viewModel.editorCode,
                                onCodeChanged = { viewModel.updateEditorCode(it) },
                                isDarkMode = viewModel.isDarkMode,
                                isAutocompleteEnabled = viewModel.isAutocompleteEnabled,
                                modifier = Modifier.weight(1f)
                            )
                            
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
                                modifier = Modifier.height(180.dp)
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
                                    AiAssistScreen(
                                        responseText = viewModel.aiResponseText,
                                        isLoading = viewModel.isAiLoading,
                                        onAskQuestion = { viewModel.askAiAssistant(it) },
                                        onApplyCodeToEditor = { viewModel.applyAiCodeToEditor(it) },
                                        onSaveAsProject = { viewModel.splitAndSaveAiResponseAsProject(it, viewModel.aiResponseText) },
                                        isDarkMode = viewModel.isDarkMode,
                                        isVoiceSupportEnabled = viewModel.isVoiceSupportEnabled
                                    )
                                }
                                5 -> {
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
                            Column(modifier = Modifier.fillMaxSize()) {
                                JsEditor(
                                    code = viewModel.editorCode,
                                    onCodeChanged = { viewModel.updateEditorCode(it) },
                                    isDarkMode = viewModel.isDarkMode,
                                    isAutocompleteEnabled = viewModel.isAutocompleteEnabled,
                                    modifier = Modifier.weight(1.0f)
                                )
                                
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
                                    modifier = Modifier.height(180.dp)
                                )
                            }
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
                            AiAssistScreen(
                                responseText = viewModel.aiResponseText,
                                isLoading = viewModel.isAiLoading,
                                onAskQuestion = { viewModel.askAiAssistant(it) },
                                onApplyCodeToEditor = { viewModel.applyAiCodeToEditor(it) },
                                onSaveAsProject = { viewModel.splitAndSaveAiResponseAsProject(it, viewModel.aiResponseText) },
                                isDarkMode = viewModel.isDarkMode,
                                isVoiceSupportEnabled = viewModel.isVoiceSupportEnabled
                            )
                        }
                        5 -> {
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

    // New File creation dialog
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { 
                Text(
                    text = if (selectedFolderForNewFile.isNotEmpty()) "New File in $selectedFolderForNewFile" else "Create New File"
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = newFileName,
                        onValueChange = { newFileName = it },
                        label = { Text("File Name (e.g. main)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Text("Select Language File Type:", style = MaterialTheme.typography.bodySmall)
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        listOf("javascript", "html", "css").forEach { lang ->
                            val isSelected = newFileLang == lang
                            FilterChip(
                                selected = isSelected,
                                onClick = { newFileLang = lang },
                                label = { Text(lang.uppercase()) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newFileName.isNotBlank()) {
                            viewModel.createFile(
                                name = newFileName.trim(), 
                                language = newFileLang, 
                                folder = selectedFolderForNewFile
                            )
                            newFileName = ""
                            showCreateDialog = false
                        }
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // New Project Folder creation dialog
    if (showCreateFolderDialog) {
        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false },
            title = { Text("Create Project Folder") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Creating a folder will automatically scaffold a 3-file web project (index.html, style.css, script.js) inside it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = newFolderName,
                        onValueChange = { newFolderName = it },
                        label = { Text("Folder Name (e.g. login_page)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newFolderName.isNotBlank()) {
                            val folderNameClean = newFolderName.trim().replace(" ", "_")
                            // Scaffold a complete 3-file workspace inside the project folder
                            viewModel.createFile("index.html", "html", folderNameClean)
                            viewModel.createFile("style.css", "css", folderNameClean)
                            viewModel.createFile("script.js", "javascript", folderNameClean)
                            newFolderName = ""
                            showCreateFolderDialog = false
                        }
                    }
                ) {
                    Text("Create Project")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFolderDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Save File As dialog
    if (showSaveAsDialog) {
        AlertDialog(
            onDismissRequest = { showSaveAsDialog = false },
            title = { Text("Save File As") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Save a copy of the current file with a new name.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = saveAsNewName,
                        onValueChange = { saveAsNewName = it },
                        label = { Text("New File Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = saveAsFolderName,
                        onValueChange = { saveAsFolderName = it },
                        label = { Text("Folder Name (leave empty for Root)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (saveAsNewName.isNotBlank()) {
                            viewModel.saveFileAs(saveAsNewName.trim(), saveAsFolderName.trim())
                            showSaveAsDialog = false
                        }
                    }
                ) {
                    Text("Save As")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveAsDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
