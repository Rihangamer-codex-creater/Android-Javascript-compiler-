package com.example.ui.terminal

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ConsoleLog
import com.example.ui.TerminalInstance
import kotlinx.coroutines.launch

@Composable
fun ConsoleTerminal(
    logs: List<ConsoleLog>,
    onClearLogs: () -> Unit,
    isDarkMode: Boolean,
    isAutoTerminateEnabled: Boolean = true,
    onToggleAutoTerminate: () -> Unit = {},
    terminalsList: List<TerminalInstance> = emptyList(),
    activeTerminalId: Int = 1,
    onSelectTerminal: (Int) -> Unit = {},
    onCreateTerminal: () -> Unit = {},
    onDeleteTerminal: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val tabsScrollState = rememberScrollState()

    val terminalHeight = if (isExpanded) 300.dp else 160.dp
    
    // VS Code-inspired color palette
    val bgTerminalColor = if (isDarkMode) Color(0xFF1E1E1E) else Color(0xFFF3F3F3)
    val headerColor = if (isDarkMode) Color(0xFF252526) else Color(0xFFE1E1E1)
    val tabActiveColor = if (isDarkMode) Color(0xFF1E1E1E) else Color(0xFFF3F3F3)
    val tabInactiveColor = if (isDarkMode) Color(0xFF2D2D2D) else Color(0xFFD6D6D6)
    val textColor = if (isDarkMode) Color(0xFFCCCCCC) else Color(0xFF333333)
    val activeTabBorderColor = if (isDarkMode) Color(0xFF007ACC) else Color(0xFF007ACC) // VS Code Blue

    // Colors for log types matching real terminal streams
    val logColor = if (isDarkMode) Color(0xFF4EC9B0) else Color(0xFF098658) // Teal-Green stdout
    val errorColor = if (isDarkMode) Color(0xFFF44747) else Color(0xFFA31515) // Bright red stderr
    val warnColor = if (isDarkMode) Color(0xFFCCA700) else Color(0xFF811F3F) // Amber-yellow warnings
    val infoColor = if (isDarkMode) Color(0xFF9CDCFE) else Color(0xFF0451A5) // Light blue system info

    // Scroll to bottom when a new log arrives
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            coroutineScope.launch {
                listState.animateScrollToItem(logs.size - 1)
            }
        }
    }

    Card(
        shape = androidx.compose.ui.graphics.RectangleShape,
        colors = CardDefaults.cardColors(containerColor = bgTerminalColor),
        modifier = modifier
            .fillMaxWidth()
            .height(terminalHeight)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // VS Code Tab Bar & Controls Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .background(headerColor)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Terminal Instances Row (VS Code tabs look)
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(tabsScrollState)
                        .padding(end = 8.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (terminalsList.isEmpty()) {
                        // Fallback tab if empty
                        Box(
                            modifier = Modifier
                                .background(tabActiveColor)
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                "bash (main)",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = textColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        terminalsList.forEach { terminal ->
                            val isActive = terminal.id == activeTerminalId
                            val tabBg = if (isActive) tabActiveColor else tabInactiveColor
                            val textWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
                            val itemTextColor = if (isActive) textColor else textColor.copy(alpha = 0.6f)

                            Column(
                                modifier = Modifier
                                    .background(tabBg)
                                    .clickable { onSelectTerminal(terminal.id) }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .background(
                                                if (isActive) activeTabBorderColor else Color.Transparent,
                                                shape = MaterialTheme.shapes.extraSmall
                                            )
                                    )
                                    Text(
                                        text = terminal.name,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = itemTextColor,
                                        fontWeight = textWeight
                                    )
                                    
                                    // Deletable tab icon (if more than 1)
                                    if (terminalsList.size > 1) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Delete terminal",
                                            tint = itemTextColor.copy(alpha = 0.5f),
                                            modifier = Modifier
                                                .size(12.dp)
                                                .clickable { onDeleteTerminal(terminal.id) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Header Utility Controls (VS Code Actions)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.padding(bottom = 4.dp)
                ) {
                    // Create Terminal (+)
                    IconButton(
                        onClick = onCreateTerminal,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Add, 
                            contentDescription = "New Terminal", 
                            tint = textColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Toggle Auto-Termination Action
                    IconButton(
                        onClick = {
                            onToggleAutoTerminate()
                            val msg = if (!isAutoTerminateEnabled) "Auto-termination enabled" else "Auto-termination disabled (Infinite Execution)"
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Timer, 
                            contentDescription = "Toggle Auto-Termination", 
                            tint = if (isAutoTerminateEnabled) activeTabBorderColor else textColor.copy(alpha = 0.5f),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Copy action
                    IconButton(
                        onClick = {
                            if (logs.isNotEmpty()) {
                                val logText = logs.joinToString("\n") { "[${it.type.uppercase()}] ${it.message}" }
                                clipboardManager.setText(AnnotatedString(logText))
                                Toast.makeText(context, "Copied logs to clipboard!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.ContentCopy, 
                            contentDescription = "Copy logs", 
                            tint = textColor,
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    // Clear logs action
                    IconButton(
                        onClick = onClearLogs,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.ClearAll, 
                            contentDescription = "Clear logs", 
                            tint = textColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Delete Active Terminal
                    IconButton(
                        onClick = { onDeleteTerminal(activeTerminalId) },
                        enabled = terminalsList.size > 1,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete, 
                            contentDescription = "Delete Active Terminal", 
                            tint = if (terminalsList.size > 1) MaterialTheme.colorScheme.error else textColor.copy(alpha = 0.3f),
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    // Expand / Collapse toggle
                    IconButton(
                        onClick = { isExpanded = !isExpanded },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                            contentDescription = "Resize Terminal",
                            tint = textColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Divider(color = if (isDarkMode) Color(0xFF333333) else Color(0xFFCCCCCC), thickness = 1.dp)

            // Scrollable Terminal Logs Window
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.0f)
                    .background(bgTerminalColor)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                if (logs.isEmpty()) {
                    item {
                        Text(
                            text = "microsoft-vscode-terminal ~ sandbox bash v1.0.0\n$ node --version\nv20.11.0\n$ \n> Terminal ready. Click the PLAY arrow to execute files and direct logs here.",
                            color = textColor.copy(alpha = 0.4f),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                } else {
                    items(logs) { logItem ->
                        val labelColor = when (logItem.type) {
                            "error" -> errorColor
                            "warn" -> warnColor
                            "info" -> infoColor
                            else -> logColor
                        }
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 1.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = "➜",
                                color = labelColor,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(end = 6.dp)
                            )
                            Text(
                                text = logItem.message,
                                color = if (logItem.type == "error") errorColor else textColor,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }
}
