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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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

    val editorBgColor = if (isDarkMode) Color(0xFF1C1B1F) else Color(0xFFFAFAFA)
    val sidebarBgColor = if (isDarkMode) Color(0xFF1C1B1F) else Color(0xFFF1F5F9)
    val textColor = if (isDarkMode) Color(0xFFE6E1E5) else Color(0xFF1C1B1F)
    val cursorColor = if (isDarkMode) Color(0xFFD0BCFF) else Color(0xFF6750A4)
    val numberColor = if (isDarkMode) Color(0xFF938F99) else Color(0xFF6A737D)

    val verticalScrollState = rememberScrollState()
    val horizontalScrollState = rememberScrollState()
    val autocompleteScrollState = rememberScrollState()
    
    val syntaxHighlighter = remember(isDarkMode) { JsSyntaxHighlighter(isDarkMode) }

    // Determine number of lines
    val lines = textFieldValue.text.split("\n")
    val lineCount = lines.size.coerceAtLeast(1)
    val lineNumbersText = remember(lineCount) {
        (1..lineCount).joinToString("\n")
    }

    // Common autocomplete and symbol insertion helper
    val insertSymbol: (String) -> Unit = { symbol ->
        val text = textFieldValue.text
        val selection = textFieldValue.selection
        val start = selection.start
        val end = selection.end
        
        val newText = text.substring(0, start) + symbol + text.substring(end)
        val newSelection = androidx.compose.ui.text.TextRange(start + symbol.length)
        
        val newValue = TextFieldValue(text = newText, selection = newSelection)
        textFieldValue = newValue
        onCodeChanged(newText)
    }

    val autocompleteSymbols = listOf(
        "{ }", "( )", "[ ]", ";", "=", "=>", "const ", "let ", "function ", 
        "console.log(", "document.getElementById(", "Math.random()", 
        "JSON.stringify(", "JSON.parse(", "async ", "await ", "if (", "else "
    )

    Column(modifier = modifier.fillMaxSize()) {
        // Autocomplete & Quick Symbols Bar
        if (isAutocompleteEnabled) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(sidebarBgColor)
                    .horizontalScroll(autocompleteScrollState)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                autocompleteSymbols.forEach { symbol ->
                    val cleanLabel = when (symbol) {
                        "{ }" -> "{ }"
                        "( )" -> "( )"
                        "[ ]" -> "[ ]"
                        else -> symbol.trim()
                    }
                    Button(
                        onClick = {
                            val insertion = when (symbol) {
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
                    .padding(vertical = 12.dp)
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
                    .padding(horizontal = 12.dp, vertical = 12.dp)
            ) {
                BasicTextField(
                    value = textFieldValue,
                    onValueChange = { newValue ->
                        textFieldValue = newValue
                        onCodeChanged(newValue.text)
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
                        .width(3000.dp)
                        .wrapContentHeight()
                )
            }
        }
    }
}
