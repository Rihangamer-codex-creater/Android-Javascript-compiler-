package com.example.ui

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.ui.speech.VoiceAssistant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class IdeViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "IdeViewModel"
    private val db = AppDatabase.getDatabase(application)
    private val repository = IdeRepository(db)

    // Debounced database saving job to eliminate typing lag
    private var saveJob: kotlinx.coroutines.Job? = null
    private val saveScope = kotlinx.coroutines.CoroutineScope(Dispatchers.IO + kotlinx.coroutines.SupervisorJob())
    private var loadedFileId: Int? = null

    // Speech and voice support
    val voiceAssistant = VoiceAssistant(application)

    // UI States
    val files: StateFlow<List<ProgramFile>> = repository.files
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentFile: StateFlow<ProgramFile?> = repository.currentFile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val libraries: StateFlow<List<NpmLibrary>> = repository.libraries
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val logs: StateFlow<List<ConsoleLog>> = repository.logs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Code editing buffer (local in-memory state for fast keyboard interaction)
    var editorCode by mutableStateOf("")
        private set

    // Code execution state
    var isCodeRunning by mutableStateOf(false)
    var runCodeEvent = MutableStateFlow<String?>(null)
    var selectedTab by mutableStateOf(0) // 0: Editor, 1: Browser/Preview, 2: Terminal, 3: Libraries, 4: Settings

    // Settings States
    var isDarkMode by mutableStateOf(false)
    var isJsExecutionEnabled by mutableStateOf(true)
    var isAutocompleteEnabled by mutableStateOf(true)
    var isVoiceSupportEnabled by mutableStateOf(false)
    var isAutoTerminateEnabled by mutableStateOf(true)

    // Storage permission state
    var isStoragePermissionGranted by mutableStateOf(false)
        private set

    fun checkStoragePermission(context: android.content.Context) {
        isStoragePermissionGranted = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            android.os.Environment.isExternalStorageManager()
        } else {
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }

        if (isStoragePermissionGranted) {
            val root = com.example.ui.files.LocalFileSystemManager.getWorkspaceRoot(context)
            if (currentPhysicalDir == null) {
                setPhysicalDir(root)
            }
            syncWithDeviceStorage()
        }
    }

    fun requestStoragePermission(context: android.content.Context) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            try {
                val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = android.net.Uri.parse("package:${context.packageName}")
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                try {
                    val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).apply {
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } catch (ex: Exception) {
                    Log.e("IdeViewModel", "Failed to launch manage all files permission screen", ex)
                }
            }
        } else {
            // Send signal to MainActivity to request legacy permissions
            val intent = android.content.Intent(context, com.example.MainActivity::class.java).apply {
                putExtra("request_legacy_permissions", true)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            context.startActivity(intent)
        }
    }

    private var hasSyncedOnce = false

    fun syncWithDeviceStorage() {
        if (hasSyncedOnce) return
        hasSyncedOnce = true
        viewModelScope.launch {
            try {
                com.example.ui.files.LocalFileSystemManager.syncPhysicalWorkspaceWithDb(getApplication(), db)
                // Select first file if none is current
                val current = repository.getFilesList().find { it.isCurrent }
                if (current == null) {
                    val all = repository.getFilesList()
                    if (all.isNotEmpty()) {
                        repository.selectFile(all.first().id)
                    }
                }
            } catch (e: Exception) {
                Log.e("IdeViewModel", "Failed syncWithDeviceStorage", e)
            } finally {
                hasSyncedOnce = false
            }
        }
    }

    // --- Device Physical Storage File Manager ---
    val externalWorkspaceUri = MutableStateFlow<String?>(null)
    val externalWorkspaceName = MutableStateFlow<String?>(null)
    val externalFilesList = androidx.compose.runtime.mutableStateListOf<ExternalStorageItem>()
    val currentExternalPath = MutableStateFlow("")

    // --- File Saving & Management System ---
    var isAutosaveEnabled by mutableStateOf(true)
    var isBrowserGridView by mutableStateOf(false)
    var fileBrowserSortBy by mutableStateOf("name_asc")
    var fileBrowserSearchQuery by mutableStateOf("")
    var currentBrowserFolder by mutableStateOf("")

    val recentlyOpenedIds = androidx.compose.runtime.mutableStateListOf<Int>()
    val favoriteFolders = androidx.compose.runtime.mutableStateListOf<String>()

    // Unsaved work recovery and dirty tracking
    val hasUnsavedChanges: Boolean
        get() = currentFile.value != null && editorCode != currentFile.value?.content

    // We can show warning dialogs
    var showUnsavedChangesWarningForId by mutableStateOf<Int?>(null)
    var showUnsavedChangesWarningForAction by mutableStateOf<(() -> Unit)?>(null)

    // Multiple terminals support
    var activeTerminalId by mutableStateOf(1)
    val terminals = androidx.compose.runtime.mutableStateListOf<TerminalInstance>()

    val activeTerminalLogs: List<ConsoleLog>
        get() = terminals.find { it.id == activeTerminalId }?.logs ?: emptyList()

    // NPM Library search states
    var searchLibraryQuery by mutableStateOf("")
    val searchLibraryResults = androidx.compose.runtime.mutableStateListOf<NpmSearchResult>()
    var isLibrarySearching by mutableStateOf(false)
    var librarySearchError by mutableStateOf<String?>(null)

    fun toggleVoiceSupport() {
        isVoiceSupportEnabled = !isVoiceSupportEnabled
        voiceAssistant.isEnabled = isVoiceSupportEnabled
        viewModelScope.launch {
            repository.saveSetting("isVoiceSupportEnabled", isVoiceSupportEnabled.toString())
        }
    }

    fun toggleAutoTerminate() {
        isAutoTerminateEnabled = !isAutoTerminateEnabled
        viewModelScope.launch {
            repository.saveSetting("isAutoTerminateEnabled", isAutoTerminateEnabled.toString())
        }
    }

    // Library downloading states
    var downloadingLibraryName by mutableStateOf<String?>(null)
    var libraryDownloadError by mutableStateOf<String?>(null)

    // Search inside file states
    var searchActive by mutableStateOf(false)
    var searchQuery by mutableStateOf("")

    // Physical File Manager States and Methods
    var currentPhysicalDir by mutableStateOf<java.io.File?>(null)
        private set
    val filesInCurrentDir = androidx.compose.runtime.mutableStateListOf<java.io.File>()

    fun setPhysicalDir(dir: java.io.File) {
        currentPhysicalDir = dir
        refreshFilesInCurrentDir()
    }

    fun refreshFilesInCurrentDir() {
        val dir = currentPhysicalDir ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val list = dir.listFiles()?.toList() ?: emptyList()
            val filtered = list.filter {
                it.isDirectory || it.name.endsWith(".html") || it.name.endsWith(".js") || it.name.endsWith(".css") || it.name.endsWith(".txt")
            }.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            
            withContext(Dispatchers.Main) {
                filesInCurrentDir.clear()
                filesInCurrentDir.addAll(filtered)
            }
        }
    }

    fun openPhysicalFile(context: android.content.Context, file: java.io.File) {
        viewModelScope.launch {
            try {
                val content = file.readText()
                val language = com.example.ui.files.ExternalFileHelper.detectLanguage(file.name)
                
                val existing = repository.getFilesList().find { it.externalUri == file.absolutePath }
                if (existing != null) {
                    val updated = existing.copy(content = content, isCurrent = true, lastModified = System.currentTimeMillis())
                    repository.updateFile(updated)
                    repository.selectFile(updated.id)
                } else {
                    db.programFileDao().clearCurrentStatus()
                    val newProgramFile = ProgramFile(
                        name = file.name,
                        content = content,
                        language = language,
                        isCurrent = true,
                        folder = file.parentFile?.name ?: "",
                        externalUri = file.absolutePath
                    )
                    val id = repository.insertFile(newProgramFile)
                    repository.selectFile(id)
                }
                
                editorCode = content
                
                viewModelScope.launch {
                    com.example.ui.files.LocalFileSystemManager.syncPhysicalWorkspaceWithDb(getApplication(), db)
                }
            } catch (e: Exception) {
                Log.e("IdeViewModel", "Failed to open physical file: ${file.absolutePath}", e)
                android.widget.Toast.makeText(context, "Error opening file: ${e.localizedMessage}", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun createPhysicalFile(context: android.content.Context, parentDir: java.io.File, name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = java.io.File(parentDir, name)
                if (!file.exists()) {
                    file.createNewFile()
                    file.writeText("")
                }
                refreshFilesInCurrentDir()
                withContext(Dispatchers.Main) {
                    openPhysicalFile(context, file)
                }
            } catch (e: Exception) {
                Log.e("IdeViewModel", "Failed to create file", e)
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Failed to create file: ${e.localizedMessage}", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun createPhysicalFolder(context: android.content.Context, parentDir: java.io.File, name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val folder = java.io.File(parentDir, name)
                if (!folder.exists()) {
                    folder.mkdirs()
                }
                refreshFilesInCurrentDir()
            } catch (e: Exception) {
                Log.e("IdeViewModel", "Failed to create folder", e)
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Failed to create folder: ${e.localizedMessage}", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun renamePhysicalItem(context: android.content.Context, item: java.io.File, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val dest = java.io.File(item.parentFile, newName)
                if (item.renameTo(dest)) {
                    val existing = repository.getFilesList().find { it.externalUri == item.absolutePath }
                    if (existing != null) {
                        val updated = existing.copy(
                            name = newName,
                            externalUri = dest.absolutePath,
                            language = com.example.ui.files.ExternalFileHelper.detectLanguage(newName)
                        )
                        repository.updateFile(updated)
                    }
                    refreshFilesInCurrentDir()
                }
            } catch (e: Exception) {
                Log.e("IdeViewModel", "Failed to rename physical item", e)
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Failed to rename: ${e.localizedMessage}", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun deletePhysicalItem(context: android.content.Context, item: java.io.File) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (item.isDirectory) {
                    item.deleteRecursively()
                } else {
                    item.delete()
                    val existing = repository.getFilesList().find { it.externalUri == item.absolutePath }
                    if (existing != null) {
                        repository.deleteFile(existing)
                    }
                }
                refreshFilesInCurrentDir()
            } catch (e: Exception) {
                Log.e("IdeViewModel", "Failed to delete physical item", e)
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Failed to delete: ${e.localizedMessage}", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun saveActiveFileAs(context: android.content.Context, parentDir: java.io.File, name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = java.io.File(parentDir, name)
                file.writeText(editorCode)
                refreshFilesInCurrentDir()
                withContext(Dispatchers.Main) {
                    openPhysicalFile(context, file)
                    android.widget.Toast.makeText(context, "Saved file as $name successfully", android.widget.Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("IdeViewModel", "Failed to save file as", e)
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Save As failed: ${e.localizedMessage}", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    init {
        // Initialize default terminal
        terminals.add(TerminalInstance(1, "1: bash (main)"))

        viewModelScope.launch {
            // Load Settings
            isDarkMode = repository.getSetting("isDarkMode", "false").toBoolean()
            isJsExecutionEnabled = repository.getSetting("isJsExecutionEnabled", "true").toBoolean()
            isAutocompleteEnabled = repository.getSetting("isAutocompleteEnabled", "true").toBoolean()
            isAutoTerminateEnabled = repository.getSetting("isAutoTerminateEnabled", "true").toBoolean()
            isAutosaveEnabled = repository.getSetting("isAutosaveEnabled", "true").toBoolean()
            isBrowserGridView = repository.getSetting("isBrowserGridView", "false").toBoolean()
            fileBrowserSortBy = repository.getSetting("fileBrowserSortBy", "name_asc")

            val extUri = repository.getSetting("externalWorkspaceUri", "")
            if (extUri.isNotEmpty()) {
                externalWorkspaceUri.value = extUri
                externalWorkspaceName.value = repository.getSetting("externalWorkspaceName", "")
                try {
                    refreshExternalFiles(application)
                } catch (e: Exception) {
                    Log.w("IdeViewModel", "Could not restore external files list on init", e)
                }
            }

            val savedFavorites = repository.getSetting("favoriteFolders", "")
            if (savedFavorites.isNotEmpty()) {
                favoriteFolders.clear()
                favoriteFolders.addAll(savedFavorites.split(","))
            }

            val savedRecents = repository.getSetting("recentlyOpenedIds", "")
            if (savedRecents.isNotEmpty()) {
                recentlyOpenedIds.clear()
                recentlyOpenedIds.addAll(savedRecents.split(",").mapNotNull { it.toIntOrNull() })
            }

            val voiceSaved = repository.getSetting("isVoiceSupportEnabled", "false").toBoolean()
            isVoiceSupportEnabled = voiceSaved
            voiceAssistant.isEnabled = voiceSaved

            // Synchronize physical filesystem with Room database
            try {
                com.example.ui.files.LocalFileSystemManager.syncPhysicalWorkspaceWithDb(application, db)
            } catch (e: Exception) {
                Log.e(TAG, "Failed initial workspace sync", e)
            }

            // Synchronize current file text with editorCode only when active file ID changes
            currentFile.collect { file ->
                if (file != null) {
                    if (loadedFileId != file.id) {
                        loadedFileId = file.id
                        editorCode = file.content
                        addFileToRecentlyOpened(file.id)
                    }
                } else {
                    loadedFileId = null
                    editorCode = ""
                }
            }
        }
    }

    fun toggleAutosave() {
        isAutosaveEnabled = !isAutosaveEnabled
        viewModelScope.launch {
            repository.saveSetting("isAutosaveEnabled", isAutosaveEnabled.toString())
        }
    }

    fun toggleBrowserGridView() {
        isBrowserGridView = !isBrowserGridView
        viewModelScope.launch {
            repository.saveSetting("isBrowserGridView", isBrowserGridView.toString())
        }
    }

    fun changeFileBrowserSortBy(sortBy: String) {
        fileBrowserSortBy = sortBy
        viewModelScope.launch {
            repository.saveSetting("fileBrowserSortBy", sortBy)
        }
    }

    // --- File Operations ---

    fun saveCurrentFileImmediately() {
        val file = currentFile.value ?: return
        saveJob?.cancel()
        saveScope.launch {
            try {
                val path = file.externalUri ?: java.io.File(com.example.ui.files.LocalFileSystemManager.getWorkspaceRoot(getApplication()), file.name).absolutePath
                
                if (path.startsWith("/")) {
                    try {
                        val physicalFile = java.io.File(path)
                        physicalFile.writeText(editorCode)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed physical write to $path", e)
                    }
                } else if (path.startsWith("content://")) {
                    try {
                        val uri = android.net.Uri.parse(path)
                        com.example.ui.files.ExternalFileHelper.writeTextToUri(getApplication(), uri, editorCode)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed SAF write to $path", e)
                    }
                } else {
                    com.example.ui.files.LocalFileSystemManager.saveFile(
                        getApplication(),
                        file.name,
                        file.folder,
                        editorCode
                    )
                }

                repository.updateFile(file.copy(content = editorCode, lastModified = System.currentTimeMillis()))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save file in saveScope context", e)
            }
        }
    }

    fun openExternalFile(context: android.content.Context, uri: android.net.Uri) {
        viewModelScope.launch {
            saveCurrentFileImmediately() // save any pending edits first
            val content = com.example.ui.files.ExternalFileHelper.readTextFromUri(context, uri)
            if (content == null) {
                addLog("error", "Failed to read file contents from system explorer.")
                return@launch
            }
            
            val name = com.example.ui.files.ExternalFileHelper.getFileNameFromUri(context, uri)
            val language = com.example.ui.files.ExternalFileHelper.detectLanguage(name)

            // Persist URI permissions across reboots
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed to persist URI permission", e)
            }

            // Check if we already have this file in db
            val existingFiles = repository.getFilesList()
            val alreadyImported = existingFiles.find { it.externalUri == uri.toString() }

            if (alreadyImported != null) {
                // Just switch to it and update the content if it changed
                val updatedFile = alreadyImported.copy(content = content, lastModified = System.currentTimeMillis())
                repository.updateFile(updatedFile)
                loadedFileId = null // Force sync to reload file content
                repository.selectFile(alreadyImported.id)
                addLog("info", "✓ Opened existing device file: $name")
            } else {
                // Insert as a new external file
                val extFile = ProgramFile(
                    name = name,
                    content = content,
                    language = language,
                    isCurrent = true,
                    externalUri = uri.toString()
                )
                val newId = repository.insertFile(extFile)
                repository.selectFile(newId)
                addLog("info", "✓ Opened and linked new device file: $name")
            }
        }
    }

    fun saveCurrentAsExternal(context: android.content.Context, uri: android.net.Uri) {
        viewModelScope.launch {
            val success = com.example.ui.files.ExternalFileHelper.writeTextToUri(context, uri, editorCode)
            if (!success) {
                addLog("error", "Failed to write content to system files.")
                return@launch
            }

            // Persist URI permissions
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed to persist URI permission", e)
            }

            val name = com.example.ui.files.ExternalFileHelper.getFileNameFromUri(context, uri)
            val language = com.example.ui.files.ExternalFileHelper.detectLanguage(name)

            val current = currentFile.value
            if (current != null) {
                // Update current file to link to external Uri
                val updated = current.copy(
                    name = name,
                    content = editorCode,
                    language = language,
                    externalUri = uri.toString(),
                    lastModified = System.currentTimeMillis()
                )
                repository.updateFile(updated)
                addLog("info", "✓ Linked and saved file as device file: $name")
            } else {
                // Scaffold dynamic file
                val extFile = ProgramFile(
                    name = name,
                    content = editorCode,
                    language = language,
                    isCurrent = true,
                    externalUri = uri.toString()
                )
                val newId = repository.insertFile(extFile)
                repository.selectFile(newId)
                addLog("info", "✓ Created and linked new device file: $name")
            }
        }
    }

    /**
     * Instantly saves the active file directly inside the device's public local Documents
     * folder under "My Files/Documents/CommuteCodeSandbox/", completely bypassing Google Drive.
     */
    fun saveToLocalPublicDocuments(context: android.content.Context) {
        val file = currentFile.value ?: return
        saveCurrentFileImmediately() // ensure DB has latest
        viewModelScope.launch {
            val successUri = com.example.ui.files.ExternalFileHelper.saveFileToPublicDocuments(context, file.name, editorCode)
            if (successUri != null) {
                addLog("info", "✓ Saved directly to My Files -> Documents/CommuteCodeSandbox/${file.name}")
                android.widget.Toast.makeText(context, "Saved to My Files -> Documents/CommuteCodeSandbox/${file.name}", android.widget.Toast.LENGTH_LONG).show()
            } else {
                addLog("error", "Failed to save file to local My Files directory.")
                android.widget.Toast.makeText(context, "Failed to save to local Documents directory.", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun createFile(name: String, language: String, folder: String = "") {
        viewModelScope.launch {
            saveCurrentFileImmediately() // Save any pending edits first
            
            val finalName = if (name.contains(".")) name else {
                when (language) {
                    "javascript" -> "$name.js"
                    "html" -> "$name.html"
                    "css" -> "$name.css"
                    else -> name
                }
            }
            
            val defaultContent = when (language) {
                "html" -> "<!DOCTYPE html>\n<html>\n<head>\n    <link rel=\"stylesheet\" href=\"style.css\">\n</head>\n<body>\n    <h2>Hello from Sandbox</h2>\n    <script src=\"script.js\"></script>\n</body>\n</html>"
                "css" -> "body {\n    background-color: #FAFAFA;\n    color: #121212;\n    font-family: sans-serif;\n}"
                else -> "console.log(\"File loaded successfully!\");"
            }
            
            // Create physically on disk
            try {
                com.example.ui.files.LocalFileSystemManager.saveFile(
                    getApplication(),
                    finalName,
                    folder,
                    defaultContent
                )
                // Synchronize with database
                com.example.ui.files.LocalFileSystemManager.syncPhysicalWorkspaceWithDb(getApplication(), db)
                
                // Select newly created file from synced DB
                val allFiles = repository.getFilesList()
                val createdFile = allFiles.find { it.name == finalName && it.folder == folder }
                if (createdFile != null) {
                    repository.selectFile(createdFile.id)
                }
                addLog("info", "Created file $finalName" + (if (folder.isNotEmpty()) " in folder $folder" else ""))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create physical file", e)
                addLog("error", "Failed to create physical file: ${e.message}")
            }
        }
    }

    fun createFileWithContent(name: String, language: String, folder: String = "", content: String) {
        viewModelScope.launch {
            saveCurrentFileImmediately() // Save any pending edits first
            
            val finalName = if (name.contains(".")) name else {
                when (language) {
                    "javascript" -> "$name.js"
                    "html" -> "$name.html"
                    "css" -> "$name.css"
                    else -> name
                }
            }
            
            try {
                com.example.ui.files.LocalFileSystemManager.saveFile(
                    getApplication(),
                    finalName,
                    folder,
                    content
                )
                // Synchronize with database
                com.example.ui.files.LocalFileSystemManager.syncPhysicalWorkspaceWithDb(getApplication(), db)
                
                // Select newly created file from synced DB
                val allFiles = repository.getFilesList()
                val createdFile = allFiles.find { it.name == finalName && it.folder == folder }
                if (createdFile != null) {
                    repository.selectFile(createdFile.id)
                }
                addLog("info", "✓ Created tutorial file $finalName" + (if (folder.isNotEmpty()) " in folder $folder" else ""))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create tutorial file", e)
                addLog("error", "Failed to create tutorial file: ${e.message}")
            }
        }
    }

    fun selectFile(fileId: Int) {
        saveCurrentFileImmediately() // Save current file before switching
        viewModelScope.launch {
            repository.selectFile(fileId)
            // Reset runner
            runCodeEvent.value = null
            isCodeRunning = false
        }
    }

    fun updateEditorCode(newCode: String) {
        editorCode = newCode
        val file = currentFile.value ?: return
        
        if (isAutosaveEnabled) {
            // Cancel the previous save job
            saveJob?.cancel()
            // Delay disk write by 800ms of inactivity to completely eliminate typing lag
            saveJob = viewModelScope.launch {
                kotlinx.coroutines.delay(800)
                
                // Write physically if not external
                if (file.externalUri == null) {
                    com.example.ui.files.LocalFileSystemManager.saveFile(
                        getApplication(),
                        file.name,
                        file.folder,
                        newCode
                    )
                }

                repository.updateFile(file.copy(content = newCode, lastModified = System.currentTimeMillis()))
                
                // Also autosave back to external system file URI if linked
                file.externalUri?.let { uriStr ->
                    try {
                        val uri = android.net.Uri.parse(uriStr)
                        com.example.ui.files.ExternalFileHelper.writeTextToUri(getApplication(), uri, newCode)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to autosave to external Uri: $uriStr", e)
                    }
                }
            }
        }
    }

    fun deleteFile(file: ProgramFile) {
        viewModelScope.launch {
            if (file.externalUri == null) {
                com.example.ui.files.LocalFileSystemManager.deleteFile(
                    getApplication(),
                    file.name,
                    file.folder
                )
            }
            repository.deleteFile(file)
            // Resync to clean up any references and ensure proper current file selection
            com.example.ui.files.LocalFileSystemManager.syncPhysicalWorkspaceWithDb(getApplication(), db)
            addLog("warn", "Deleted file ${file.name}")
        }
    }

    // --- Code Execution ---

    fun autoTerminateCode() {
        viewModelScope.launch {
            if (isCodeRunning) {
                isCodeRunning = false
                runCodeEvent.value = null
                addLog("warn", "⏹️ Execution automatically completed & program terminated.")
                voiceAssistant.speak("Program terminated successfully")
            }
        }
    }

    fun runCode() {
        if (!isJsExecutionEnabled) {
            addLog("error", "Execution aborted: JavaScript execution is toggled OFF in settings.")
            voiceAssistant.speak("Execution aborted. JavaScript execution is off.")
            return
        }

        val file = currentFile.value ?: return
        saveCurrentFileImmediately() // Ensure latest changes are saved before bundling
        addLog("info", "▶️ Starting execution of ${file.name}...")
        voiceAssistant.speak("Running code")
        isCodeRunning = true

        // Prepare the payload to run
        viewModelScope.launch {
            val codeToRun = compileCodeBundle(file)
            runCodeEvent.value = codeToRun
            // Switch tab to browser window for HTML or dedicated terminal for scripts
            if (file.language == "html") {
                selectedTab = 1 // Browser Preview
            } else {
                selectedTab = 2 // Dedicated Terminal Page
            }
        }
    }

    fun stopCode() {
        isCodeRunning = false
        runCodeEvent.value = null
        addLog("warn", "⏹️ Execution halted by user.")
        voiceAssistant.speak("Execution stopped")
    }

    // Console logs interface helper
    fun addLog(type: String, message: String) {
        viewModelScope.launch {
            repository.insertLog(type, message)
            
            // Append to currently active terminal logs in-memory
            val index = terminals.indexOfFirst { it.id == activeTerminalId }
            if (index != -1) {
                val currentTerminal = terminals[index]
                val updatedLogs = currentTerminal.logs + ConsoleLog(
                    type = type,
                    message = message,
                    timestamp = System.currentTimeMillis()
                )
                terminals[index] = currentTerminal.copy(logs = updatedLogs)
            } else if (terminals.isNotEmpty()) {
                val currentTerminal = terminals.first()
                val updatedLogs = currentTerminal.logs + ConsoleLog(
                    type = type,
                    message = message,
                    timestamp = System.currentTimeMillis()
                )
                terminals[0] = currentTerminal.copy(logs = updatedLogs)
            }
            
            // Speak errors if voice support is enabled
            if (type == "error" && isVoiceSupportEnabled) {
                voiceAssistant.speak("Error detected: $message")
            }
        }
    }

    fun clearLogs() {
        viewModelScope.launch {
            repository.clearLogs()
            clearTerminalLogs(activeTerminalId)
        }
    }

    // Helper to package scripts, injected import maps, and consoles
    private suspend fun compileCodeBundle(file: ProgramFile): String {
        // Collect active npm packages to construct import maps
        val activeLibraries = db.npmLibraryDao().getAllLibrariesFlow().first()
        val importsMapJson = JSONObject().apply {
            val importsObj = JSONObject()
            for (lib in activeLibraries) {
                // If we downloaded it, we can resolve it via interception, or use CDN URL
                importsObj.put(lib.name, lib.url)
            }
            put("imports", importsObj)
        }.toString()

        val loggerScript = """
            <script>
            (function() {
                const send = (type, args) => {
                    const msg = args.map(arg => {
                        if (arg === null) return 'null';
                        if (arg === undefined) return 'undefined';
                        if (typeof arg === 'object') {
                            try { return JSON.stringify(arg); } catch(e) { return String(arg); }
                        }
                        return String(arg);
                    }).join(' ');
                    if (window.AndroidTerminal) {
                        window.AndroidTerminal.log(type, msg);
                    }
                };

                console.log = function(...args) { send('log', args); };
                console.error = function(...args) { send('error', args); };
                console.warn = function(...args) { send('warn', args); };
                console.info = function(...args) { send('info', args); };

                window.onerror = function(message, source, lineno, colno, error) {
                    const errStr = message + " at line " + lineno + ":" + colno;
                    if (window.AndroidTerminal) {
                        window.AndroidTerminal.log('error', errStr);
                    }
                    return false;
                };

                window.addEventListener('unhandledrejection', function(event) {
                    if (window.AndroidTerminal) {
                        window.AndroidTerminal.log('error', 'Unhandled Promise: ' + event.reason);
                    }
                });
            })();
            </script>
        """.trimIndent()

        val htmlAutoTerminateScript = """
            <script>
            window.addEventListener('load', function() {
                // Terminate after 3 seconds of load to let page render and execute initial scripts
                setTimeout(function() {
                    if (window.AndroidTerminal) {
                        window.AndroidTerminal.log('info', '⏹️ Page render complete. Program auto-terminated.');
                        window.AndroidTerminal.autoTerminate();
                    }
                }, 3000);
            });
            </script>
        """.trimIndent()

        return if (file.language == "html") {
            var rawHtml = file.content
            
            // Inject Logger at the very top of <head> or <html>
            val headIndex = rawHtml.indexOf("<head>")
            if (headIndex != -1) {
                rawHtml = rawHtml.substring(0, headIndex + 6) + "\n" + loggerScript + rawHtml.substring(headIndex + 6)
            } else {
                val htmlIndex = rawHtml.indexOf("<html>")
                if (htmlIndex != -1) {
                    rawHtml = rawHtml.substring(0, htmlIndex + 6) + "\n" + loggerScript + rawHtml.substring(htmlIndex + 6)
                } else {
                    rawHtml = loggerScript + "\n" + rawHtml
                }
            }

            // Inject Import Maps for dependencies
            val importMapScript = "<script type=\"importmap\">\n$importsMapJson\n</script>"
            val titleIndex = rawHtml.indexOf("<title>")
            if (titleIndex != -1) {
                rawHtml = rawHtml.substring(0, titleIndex) + importMapScript + "\n" + rawHtml.substring(titleIndex)
            } else {
                val headIndexNew = rawHtml.indexOf("<head>")
                if (headIndexNew != -1) {
                    rawHtml = rawHtml.substring(0, headIndexNew + 6) + "\n" + importMapScript + rawHtml.substring(headIndexNew + 6)
                }
            }

            // Inject auto-termination script
            if (isAutoTerminateEnabled) {
                if (rawHtml.contains("</body>")) {
                    rawHtml = rawHtml.replace("</body>", "$htmlAutoTerminateScript\n</body>")
                } else {
                    rawHtml = rawHtml + "\n" + htmlAutoTerminateScript
                }
            }
            rawHtml
        } else {
            // It's a plain JS script, bundle it into an HTML template
            """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="utf-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>JS Runtime</title>
                    $loggerScript
                    <script type="importmap">
                    $importsMapJson
                    </script>
                </head>
                <body>
                    <div style="padding: 20px; font-family: sans-serif; color: #888;">
                        <h3>JavaScript Output Sandbox</h3>
                        <p>Code is running in background. Outputs and errors are redirected to the Console Terminal below.</p>
                    </div>
                    <script type="module">
                    try {
                        $editorCode
                    } catch(e) {
                        console.error(e.message + " " + e.stack);
                    } finally {
                        // Wait 1.2s to capture asynchronous logs, then auto-terminate if enabled
                        if ($isAutoTerminateEnabled) {
                            setTimeout(() => {
                                if (window.AndroidTerminal) {
                                    window.AndroidTerminal.log('info', '⏹️ JavaScript execution complete. Program auto-terminated.');
                                    window.AndroidTerminal.autoTerminate();
                                }
                            }, 1200);
                        }
                    }
                    </script>
                </body>
                </html>
            """.trimIndent()
        }
    }

    // --- NPM Library Downloading (Offline cache) ---

    fun downloadLibrary(lib: NpmLibrary) {
        downloadingLibraryName = lib.name
        libraryDownloadError = null
        
        viewModelScope.launch(Dispatchers.IO) {
            val client = OkHttpClient()
            val request = Request.Builder()
                .url(lib.url)
                .build()
            
            try {
                addLog("info", "Downloading ${lib.name} for offline use from ${lib.url}...")
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("HTTP Unexpected code $response")
                    }
                    val bodyString = response.body?.string() ?: ""
                    if (bodyString.isEmpty()) {
                        throw IOException("Received empty response body.")
                    }

                    // Save downloaded javascript directly in DB
                    val updatedLib = lib.copy(
                        isDownloaded = true,
                        localContent = bodyString
                    )
                    db.npmLibraryDao().insertLibrary(updatedLib)
                    
                    withContext(Dispatchers.Main) {
                        downloadingLibraryName = null
                        addLog("info", "✓ Successfully cached ${lib.name} locally! Now works 100% offline.")
                        voiceAssistant.speak("${lib.name} downloaded successfully")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to download library ${lib.name}", e)
                withContext(Dispatchers.Main) {
                    downloadingLibraryName = null
                    libraryDownloadError = e.message
                    addLog("error", "Failed to cache library ${lib.name}: ${e.message}")
                }
            }
        }
    }

    fun removeCachedLibrary(lib: NpmLibrary) {
        viewModelScope.launch {
            val updated = lib.copy(isDownloaded = false, localContent = null)
            db.npmLibraryDao().insertLibrary(updated)
            addLog("warn", "Removed cached package ${lib.name}")
        }
    }

    fun addLibrary(name: String, url: String) {
        viewModelScope.launch {
            db.npmLibraryDao().insertLibrary(NpmLibrary(name = name, url = url))
            addLog("info", "✓ Added library reference: $name")
        }
    }

    // --- Settings togglers ---

    fun toggleDarkMode() {
        isDarkMode = !isDarkMode
        viewModelScope.launch {
            repository.saveSetting("isDarkMode", isDarkMode.toString())
        }
    }

    fun toggleJsExecution() {
        isJsExecutionEnabled = !isJsExecutionEnabled
        viewModelScope.launch {
            repository.saveSetting("isJsExecutionEnabled", isJsExecutionEnabled.toString())
        }
    }

    fun toggleAutocomplete() {
        isAutocompleteEnabled = !isAutocompleteEnabled
        viewModelScope.launch {
            repository.saveSetting("isAutocompleteEnabled", isAutocompleteEnabled.toString())
        }
    }

    // --- File Ops: Save As & Delete Folder ---

    fun saveFileAs(newName: String, folder: String) {
        val current = currentFile.value ?: return
        viewModelScope.launch {
            val cleanName = newName.trim()
            val finalName = if (cleanName.contains(".")) {
                cleanName
            } else {
                when (current.language) {
                    "javascript" -> "$cleanName.js"
                    "html" -> "$cleanName.html"
                    "css" -> "$cleanName.css"
                    else -> cleanName
                }
            }
            
            // Save physically first
            com.example.ui.files.LocalFileSystemManager.saveFile(
                getApplication(),
                finalName,
                folder,
                editorCode
            )
            
            // Synchronize database
            com.example.ui.files.LocalFileSystemManager.syncPhysicalWorkspaceWithDb(getApplication(), db)
            
            // Select cloned file
            val allFiles = repository.getFilesList()
            val createdFile = allFiles.find { it.name == finalName && it.folder == folder }
            if (createdFile != null) {
                repository.selectFile(createdFile.id)
            }
            addLog("info", "Cloned ${current.name} to $finalName via Save As")
        }
    }

    fun deleteFolder(folderName: String) {
        viewModelScope.launch {
            com.example.ui.files.LocalFileSystemManager.deleteFolder(getApplication(), folderName)
            com.example.ui.files.LocalFileSystemManager.syncPhysicalWorkspaceWithDb(getApplication(), db)
            addLog("warn", "Deleted folder $folderName and all its files")
        }
    }

    // --- Multiple terminals helper functions ---

    fun createTerminal() {
        val nextId = if (terminals.isEmpty()) 1 else (terminals.maxOf { it.id } + 1)
        val name = "$nextId: bash"
        val newTerm = TerminalInstance(nextId, name)
        terminals.add(newTerm)
        activeTerminalId = nextId
        addLog("info", "Created new terminal instance: $name")
    }

    fun deleteTerminal(terminalId: Int) {
        if (terminals.size <= 1) {
            addLog("warn", "Cannot delete the only active terminal.")
            return
        }
        val index = terminals.indexOfFirst { it.id == terminalId }
        if (index != -1) {
            val removed = terminals.removeAt(index)
            if (activeTerminalId == terminalId) {
                activeTerminalId = terminals.first().id
            }
            addLog("warn", "Deleted terminal: ${removed.name}")
        }
    }

    fun clearTerminalLogs(terminalId: Int) {
        val index = terminals.indexOfFirst { it.id == terminalId }
        if (index != -1) {
            terminals[index] = terminals[index].copy(logs = emptyList())
        }
    }

    // --- Open in External Browser ---

    fun openInExternalBrowser(context: android.content.Context) {
        val file = currentFile.value ?: return
        viewModelScope.launch {
            val codeToRun = compileCodeBundle(file)
            try {
                val cacheDir = context.cacheDir
                val tempFile = java.io.File(cacheDir, "run_preview.html")
                tempFile.writeText(codeToRun)
                
                val authority = "${context.packageName}.fileprovider"
                val fileUri = androidx.core.content.FileProvider.getUriForFile(context, authority, tempFile)
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                    setDataAndType(fileUri, "text/html")
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(intent)
                addLog("info", "Opened preview in external system browser")
            } catch (e: Exception) {
                addLog("error", "Failed to open external browser: ${e.message}")
            }
        }
    }

    // --- Online Library Search ---

    fun searchLibrariesOnline(query: String) {
        if (query.isBlank()) return
        searchLibraryQuery = query
        isLibrarySearching = true
        librarySearchError = null
        searchLibraryResults.clear()
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val url = java.net.URL("https://registry.npmjs.org/-/v1/search?text=${java.net.URLEncoder.encode(query, "UTF-8")}&size=15")
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                
                if (connection.responseCode == 200) {
                    val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = org.json.JSONObject(responseText)
                    val objects = json.getJSONArray("objects")
                    
                    val resultsList = mutableListOf<NpmSearchResult>()
                    for (i in 0 until objects.length()) {
                        val obj = objects.getJSONObject(i)
                        val pkg = obj.getJSONObject("package")
                        val name = pkg.getString("name")
                        val description = pkg.optString("description", "")
                        val version = pkg.optString("version", "")
                        resultsList.add(NpmSearchResult(name, description, version))
                    }
                    
                    withContext(Dispatchers.Main) {
                        searchLibraryResults.addAll(resultsList)
                        isLibrarySearching = false
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        librarySearchError = "Server returned code ${connection.responseCode}"
                        isLibrarySearching = false
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    librarySearchError = "Network error: ${e.message}"
                    isLibrarySearching = false
                }
            }
        }
    }

    // --- Advanced File Saving & Management Operations ---

    fun renameFile(file: ProgramFile, newName: String) {
        viewModelScope.launch {
            val cleanName = newName.trim()
            if (cleanName.isEmpty()) return@launch
            
            if (file.externalUri == null) {
                com.example.ui.files.LocalFileSystemManager.renameFile(
                    getApplication(),
                    file.name,
                    file.folder,
                    cleanName
                )
            }
            
            com.example.ui.files.LocalFileSystemManager.syncPhysicalWorkspaceWithDb(getApplication(), db)
            addLog("info", "✓ Renamed file: ${file.name} -> $cleanName")
            
            // If this is the current file, refresh loaded file ID
            if (currentFile.value?.id == file.id) {
                loadedFileId = null // Force reload/sync
            }
        }
    }

    fun duplicateFile(file: ProgramFile, duplicateName: String) {
        viewModelScope.launch {
            val cleanName = duplicateName.trim()
            if (cleanName.isEmpty()) return@launch
            
            if (file.externalUri == null) {
                com.example.ui.files.LocalFileSystemManager.duplicateFile(
                    getApplication(),
                    file.name,
                    file.folder,
                    cleanName
                )
            }
            
            com.example.ui.files.LocalFileSystemManager.syncPhysicalWorkspaceWithDb(getApplication(), db)
            addLog("info", "✓ Duplicated file: ${file.name} -> $cleanName")
        }
    }

    fun moveFile(file: ProgramFile, destFolder: String) {
        viewModelScope.launch {
            val targetFolderNormalized = destFolder.trim().replace("//", "/").trim('/')
            if (file.externalUri == null) {
                com.example.ui.files.LocalFileSystemManager.moveFile(
                    getApplication(),
                    file.name,
                    file.folder,
                    targetFolderNormalized
                )
            }
            
            com.example.ui.files.LocalFileSystemManager.syncPhysicalWorkspaceWithDb(getApplication(), db)
            addLog("info", "✓ Moved file: ${file.name} to folder ${if (destFolder.isEmpty()) "Root" else destFolder}")
            
            if (currentFile.value?.id == file.id) {
                loadedFileId = null // Force sync
            }
        }
    }

    fun copyFile(file: ProgramFile, destFolder: String) {
        viewModelScope.launch {
            val targetFolderNormalized = destFolder.trim().replace("//", "/").trim('/')
            if (file.externalUri == null) {
                com.example.ui.files.LocalFileSystemManager.copyFile(
                    getApplication(),
                    file.name,
                    file.folder,
                    targetFolderNormalized
                )
            }
            
            com.example.ui.files.LocalFileSystemManager.syncPhysicalWorkspaceWithDb(getApplication(), db)
            addLog("info", "✓ Copied file: ${file.name} into folder ${if (destFolder.isEmpty()) "Root" else destFolder}")
        }
    }

    fun createNewFolder(folderPath: String) {
        viewModelScope.launch {
            val cleanFolder = folderPath.trim().replace("//", "/").trim('/')
            if (cleanFolder.isEmpty()) return@launch
            
            com.example.ui.files.LocalFileSystemManager.createFolder(getApplication(), cleanFolder)
            com.example.ui.files.LocalFileSystemManager.saveFile(
                getApplication(),
                "index.html",
                cleanFolder,
                "<!DOCTYPE html>\n<html>\n<body>\n    <h2>My Folder project: $cleanFolder</h2>\n</body>\n</html>"
            )
            
            com.example.ui.files.LocalFileSystemManager.syncPhysicalWorkspaceWithDb(getApplication(), db)
            addLog("info", "✓ Created new folder: $cleanFolder (Scaffolded with index.html)")
        }
    }

    fun addFileToRecentlyOpened(fileId: Int) {
        viewModelScope.launch {
            recentlyOpenedIds.remove(fileId)
            recentlyOpenedIds.add(0, fileId)
            if (recentlyOpenedIds.size > 10) {
                recentlyOpenedIds.removeAt(recentlyOpenedIds.lastIndex)
            }
            repository.saveSetting("recentlyOpenedIds", recentlyOpenedIds.joinToString(","))
        }
    }

    fun toggleFavoriteFolder(folderName: String) {
        viewModelScope.launch {
            if (favoriteFolders.contains(folderName)) {
                favoriteFolders.remove(folderName)
            } else {
                favoriteFolders.add(folderName)
            }
            repository.saveSetting("favoriteFolders", favoriteFolders.joinToString(","))
        }
    }

    // --- Physical Device Storage File Manager Operations ---

    private fun getDocumentFileForCurrentPath(context: android.content.Context): androidx.documentfile.provider.DocumentFile? {
        val uriStr = externalWorkspaceUri.value ?: return null
        val rootUri = android.net.Uri.parse(uriStr)
        val rootDoc = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, rootUri) ?: return null
        
        val path = currentExternalPath.value
        if (path.isEmpty()) return rootDoc
        
        var currentDoc = rootDoc
        val parts = path.split("/")
        for (part in parts) {
            if (part.isEmpty()) continue
            val found = currentDoc.findFile(part)
            if (found != null && found.isDirectory) {
                currentDoc = found
            } else {
                return null
            }
        }
        return currentDoc
    }

    fun refreshExternalFiles(context: android.content.Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val targetFolder = getDocumentFileForCurrentPath(context)
                if (targetFolder == null) {
                    withContext(Dispatchers.Main) {
                        externalFilesList.clear()
                        addLog("error", "Could not access storage folder at path: ${currentExternalPath.value}")
                    }
                    return@launch
                }
                
                val list = targetFolder.listFiles()
                val items = list.map { doc ->
                    ExternalStorageItem(
                        uriString = doc.uri.toString(),
                        name = doc.name ?: "Untitled",
                        isDirectory = doc.isDirectory,
                        size = doc.length(),
                        lastModified = doc.lastModified(),
                        mimeType = doc.type,
                        absolutePath = if (currentExternalPath.value.isEmpty()) (doc.name ?: "") else "${currentExternalPath.value}/${doc.name ?: ""}"
                    )
                }.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                
                withContext(Dispatchers.Main) {
                    externalFilesList.clear()
                    externalFilesList.addAll(items)
                }
            } catch (e: Exception) {
                Log.e("IdeViewModel", "Error refreshing external files", e)
                withContext(Dispatchers.Main) {
                    addLog("error", "Failed to scan device storage: ${e.message}")
                }
            }
        }
    }

    fun setExternalWorkspace(context: android.content.Context, uri: android.net.Uri) {
        viewModelScope.launch {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed to persist tree URI permission", e)
            }

            val docFile = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, uri)
            val name = docFile?.name ?: "Device Storage"

            externalWorkspaceUri.value = uri.toString()
            externalWorkspaceName.value = name
            currentExternalPath.value = ""

            repository.saveSetting("externalWorkspaceUri", uri.toString())
            repository.saveSetting("externalWorkspaceName", name)

            addLog("info", "✓ Connected storage workspace: $name")
            refreshExternalFiles(context)
        }
    }

    fun disconnectExternalWorkspace() {
        viewModelScope.launch {
            externalWorkspaceUri.value = null
            externalWorkspaceName.value = null
            externalFilesList.clear()
            currentExternalPath.value = ""

            repository.saveSetting("externalWorkspaceUri", "")
            repository.saveSetting("externalWorkspaceName", "")
            addLog("warn", "Disconnected storage workspace.")
        }
    }

    fun createExternalFile(context: android.content.Context, name: String, extension: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val targetFolder = getDocumentFileForCurrentPath(context)
            if (targetFolder == null) {
                withContext(Dispatchers.Main) { addLog("error", "Cannot create file: Current folder unavailable.") }
                return@launch
            }
            
            val fileName = if (name.endsWith(".$extension")) name else "$name.$extension"
            val mimeType = when (extension) {
                "html" -> "text/html"
                "css" -> "text/css"
                else -> "text/javascript"
            }
            
            val newFile = targetFolder.createFile(mimeType, fileName)
            if (newFile != null) {
                val initialContent = when (extension) {
                    "html" -> "<!DOCTYPE html>\n<html>\n<body>\n    <h2>Created in Device Storage</h2>\n</body>\n</html>"
                    "css" -> "body {\n    background-color: #121212;\n    color: #ffffff;\n}"
                    else -> "console.log(\"CodeX Editor physical file created!\");"
                }
                com.example.ui.files.ExternalFileHelper.writeTextToUri(context, newFile.uri, initialContent)
                withContext(Dispatchers.Main) {
                    addLog("info", "✓ Created physical file: $fileName")
                    refreshExternalFiles(context)
                }
            } else {
                withContext(Dispatchers.Main) {
                    addLog("error", "Failed to create physical file $fileName on storage.")
                }
            }
        }
    }

    fun createExternalFolder(context: android.content.Context, name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val targetFolder = getDocumentFileForCurrentPath(context)
            if (targetFolder == null) {
                withContext(Dispatchers.Main) { addLog("error", "Cannot create folder: Current path unavailable.") }
                return@launch
            }
            
            val newDir = targetFolder.createDirectory(name)
            if (newDir != null) {
                withContext(Dispatchers.Main) {
                    addLog("info", "✓ Created directory: $name")
                    refreshExternalFiles(context)
                }
            } else {
                withContext(Dispatchers.Main) {
                    addLog("error", "Failed to create directory $name on storage.")
                }
            }
        }
    }

    fun deleteExternalItem(context: android.content.Context, item: ExternalStorageItem) {
        viewModelScope.launch(Dispatchers.IO) {
            val uri = android.net.Uri.parse(item.uriString)
            val docFile = if (item.isDirectory) {
                androidx.documentfile.provider.DocumentFile.fromTreeUri(context, uri)
            } else {
                androidx.documentfile.provider.DocumentFile.fromSingleUri(context, uri)
            }
            
            if (docFile != null && docFile.exists()) {
                val deleted = docFile.delete()
                if (deleted) {
                    withContext(Dispatchers.Main) {
                        addLog("warn", "Deleted storage item: ${item.name}")
                        refreshExternalFiles(context)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        addLog("error", "Failed to delete storage item ${item.name}")
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    addLog("error", "Storage item ${item.name} not found.")
                }
            }
        }
    }

    fun renameExternalItem(context: android.content.Context, item: ExternalStorageItem, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val uri = android.net.Uri.parse(item.uriString)
            val docFile = if (item.isDirectory) {
                androidx.documentfile.provider.DocumentFile.fromTreeUri(context, uri)
            } else {
                androidx.documentfile.provider.DocumentFile.fromSingleUri(context, uri)
            }
            
            if (docFile != null && docFile.exists()) {
                val renamed = docFile.renameTo(newName)
                if (renamed) {
                    withContext(Dispatchers.Main) {
                        addLog("info", "✓ Renamed storage item: ${item.name} to $newName")
                        refreshExternalFiles(context)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        addLog("error", "Failed to rename ${item.name}")
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    addLog("error", "Storage item ${item.name} not found.")
                }
            }
        }
    }

    fun openExternalStorageFile(context: android.content.Context, item: ExternalStorageItem) {
        val uri = android.net.Uri.parse(item.uriString)
        openExternalFile(context, uri)
    }

    override fun onCleared() {
        super.onCleared()
        voiceAssistant.shutdown()
    }
}

data class TerminalInstance(
    val id: Int,
    val name: String,
    val logs: List<ConsoleLog> = emptyList()
)

data class NpmSearchResult(
    val name: String,
    val description: String,
    val version: String
)

data class ExternalStorageItem(
    val uriString: String,
    val name: String,
    val isDirectory: Boolean,
    val size: Long = 0,
    val lastModified: Long = 0,
    val mimeType: String? = null,
    val absolutePath: String = ""
)
