package com.example.ui.editor

object CodeFormatter {
    fun format(code: String, language: String): String {
        val trimmed = code.trim()
        if (trimmed.isEmpty()) return ""

        val lines = trimmed.split("\n")
        val formattedLines = mutableListOf<String>()
        var indentLevel = 0
        val tabSize = 4
        val indentSpace = " ".repeat(tabSize)

        // Simple tag list for HTML
        val selfClosingTags = setOf("img", "br", "hr", "meta", "link", "input", "col", "base")

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty()) {
                formattedLines.add("")
                continue
            }

            when (language.lowercase()) {
                "html" -> {
                    // Check if it's a closing tag
                    val isClosing = line.startsWith("</")
                    if (isClosing && indentLevel > 0) {
                        indentLevel--
                    }

                    val currentIndent = indentSpace.repeat(indentLevel)
                    formattedLines.add(currentIndent + line)

                    // Check if we should indent the next line
                    // Starts with <, not </, not <! (doctype/comment), doesn't end with /> and is not self-closing
                    val isOpening = line.startsWith("<") && 
                                    !line.startsWith("</") && 
                                    !line.startsWith("<!") && 
                                    !line.startsWith("<?") &&
                                    !line.endsWith("/>") && 
                                    !line.contains("</")

                    if (isOpening) {
                        // Extract tag name
                        val tagName = line.substringAfter("<").substringBefore(" ").substringBefore(">").lowercase().trim('/')
                        if (tagName.isNotEmpty() && !selfClosingTags.contains(tagName)) {
                            indentLevel++
                        }
                    }
                }
                else -> { // "javascript", "css"
                    // Count closing braces in this line
                    val closeBraces = line.count { it == '}' || it == ']' || it == ')' }
                    val openBraces = line.count { it == '{' || it == '[' || it == '(' }

                    val netBraceChange = openBraces - closeBraces

                    // If line starts with a closing brace, decrease indent immediately
                    val startsWithClose = line.startsWith("}") || line.startsWith("]") || line.startsWith(")")
                    if (startsWithClose && indentLevel > 0) {
                        indentLevel--
                    } else if (closeBraces > openBraces && indentLevel > 0) {
                        // More closing braces than opening braces
                        indentLevel = (indentLevel + netBraceChange).coerceAtLeast(0)
                    }

                    val currentIndent = indentSpace.repeat(indentLevel)
                    formattedLines.add(currentIndent + line)

                    // Adjust for opening braces
                    if (!startsWithClose) {
                        if (netBraceChange > 0) {
                            indentLevel += netBraceChange
                        }
                    } else {
                        if (openBraces > 0) {
                            indentLevel += openBraces
                        }
                    }
                }
            }
        }

        return formattedLines.joinToString("\n")
    }
}
