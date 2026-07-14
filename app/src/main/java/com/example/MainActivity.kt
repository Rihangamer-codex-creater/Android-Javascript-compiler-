package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val isDarkMode = viewModel.isDarkMode
            val systemBgColor = if (isDarkMode) 0xFF1C1B1F.toInt() else 0xFFFAFAFA.toInt()
            window.decorView.setBackgroundColor(systemBgColor)

            MyApplicationTheme(darkTheme = isDarkMode) {
                MainLayout(viewModel)
            }
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
