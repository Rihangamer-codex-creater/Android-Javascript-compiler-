@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.example.ui.explorer

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ProgramFile
import com.example.ui.IdeViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileManagerScreen(
    viewModel: IdeViewModel,
    files: List<ProgramFile>,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDarkMode = viewModel.isDarkMode
    val bgColor = if (isDarkMode) Color(0xFF1C1B1F) else Color(0xFFFAFAFA)
    val textColor = if (isDarkMode) Color(0xFFE6E1E5) else Color(0xFF1C1B1F)
    val cardBgColor = if (isDarkMode) Color(0xFF25232A) else Color(0xFFFFFFFF)
    val borderColor = if (isDarkMode) Color(0xFF49454F) else Color(0xFFE2E8F0)

    val context = androidx.compose.ui.platform.LocalContext.current
    val openTreeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            viewModel.setExternalWorkspace(context, uri)
        }
    }

    var showNewFileDialog by remember { mutableStateOf(false) }
    var showNewFolderDialog by remember { mutableStateOf(false) }

    // Selected file context action states
    var selectedFileForAction by remember { mutableStateOf<ProgramFile?>(null) }
    var contextMenuFile by remember { mutableStateOf<ProgramFile?>(null) }
    var contextMenuFolder by remember { mutableStateOf<String?>(null) }
    
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDuplicateDialog by remember { mutableStateOf(false) }
    var showMoveDialog by remember { mutableStateOf(false) }
    var showCopyDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showFolderDeleteConfirmDialog by remember { mutableStateOf(false) }

    // Filter, search and group files in current view folder
    val currentFolder = viewModel.currentBrowserFolder
    val searchQuery = viewModel.fileBrowserSearchQuery
    val sortBy = viewModel.fileBrowserSortBy

    // Get all folders and files in current virtual workspace folder
    val currentLevelFolders = remember(files, currentFolder, searchQuery) {
        files.map { it.folder }
            .filter { folder ->
                if (currentFolder.isEmpty()) {
                    folder.isNotEmpty() && !folder.contains("/")
                } else {
                    folder.startsWith("$currentFolder/") && !folder.substring(currentFolder.length + 1).contains("/")
                }
            }
            .distinct()
            .map { it.substringAfterLast('/') }
            .filter { it.contains(searchQuery, ignoreCase = true) }
    }

    val currentLevelFiles = remember(files, currentFolder, searchQuery, sortBy) {
        val filtered = files.filter { file ->
            val isInFolder = file.folder == currentFolder
            val matchesSearch = file.name.contains(searchQuery, ignoreCase = true)
            isInFolder && matchesSearch
        }
        
        when (sortBy) {
            "name_desc" -> filtered.sortedByDescending { it.name.lowercase() }
            "date_desc" -> filtered.sortedByDescending { it.lastModified }
            "size_desc" -> filtered.sortedByDescending { it.content.length }
            "type_asc" -> filtered.sortedBy { it.language }
            else -> filtered.sortedBy { it.name.lowercase() }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("File Manager", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Editor")
                    }
                },
                actions = {
                    // Search Bar toggle
                    IconButton(onClick = { viewModel.toggleBrowserGridView() }) {
                        Icon(
                            imageVector = if (viewModel.isBrowserGridView) Icons.Default.List else Icons.Default.GridView,
                            contentDescription = "Toggle Grid/List View"
                        )
                    }
                    IconButton(onClick = { showNewFolderDialog = true }) {
                        Icon(Icons.Default.CreateNewFolder, contentDescription = "New Folder")
                    }
                    IconButton(onClick = { showNewFileDialog = true }) {
                        Icon(Icons.Default.NoteAdd, contentDescription = "New File")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = bgColor,
                    titleContentColor = textColor,
                    actionIconContentColor = textColor,
                    navigationIconContentColor = textColor
                )
            )
        },
        containerColor = bgColor
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            var activeFileTab by remember { mutableStateOf(0) }

            TabRow(
                selectedTabIndex = activeFileTab,
                containerColor = bgColor,
                contentColor = textColor,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            ) {
                Tab(
                    selected = activeFileTab == 0,
                    onClick = { activeFileTab = 0 },
                    text = { Text("App Sandbox", fontWeight = FontWeight.Bold, fontSize = 13.sp) },
                    icon = { Icon(Icons.Default.CloudQueue, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = activeFileTab == 1,
                    onClick = { activeFileTab = 1 },
                    text = { Text("Device Storage", fontWeight = FontWeight.Bold, fontSize = 13.sp) },
                    icon = { Icon(Icons.Default.SdCard, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
            }

            if (activeFileTab == 0) {
                // Live Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.fileBrowserSearchQuery = it },
                placeholder = { Text("Search files...", fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.fileBrowserSearchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = textColor,
                    unfocusedTextColor = textColor,
                    focusedContainerColor = cardBgColor,
                    unfocusedContainerColor = cardBgColor
                )
            )

            // Dynamic Breadcrumbs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Root Button
                TextButton(
                    onClick = { viewModel.currentBrowserFolder = "" },
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Icon(Icons.Default.Home, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Root", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                if (currentFolder.isNotEmpty()) {
                    val parts = currentFolder.split("/")
                    var accumulatedPath = ""
                    parts.forEach { part ->
                        accumulatedPath = if (accumulatedPath.isEmpty()) part else "$accumulatedPath/$part"
                        val currentAccPath = accumulatedPath
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = textColor.copy(alpha = 0.5f),
                            modifier = Modifier.size(14.dp)
                        )
                        TextButton(
                            onClick = { viewModel.currentBrowserFolder = currentAccPath },
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(part, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Sorting dropdown selector
                var showSortMenu by remember { mutableStateOf(false) }
                IconButton(onClick = { showSortMenu = true }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Sort, contentDescription = "Sort Files", modifier = Modifier.size(20.dp))
                }
                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { showSortMenu = false }
                ) {
                    val sortOptions = listOf(
                        "name_asc" to "Name (A-Z)",
                        "name_desc" to "Name (Z-A)",
                        "date_desc" to "Date Modified",
                        "size_desc" to "File Size",
                        "type_asc" to "File Type"
                    )
                    sortOptions.forEach { (key, label) ->
                        DropdownMenuItem(
                            text = { Text(label, fontSize = 13.sp) },
                            onClick = {
                                viewModel.changeFileBrowserSortBy(key)
                                showSortMenu = false
                            },
                            leadingIcon = {
                                if (sortBy == key) {
                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            }
                        )
                    }
                }
            }

            // Quick Access Panels: Favorites & Recents (Show only at root level or when not searching)
            if (searchQuery.isEmpty()) {
                // Favorite Folders List
                if (viewModel.favoriteFolders.isNotEmpty()) {
                    Text(
                        text = "FAVORITE FOLDERS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor.copy(alpha = 0.6f),
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        viewModel.favoriteFolders.forEach { favorite ->
                            AssistChip(
                                onClick = { viewModel.currentBrowserFolder = favorite },
                                label = { Text(favorite, fontSize = 11.sp) },
                                leadingIcon = { Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFB703), modifier = Modifier.size(14.dp)) }
                            )
                        }
                    }
                }

                // Recently Opened Files Carousel
                if (viewModel.recentlyOpenedIds.isNotEmpty()) {
                    val recentFiles = remember(files, viewModel.recentlyOpenedIds) {
                        viewModel.recentlyOpenedIds.mapNotNull { id -> files.find { it.id == id } }
                    }
                    if (recentFiles.isNotEmpty()) {
                        Text(
                            text = "RECENTLY OPENED",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor.copy(alpha = 0.6f),
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                        )
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 100.dp)
                                .padding(bottom = 8.dp)
                        ) {
                            items(recentFiles.take(3)) { file ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = cardBgColor),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp)
                                        .combinedClickable(
                                            onClick = {
                                                viewModel.selectFile(file.id)
                                                onClose()
                                            }
                                        )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.History,
                                            contentDescription = null,
                                            tint = textColor.copy(alpha = 0.5f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = if (file.folder.isEmpty()) file.name else "${file.folder}/${file.name}",
                                            fontSize = 12.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = textColor,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Divider(color = borderColor, thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))

            // MAIN FILES & FOLDERS LIST
            if (currentLevelFolders.isEmpty() && currentLevelFiles.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = textColor.copy(alpha = 0.3f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No files or folders found here",
                            fontSize = 13.sp,
                            color = textColor.copy(alpha = 0.5f)
                        )
                    }
                }
            } else {
                if (viewModel.isBrowserGridView) {
                    // GRID VIEW LAYOUT
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        items(currentLevelFolders) { folder ->
                            val fullFolderPath = if (currentFolder.isEmpty()) folder else "$currentFolder/$folder"
                            val isFav = viewModel.favoriteFolders.contains(fullFolderPath)
                            FolderCardGrid(
                                name = folder,
                                isFavorite = isFav,
                                onClick = { viewModel.currentBrowserFolder = fullFolderPath },
                                onFavoriteToggle = { viewModel.toggleFavoriteFolder(fullFolderPath) },
                                onMoreOptions = {
                                    contextMenuFolder = fullFolderPath
                                },
                                cardBgColor = cardBgColor,
                                textColor = textColor
                            )
                        }

                        items(currentLevelFiles) { file ->
                            FileCardGrid(
                                file = file,
                                onClick = {
                                    viewModel.selectFile(file.id)
                                    onClose()
                                },
                                onMoreOptions = {
                                    selectedFileForAction = file
                                    contextMenuFile = file
                                },
                                cardBgColor = cardBgColor,
                                textColor = textColor
                            )
                        }
                    }
                } else {
                    // LIST VIEW LAYOUT
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        items(currentLevelFolders) { folder ->
                            val fullFolderPath = if (currentFolder.isEmpty()) folder else "$currentFolder/$folder"
                            val isFav = viewModel.favoriteFolders.contains(fullFolderPath)
                            FolderRowList(
                                name = folder,
                                isFavorite = isFav,
                                onClick = { viewModel.currentBrowserFolder = fullFolderPath },
                                onFavoriteToggle = { viewModel.toggleFavoriteFolder(fullFolderPath) },
                                onMoreOptions = {
                                    contextMenuFolder = fullFolderPath
                                },
                                textColor = textColor
                            )
                        }

                        items(currentLevelFiles) { file ->
                            FileRowList(
                                file = file,
                                onClick = {
                                    viewModel.selectFile(file.id)
                                    onClose()
                                },
                                onMoreOptions = {
                                    selectedFileForAction = file
                                    contextMenuFile = file
                                },
                                textColor = textColor
                            )
                        }
                    }
                }
            }
        }

        if (activeFileTab == 1) {
            // Device Storage Manager UI
            val externalUriState = viewModel.externalWorkspaceUri.collectAsState()
            val externalNameState = viewModel.externalWorkspaceName.collectAsState()
            val externalPathState = viewModel.currentExternalPath.collectAsState()
            
            val externalUri = externalUriState.value
            val externalName = externalNameState.value
            val externalPath = externalPathState.value
            
            // Dialog states for external storage
            var showNewExtFileDialog by remember { mutableStateOf(false) }
            var showNewExtFolderDialog by remember { mutableStateOf(false) }
            var extItemToDelete by remember { mutableStateOf<com.example.ui.ExternalStorageItem?>(null) }
            var extItemToRename by remember { mutableStateOf<com.example.ui.ExternalStorageItem?>(null) }
            
            if (externalUri == null) {
                // Not connected empty state
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                            modifier = Modifier.size(80.dp)
                        )
                        Text(
                            text = "Access Real Device Storage",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = textColor
                        )
                        Text(
                            text = "CodeX Editor allows you to directly read, write, create, and manage folders and files on your physical device. Connect any folder from your storage to get started.",
                            fontSize = 13.sp,
                            color = textColor.copy(alpha = 0.7f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        
                        Card(
                            colors = CardDefaults.cardColors(containerColor = cardBgColor.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.Security,
                                    contentDescription = "Security",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Uses Android Storage Access Framework (SAF) tree permissions. Secure, sandbox-compliant, and persistent across app relaunches.",
                                    fontSize = 11.sp,
                                    color = textColor.copy(alpha = 0.6f)
                                )
                            }
                        }
                        
                        Button(
                            onClick = { openTreeLauncher.launch(null) },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Select Storage Workspace", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                // Connected to a real folder tree!
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Storage,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = externalName ?: "Device Storage",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    TextButton(
                        onClick = { viewModel.disconnectExternalWorkspace() },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.PowerOff, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Disconnect", fontSize = 11.sp)
                    }
                }
                
                Divider(color = borderColor, thickness = 1.dp, modifier = Modifier.padding(vertical = 4.dp))
                
                // Device breadcrumbs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            viewModel.currentExternalPath.value = ""
                            viewModel.refreshExternalFiles(context)
                        },
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Icon(Icons.Default.Home, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Root", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    
                    if (externalPath.isNotEmpty()) {
                        val parts = externalPath.split("/")
                        var accumulatedPath = ""
                        parts.forEach { part ->
                            accumulatedPath = if (accumulatedPath.isEmpty()) part else "$accumulatedPath/$part"
                            val currentAccPath = accumulatedPath
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = textColor.copy(alpha = 0.5f),
                                modifier = Modifier.size(14.dp)
                            )
                            TextButton(
                                onClick = {
                                    viewModel.currentExternalPath.value = currentAccPath
                                    viewModel.refreshExternalFiles(context)
                                },
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text(part, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.weight(1f))
                    
                    // Add File & Add Folder quick buttons in workspace
                    IconButton(onClick = { showNewExtFolderDialog = true }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.CreateNewFolder, contentDescription = "Create folder", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = { showNewExtFileDialog = true }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.NoteAdd, contentDescription = "Create file", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
                
                Divider(color = borderColor, thickness = 1.dp, modifier = Modifier.padding(vertical = 4.dp))
                
                // File/Folder listing
                if (viewModel.externalFilesList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.FolderOpen,
                                contentDescription = null,
                                tint = textColor.copy(alpha = 0.3f),
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Empty Directory",
                                fontSize = 13.sp,
                                color = textColor.copy(alpha = 0.5f)
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        // Render Go Up if inside subfolder
                        if (externalPath.isNotEmpty()) {
                            item {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = cardBgColor.copy(alpha = 0.3f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp)
                                        .clickable {
                                            val lastSlash = externalPath.lastIndexOf("/")
                                            val parentPath = if (lastSlash == -1) "" else externalPath.substring(0, lastSlash)
                                            viewModel.currentExternalPath.value = parentPath
                                            viewModel.refreshExternalFiles(context)
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = "Go up",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(".. (Parent Folder)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = textColor)
                                    }
                                }
                            }
                        }
                        
                        items(viewModel.externalFilesList) { item ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = cardBgColor),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (item.isDirectory) {
                                                viewModel.currentExternalPath.value = item.absolutePath
                                                viewModel.refreshExternalFiles(context)
                                            } else {
                                                viewModel.openExternalStorageFile(context, item)
                                                onClose()
                                            }
                                        }
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (item.isDirectory) Icons.Default.Folder else Icons.Default.Code,
                                        contentDescription = null,
                                        tint = if (item.isDirectory) Color(0xFFFFB703) else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    
                                    Spacer(modifier = Modifier.width(12.dp))
                                    
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.name,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = textColor,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (!item.isDirectory) {
                                            val sizeStr = remember(item.size) {
                                                if (item.size > 1024 * 1024) String.format("%.1f MB", item.size / (1024.0 * 1024.0))
                                                else if (item.size > 1024) String.format("%.1f KB", item.size / 1024.0)
                                                else "${item.size} bytes"
                                            }
                                            val dateStr = remember(item.lastModified) {
                                                SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(item.lastModified))
                                            }
                                            Text(
                                                text = "$sizeStr  •  $dateStr",
                                                fontSize = 10.sp,
                                                color = textColor.copy(alpha = 0.5f)
                                            )
                                        }
                                    }
                                    
                                    var showExtMenu by remember { mutableStateOf(false) }
                                    Box {
                                        IconButton(onClick = { showExtMenu = true }, modifier = Modifier.size(24.dp)) {
                                            Icon(Icons.Default.MoreVert, contentDescription = "Options", modifier = Modifier.size(16.dp))
                                        }
                                        DropdownMenu(expanded = showExtMenu, onDismissRequest = { showExtMenu = false }) {
                                            DropdownMenuItem(
                                                text = { Text("Rename") },
                                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                                onClick = {
                                                    showExtMenu = false
                                                    extItemToRename = item
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp)) },
                                                onClick = {
                                                    showExtMenu = false
                                                    extItemToDelete = item
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            // Device Manager dialog implementations
            if (showNewExtFileDialog) {
                var newFileName by remember { mutableStateOf("") }
                var selectedExt by remember { mutableStateOf("js") }
                AlertDialog(
                    onDismissRequest = { showNewExtFileDialog = false },
                    title = { Text("New File in Device Storage", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = newFileName,
                                onValueChange = { newFileName = it },
                                label = { Text("File Name") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text("File Type:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = textColor.copy(alpha = 0.6f))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("js" to "JavaScript", "html" to "HTML", "css" to "CSS").forEach { (ext, label) ->
                                    FilterChip(
                                        selected = selectedExt == ext,
                                        onClick = { selectedExt = ext },
                                        label = { Text(label) }
                                    )
                                }
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (newFileName.isNotEmpty()) {
                                    viewModel.createExternalFile(context, newFileName, selectedExt)
                                    showNewExtFileDialog = false
                                }
                            }
                        ) { Text("Create") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showNewExtFileDialog = false }) { Text("Cancel") }
                    }
                )
            }
            
            if (showNewExtFolderDialog) {
                var newFolderName by remember { mutableStateOf("") }
                AlertDialog(
                    onDismissRequest = { showNewExtFolderDialog = false },
                    title = { Text("New Folder in Device Storage", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
                    text = {
                        OutlinedTextField(
                            value = newFolderName,
                            onValueChange = { newFolderName = it },
                            label = { Text("Folder Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (newFolderName.isNotEmpty()) {
                                    viewModel.createExternalFolder(context, newFolderName)
                                    showNewExtFolderDialog = false
                                }
                            }
                        ) { Text("Create") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showNewExtFolderDialog = false }) { Text("Cancel") }
                    }
                )
            }
            
            extItemToDelete?.let { item ->
                AlertDialog(
                    onDismissRequest = { extItemToDelete = null },
                    title = { Text("Confirm Deletion", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
                    text = { Text("Are you sure you want to permanently delete '${item.name}' from device storage? This cannot be undone.") },
                    confirmButton = {
                        Button(
                            onClick = {
                                viewModel.deleteExternalItem(context, item)
                                extItemToDelete = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) { Text("Delete") }
                    },
                    dismissButton = {
                        TextButton(onClick = { extItemToDelete = null }) { Text("Cancel") }
                    }
                )
            }
            
            extItemToRename?.let { item ->
                var renameVal by remember { mutableStateOf(item.name) }
                AlertDialog(
                    onDismissRequest = { extItemToRename = null },
                    title = { Text("Rename Item", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
                    text = {
                        OutlinedTextField(
                            value = renameVal,
                            onValueChange = { renameVal = it },
                            label = { Text("New Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (renameVal.isNotEmpty() && renameVal != item.name) {
                                    viewModel.renameExternalItem(context, item, renameVal)
                                    extItemToRename = null
                                }
                            }
                        ) { Text("Rename") }
                    },
                    dismissButton = {
                        TextButton(onClick = { extItemToRename = null }) { Text("Cancel") }
                    }
                )
            }
        }
    }
        }

    // Context Dropdown Menu for File
    contextMenuFile?.let { file ->
        AlertDialog(
            onDismissRequest = { contextMenuFile = null },
            title = { Text("File Options: ${file.name}", fontSize = 15.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            showRenameDialog = true
                            contextMenuFile = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = cardBgColor, contentColor = textColor)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Rename File")
                    }

                    Button(
                        onClick = {
                            showDuplicateDialog = true
                            contextMenuFile = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = cardBgColor, contentColor = textColor)
                    ) {
                        Icon(Icons.Default.FileCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Duplicate File")
                    }

                    Button(
                        onClick = {
                            showMoveDialog = true
                            contextMenuFile = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = cardBgColor, contentColor = textColor)
                    ) {
                        Icon(Icons.Default.DriveFileMove, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Move File")
                    }

                    Button(
                        onClick = {
                            showCopyDialog = true
                            contextMenuFile = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = cardBgColor, contentColor = textColor)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Copy File")
                    }

                    Button(
                        onClick = {
                            showDeleteConfirmDialog = true
                            contextMenuFile = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Delete File")
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { contextMenuFile = null }) {
                    Text("Close")
                }
            }
        )
    }

    // Context Dialog for Folder
    contextMenuFolder?.let { folderPath ->
        val folderName = folderPath.substringAfterLast('/')
        AlertDialog(
            onDismissRequest = { contextMenuFolder = null },
            title = { Text("Folder Options: $folderName", fontSize = 15.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            viewModel.toggleFavoriteFolder(folderPath)
                            contextMenuFolder = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = cardBgColor, contentColor = textColor)
                    ) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFB703), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (viewModel.favoriteFolders.contains(folderPath)) "Unfavorite Folder" else "Favorite Folder")
                    }

                    Button(
                        onClick = {
                            showFolderDeleteConfirmDialog = true
                            contextMenuFolder = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Delete Folder & Files")
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { contextMenuFolder = null }) {
                    Text("Close")
                }
            }
        )
    }

    // Dialog: Rename File
    if (showRenameDialog && selectedFileForAction != null) {
        var newName by remember { mutableStateOf(selectedFileForAction!!.name) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename File") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("New Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (newName.isNotBlank()) {
                        viewModel.renameFile(selectedFileForAction!!, newName.trim())
                        showRenameDialog = false
                    }
                }) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Dialog: Duplicate File
    if (showDuplicateDialog && selectedFileForAction != null) {
        var dupName by remember { mutableStateOf("Copy_of_" + selectedFileForAction!!.name) }
        AlertDialog(
            onDismissRequest = { showDuplicateDialog = false },
            title = { Text("Duplicate File") },
            text = {
                OutlinedTextField(
                    value = dupName,
                    onValueChange = { dupName = it },
                    label = { Text("Duplicate Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (dupName.isNotBlank()) {
                        viewModel.duplicateFile(selectedFileForAction!!, dupName.trim())
                        showDuplicateDialog = false
                    }
                }) {
                    Text("Duplicate")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDuplicateDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Dialog: Move File
    if (showMoveDialog && selectedFileForAction != null) {
        var destFolder by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showMoveDialog = false },
            title = { Text("Move File") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter destination folder path (e.g. src/utils, or leave blank for root):", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = destFolder,
                        onValueChange = { destFolder = it },
                        label = { Text("Destination Folder") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.moveFile(selectedFileForAction!!, destFolder)
                    showMoveDialog = false
                }) {
                    Text("Move")
                }
            },
            dismissButton = {
                TextButton(onClick = { showMoveDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Dialog: Copy File
    if (showCopyDialog && selectedFileForAction != null) {
        var destFolder by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCopyDialog = false },
            title = { Text("Copy File") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter destination folder path (e.g. src/utils, or leave blank for root):", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = destFolder,
                        onValueChange = { destFolder = it },
                        label = { Text("Destination Folder") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.copyFile(selectedFileForAction!!, destFolder)
                    showCopyDialog = false
                }) {
                    Text("Copy")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCopyDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Dialog: Delete File Confirmation
    if (showDeleteConfirmDialog && selectedFileForAction != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Confirm Deletion") },
            text = { Text("Are you sure you want to permanently delete '${selectedFileForAction!!.name}'?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteFile(selectedFileForAction!!)
                        showDeleteConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Dialog: Delete Folder Confirmation
    if (showFolderDeleteConfirmDialog && contextMenuFolder != null) {
        AlertDialog(
            onDismissRequest = { showFolderDeleteConfirmDialog = false },
            title = { Text("Confirm Folder Deletion") },
            text = { Text("Are you sure you want to delete folder '$contextMenuFolder' and ALL files inside it? This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteFolder(contextMenuFolder!!)
                        showFolderDeleteConfirmDialog = false
                        contextMenuFolder = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete Folder")
                }
            },
            dismissButton = {
                TextButton(onClick = { showFolderDeleteConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Dialog: Create New File
    if (showNewFileDialog) {
        var newName by remember { mutableStateOf("") }
        var newFileLang by remember { mutableStateOf("javascript") }
        AlertDialog(
            onDismissRequest = { showNewFileDialog = false },
            title = { Text(if (currentFolder.isNotEmpty()) "New File in $currentFolder" else "Create New File") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("File Name (e.g. script.js)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Select Language:", style = MaterialTheme.typography.bodySmall)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        listOf("javascript", "html", "css").forEach { lang ->
                            FilterChip(
                                selected = newFileLang == lang,
                                onClick = { newFileLang = lang },
                                label = { Text(lang.uppercase()) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (newName.isNotBlank()) {
                        viewModel.createFile(newName.trim(), newFileLang, currentFolder)
                        showNewFileDialog = false
                    }
                }) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewFileDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Dialog: Create New Folder
    if (showNewFolderDialog) {
        var folderName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewFolderDialog = false },
            title = { Text("Create New Folder") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Folders are created virtually inside the workspace database:", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = folderName,
                        onValueChange = { folderName = it },
                        label = { Text("Folder Name (e.g. assets)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (folderName.isNotBlank()) {
                        val finalPath = if (currentFolder.isEmpty()) folderName.trim() else "$currentFolder/${folderName.trim()}"
                        viewModel.createNewFolder(finalPath)
                        showNewFolderDialog = false
                    }
                }) {
                    Text("Create Folder")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewFolderDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// --- List Items UI ---

@Composable
fun FolderRowList(
    name: String,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onMoreOptions: () -> Unit,
    textColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Transparent, shape = MaterialTheme.shapes.small)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onMoreOptions
            )
            .padding(vertical = 8.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Folder, contentDescription = null, tint = Color(0xFFFFB703), modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = textColor)
            Text("Folder Directory", fontSize = 11.sp, color = textColor.copy(alpha = 0.5f))
        }
        IconButton(onClick = onFavoriteToggle, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                contentDescription = null,
                tint = if (isFavorite) Color(0xFFFFB703) else textColor.copy(alpha = 0.4f),
                modifier = Modifier.size(18.dp)
            )
        }
        IconButton(onClick = onMoreOptions, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = textColor.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun FileRowList(
    file: ProgramFile,
    onClick: () -> Unit,
    onMoreOptions: () -> Unit,
    textColor: Color
) {
    val dateStr = remember(file.lastModified) {
        val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
        sdf.format(Date(file.lastModified))
    }
    val sizeStr = remember(file.content) {
        val bytes = file.content.length
        if (bytes < 1024) "$bytes B" else String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Transparent, shape = MaterialTheme.shapes.small)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onMoreOptions
            )
            .padding(vertical = 8.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = when (file.language) {
                "html" -> Icons.Default.Language
                "css" -> Icons.Default.Css
                else -> Icons.Default.Code
            },
            contentDescription = null,
            tint = when (file.language) {
                "html" -> Color(0xFFE34F26)
                "css" -> Color(0xFF264DE4)
                else -> Color(0xFFF7DF1E)
            },
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(file.name, fontSize = 14.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, color = textColor)
            Text("$sizeStr • Modified $dateStr", fontSize = 11.sp, color = textColor.copy(alpha = 0.5f))
        }
        if (file.externalUri != null) {
            Icon(Icons.Default.Link, contentDescription = "Linked to device path", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
        }
        IconButton(onClick = onMoreOptions, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = textColor.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
        }
    }
}

// --- Grid Items UI ---

@Composable
fun FolderCardGrid(
    name: String,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onMoreOptions: () -> Unit,
    cardBgColor: Color,
    textColor: Color
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = cardBgColor),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onMoreOptions
            )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Folder, contentDescription = null, tint = Color(0xFFFFB703), modifier = Modifier.size(36.dp))
                Row {
                    IconButton(onClick = onFavoriteToggle, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = null,
                            tint = if (isFavorite) Color(0xFFFFB703) else textColor.copy(alpha = 0.4f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(onClick = onMoreOptions, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = textColor.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = textColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Directory", fontSize = 10.sp, color = textColor.copy(alpha = 0.5f))
        }
    }
}

@Composable
fun FileCardGrid(
    file: ProgramFile,
    onClick: () -> Unit,
    onMoreOptions: () -> Unit,
    cardBgColor: Color,
    textColor: Color
) {
    val sizeStr = remember(file.content) {
        val bytes = file.content.length
        if (bytes < 1024) "$bytes B" else String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = cardBgColor),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onMoreOptions
            )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = when (file.language) {
                        "html" -> Icons.Default.Language
                        "css" -> Icons.Default.Css
                        else -> Icons.Default.Code
                    },
                    contentDescription = null,
                    tint = when (file.language) {
                        "html" -> Color(0xFFE34F26)
                        "css" -> Color(0xFF264DE4)
                        else -> Color(0xFFF7DF1E)
                    },
                    modifier = Modifier.size(32.dp)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (file.externalUri != null) {
                        Icon(Icons.Default.Link, contentDescription = "Linked", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    IconButton(onClick = onMoreOptions, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = textColor.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(file.name, fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = textColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sizeStr, fontSize = 10.sp, color = textColor.copy(alpha = 0.5f))
        }
    }
}
