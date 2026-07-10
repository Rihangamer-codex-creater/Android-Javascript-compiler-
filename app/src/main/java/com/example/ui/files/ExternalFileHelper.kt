package com.example.ui.files

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log

/**
 * Clean OOP helper class representing an External File System interface.
 * Handles reading, writing, naming, and language determination for Android URIs.
 */
object ExternalFileHelper {
    private const val TAG = "ExternalFileHelper"

    /**
     * Reads text content from a shared storage Uri (SAF).
     */
    fun readTextFromUri(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                inputStream.bufferedReader().use { it.readText() }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read content from URI: $uri", e)
            null
        }
    }

    /**
     * Writes text content directly to a shared storage Uri (SAF) with "rwt" (truncate write) mode.
     */
    fun writeTextToUri(context: Context, uri: Uri, content: String): Boolean {
        return try {
            context.contentResolver.openOutputStream(uri, "rwt")?.use { outputStream ->
                outputStream.bufferedWriter().use { 
                    it.write(content)
                    it.flush()
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write content to URI: $uri", e)
            false
        }
    }

    /**
     * Extracts the user-visible display name from a system URI.
     */
    fun getFileNameFromUri(context: Context, uri: Uri): String {
        var name = "Untitled.js"
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    name = cursor.getString(nameIndex)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to query filename for URI: $uri", e)
        }
        return name
    }

    /**
     * Guesses the code language from the file extension.
     */
    fun detectLanguage(fileName: String): String {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return when (extension) {
            "html", "htm" -> "html"
            "css" -> "css"
            "js", "mjs", "jsx", "ts" -> "javascript"
            else -> "javascript"
        }
    }
}
