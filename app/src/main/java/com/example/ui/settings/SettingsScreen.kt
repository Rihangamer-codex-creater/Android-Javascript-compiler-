package com.example.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsScreen(
    isDarkMode: Boolean,
    onToggleDarkMode: () -> Unit,
    isJsExecutionEnabled: Boolean,
    onToggleJsExecution: () -> Unit,
    isAutocompleteEnabled: Boolean,
    onToggleAutocomplete: () -> Unit,
    isVoiceSupportEnabled: Boolean,
    onToggleVoiceSupport: () -> Unit,
    isAutoTerminateEnabled: Boolean = true,
    onToggleAutoTerminate: () -> Unit = {},
    onOpenGuide: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val bgColor = if (isDarkMode) Color(0xFF1C1B1F) else Color(0xFFFAFAFA)
    val cardBgColor = if (isDarkMode) Color(0xFF25232A) else Color(0xFFFFFFFF)
    val textColor = if (isDarkMode) Color(0xFFE6E1E5) else Color(0xFF1C1B1F)
    val secondaryTextColor = if (isDarkMode) Color(0xFF938F99) else Color(0xFF6A737D)
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(bgColor)
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        Text(
            text = "⚙️ IDE System Settings",
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = textColor,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // General toggles card using Material 3 ListItems for perfect alignment
        Card(
            colors = CardDefaults.cardColors(containerColor = cardBgColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                // 1. Dark Theme
                ListItem(
                    headlineContent = {
                        Text("Dark Coding Theme", fontWeight = FontWeight.Bold, color = textColor)
                    },
                    supportingContent = {
                        Text("Switch editor to high-contrast Monokai", fontSize = 11.sp, color = secondaryTextColor)
                    },
                    trailingContent = {
                        Switch(
                            checked = isDarkMode,
                            onCheckedChange = { onToggleDarkMode() }
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )

                HorizontalDivider(color = secondaryTextColor.copy(alpha = 0.2f), modifier = Modifier.padding(horizontal = 16.dp))

                // 2. JS Execution
                ListItem(
                    headlineContent = {
                        Text("Enable JavaScript Runtime", fontWeight = FontWeight.Bold, color = textColor)
                    },
                    supportingContent = {
                        Text("Runs your code in fully sandboxed WebView V8", fontSize = 11.sp, color = secondaryTextColor)
                    },
                    trailingContent = {
                        Switch(
                            checked = isJsExecutionEnabled,
                            onCheckedChange = { onToggleJsExecution() }
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )

                HorizontalDivider(color = secondaryTextColor.copy(alpha = 0.2f), modifier = Modifier.padding(horizontal = 16.dp))

                // 3. Autocomplete toolbar
                ListItem(
                    headlineContent = {
                        Text("Monaco Autocomplete Bar", fontWeight = FontWeight.Bold, color = textColor)
                    },
                    supportingContent = {
                        Text("Shows quick-insert JS tags above keyboard", fontSize = 11.sp, color = secondaryTextColor)
                    },
                    trailingContent = {
                        Switch(
                            checked = isAutocompleteEnabled,
                            onCheckedChange = { onToggleAutocomplete() }
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )

                HorizontalDivider(color = secondaryTextColor.copy(alpha = 0.2f), modifier = Modifier.padding(horizontal = 16.dp))

                // 4. TTS speech support
                ListItem(
                    headlineContent = {
                        Text("Speech Synthesizer / Voice Assistant", fontWeight = FontWeight.Bold, color = textColor)
                    },
                    supportingContent = {
                        Text("Dictates editor triggers & speaks runtime error states", fontSize = 11.sp, color = secondaryTextColor)
                    },
                    trailingContent = {
                        Switch(
                            checked = isVoiceSupportEnabled,
                            onCheckedChange = { onToggleVoiceSupport() }
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )

                HorizontalDivider(color = secondaryTextColor.copy(alpha = 0.2f), modifier = Modifier.padding(horizontal = 16.dp))

                // 5. Auto Terminate Program execution
                ListItem(
                    headlineContent = {
                        Text("Auto Terminate Run", fontWeight = FontWeight.Bold, color = textColor)
                    },
                    supportingContent = {
                        Text("Automatically ends execution after program finishes", fontSize = 11.sp, color = secondaryTextColor)
                    },
                    trailingContent = {
                        Switch(
                            checked = isAutoTerminateEnabled,
                            onCheckedChange = { onToggleAutoTerminate() }
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Interactive Guide Card
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            onClick = onOpenGuide,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MenuBook,
                    contentDescription = "Menu Book",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "📖 CodeX Editor Interactive Guide",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "Interactive tutorials, quick templates, shortcuts & NPM help.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Info Card
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isDarkMode) Color(0xFF25232A) else Color(0xFFF1F5F9)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Default.Info, contentDescription = "Info", tint = MaterialTheme.colorScheme.primary)
                Column {
                    Text(
                        text = "CodeX Editor v3.8",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = textColor
                    )
                    Text(
                        text = "Developed by CodeX",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Designed for fully functional mobile JS & HTML development on the go.",
                        fontSize = 11.sp,
                        color = secondaryTextColor
                    )
                }
            }
        }
    }
}
