package com.example.ui.files

import android.content.Context
import android.util.Log
import com.example.data.AppDatabase
import com.example.data.ProgramFile
import java.io.File

/**
 * Senior Architect file system manager.
 * Implements real physical file and folder persistence on internal storage (context.filesDir/workspace),
 * and synchronizes directory contents recursively with the Room SQLite database to provide
 * ultra-low latency metadata UI updates.
 */
object LocalFileSystemManager {
    private const val TAG = "LocalFileSystemManager"

    /**
     * Gets the workspace root directory.
     */
    fun getWorkspaceRoot(context: Context): File {
        val root = File(context.filesDir, "workspace")
        if (!root.exists()) {
            root.mkdirs()
        }
        return root
    }

    /**
     * Prepopulates physical files if workspace is completely empty.
     */
    fun prepopulatePhysicalWorkspace(context: Context) {
        val root = getWorkspaceRoot(context)
        val files = root.listFiles()
        if (files == null || files.isEmpty()) {
            // Write default index.js
            val indexJs = File(root, "index.js")
            try {
                indexJs.writeText("""// 🚀 Welcome to JavaScript IDE!
// Tap "Run Code" above to execute this script.
// Outputs will show up in the Debug Terminal below.

console.log("=== App Initialization ===");
console.log("Hello, developer! Welcome to coding on the go.");
console.log("Current time: " + new Date().toLocaleString());

// 1. Array Manipulations & Filter
const languages = ["JavaScript", "Kotlin", "Python", "Swift", "C++", "TypeScript"];
const modernWeb = languages.filter(lang => ["JavaScript", "TypeScript"].includes(lang));
console.log("Modern web languages:", modernWeb);

// 2. Class & OOP Demonstration
class Developer {
    constructor(name, favoriteLanguage) {
        this.name = name;
        this.favoriteLanguage = favoriteLanguage;
    }
    
    introduce() {
        return `Hi! I am ${"$"}{this.name}, coding JS in ${"$"}{this.favoriteLanguage}!`;
    }
}

const me = new Developer("Commuter", "JavaScript");
console.log(me.introduce());

// 3. Asynchronous Execution Demo
console.log("Scheduling future job...");
setTimeout(() => {
    console.log("⏰ Timer triggered! Offline execution completed successfully.");
}, 1000);
""")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to prepopulate index.js", e)
            }

            // Write canvas_art.html
            val canvasHtml = File(root, "canvas_art.html")
            try {
                canvasHtml.writeText("""<!DOCTYPE html>
<html>
<head>
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <style>
        body, html {
            margin: 0;
            padding: 0;
            overflow: hidden;
            background-color: #0d1117;
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
            color: #c9d1d9;
        }
        canvas {
            display: block;
            width: 100vw;
            height: 100vh;
        }
        .hud {
            position: absolute;
            top: 20px;
            left: 20px;
            pointer-events: none;
            background: rgba(22, 27, 34, 0.85);
            padding: 15px;
            border-radius: 8px;
            border: 1px solid #30363d;
            max-width: 250px;
        }
        h3 { margin: 0 0 5px 0; font-size: 16px; color: #58a6ff; }
        p { margin: 0; font-size: 12px; color: #8b949e; line-height: 1.4; }
    </style>
</head>
<body>
    <div class="hud">
        <h3>Cosmic Canvas</h3>
        <p>Drag or swipe your screen to generate particle explosions. Works fully offline!</p>
    </div>
    <canvas id="canvas"></canvas>

    <script>
        const canvas = document.getElementById('canvas');
        const ctx = canvas.getContext('2d');

        function resize() {
            canvas.width = window.innerWidth;
            canvas.height = window.innerHeight;
        }
        window.addEventListener('resize', resize);
        resize();

        const particles = [];
        const colors = ['#ff5964', '#35a7ff', '#386150', '#ffe74c', '#ffffff', '#a480cf'];

        class Particle {
            constructor(x, y) {
                this.x = x;
                this.y = y;
                this.size = Math.random() * 6 + 1;
                this.speedX = Math.random() * 6 - 3;
                this.speedY = Math.random() * 6 - 3;
                this.color = colors[Math.floor(Math.random() * colors.length)];
                this.alpha = 1;
                this.decay = Math.random() * 0.02 + 0.005;
            }

            update() {
                this.x += this.speedX;
                this.y += this.speedY;
                this.alpha -= this.decay;
            }

            draw() {
                ctx.save();
                ctx.globalAlpha = this.alpha;
                ctx.fillStyle = this.color;
                ctx.beginPath();
                ctx.arc(this.x, this.y, this.size, 0, Math.PI * 2);
                ctx.fill();
                ctx.restore();
            }
        }

        function createExplosion(x, y) {
            for (let i = 0; i < 15; i++) {
                particles.push(new Particle(x, y));
            }
        }

        window.addEventListener('pointermove', (e) => {
            createExplosion(e.clientX, e.clientY);
        });

        window.addEventListener('pointerdown', (e) => {
            createExplosion(e.clientX, e.clientY);
        });

        function animate() {
            ctx.fillStyle = 'rgba(13, 17, 23, 0.2)';
            ctx.fillRect(0, 0, canvas.width, canvas.height);

            for (let i = particles.length - 1; i >= 0; i--) {
                const p = particles[i];
                p.update();
                p.draw();
                if (p.alpha <= 0) {
                    particles.splice(i, 1);
                }
            }
            requestAnimationFrame(animate);
        }

        setInterval(() => {
            if (particles.length < 50) {
                createExplosion(Math.random() * canvas.width, Math.random() * canvas.height);
            }
        }, 500);

        animate();
        console.log("🎨 Canvas Animation started successfully.");
    </script>
</body>
</html>""")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to prepopulate canvas_art.html", e)
            }
        }
    }

