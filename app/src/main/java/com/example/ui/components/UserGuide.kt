package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.IdeViewModel

@Composable
fun UserGuideDialog(
    viewModel: IdeViewModel,
    onDismiss: () -> Unit
) {
    val isDarkMode = viewModel.isDarkMode
    val bgColor = if (isDarkMode) Color(0xFF1E1E1E) else Color(0xFFFAFAFA)
    val cardBgColor = if (isDarkMode) Color(0xFF2D2D2D) else Color(0xFFFFFFFF)
    val textColor = if (isDarkMode) Color(0xFFE6E1E5) else Color(0xFF1C1B1F)
    val secondaryTextColor = if (isDarkMode) Color(0xFFB5B5B5) else Color(0xFF5A636C)
    val dividerColor = if (isDarkMode) Color(0xFF3F3F3F) else Color(0xFFE2E8F0)
    val accentColor = Color(0xFF9F5FEE)

    var activeTab by remember { mutableStateOf(0) }
    val tabTitles = listOf("🚀 Start", "💻 Sandbox", "🖥️ Terminal", "📦 NPM", "🎮 Playground")
    val tabIcons = listOf(
        Icons.Default.PlayArrow,
        Icons.Default.Code,
        Icons.Default.Terminal,
        Icons.Default.Inventory,
        Icons.Default.Extension
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f)
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, dividerColor, RoundedCornerShape(16.dp)),
            color = bgColor
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.MenuBook,
                            contentDescription = "Book Icon",
                            tint = accentColor,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Spark Code Guide & Tutorials",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = textColor
                            )
                            Text(
                                text = "Version 2.8 • Offline JS IDE",
                                fontSize = 10.sp,
                                color = secondaryTextColor
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Guide",
                            tint = textColor
                        )
                    }
                }

                Divider(color = dividerColor, thickness = 1.dp)

                // Navigation Tabs
                ScrollableTabRow(
                    selectedTabIndex = activeTab,
                    containerColor = if (isDarkMode) Color(0xFF151515) else Color(0xFFF1F5F9),
                    contentColor = accentColor,
                    edgePadding = 8.dp,
                    indicator = { tabPositions ->
                        if (activeTab < tabPositions.size) {
                            TabRowDefaults.SecondaryIndicator(
                                Modifier.tabIndicatorOffset(tabPositions[activeTab]),
                                color = accentColor
                            )
                        }
                    }
                ) {
                    tabTitles.forEachIndexed { index, title ->
                        Tab(
                            selected = activeTab == index,
                            onClick = { activeTab = index },
                            icon = {
                                Icon(
                                    imageVector = tabIcons[index],
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            text = { Text(title, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            selectedContentColor = accentColor,
                            unselectedContentColor = textColor.copy(alpha = 0.6f)
                        )
                    }
                }

                // Tab Content Card
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    val scrollState = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        when (activeTab) {
                            0 -> WelcomeSection(textColor, secondaryTextColor, cardBgColor, isDarkMode)
                            1 -> EditorSandboxSection(textColor, secondaryTextColor, cardBgColor, isDarkMode)
                            2 -> TerminalSection(textColor, secondaryTextColor, cardBgColor, isDarkMode)
                            3 -> NpmSection(textColor, secondaryTextColor, cardBgColor, isDarkMode)
                            4 -> PlaygroundSection(viewModel, textColor, secondaryTextColor, cardBgColor, isDarkMode, onDismiss)
                        }
                    }
                }

                Divider(color = dividerColor, thickness = 1.dp)

                // Bottom actions
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Tip: Files auto-sync to your local disk storage!",
                        fontSize = 11.sp,
                        color = textColor.copy(alpha = 0.6f),
                        fontFamily = FontFamily.Monospace
                    )

                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                    ) {
                        Text("Got It", color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun WelcomeSection(
    textColor: Color,
    secondaryTextColor: Color,
    cardBgColor: Color,
    isDarkMode: Boolean
) {
    Text(
        text = "Welcome to Spark Code! ⚡",
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        color = textColor
    )

    Text(
        text = "Spark Code is a professional, mobile-first JavaScript IDE designed to let you build, run, and test JavaScript code directly on your Android device—even when completely offline.",
        fontSize = 13.sp,
        color = secondaryTextColor,
        lineHeight = 18.sp
    )

    Text(
        text = "Key Features Walkthrough:",
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = textColor
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FeatureItem("🚀 Rich Editor", "Custom Monokai styling, automatic text backups, and a sticky autocomplete toolbar designed for compact phone layout coding.", isDarkMode)
        FeatureItem("💻 Sandboxed Runner", "Runs code in a headless V8 context or an interactive live HTML canvas layout right inside your app.", isDarkMode)
        FeatureItem("🖥️ Multi-Terminal Console", "Pipelines raw JavaScript compiler errors and `console.log()` streams directly into named interactive tabs.", isDarkMode)
        FeatureItem("📦 Offline NPM Packages", "Fetches and stores external libraries locally, keeping your workspace fast and fully independent.", isDarkMode)
        FeatureItem("💾 Real Disk Sync", "Uses Android physical file structures synced with an offline SQLite Room Database. Create folders and files safely.", isDarkMode)
    }
}

@Composable
fun EditorSandboxSection(
    textColor: Color,
    secondaryTextColor: Color,
    cardBgColor: Color,
    isDarkMode: Boolean
) {
    Text(
        text = "Writing & Saving Code 💻",
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        color = textColor
    )

    Text(
        text = "Our Monaco-inspired lightweight editor brings desk-quality coding to your fingertips. Here is how to maximize your productivity:",
        fontSize = 13.sp,
        color = secondaryTextColor,
        lineHeight = 18.sp
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = cardBgColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("💡 PRO TIPS:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFFFFB703))
            
            BulletRow("Sticky Symbols Bar", "Use the quick-action panel directly above your keyboard to insert braces, loops, brackets, variables, or functions instantly.", textColor)
            BulletRow("Disk Synchronizer", "Every keystroke saves your progress locally. To link files onto your device's global Downloads folder, tap the Drawer -> Save directly to My Files.", textColor)
            BulletRow("Offline DB Safety", "If you crash or restart, Room DB auto-sync retrieves your local code tree perfectly untouched.", textColor)
        }
    }
}

@Composable
fun TerminalSection(
    textColor: Color,
    secondaryTextColor: Color,
    cardBgColor: Color,
    isDarkMode: Boolean
) {
    Text(
        text = "Debugging with Console Terminal 🖥️",
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        color = textColor
    )

    Text(
        text = "We built a robust, multiple-process console router that pipes JavaScript log flows directly to a terminal log dashboard.",
        fontSize = 13.sp,
        color = secondaryTextColor,
        lineHeight = 18.sp
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = cardBgColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("🛠️ HOW TO DEBUG:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF62B6CB))
            
            BulletRow("Write Log Commands", "Insert standard expressions like 'console.log(\"Testing values: \", data)' in your JS.", textColor)
            BulletRow("Watch Logs", "Run your project via the green 'Run' button, then switch to the 'Terminal' tab to inspect output levels: Info (blue), Warn (yellow), and Error (red).", textColor)
            BulletRow("Manage Terminals", "Tap '+' in the console tab to create clean separate workspace processes. Tap 'Delete' or 'Clear' to clean up log arrays.", textColor)
            BulletRow("Speech Synthesis", "Enable Voice Assistant in settings! It will literally speak compiler stack trace errors out loud, helping you fix typos easily.", textColor)
        }
    }
}

@Composable
fun NpmSection(
    textColor: Color,
    secondaryTextColor: Color,
    cardBgColor: Color,
    isDarkMode: Boolean
) {
    Text(
        text = "NPM Libraries & Caching 📦",
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        color = textColor
    )

    Text(
        text = "No IDE is complete without support for packages. Spark Code allows injecting any front-end NPM or JavaScript library directly into your sandboxed code workspace.",
        fontSize = 13.sp,
        color = secondaryTextColor,
        lineHeight = 18.sp
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = cardBgColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("📥 DOWNLOADING PACKAGES:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF4CAF50))
            
            BulletRow("Go to Libraries", "Tap the 'Libraries' tab. Search for popular online modules (like Lodash, Chart.js, anime.js).", textColor)
            BulletRow("Download & Use", "Tap 'Download' to request the package. Spark Code will query unpkg and cache the code bundle offline in your private folder structure.", textColor)
            BulletRow("Automatic Injection", "Injected packages are automatically pre-loaded when executing html/js, meaning they run flawlessly even without cell service!", textColor)
        }
    }
}

@Composable
fun PlaygroundSection(
    viewModel: IdeViewModel,
    textColor: Color,
    secondaryTextColor: Color,
    cardBgColor: Color,
    isDarkMode: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    Text(
        text = "🎮 Interactive Code Playground",
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        color = textColor
    )

    Text(
        text = "Load a fully-functional interactive demo project directly into your active workspace with one tap. This is the fastest way to learn how HTML, JS, CSS, and libraries fit together in Spark Code!",
        fontSize = 13.sp,
        color = secondaryTextColor,
        lineHeight = 18.sp
    )

    Divider(color = if (isDarkMode) Color(0xFF3F3F3F) else Color(0xFFE2E8F0))

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PlaygroundTemplateCard(
            title = "✨ HTML5 Cosmic Particles Canvas",
            description = "Loads an interactive particle engine calculating orbits and bouncing waves on a full-screen canvas element.",
            color = Color(0xFFE34F26),
            onClick = {
                val canvasHtml = """<!DOCTYPE html>
<html>
<head>
    <style>
        body, html {
            margin: 0; padding: 0; overflow: hidden;
            background-color: #0c0b11; width: 100%; height: 100%;
        }
        canvas { display: block; }
    </style>
</head>
<body>
    <canvas id="cosmicCanvas"></canvas>
    <script>
        const canvas = document.getElementById("cosmicCanvas");
        const ctx = canvas.getContext("2d");
        let width = canvas.width = window.innerWidth;
        let height = canvas.height = window.innerHeight;

        const particles = [];
        const count = 75;

        for (let i = 0; i < count; i++) {
            particles.push({
                x: Math.random() * width,
                y: Math.random() * height,
                vx: (Math.random() - 0.5) * 3,
                vy: (Math.random() - 0.5) * 3,
                radius: Math.random() * 4 + 2,
                color: "hsla(" + (Math.random() * 360) + ", 80%, 70%, 0.8)"
            });
        }

        function animate() {
            ctx.fillStyle = "rgba(12, 11, 17, 0.2)";
            ctx.fillRect(0, 0, width, height);

            particles.forEach(p => {
                p.x += p.vx;
                p.y += p.vy;

                if (p.x < 0 || p.x > width) p.vx *= -1;
                if (p.y < 0 || p.y > height) p.vy *= -1;

                ctx.beginPath();
                ctx.arc(p.x, p.y, p.radius, 0, Math.PI * 2);
                ctx.fillStyle = p.color;
                ctx.fill();
            });

            requestAnimationFrame(animate);
        }
        
        console.log("🚀 Cosmic Particle Engine initialized! Enjoy the visual rendering in the Browser tab.");
        animate();
    </script>
</body>
</html>"""
                viewModel.createFileWithContent("CosmicParticles", "html", "", canvasHtml)
                android.widget.Toast.makeText(context, "Loaded CosmicParticles.html!", android.widget.Toast.LENGTH_SHORT).show()
                onDismiss()
            }
        )

        PlaygroundTemplateCard(
            title = "⚡ Async API Fetcher Demo",
            description = "Loads an async JS block that queries public API nodes, demonstrates json decoding, error catching, and speech synthesis output.",
            color = Color(0xFFF7DF1E),
            onClick = {
                val apiJs = """// Async JavaScript fetch and parse template
async function fetchSystemStats() {
    console.log("⚡ Contacting external API endpoint...");
    try {
        const res = await fetch("https://api.coindesk.com/v1/bpi/currentprice.json");
        if (!res.ok) throw new Error("HTTP connection failed! Code: " + res.status);
        const data = await res.json();
        
        console.log("✓ Success! Received data payload.");
        console.log("Asset: " + data.chartName);
        console.log("Price (USD): ${'$'}" + data.bpi.USD.rate);
        console.log("Update time: " + data.time.updated);
    } catch (err) {
        console.error("❌ Failed API transaction: " + err.message);
    }
}

fetchSystemStats();"""
                viewModel.createFileWithContent("AsyncFetcher", "javascript", "", apiJs)
                android.widget.Toast.makeText(context, "Loaded AsyncFetcher.js!", android.widget.Toast.LENGTH_SHORT).show()
                onDismiss()
            }
        )

        PlaygroundTemplateCard(
            title = "🎨 Neon CSS Lightbox Layout",
            description = "Loads an elegant layout showing responsive grid design, modern CSS parameters, and glowing animation styles.",
            color = Color(0xFF264DE4),
            onClick = {
                val neonHtml = """<!DOCTYPE html>
<html>
<head>
    <style>
        body {
            background: linear-gradient(135deg, #121214 0%, #1a1a24 100%);
            color: #FAFAFA;
            font-family: system-ui, sans-serif;
            display: flex;
            justify-content: center;
            align-items: center;
            min-height: 100vh;
            margin: 0;
        }
        .box {
            background: rgba(255, 255, 255, 0.05);
            border: 2px solid #9F5FEE;
            border-radius: 16px;
            padding: 30px;
            text-align: center;
            box-shadow: 0 0 20px rgba(159, 95, 238, 0.4);
            animation: pulse 3s infinite alternate;
        }
        h1 { color: #9F5FEE; margin-bottom: 10px; }
        @keyframes pulse {
            0% { box-shadow: 0 0 20px rgba(159, 95, 238, 0.3); }
            100% { box-shadow: 0 0 40px rgba(159, 95, 238, 0.7); }
        }
    </style>
</head>
<body>
    <div class="box">
        <h1>Spark Code</h1>
        <p>Responsive Neon CSS Playground is active!</p>
    </div>
</body>
</html>"""
                viewModel.createFileWithContent("NeonLightbox", "html", "", neonHtml)
                android.widget.Toast.makeText(context, "Loaded NeonLightbox.html!", android.widget.Toast.LENGTH_SHORT).show()
                onDismiss()
            }
        )
    }
}

@Composable
fun PlaygroundTemplateCard(
    title: String,
    description: String,
    color: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(color)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = description,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    lineHeight = 15.sp
                )
            }

            Icon(
                imageVector = Icons.Default.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun FeatureItem(
    title: String,
    description: String,
    isDarkMode: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = Color(0xFF4CAF50),
            modifier = Modifier.size(18.dp)
        )
        Column {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = if (isDarkMode) Color.White else Color.Black
            )
            Text(
                text = description,
                fontSize = 11.sp,
                color = if (isDarkMode) Color(0xFFCAC4D0) else Color(0xFF625B71),
                lineHeight = 15.sp
            )
        }
    }
}

@Composable
fun BulletRow(
    boldPrefix: String,
    text: String,
    textColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("•", color = textColor, fontWeight = FontWeight.Bold)
        Column {
            Text(
                text = boldPrefix,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = textColor
            )
            Text(
                text = text,
                fontSize = 11.sp,
                color = textColor.copy(alpha = 0.8f),
                lineHeight = 15.sp
            )
        }
    }
}
