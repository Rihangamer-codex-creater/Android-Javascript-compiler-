package com.example.ui.explorer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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

@Composable
fun ProjectDrawerContent(
    viewModel: IdeViewModel,
    files: List<ProgramFile>,
    currentFile: ProgramFile?,
    onOpenExternalFileTriggered: () -> Unit,
    onSaveAsExternalTriggered: () -> Unit,
    onSaveToPublicDocumentsTriggered: () -> Unit,
    onNewFileTriggered: (String) -> Unit,
    onNewFolderTriggered: () -> Unit,
    onSaveAsLocalTriggered: () -> Unit,
    onOpenFileManagerTriggered: () -> Unit,
    onDismissDrawer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDarkMode = viewModel.isDarkMode
    val drawerBgColor = if (isDarkMode) Color(0xFF1C1B1F) else Color(0xFFFAFAFA)
    val textColor = if (isDarkMode) Color(0xFFE6E1E5) else Color(0xFF1C1B1F)
    val activePillColor = if (isDarkMode) Color(0xFF49454F) else Color(0xFFE2E8F0)
    val cardBgColor = if (isDarkMode) Color(0xFF25232A) else Color(0xFFFFFFFF)
    val borderColor = if (isDarkMode) Color(0xFF49454F) else Color(0xFFE2E8F0)

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(320.dp)
            .background(drawerBgColor)
            .padding(16.dp)
    ) {
        // App Title
        Text(
            text = "CodeX Editor",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = textColor,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            text = "Device-first Offline Development",
            fontSize = 11.sp,
            color = textColor.copy(alpha = 0.5f),
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Divider(color = borderColor, thickness = 1.dp)

        // 1. File Operations Menu (NEW, OPEN, SAVE, SAVE AS, CLOSE)
        Text(
            text = "FILE OPERATIONS",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = textColor.copy(alpha = 0.6f),
            modifier = Modifier.padding(vertical = 12.dp)
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = cardBgColor),
            border = BorderStroke(1.dp, borderColor),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Row 1: Create New Project
                Button(
                    onClick = onNewFolderTriggered, // Triggers CreateProjectDialog
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Create New Project", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                // Row 2: Load Device File & New File (Inside workspace/project)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onOpenExternalFileTriggered, // Load external file via SAF
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary,
                            contentColor = MaterialTheme.colorScheme.onSecondary
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Load File", fontSize = 11.sp, maxLines = 1)
                    }

                    Button(
                        onClick = { onNewFileTriggered("") },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary,
                            contentColor = MaterialTheme.colorScheme.onTertiary
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("New File", fontSize = 11.sp, maxLines = 1)
                    }
                }

                // Row 3: Save & Save As (Device Storage)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            viewModel.saveCurrentFileImmediately()
                            onDismissDrawer()
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = textColor),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Save File", fontSize = 11.sp)
                    }

                    Button(
                        onClick = onSaveAsExternalTriggered, // Save copy to device storage via SAF
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.SaveAs, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Save As", fontSize = 11.sp, maxLines = 1)
                    }
                }

                // Row 4: Close Active File
                OutlinedButton(
                    onClick = {
                        viewModel.closeCurrentFile()
                        onDismissDrawer()
                    },
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Close Current File", fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Divider(color = borderColor, thickness = 1.dp)

        // 2. Local Workspace Explorer (Grouping files by folders)
        Text(
            text = "LOCAL WORKSPACE FILES",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = textColor.copy(alpha = 0.6f),
            modifier = Modifier.padding(vertical = 12.dp)
        )

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1.0f)
                        ) {
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
                                color = textColor.copy(alpha = 0.8f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Add file directly inside this folder
                            IconButton(
                                onClick = { onNewFileTriggered(folderName) },
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
                                onDismissDrawer()
                            }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
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
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = file.name,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp,
                                    color = textColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (file.externalUri != null) {
                                    Text(
                                        text = "Linked to Device File",
                                        fontSize = 9.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
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

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "CodeX Editor v3.8 | Created by CodeX",
            fontSize = 10.sp,
            color = textColor.copy(alpha = 0.4f),
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}
