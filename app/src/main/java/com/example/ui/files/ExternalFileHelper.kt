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

    /**
     * Detects standard MIME types for file saving.
     */
    fun detectMimeType(fileName: String): String {
        return when (fileName.substringAfterLast('.', "").lowercase()) {
            "html", "htm" -> "text/html"
            "css" -> "text/css"
            "js", "mjs", "jsx", "ts" -> "text/javascript"
            else -> "text/plain"
        }
    }

    /**
     * Saves a file directly to the device's public local Documents folder under "CommuteCodeSandbox"
     * bypassing Google Drive and SAF pickers completely.
     */
    fun saveFileToPublicDocuments(context: Context, fileName: String, content: String): Uri? {
        val resolver = context.contentResolver
        val contentValues = android.content.ContentValues().apply {
            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, detectMimeType(fileName))
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOCUMENTS + "/CommuteCodeSandbox")
                put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val collectionUri = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            android.provider.MediaStore.Files.getContentUri(android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            android.provider.MediaStore.Files.getContentUri("external")
        }

        var fileUri: Uri? = null
        try {
            fileUri = resolver.insert(collectionUri, contentValues)
            if (fileUri != null) {
                resolver.openOutputStream(fileUri)?.use { out ->
                    out.write(content.toByteArray(java.nio.charset.StandardCharsets.UTF_8))
                    out.flush()
                }
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(fileUri, contentValues, null, null)
                }
                return fileUri
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save file to public Documents directory", e)
        }
        return null
    }
}
