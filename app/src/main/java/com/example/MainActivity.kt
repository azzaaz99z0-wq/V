package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.ui.StreamVaultScreen
import com.example.ui.theme.StreamVaultTheme
import com.example.viewmodel.MediaExtractorViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MediaExtractorViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val uiState by viewModel.uiState.collectAsState()
            StreamVaultTheme(themeMode = uiState.themeMode) {
                StreamVaultScreen(viewModel = viewModel)
            }
        }
    }
}
