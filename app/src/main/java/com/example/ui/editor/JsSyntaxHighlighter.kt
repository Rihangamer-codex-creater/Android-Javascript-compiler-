package com.example.ui.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import java.util.regex.Pattern

class JsSyntaxHighlighter(private val isDarkMode: Boolean) : VisualTransformation {

    companion object {
        // Pre-compile regexes so they are not rebuilt on every character stroke
        private val keywordsPattern = Pattern.compile(
            "\\b(break|case|catch|class|const|continue|debugger|default|delete|do|else|export|extends|finally|for|function|if|import|in|instanceof|new|return|super|switch|this|throw|try|typeof|var|void|while|with|yield|let|await|async)\\b"
        )
        private val builtInPattern = Pattern.compile(
            "\\b(console|window|document|Math|JSON|Promise|Set|Map|Array|Object|String|Number|Boolean|Date|Error|undefined|null|true|false|require|process)\\b"
        )
        private val stringPattern = Pattern.compile(
            "\"(\\\\.|[^\"])*\"|'(\\\\.|[^'])*'|`(\\\\.|[^`])*`"
        )
        private val numberPattern = Pattern.compile(
            "\\b(0x[0-9a-fA-F]+|\\d+(\\.\\d*)?([eE][+-]?\\d+)?)\\b"
        )
        private val commentPattern = Pattern.compile(
            "//.*|/\\*[\\s\\S]*?\\*/"
        )
        private val functionPattern = Pattern.compile(
            "\\b([a-zA-Z_]\\w*)\\s*(?=\\()"
        )
    }

    // Colors for Dark Theme (Monokai-ish)
    private val darkKeywordColor = Color(0xFFFF79C6) // Coral Pink
    private val darkStringColor = Color(0xFF50FA7B) // Bright Green
    private val darkNumberColor = Color(0xBD8BE9FD) // Cyan
    private val darkCommentColor = Color(0xFF6272A4) // Muted Gray-Blue
    private val darkBuiltInColor = Color(0xFF8BE9FD) // Cyan
    private val darkFunctionColor = Color(0xFFF1FA8C) // Pale Yellow
    private val darkNormalColor = Color(0xFFF8F8F2) // Off-white

    // Colors for Light Theme (Github-ish)
    private val lightKeywordColor = Color(0xFFD73A49) // Deep Red
    private val lightStringColor = Color(0xFF22863A) // Forest Green
    private val lightNumberColor = Color(0xFF005CC5) // Blue
    private val lightCommentColor = Color(0xFF6A737D) // Cool Gray
    private val lightBuiltInColor = Color(0xFF6F42C1) // Royal Purple
    private val lightFunctionColor = Color(0xFFE36209) // Warm Orange
    private val lightNormalColor = Color(0xFF24292E) // Dark slate

    override fun filter(text: AnnotatedString): TransformedText {
        val highlighted = highlight(text.text)
        return TransformedText(highlighted, OffsetMapping.Identity)
    }

    private fun highlight(code: String): AnnotatedString {
        val builder = AnnotatedString.Builder(code)
        
        // Use normal color as fallback base style
        val normalColor = if (isDarkMode) darkNormalColor else lightNormalColor
        builder.addStyle(
            SpanStyle(color = normalColor, fontFamily = FontFamily.Monospace),
            0,
            code.length
        )

        val keywordColor = if (isDarkMode) darkKeywordColor else lightKeywordColor
        val stringColor = if (isDarkMode) darkStringColor else lightStringColor
        val numberColor = if (isDarkMode) darkNumberColor else lightNumberColor
        val commentColor = if (isDarkMode) darkCommentColor else lightCommentColor
        val builtInColor = if (isDarkMode) darkBuiltInColor else lightBuiltInColor
        val functionColor = if (isDarkMode) darkFunctionColor else lightFunctionColor

        // Apply string colors (high priority so comments inside strings don't conflict, but we do them in order)
        // Let's run matches and add style spans

        // Function calls
        val funcMatcher = functionPattern.matcher(code)
        while (funcMatcher.find()) {
            builder.addStyle(SpanStyle(color = functionColor, fontWeight = FontWeight.Bold), funcMatcher.start(), funcMatcher.end())
        }

        // Keywords
        val kwMatcher = keywordsPattern.matcher(code)
        while (kwMatcher.find()) {
            builder.addStyle(SpanStyle(color = keywordColor, fontWeight = FontWeight.Bold), kwMatcher.start(), kwMatcher.end())
        }

        // Built-ins
        val biMatcher = builtInPattern.matcher(code)
        while (biMatcher.find()) {
            builder.addStyle(SpanStyle(color = builtInColor, fontWeight = FontWeight.SemiBold), biMatcher.start(), biMatcher.end())
        }

        // Numbers
        val numMatcher = numberPattern.matcher(code)
        while (numMatcher.find()) {
            builder.addStyle(SpanStyle(color = numberColor), numMatcher.start(), numMatcher.end())
        }

        // Strings
        val strMatcher = stringPattern.matcher(code)
        while (strMatcher.find()) {
            builder.addStyle(SpanStyle(color = stringColor), strMatcher.start(), strMatcher.end())
        }

        // Comments (highest priority over keywords and strings)
        val commentMatcher = commentPattern.matcher(code)
        while (commentMatcher.find()) {
            builder.addStyle(SpanStyle(color = commentColor, fontFamily = FontFamily.Monospace), commentMatcher.start(), commentMatcher.end())
        }

        return builder.toAnnotatedString()
    }
}
