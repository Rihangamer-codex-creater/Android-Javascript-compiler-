package com.example

import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.example.ui.IdeViewModel
import com.example.ui.MainLayout
import com.example.ui.theme.MyApplicationTheme

/**
 * Main Application Activity.
 * Adheres strictly to clean OOP design principles: Acts solely as the application
 * lifecycle controller and bootstraps the modular UI.
 */
class MainActivity : ComponentActivity() {
    private val viewModel: IdeViewModel by viewModels()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        viewModel.checkStoragePermission(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initial permission check
        viewModel.checkStoragePermission(this)

        setContent {
            val isDarkMode = viewModel.isDarkMode
            val systemBgColor = if (isDarkMode) 0xFF1C1B1F.toInt() else 0xFFFAFAFA.toInt()
            window.decorView.setBackgroundColor(systemBgColor)

            MyApplicationTheme(darkTheme = isDarkMode) {
                MainLayout(viewModel)
            }
        }

        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        viewModel.checkStoragePermission(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra("request_legacy_permissions", false) == true) {
            requestPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            )
        }
    }

    override fun onPause() {
        super.onPause()
        viewModel.saveCurrentFileImmediately()
    }

    override fun onStop() {
        super.onStop()
        viewModel.saveCurrentFileImmediately()
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.voiceAssistant.shutdown()
    }
}
