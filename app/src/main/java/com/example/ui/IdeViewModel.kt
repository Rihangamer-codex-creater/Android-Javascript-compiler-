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

    init {
        // Initialize default terminal
        terminals.add(TerminalInstance(1, "1: bash (main)"))

        viewModelScope.launch {
            // Load Settings
            isDarkMode = repository.getSetting("isDarkMode", "false").toBoolean()
            isJsExecutionEnabled = repository.getSetting("isJsExecutionEnabled", "true").toBoolean()
            isAutocompleteEnabled = repository.getSetting("isAutocompleteEnabled", "true").toBoolean()
            isAutoTerminateEnabled = repository.getSetting("isAutoTerminateEnabled", "true").toBoolean()
            val voiceSaved = repository.getSetting("isVoiceSupportEnabled", "false").toBoolean()
            isVoiceSupportEnabled = voiceSaved
            voiceAssistant.isEnabled = voiceSaved

            // Prepopulate default samples & npm references
            repository.prepopulateIfNeeded()

            // Synchronize current file text with editorCode
            currentFile.collect { file ->
                if (file != null && editorCode != file.content) {
                    editorCode = file.content
                }
            }
        }
    }

    // --- File Operations ---

    fun saveCurrentFileImmediately() {
        val file = currentFile.value ?: return
        saveJob?.cancel()
        viewModelScope.launch {
            repository.updateFile(file.copy(content = editorCode, lastModified = System.currentTimeMillis()))
            
            // Also write back to external system file URI if linked
            file.externalUri?.let { uriStr ->
                try {
                    val uri = android.net.Uri.parse(uriStr)
                    com.example.ui.files.ExternalFileHelper.writeTextToUri(getApplication(), uri, editorCode)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to auto-save to external Uri: $uriStr", e)
                }
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
            
            val newFile = ProgramFile(
                name = finalName,
                content = defaultContent,
                language = language,
                isCurrent = true,
                folder = folder
            )
            val fileId = repository.insertFile(newFile)
            repository.selectFile(fileId)
            addLog("info", "Created file $finalName" + (if (folder.isNotEmpty()) " in folder $folder" else ""))
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
        
        // Cancel the previous save job
        saveJob?.cancel()
        // Delay disk write by 800ms of inactivity to completely eliminate typing lag
        saveJob = viewModelScope.launch {
            kotlinx.coroutines.delay(800)
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

    fun deleteFile(file: ProgramFile) {
        viewModelScope.launch {
            repository.deleteFile(file)
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
            
            val clonedFile = ProgramFile(
                name = finalName,
                content = editorCode,
                language = current.language,
                isCurrent = true,
                folder = folder
            )
            val fileId = repository.insertFile(clonedFile)
            repository.selectFile(fileId)
            addLog("info", "Cloned ${current.name} to $finalName via Save As")
        }
    }

    fun deleteFolder(folderName: String) {
        viewModelScope.launch {
            val allFiles = repository.getFilesList()
            allFiles.filter { it.folder == folderName }.forEach { file ->
                repository.deleteFile(file)
            }
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
