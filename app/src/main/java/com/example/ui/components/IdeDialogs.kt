package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ui.IdeViewModel

@Composable
fun CreateFileDialog(
    selectedFolder: String,
    onDismiss: () -> Unit,
    viewModel: IdeViewModel
) {
    var newFileName by remember { mutableStateOf("") }
    var newFileLang by remember { mutableStateOf("javascript") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Text(
                text = if (selectedFolder.isNotEmpty()) "New File in $selectedFolder" else "Create New File"
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
                            folder = selectedFolder
                        )
                        onDismiss()
                    }
                }
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun CreateFolderDialog(
    onDismiss: () -> Unit,
    viewModel: IdeViewModel
) {
    var newFolderName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
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
                        viewModel.createFile("index.html", "html", folderNameClean)
                        viewModel.createFile("style.css", "css", folderNameClean)
                        viewModel.createFile("script.js", "javascript", folderNameClean)
                        onDismiss()
                    }
                }
            ) {
                Text("Create Project")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun SaveFileAsDialog(
    onDismiss: () -> Unit,
    viewModel: IdeViewModel
) {
    var saveAsNewName by remember { mutableStateOf("") }
    var saveAsFolderName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save File As") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Save a copy of the current file in local workspace.",
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
                        onDismiss()
                    }
                }
            ) {
                Text("Save As")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
