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

    val files by viewModel.files.collectAsState()
    val finalName = if (newFileName.contains(".")) {
        newFileName.trim()
    } else {
        val ext = when (newFileLang) {
            "javascript" -> "js"
            "html" -> "html"
            "css" -> "css"
            else -> "js"
        }
        "${newFileName.trim()}.$ext"
    }
    val fileExists = files.any { it.name.equals(finalName, ignoreCase = true) && it.folder == selectedFolder }

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
                    isError = fileExists,
                    modifier = Modifier.fillMaxWidth()
                )
                
                if (fileExists) {
                    Text(
                        text = "⚠️ A file named '$finalName' already exists here.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

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
                    if (newFileName.isNotBlank() && !fileExists) {
                        viewModel.createFile(
                            name = newFileName.trim(), 
                            language = newFileLang, 
                            folder = selectedFolder
                        )
                        onDismiss()
                    }
                },
                enabled = newFileName.isNotBlank() && !fileExists
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
fun CreateProjectDialog(
    onDismiss: () -> Unit,
    viewModel: IdeViewModel
) {
    var newProjectName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create New Project") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "A new project will scaffold standard front-end files (index.html, style.css, script.js) with dynamic connections, allowing instant execution.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = newProjectName,
                    onValueChange = { newProjectName = it },
                    label = { Text("Project Name (e.g. login_page)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (newProjectName.isNotBlank()) {
                        val cleanName = newProjectName.trim().replace(" ", "_")
                        viewModel.createNewProject(cleanName)
                        onDismiss()
                    }
                },
                enabled = newProjectName.isNotBlank()
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
    var showOverwriteWarning by remember { mutableStateOf(false) }

    val files by viewModel.files.collectAsState()
    val currentFile by viewModel.currentFile.collectAsState()

    val finalName = if (saveAsNewName.contains(".")) {
        saveAsNewName.trim()
    } else {
        val extension = when (currentFile?.language) {
            "javascript" -> "js"
            "html" -> "html"
            "css" -> "css"
            else -> "js"
        }
        "${saveAsNewName.trim()}.$extension"
    }
    val fileExists = files.any { it.name.equals(finalName, ignoreCase = true) && it.folder == saveAsFolderName.trim() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (showOverwriteWarning) "Overwrite Warning" else "Save File As") },
        text = {
            if (showOverwriteWarning) {
                Text(
                    text = "A file named '$finalName' already exists in folder '${if (saveAsFolderName.trim().isEmpty()) "Root" else saveAsFolderName.trim()}'. Are you sure you want to overwrite it?",
                    color = MaterialTheme.colorScheme.error
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Save a copy of the current file in local workspace.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = saveAsNewName,
                        onValueChange = { saveAsNewName = it; showOverwriteWarning = false },
                        label = { Text("New File Name") },
                        singleLine = true,
                        isError = saveAsNewName.isNotBlank() && fileExists,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = saveAsFolderName,
                        onValueChange = { saveAsFolderName = it; showOverwriteWarning = false },
                        label = { Text("Folder Name (leave empty for Root)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (saveAsNewName.isNotBlank() && fileExists) {
                        Text(
                            text = "⚠️ File already exists. Clicking Save As will prompt to overwrite.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (saveAsNewName.isNotBlank()) {
                        if (fileExists && !showOverwriteWarning) {
                            showOverwriteWarning = true
                        } else {
                            viewModel.saveFileAs(saveAsNewName.trim(), saveAsFolderName.trim())
                            onDismiss()
                        }
                    }
                },
                enabled = saveAsNewName.isNotBlank(),
                colors = if (showOverwriteWarning) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error) else ButtonDefaults.buttonColors()
            ) {
                Text(if (showOverwriteWarning) "Overwrite" else "Save As")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    if (showOverwriteWarning) {
                        showOverwriteWarning = false
                    } else {
                        onDismiss()
                    }
                }
            ) {
                Text("Cancel")
            }
        }
    )
}
