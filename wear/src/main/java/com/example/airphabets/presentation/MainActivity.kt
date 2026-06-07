package com.example.airphabets.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.tooling.preview.devices.WearDevices
import com.example.airphabets.presentation.navigation.AppNavigation
import com.example.airphabets.presentation.screens.splash.CustomSplashScreen
import com.example.airphabets.presentation.service.PhoneCommunicationManager

/**
 * Main Activity - Entry point for the Kusho Wear OS app
 *
 * Responsibilities:
 * - Activity lifecycle management
 * - Window configuration
 * - App initialization
 * - Phone communication management
 */
class MainActivity : ComponentActivity() {
    
    private lateinit var phoneCommunicationManager: PhoneCommunicationManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize phone communication manager
        phoneCommunicationManager = PhoneCommunicationManager(this)
        phoneCommunicationManager.startBatteryMonitoring()
        phoneCommunicationManager.sendInitialInfo()

        // Set black window background to prevent system icon flash
        window.setBackgroundDrawableResource(android.R.color.black)

        setContent {
            KushoWearApp()
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Resume battery monitoring when app comes to foreground
        phoneCommunicationManager.startBatteryMonitoring()
    }
    
    override fun onPause() {
        super.onPause()
        // Pause battery monitoring when app goes to background
        phoneCommunicationManager.stopBatteryMonitoring()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        phoneCommunicationManager.cleanup()
    }
}

/**
 * Main composable for the Kusho Wear app
 *
 * Handles splash screen display and navigation initialization
 */
@Composable
fun KushoWearApp() {
    var showSplash by remember { mutableStateOf(true) }

    MaterialTheme {
        if (showSplash) {
            CustomSplashScreen(onTimeout = { showSplash = false })
        } else {
            AppNavigation()
        }
    }
}

/**
 * Preview for the Kusho Wear app
 */
@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true)
@Composable
fun DefaultPreview() {
    KushoWearApp()
}

