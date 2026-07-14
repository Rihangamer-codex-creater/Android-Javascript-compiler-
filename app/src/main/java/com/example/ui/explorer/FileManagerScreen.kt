@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.example.ui.explorer

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    val currentDir = viewModel.currentPhysicalDir ?: File(context.filesDir, "workspace")

    // Dialog trigger states
    var showNewFileDialog by remember { mutableStateOf(false) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var showSaveAsDialog by remember { mutableStateOf(false) }
    
    var itemToRename by remember { mutableStateOf<File?>(null) }
    var itemToDelete by remember { mutableStateOf<File?>(null) }

    // Input fields for dialogs
    var inputName by remember { mutableStateOf("") }
    var selectedExtension by remember { mutableStateOf(".html") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Physical File Manager", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Editor")
                    }
                },
                actions = {
                    IconButton(onClick = { showSaveAsDialog = true }) {
                        Icon(Icons.Default.SaveAs, contentDescription = "Save As Copy", tint = MaterialTheme.colorScheme.primary)
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
            // Privacy Shield Banner
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = "Privacy Secure",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Privacy Shield Active",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = textColor
                        )
                        Text(
                            text = "CodeX operates 100% locally and offline. None of your device storage or personal data is collected or compromised.",
                            fontSize = 11.sp,
                            color = textColor.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            // Scrollable Breadcrumbs Path
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Storage,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))

                val segments = currentDir.absolutePath.split("/").filter { it.isNotEmpty() }
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            viewModel.setPhysicalDir(File("/storage/emulated/0"))
                        },
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text("Storage", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    var accumulatedPath = ""
                    segments.drop(2).forEach { segment -> // Drop /storage/emulated
                        accumulatedPath += "/$segment"
                        val fullPath = "/storage/emulated/0$accumulatedPath"
                        
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = textColor.copy(alpha = 0.4f),
                            modifier = Modifier.size(14.dp)
                        )
                        TextButton(
                            onClick = {
                                viewModel.setPhysicalDir(File(fullPath))
                            },
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(segment, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                 segments.getOrNull(0)?.let { first ->
                     if (segments.size <= 2) { // Non-standard external folder path
                         Icon(
                             imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                             contentDescription = null,
                             tint = textColor.copy(alpha = 0.4f),
                             modifier = Modifier.size(14.dp)
                         )
                         Text(first, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = textColor)
                     }
                 }
                }
            }

            HorizontalDivider(color = borderColor, thickness = 1.dp, modifier = Modifier.padding(vertical = 4.dp))

            // File Listing
            if (viewModel.filesInCurrentDir.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = textColor.copy(alpha = 0.3f),
                            modifier = Modifier.size(64.dp)
                        )
                        Text("This folder is empty or contains unsupported files", fontSize = 13.sp, color = textColor.copy(alpha = 0.5f))
                        Text("Supported formats: .html, .js, .txt, .css", fontSize = 11.sp, color = textColor.copy(alpha = 0.4f))
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    // "Go Up" item
                    if (currentDir.parentFile != null && currentDir.absolutePath != "/storage/emulated/0") {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = cardBgColor.copy(alpha = 0.3f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.setPhysicalDir(currentDir.parentFile!!)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowUpward,
                                        contentDescription = "Go Up",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(".. (Parent Folder)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = textColor)
                                }
                            }
                        }
                    }

                    items(viewModel.filesInCurrentDir) { file ->
                        var showMenu by remember { mutableStateOf(false) }

                        Card(
                            colors = CardDefaults.cardColors(containerColor = cardBgColor),
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = {
                                        if (file.isDirectory) {
                                            viewModel.setPhysicalDir(file)
                                        } else {
                                            viewModel.openPhysicalFile(context, file)
                                            onClose()
                                        }
                                    },
                                    onLongClick = { showMenu = true }
                                )
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    val icon = if (file.isDirectory) Icons.Default.Folder else Icons.Default.Description
                                    val iconColor = if (file.isDirectory) {
                                        Color(0xFFFFC107) // Folder Yellow
                                    } else {
                                        when (file.extension.lowercase()) {
                                            "html" -> Color(0xFFE44D26)
                                            "css" -> Color(0xFF264DE4)
                                            "js" -> Color(0xFFF7DF1E)
                                            else -> Color(0xFF90A4AE)
                                        }
                                    }

                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = iconColor,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column {
                                        Text(
                                            text = file.name,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.sp,
                                            color = textColor,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        
                                        val dateStr = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(file.lastModified()))
                                        val detailText = if (file.isDirectory) {
                                            val count = file.listFiles()?.size ?: 0
                                            "$count items | $dateStr"
                                        } else {
                                            val sizeKb = String.format(Locale.US, "%.1f", file.length() / 1024.0)
                                            "$sizeKb KB | $dateStr"
                                        }

                                        Text(
                                            text = detailText,
                                            fontSize = 11.sp,
                                            color = textColor.copy(alpha = 0.5f)
                                        )
                                    }
                                }

                                Box {
                                    IconButton(onClick = { showMenu = true }) {
                                        Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = textColor.copy(alpha = 0.7f))
                                    }

                                    DropdownMenu(
                                        expanded = showMenu,
                                        onDismissRequest = { showMenu = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Rename") },
                                            onClick = {
                                                showMenu = false
                                                inputName = file.name
                                                itemToRename = file
                                            },
                                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                            onClick = {
                                                showMenu = false
                                                itemToDelete = file
                                            },
                                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialogs Implementation

    // 1. Create File Dialog
    if (showNewFileDialog) {
        inputName = ""
        AlertDialog(
            onDismissRequest = { showNewFileDialog = false },
            title = { Text("Create New File", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = inputName,
                        onValueChange = { inputName = it },
                        label = { Text("Filename (without extension)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Select Extension:", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val extensions = listOf(".html", ".js", ".css", ".txt")
                        extensions.forEach { ext ->
                            val isSelected = selectedExtension == ext
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedExtension = ext },
                                label = { Text(ext, fontSize = 11.sp) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cleanName = inputName.trim()
                        if (cleanName.isNotEmpty()) {
                            val finalName = if (cleanName.endsWith(selectedExtension)) cleanName else "$cleanName$selectedExtension"
                            viewModel.createPhysicalFile(context, currentDir, finalName)
                        }
                        showNewFileDialog = false
                    }
                ) {
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

    // 2. Create Folder Dialog
    if (showNewFolderDialog) {
        inputName = ""
        AlertDialog(
            onDismissRequest = { showNewFolderDialog = false },
            title = { Text("Create New Folder", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
            text = {
                OutlinedTextField(
                    value = inputName,
                    onValueChange = { inputName = it },
                    label = { Text("Folder Name") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cleanName = inputName.trim()
                        if (cleanName.isNotEmpty()) {
                            viewModel.createPhysicalFolder(context, currentDir, cleanName)
                        }
                        showNewFolderDialog = false
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewFolderDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // 3. Save As Copy Dialog
    if (showSaveAsDialog) {
        inputName = ""
        AlertDialog(
            onDismissRequest = { showSaveAsDialog = false },
            title = { Text("Save Current File As", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Save active editor code as a real local file in the current directory.", fontSize = 12.sp)
                    OutlinedTextField(
                        value = inputName,
                        onValueChange = { inputName = it },
                        label = { Text("Filename (without extension)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Select Extension:", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val extensions = listOf(".html", ".js", ".css", ".txt")
                        extensions.forEach { ext ->
                            val isSelected = selectedExtension == ext
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedExtension = ext },
                                label = { Text(ext, fontSize = 11.sp) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cleanName = inputName.trim()
                        if (cleanName.isNotEmpty()) {
                            val finalName = if (cleanName.endsWith(selectedExtension)) cleanName else "$cleanName$selectedExtension"
                            viewModel.saveActiveFileAs(context, currentDir, finalName)
                        }
                        showSaveAsDialog = false
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

    // 4. Rename Dialog
    itemToRename?.let { item ->
        AlertDialog(
            onDismissRequest = { itemToRename = null },
            title = { Text("Rename Item", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
            text = {
                OutlinedTextField(
                    value = inputName,
                    onValueChange = { inputName = it },
                    label = { Text("New Name") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cleanName = inputName.trim()
                        if (cleanName.isNotEmpty() && cleanName != item.name) {
                            viewModel.renamePhysicalItem(context, item, cleanName)
                        }
                        itemToRename = null
                    }
                ) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToRename = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // 5. Delete Confirm Dialog
    itemToDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text("Delete Item?", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
            text = {
                Text(
                    text = "Are you sure you want to permanently delete \"${item.name}\"? This action cannot be undone.",
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deletePhysicalItem(context, item)
                        itemToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