    /**
     * Recursively gathers all physical files in the workspace.
     */
    fun getAllPhysicalFiles(root: File, currentDir: File = root): List<File> {
        val list = mutableListOf<File>()
        val files = currentDir.listFiles() ?: return list
        for (f in files) {
            if (f.isDirectory) {
                list.addAll(getAllPhysicalFiles(root, f))
            } else {
                list.add(f)
            }
        }
        return list
    }

    /**
     * Synchronizes disk contents with Room database.
     * Disk is treated as the absolute source of truth.
     */
    suspend fun syncPhysicalWorkspaceWithDb(context: Context, db: AppDatabase) {
        val root = getWorkspaceRoot(context)
        prepopulatePhysicalWorkspace(context)

        val physicalFiles = getAllPhysicalFiles(root)
        val fileDao = db.programFileDao()
        val dbFiles = fileDao.getAllFiles()

        // Track seen relative paths
        val seenRelativePaths = mutableSetOf<String>()

        for (file in physicalFiles) {
            val relativePath = file.relativeTo(root).path
            seenRelativePaths.add(relativePath)

            val parts = relativePath.split(File.separator)
            val name = parts.last()
            val folder = if (parts.size > 1) {
                parts.subList(0, parts.size - 1).joinToString("/")
            } else {
                ""
            }

            val matchingDbFile = dbFiles.find { it.name == name && it.folder == folder && it.externalUri == null }

            val fileContent = try {
                file.readText()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to read physical file: $relativePath", e)
                ""
            }

            if (matchingDbFile == null) {
                val language = ExternalFileHelper.detectLanguage(name)
                val newDbFile = ProgramFile(
                    name = name,
                    content = fileContent,
                    language = language,
                    folder = folder,
                    lastModified = file.lastModified(),
                    isCurrent = false
                )
                fileDao.insertFile(newDbFile)
            } else {
                // If content differs, sync SQLite to match physical storage
                if (matchingDbFile.content != fileContent) {
                    fileDao.updateFile(matchingDbFile.copy(
                        content = fileContent,
                        lastModified = file.lastModified()
                    ))
                }
            }
        }

        // Clean up DB for deleted files (with no externalUri)
        for (dbFile in dbFiles) {
            if (dbFile.externalUri == null) {
                val relativePath = if (dbFile.folder.isEmpty()) dbFile.name else "${dbFile.folder}/${dbFile.name}"
                val normalizedPath = relativePath.replace("/", File.separator)
                if (!seenRelativePaths.contains(normalizedPath)) {
                    fileDao.deleteFile(dbFile)
                }
            }
        }

        // Ensure there is at least one active file selected
        val currentFile = fileDao.getCurrentFile()
        if (currentFile == null) {
            val all = fileDao.getAllFiles()
            if (all.isNotEmpty()) {
                fileDao.setCurrentFile(all.first().id)
            }
        }
    }

