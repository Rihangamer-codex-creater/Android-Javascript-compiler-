package com.example.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.Redo
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource

@Composable
fun JsEditor(
    fileId: Int,
    code: String,
    onCodeChanged: (String) -> Unit,
    isDarkMode: Boolean,
    isAutocompleteEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    // Keep a state for TextFieldValue to track cursor position, keyed on fileId
    var textFieldValue by remember(fileId) {
        mutableStateOf(
            TextFieldValue(
                text = code,
                selection = androidx.compose.ui.text.TextRange(code.length)
            )
        )
    }

    // Undo & Redo History stacks keyed on fileId for robust offline state control
    val undoStack = remember(fileId) { mutableStateListOf<TextFieldValue>() }
    val redoStack = remember(fileId) { mutableStateListOf<TextFieldValue>() }
    val focusRequester = remember { FocusRequester() }

    val editorBgColor = if (isDarkMode) Color(0xFF1C1B1F) else Color(0xFFFAFAFA)
    val sidebarBgColor = if (isDarkMode) Color(0xFF1C1B1F) else Color(0xFFF1F5F9)
    val textColor = if (isDarkMode) Color(0xFFE6E1E5) else Color(0xFF1C1B1F)
    val cursorColor = if (isDarkMode) Color(0xFFD0BCFF) else Color(0xFF6750A4)
    val numberColor = if (isDarkMode) Color(0xFF938F99) else Color(0xFF6A737D)

    val verticalScrollState = rememberScrollState()
    val horizontalScrollState = rememberScrollState()
    val autocompleteScrollState = rememberScrollState()
    
    val syntaxHighlighter = remember(isDarkMode) { JsSyntaxHighlighter(isDarkMode) }

    // Determine number of lines and longest line with caching to prevent scroll/render lag
    val lines = remember(textFieldValue.text) { textFieldValue.text.split("\n") }
    val lineCount = remember(lines) { lines.size.coerceAtLeast(1) }
    val lineNumbersText = remember(lineCount) {
        (1..lineCount).joinToString("\n")
    }
    val longestLineLength = remember(lines) {
        lines.maxOfOrNull { it.length } ?: 0
    }
    val configuration = LocalConfiguration.current
    val availableWidthDp = remember(configuration.screenWidthDp) {
        java.lang.Math.max(300, configuration.screenWidthDp - 44).dp
    }
    val longestLineDp = remember(longestLineLength) {
        (longestLineLength * 8 + 32).dp
    }
    val editorWidth = remember(availableWidthDp, longestLineDp) {
        if (longestLineDp > availableWidthDp) longestLineDp else availableWidthDp
    }

    // Common autocomplete and symbol insertion helper with Undo history recording
    val insertSymbol: (String) -> Unit = { symbol ->
        val text = textFieldValue.text
        val selection = textFieldValue.selection
        val start = selection.start
        val end = selection.end
        
        val newText = text.substring(0, start) + symbol + text.substring(end)
        val newSelection = androidx.compose.ui.text.TextRange(start + symbol.length)
        
        val newValue = TextFieldValue(text = newText, selection = newSelection)
        
        // Save history checkpoint before inserting a symbol
        redoStack.clear()
        if (undoStack.size >= 50) {
            undoStack.removeAt(0)
        }
        undoStack.add(textFieldValue)
        
        textFieldValue = newValue
        onCodeChanged(newText)
    }

    // Perform Undo history pop
    val performUndo: () -> Unit = {
        if (undoStack.isNotEmpty()) {
            val previousState = undoStack.removeAt(undoStack.lastIndex)
            redoStack.add(textFieldValue)
            textFieldValue = previousState
            onCodeChanged(previousState.text)
        }
    }

    // Perform Redo history pop
    val performRedo: () -> Unit = {
        if (redoStack.isNotEmpty()) {
            val nextState = redoStack.removeAt(redoStack.lastIndex)
            undoStack.add(textFieldValue)
            textFieldValue = nextState
            onCodeChanged(nextState.text)
        }
    }

    val autocompleteSymbols = listOf(
        "\t", "{ }", "( )", "[ ]", ";", "=", "=>", "const ", "let ", "function ", 
        "console.log(", "document.getElementById(", "Math.random()", 
        "JSON.stringify(", "JSON.parse(", "async ", "await ", "if (", "else "
    )

    Column(modifier = modifier.fillMaxSize()) {
        // Sticky Controls & Quick Symbols Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(sidebarBgColor)
                .padding(vertical = 4.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Undo Control Button
            IconButton(
                onClick = performUndo,
                enabled = undoStack.isNotEmpty(),
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Undo,
                    contentDescription = "Undo",
                    tint = if (undoStack.isNotEmpty()) textColor else textColor.copy(alpha = 0.3f),
                    modifier = Modifier.size(20.dp)
                )
            }

            // Redo Control Button
            IconButton(
                onClick = performRedo,
                enabled = redoStack.isNotEmpty(),
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Redo,
                    contentDescription = "Redo",
                    tint = if (redoStack.isNotEmpty()) textColor else textColor.copy(alpha = 0.3f),
                    modifier = Modifier.size(20.dp)
                )
            }

            VerticalDivider(
                modifier = Modifier
                    .height(24.dp)
                    .padding(horizontal = 6.dp),
                color = if (isDarkMode) Color(0xFF49454F) else Color(0xFFE2E8F0)
            )

            // Autocomplete & Quick Symbols Scrollable Bar
            if (isAutocompleteEnabled) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(autocompleteScrollState),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    autocompleteSymbols.forEach { symbol ->
                        val cleanLabel = when (symbol) {
                            "\t" -> "Tab"
                            "{ }" -> "{ }"
                            "( )" -> "( )"
                            "[ ]" -> "[ ]"
                            else -> symbol.trim()
                        }
                        Button(
                            onClick = {
                                val insertion = when (symbol) {
                                    "\t" -> "\t"
                                    "{ }" -> "{\n    \n}"
                                    "( )" -> "()"
                                    "[ ]" -> "[]"
                                    else -> symbol
                                }
                                insertSymbol(insertion)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isDarkMode) Color(0xFF49454F) else Color(0xFFE2E8F0),
                                contentColor = if (isDarkMode) Color(0xFFE6E1E5) else Color(0xFF1C1B1F)
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text(
                                text = cleanLabel,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
        }

        Divider(color = if (isDarkMode) Color(0xFF49454F) else Color(0xFFE2E8F0), thickness = 1.dp)

        // Editor Workspace containing Line numbers and input
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(editorBgColor)
        ) {
            // Line numbers panel (scrolls in sync with code) using a single Text layout for 100% pixel alignment
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(44.dp)
                    .background(sidebarBgColor)
                    .verticalScroll(verticalScrollState)
                    .padding(top = 12.dp, bottom = 300.dp)
            ) {
                Text(
                    text = lineNumbersText,
                    color = numberColor,
                    style = TextStyle(
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 18.sp,
                        textAlign = TextAlign.End
                    ),
                    softWrap = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 8.dp)
                )
            }

            VerticalDivider(color = if (isDarkMode) Color(0xFF49454F) else Color(0xFFE2E8F0), thickness = 1.dp)

            // Dynamic Scrollable TextField
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(verticalScrollState)
                    .horizontalScroll(horizontalScrollState)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        focusRequester.requestFocus()
                    }
                    .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 300.dp)
            ) {
                BasicTextField(
                    value = textFieldValue,
                    onValueChange = { newValue ->
                        val oldText = textFieldValue.text
                        val newText = newValue.text
                        if (newText != oldText) {
                            // Clear redo stack on manual text input
                            redoStack.clear()
                            
                            // History saving heuristic (spaces, newlines, or block edits of 6+ characters)
                            val lastSaved = undoStack.lastOrNull()
                            val shouldPush = lastSaved == null ||
                                    newText.endsWith(" ") ||
                                    newText.endsWith("\n") ||
                                    java.lang.Math.abs(newText.length - lastSaved.text.length) >= 6
                            
                            if (shouldPush) {
                                if (undoStack.size >= 50) {
                                    undoStack.removeAt(0)
                                }
                                undoStack.add(textFieldValue)
                            }
                        }
                        textFieldValue = newValue
                        onCodeChanged(newText)
                    },
                    textStyle = TextStyle(
                        color = textColor,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 18.sp
                    ),
                    cursorBrush = SolidColor(cursorColor),
                    visualTransformation = syntaxHighlighter,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Ascii,
                        imeAction = ImeAction.Default,
                        autoCorrectEnabled = false
                    ),
                    modifier = Modifier
                        .focusRequester(focusRequester)
                        .fillMaxHeight()
                        .width(editorWidth)
                        .wrapContentHeight()
                )
            }
        }
    }
}
