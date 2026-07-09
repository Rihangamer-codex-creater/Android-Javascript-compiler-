package com.example.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class IdeRepository(private val db: AppDatabase) {
    val files: Flow<List<ProgramFile>> = db.programFileDao().getAllFilesFlow()
    val currentFile: Flow<ProgramFile?> = db.programFileDao().getCurrentFileFlow()
    val libraries: Flow<List<NpmLibrary>> = db.npmLibraryDao().getAllLibrariesFlow()
    val logs: Flow<List<ConsoleLog>> = db.consoleLogDao().getAllLogsFlow()

    private val fileDao = db.programFileDao()
    private val libraryDao = db.npmLibraryDao()
    private val logDao = db.consoleLogDao()
    private val settingDao = db.appSettingDao()

    suspend fun getFilesList(): List<ProgramFile> = fileDao.getAllFiles()

    suspend fun insertFile(file: ProgramFile): Int {
        val id = fileDao.insertFile(file)
        return id.toInt()
    }

    suspend fun updateFile(file: ProgramFile) {
        fileDao.updateFile(file)
    }

    suspend fun deleteFile(file: ProgramFile) {
        fileDao.deleteFile(file)
        // If we deleted the current file, mark another one as current
        val current = fileDao.getCurrentFile()
        if (current == null) {
            val all = fileDao.getAllFiles()
            if (all.isNotEmpty()) {
                fileDao.setCurrentFile(all.first().id)
            }
        }
    }

    suspend fun selectFile(fileId: Int) {
        fileDao.setCurrentFile(fileId)
    }

    suspend fun insertLibrary(library: NpmLibrary) {
        libraryDao.insertLibrary(library)
    }

    suspend fun deleteLibrary(library: NpmLibrary) {
        libraryDao.deleteLibrary(library)
    }

    suspend fun insertLog(type: String, message: String) {
        logDao.insertLog(ConsoleLog(type = type, message = message))
    }

    suspend fun clearLogs() {
        logDao.clearLogs()
    }

    suspend fun getSetting(key: String, defaultValue: String): String {
        return settingDao.getSetting(key)?.value ?: defaultValue
    }

    suspend fun saveSetting(key: String, value: String) {
        settingDao.saveSetting(AppSetting(key, value))
    }

    // Prepopulate database with beautiful samples if it is empty
    suspend fun prepopulateIfNeeded() {
        val allFiles = fileDao.getAllFiles()
        if (allFiles.isEmpty()) {
            // 1. Simple Hello & timing sample (javascript)
            val indexJs = ProgramFile(
                name = "index.js",
                language = "javascript",
                isCurrent = true,
                content = """// 🚀 Welcome to JavaScript IDE!
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

// 4. Test error catching (uncomment to see error handling in action):
// throw new Error("Oops! This is a test runtime error.");
"""
            )
            val indexId = fileDao.insertFile(indexJs)

            // 2. HTML canvas interactive sandbox (html)
            val canvasHtml = ProgramFile(
                name = "canvas_art.html",
                language = "html",
                isCurrent = false,
                content = """<!DOCTYPE html>
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

        // Resize Canvas
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

        // Mouse/Touch Interaction
        window.addEventListener('pointermove', (e) => {
            createExplosion(e.clientX, e.clientY);
        });

        window.addEventListener('pointerdown', (e) => {
            createExplosion(e.clientX, e.clientY);
        });

        // Loop
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

        // Add some starting floating particles
        setInterval(() => {
            if (particles.length < 50) {
                createExplosion(Math.random() * canvas.width, Math.random() * canvas.height);
            }
        }, 500);

        animate();
        console.log("🎨 Canvas Animation started successfully.");
    </script>
</body>
</html>
"""
            )
            fileDao.insertFile(canvasHtml)

            // 3. Simple React client-side demonstration using script tags (html)
            val reactHtml = ProgramFile(
                name = "react_counter.html",
                language = "html",
                isCurrent = false,
                content = """<!DOCTYPE html>
<html>
<head>
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <!-- Load React & ReactDOM via ES Modules for speed/compatibility -->
    <script type="importmap">
      {
        "imports": {
          "react": "https://esm.sh/react@18.2.0?dev",
          "react-dom/client": "https://esm.sh/react-dom@18.2.0/client?dev"
        }
      }
    </script>
    <style>
        body {
            margin: 0;
            padding: 24px;
            background-color: #121212;
            color: #ffffff;
            font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
            display: flex;
            justify-content: center;
            align-items: center;
            min-height: 80vh;
        }
        .card {
            background-color: #1e1e1e;
            padding: 32px;
            border-radius: 16px;
            box-shadow: 0 8px 32px rgba(0,0,0,0.5);
            text-align: center;
            width: 100%;
            max-width: 320px;
            border: 1px solid #333;
        }
        h1 { margin-top: 0; color: #61dafb; font-size: 28px; }
        .count { font-size: 64px; font-weight: bold; margin: 20px 0; color: #fff; }
        .btn-group { display: flex; gap: 12px; justify-content: center; }
        button {
            padding: 12px 24px;
            font-size: 18px;
            font-weight: bold;
            border: none;
            border-radius: 8px;
            cursor: pointer;
            transition: all 0.2s;
        }
        .btn-add { background-color: #61dafb; color: #121212; }
        .btn-sub { background-color: #333; color: #fff; border: 1px solid #555; }
        button:active { transform: scale(0.95); }
        .footer { font-size: 12px; color: #666; margin-top: 24px; }
    </style>
</head>
<body>
    <div id="root"></div>

    <script type="module">
        import React, { useState } from 'react';
        import ReactDOM from 'react-dom/client';

        function CounterApp() {
            const [count, setCount] = useState(0);

            return React.createElement('div', { className: 'card' },
                React.createElement('h1', null, '⚛️ React Sandbox'),
                React.createElement('p', { style: { color: '#888', fontSize: '14px' } }, 'State-driven fully responsive UI'),
                React.createElement('div', { className: 'count' }, count),
                React.createElement('div', { className: 'btn-group' },
                    React.createElement('button', { className: 'btn-sub', onClick: () => setCount(count - 1) }, '−'),
                    React.createElement('button', { className: 'btn-add', onClick: () => setCount(count + 1) }, '＋')
                ),
                React.createElement('div', { className: 'footer' }, 'Running locally in sandboxed WebView')
            );
        }

        const root = ReactDOM.createRoot(document.getElementById('root'));
        root.render(React.createElement(CounterApp));
        console.log("⚛️ React counter component rendered successfully.");
    </script>
</body>
</html>
"""
            )
            fileDao.insertFile(reactHtml)
        }

        // Populate popular npm libraries references
        val allLibraries = libraryDao.getAllLibrariesFlow().first()
        if (allLibraries.isEmpty()) {
            val defaultLibs = listOf(
                NpmLibrary("react", "https://esm.sh/react@18.2.0"),
                NpmLibrary("react-dom", "https://esm.sh/react-dom@18.2.0"),
                NpmLibrary("lodash", "https://esm.sh/lodash@4.17.21"),
                NpmLibrary("axios", "https://esm.sh/axios@1.6.8"),
                NpmLibrary("chart.js", "https://esm.sh/chart.js@4.4.2"),
                NpmLibrary("three.js", "https://esm.sh/three@0.163.0"),
                NpmLibrary("uuid", "https://esm.sh/uuid@9.0.1"),
                NpmLibrary("moment", "https://esm.sh/moment@2.30.1")
            )
            for (lib in defaultLibs) {
                libraryDao.insertLibrary(lib)
            }
        }
    }
}
