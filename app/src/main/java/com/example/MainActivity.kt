package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import com.example.ui.*
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    private lateinit var viewModel: AppViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Robust singleton ViewModel instantiation
        viewModel = ViewModelProvider(this)[AppViewModel::class.java]

        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    val currentScreen by viewModel.currentScreen.collectAsState()

                    Crossfade(
                        targetState = currentScreen,
                        label = "ScreenTransition",
                        modifier = Modifier.padding(innerPadding)
                    ) { screen ->
                        when (screen) {
                            HdrScreen.HOME -> HomeScreen(viewModel = viewModel)
                            HdrScreen.CAPTURE -> CaptureScreen(viewModel = viewModel)
                            HdrScreen.PROCESSING -> ProcessingScreen(viewModel = viewModel)
                            HdrScreen.PREVIEW -> PreviewScreen(viewModel = viewModel)
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::viewModel.isInitialized && viewModel.currentScreen.value == HdrScreen.CAPTURE) {
            viewModel.startSensors()
        }
    }

    override fun onPause() {
        super.onPause()
        if (::viewModel.isInitialized) {
            viewModel.stopSensors()
        }
    }
}
