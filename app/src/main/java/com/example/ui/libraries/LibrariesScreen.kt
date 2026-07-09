package com.example.ui.libraries

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.NpmLibrary
import com.example.ui.NpmSearchResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibrariesScreen(
    libraries: List<NpmLibrary>,
    downloadingName: String?,
    downloadError: String?,
    onDownload: (NpmLibrary) -> Unit,
    onRemoveCache: (NpmLibrary) -> Unit,
    onAddLibrary: (String, String) -> Unit,
    isDarkMode: Boolean,
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {},
    onSearchOnline: (String) -> Unit = {},
    searchResults: List<NpmSearchResult> = emptyList(),
    isSearchingOnline: Boolean = false,
    searchError: String? = null,
    modifier: Modifier = Modifier
) {
    var newLibName by remember { mutableStateOf("") }
    var newLibUrl by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }
    var activeSubTab by remember { mutableStateOf(0) } // 0: Installed, 1: Search NPM Online

    val bgColor = if (isDarkMode) Color(0xFF1C1B1F) else Color(0xFFFAFAFA)
    val cardBgColor = if (isDarkMode) Color(0xFF25232A) else Color(0xFFFFFFFF)
    val textColor = if (isDarkMode) Color(0xFFE6E1E5) else Color(0xFF1C1B1F)
    val secondaryTextColor = if (isDarkMode) Color(0xFF938F99) else Color(0xFF6A737D)
    val keyboardController = LocalSoftwareKeyboardController.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(bgColor)
            .padding(16.dp)
    ) {
        // Hero Card
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isDarkMode) Color(0xFF25232A) else Color(0xFFF1F5F9)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "📦 NPM Package Manager",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Add third-party CDN libraries to run complex apps. Download to cache references so they run 100% offline!",
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryTextColor
                )
            }
        }

        // Sub Tabs
        TabRow(
            selectedTabIndex = activeSubTab,
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 12.dp)
        ) {
            Tab(
                selected = activeSubTab == 0,
                onClick = { activeSubTab = 0 },
                text = { Text("Installed (${libraries.size})", fontWeight = FontWeight.Bold) }
            )
            Tab(
                selected = activeSubTab == 1,
                onClick = { activeSubTab = 1 },
                text = { Text("Search NPM Online", fontWeight = FontWeight.Bold) }
            )
        }

        // TAB 0: Installed References
        if (activeSubTab == 0) {
            // Action Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Project References",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = textColor
                )
                
                Button(
                    onClick = { showAddDialog = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Custom URL", fontSize = 12.sp)
                }
            }

            if (downloadError != null) {
                Text(
                    text = "⚠️ Error: $downloadError",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            // Installed list
            if (libraries.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No custom libraries imported yet.\nTap 'Search NPM Online' or add custom URLs.",
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        color = secondaryTextColor,
                        fontSize = 13.sp
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(libraries) { lib ->
                        val isDownloading = downloadingName == lib.name

                        Card(
                            colors = CardDefaults.cardColors(containerColor = cardBgColor),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1.0f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = lib.name,
                                            fontWeight = FontWeight.Bold,
                                            color = textColor,
                                            fontSize = 14.sp
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        if (lib.isDownloaded) {
                                            Box(
                                                modifier = Modifier
                                                    .background(
                                                        color = Color(0x204CAF50),
                                                        shape = MaterialTheme.shapes.extraSmall
                                                    )
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = "Offline Cached",
                                                    color = Color(0xFF4CAF50),
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = lib.url,
                                        color = secondaryTextColor,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 1
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                // Download status or actions
                                when {
                                    isDownloading -> {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                            strokeWidth = 2.dp
                                        )
                                    }
                                    lib.isDownloaded -> {
                                        IconButton(onClick = { onRemoveCache(lib) }) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "Delete cache",
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                    else -> {
                                        IconButton(onClick = { onDownload(lib) }) {
                                            Icon(
                                                Icons.Default.CloudDownload,
                                                contentDescription = "Download package",
                                                tint = MaterialTheme.colorScheme.primary
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

        // TAB 1: Search NPM Online
        if (activeSubTab == 1) {
            // Search Input Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = { Text("Search NPM (e.g. lodash, d3, axios)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        keyboardController?.hide()
                        onSearchOnline(searchQuery)
                    }),
                    modifier = Modifier.weight(1f)
                )

                Button(
                    onClick = {
                        keyboardController?.hide()
                        onSearchOnline(searchQuery)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.Search, contentDescription = "Search")
                }
            }

            if (isSearchingOnline) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (searchError != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "⚠️ Error: $searchError",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 14.sp
                    )
                }
            } else if (searchResults.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Search npm and download files for offline execution instantly.",
                        color = secondaryTextColor,
                        fontSize = 13.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(searchResults) { result ->
                        // Check if this library is already imported
                        val isAlreadyAdded = libraries.any { it.name == result.name }

                        Card(
                            colors = CardDefaults.cardColors(containerColor = cardBgColor),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1.0f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = result.name,
                                            fontWeight = FontWeight.Bold,
                                            color = textColor,
                                            fontSize = 14.sp
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "v${result.version}",
                                            color = secondaryTextColor,
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = result.description,
                                        color = secondaryTextColor,
                                        fontSize = 12.sp,
                                        maxLines = 2
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                if (isAlreadyAdded) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Added",
                                        tint = Color(0xFF4CAF50),
                                        modifier = Modifier.size(24.dp)
                                    )
                                } else {
                                    IconButton(
                                        onClick = {
                                            // Auto-map npm registry item to standard esm.sh CDN
                                            val esmUrl = "https://esm.sh/${result.name}@${result.version}"
                                            onAddLibrary(result.name, esmUrl)
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AddCircle,
                                            contentDescription = "Add library to project",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(28.dp)
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

    // Custom Add Dialog
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add Custom CDN Reference") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Input CDN module URLs (e.g. esm.sh, unpkg, or cdnjs).",
                        style = MaterialTheme.typography.bodySmall,
                        color = secondaryTextColor
                    )
                    OutlinedTextField(
                        value = newLibName,
                        onValueChange = { newLibName = it },
                        label = { Text("Library Module Name") },
                        placeholder = { Text("e.g. axios") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newLibUrl,
                        onValueChange = { newLibUrl = it },
                        label = { Text("CDN Direct URL") },
                        placeholder = { Text("e.g. https://esm.sh/axios@1.6.8") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newLibName.isNotBlank() && newLibUrl.isNotBlank()) {
                            onAddLibrary(newLibName.trim(), newLibUrl.trim())
                            newLibName = ""
                            newLibUrl = ""
                            showAddDialog = false
                        }
                    }
                ) {
                    Text("Add Reference")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
