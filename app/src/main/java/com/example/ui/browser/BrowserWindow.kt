package com.example.ui.browser

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.view.ViewGroup
import android.webkit.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Web
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.NpmLibrary
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserWindow(
    codeBundle: String?,
    isDarkMode: Boolean,
    downloadedLibraries: List<NpmLibrary>,
    allFiles: List<com.example.data.ProgramFile>,
    onJsConsoleLog: (String, String) -> Unit,
    onAutoTerminate: () -> Unit,
    onOpenExternalBrowser: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var webView: WebView? by remember { mutableStateOf(null) }
    var currentUrl by remember { mutableStateOf("about:blank") }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }

    val barBgColor = if (isDarkMode) Color(0xFF1C1B1F) else Color(0xFFFAFAFA)
    val barTextColor = if (isDarkMode) Color(0xFFE6E1E5) else Color(0xFF1C1B1F)
    val addressBarBg = if (isDarkMode) Color(0xFF25232A) else Color(0xFFE2E8F0)

    // JS interface helper to redirect console log calls
    class WebConsoleInterface {
        @JavascriptInterface
        fun log(type: String, msg: String) {
            onJsConsoleLog(type, msg)
        }

        @JavascriptInterface
        fun autoTerminate() {
            onAutoTerminate()
        }
    }

    // Effect to reload / update when a new bundle is pushed
    LaunchedEffect(codeBundle) {
        if (codeBundle != null && webView != null) {
            webView?.loadDataWithBaseURL(
                "https://localhost/sandbox/", // Base URL to satisfy ESM imports
                codeBundle,
                "text/html",
                "UTF-8",
                null
            )
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Browser Toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(barBgColor)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Navigation Buttons
            IconButton(
                onClick = { webView?.goBack() },
                enabled = canGoBack,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Default.ArrowBack, 
                    contentDescription = "Back",
                    tint = if (canGoBack) barTextColor else barTextColor.copy(alpha = 0.3f)
                )
            }
            
            IconButton(
                onClick = { webView?.goForward() },
                enabled = canGoForward,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Default.ArrowForward, 
                    contentDescription = "Forward",
                    tint = if (canGoForward) barTextColor else barTextColor.copy(alpha = 0.3f)
                )
            }

            IconButton(
                onClick = { webView?.reload() },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = barTextColor)
            }

            if (onOpenExternalBrowser != null) {
                IconButton(
                    onClick = onOpenExternalBrowser,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.OpenInNew, contentDescription = "Open in External Browser", tint = barTextColor)
                }
            }

            // Address Bar
            Box(
                modifier = Modifier
                    .weight(1.0f)
                    .height(34.dp)
                    .background(addressBarBg, shape = MaterialTheme.shapes.small)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Default.Web, 
                        contentDescription = "Web", 
                        tint = barTextColor.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = currentUrl.replace("https://", ""),
                        fontSize = 12.sp,
                        color = barTextColor,
                        maxLines = 1
                    )
                }
            }
            
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Divider(color = if (isDarkMode) Color(0xFF49454F) else Color(0xFFE2E8F0), thickness = 1.dp)

        // Embedded Safe WebView Sandbox
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        allowContentAccess = true
                        allowFileAccess = true
                        databaseEnabled = true
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    }

                    // Enable debugging
                    WebView.setWebContentsDebuggingEnabled(true)

                    // Inject Bridge API
                    addJavascriptInterface(WebConsoleInterface(), "AndroidTerminal")

                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            super.onProgressChanged(view, newProgress)
                            isLoading = newProgress < 100
                        }
                    }

                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            super.onPageStarted(view, url, favicon)
                            currentUrl = url ?: "about:blank"
                            isLoading = true
                            canGoBack = view?.canGoBack() ?: false
                            canGoForward = view?.canGoForward() ?: false
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            currentUrl = url ?: "about:blank"
                            isLoading = false
                            canGoBack = view?.canGoBack() ?: false
                            canGoForward = view?.canGoForward() ?: false
                        }

                        // Intercept requests to serve downloaded libraries locally offline, or local sandbox multi-files!
                        override fun shouldInterceptRequest(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): WebResourceResponse? {
                            val urlStr = request?.url?.toString() ?: return null
                            
                            // 1. Check if it's a request for a local sandbox multi-file
                            if (urlStr.startsWith("https://localhost/sandbox/")) {
                                val relativePath = urlStr.substring("https://localhost/sandbox/".length)
                                val matchingFile = allFiles.find { file ->
                                    file.name.equals(relativePath, ignoreCase = true) ||
                                    "${file.folder}/${file.name}".replace("//", "/").trim('/')
                                        .equals(relativePath.trim('/'), ignoreCase = true) ||
                                    file.name.endsWith("/$relativePath", ignoreCase = true)
                                }
                                
                                if (matchingFile != null) {
                                    val mimeType = when {
                                        matchingFile.name.endsWith(".css", ignoreCase = true) -> "text/css"
                                        matchingFile.name.endsWith(".js", ignoreCase = true) || matchingFile.name.endsWith(".jsx", ignoreCase = true) -> "application/javascript"
                                        matchingFile.name.endsWith(".html", ignoreCase = true) -> "text/html"
                                        else -> "text/plain"
                                    }
                                    val contentBytes = matchingFile.content.toByteArray(StandardCharsets.UTF_8)
                                    val inputStream = ByteArrayInputStream(contentBytes)
                                    onJsConsoleLog("info", "⚡ Serving sandbox file: ${matchingFile.name}")
                                    return WebResourceResponse(
                                        mimeType,
                                        "UTF-8",
                                        inputStream
                                    )
                                }
                            }
                            
                            // 2. Check if the requested url belongs to an ESM/CDN package that we cached offline!
                            val matchingLib = downloadedLibraries.find { lib ->
                                lib.isDownloaded && lib.localContent != null && (
                                    urlStr.contains(lib.name) || urlStr.equals(lib.url, ignoreCase = true)
                                )
                            }
                            
                            if (matchingLib != null && matchingLib.localContent != null) {
                                val contentBytes = matchingLib.localContent.toByteArray(StandardCharsets.UTF_8)
                                val inputStream = ByteArrayInputStream(contentBytes)
                                onJsConsoleLog("info", "⚡ Serving cached NPM package locally: ${matchingLib.name}")
                                return WebResourceResponse(
                                    "application/javascript",
                                    "UTF-8",
                                    inputStream
                                )
                            }
                            return super.shouldInterceptRequest(view, request)
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: WebResourceError?
                        ) {
                            super.onReceivedError(view, request, error)
                            // Log network errors in the terminal
                            val failingUrl = request?.url?.toString() ?: ""
                            if (failingUrl.endsWith(".js") || failingUrl.contains("esm.sh")) {
                                onJsConsoleLog("error", "Failed to load script: $failingUrl - Error: ${error?.description}")
                            }
                        }
                    }

                    webView = this
                }
            },
            modifier = Modifier.weight(1.0f)
        )
    }
}