    /**
     * Saves a file physically to workspace disk.
     */
    fun saveFile(context: Context, name: String, folder: String, content: String): File {
        val root = getWorkspaceRoot(context)
        val targetDir = if (folder.isEmpty()) root else File(root, folder)
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }
        val file = File(targetDir, name)
        file.writeText(content)
        return file
    }

    /**
     * Deletes a file physically.
     */
    fun deleteFile(context: Context, name: String, folder: String): Boolean {
        val root = getWorkspaceRoot(context)
        val targetDir = if (folder.isEmpty()) root else File(root, folder)
        val file = File(targetDir, name)
        return if (file.exists()) {
            file.delete()
        } else {
            false
        }
    }

    /**
     * Deletes a folder physically.
     */
    fun deleteFolder(context: Context, folderPath: String): Boolean {
        val root = getWorkspaceRoot(context)
        val dir = File(root, folderPath)
        return if (dir.exists() && dir.isDirectory) {
            dir.deleteRecursively()
        } else {
            false
        }
    }

    /**
     * Renames a file physically.
     */
    fun renameFile(context: Context, name: String, folder: String, newName: String): Boolean {
        val root = getWorkspaceRoot(context)
        val targetDir = if (folder.isEmpty()) root else File(root, folder)
        val file = File(targetDir, name)
        val newFile = File(targetDir, newName)
        return if (file.exists() && !newFile.exists()) {
            file.renameTo(newFile)
        } else {
            false
        }
    }

    /**
     * Duplicates a file physically.
     */
    fun duplicateFile(context: Context, name: String, folder: String, duplicateName: String): Boolean {
        val root = getWorkspaceRoot(context)
        val targetDir = if (folder.isEmpty()) root else File(root, folder)
        val file = File(targetDir, name)
        val newFile = File(targetDir, duplicateName)
        return if (file.exists() && !newFile.exists()) {
            try {
                file.copyTo(newFile)
                true
            } catch (e: Exception) {
                false
            }
        } else {
            false
        }
    }

    /**
     * Moves a file physically.
     */
    fun moveFile(context: Context, name: String, srcFolder: String, destFolder: String): Boolean {
        val root = getWorkspaceRoot(context)
        val srcDir = if (srcFolder.isEmpty()) root else File(root, srcFolder)
        val file = File(srcDir, name)

        val targetFolderNormalized = destFolder.trim().replace("//", "/").trim('/')
        val destDir = if (targetFolderNormalized.isEmpty()) root else File(root, targetFolderNormalized)
        if (!destDir.exists()) {
            destDir.mkdirs()
        }

        val targetFile = File(destDir, name)
        return if (file.exists() && !targetFile.exists()) {
            file.renameTo(targetFile)
        } else {
            false
        }
    }

    /**
     * Copies a file physically.
     */
    fun copyFile(context: Context, name: String, srcFolder: String, destFolder: String): Boolean {
        val root = getWorkspaceRoot(context)
        val srcDir = if (srcFolder.isEmpty()) root else File(root, srcFolder)
        val file = File(srcDir, name)

        val targetFolderNormalized = destFolder.trim().replace("//", "/").trim('/')
        val destDir = if (targetFolderNormalized.isEmpty()) root else File(root, targetFolderNormalized)
        if (!destDir.exists()) {
            destDir.mkdirs()
        }

        val targetFile = File(destDir, "Copy_of_" + name)
        return if (file.exists() && !targetFile.exists()) {
            try {
                file.copyTo(targetFile)
                true
            } catch (e: Exception) {
                false
            }
        } else {
            false
        }
    }

    /**
     * Creates a directory physically.
     */
    fun createFolder(context: Context, folderPath: String): Boolean {
        val root = getWorkspaceRoot(context)
        val dir = File(root, folderPath)
        return dir.mkdirs()
    }
}
