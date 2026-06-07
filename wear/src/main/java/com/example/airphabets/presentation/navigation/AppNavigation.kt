package com.example.airphabets.presentation.navigation

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Text
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.example.airphabets.R
import com.example.airphabets.presentation.pairing.PairingScreen
import com.example.airphabets.presentation.pairing.PairingViewModel
import com.example.airphabets.presentation.screens.home.HomeScreen
import com.example.airphabets.presentation.practice.PracticeModeScreen
import com.example.airphabets.presentation.tutorial.TutorialModeScreen
import com.example.airphabets.presentation.learn.LearnModeScreen
import com.example.airphabets.presentation.service.ConnectionMonitor

/**
 * Navigation graph for the app
 */
@Composable
fun AppNavigation() {
    val navController = rememberSwipeDismissableNavController()
    val context = LocalContext.current
    
    // Get connection monitor instance
    val connectionMonitor = ConnectionMonitor.getInstance(context)
    val isConnected by connectionMonitor.isConnected.collectAsState()
    
    // Check if previously paired
    val prefs = context.getSharedPreferences(PairingViewModel.PREFS_NAME, android.content.Context.MODE_PRIVATE)
    val isPaired = prefs.getBoolean("is_paired", false)
    val isSkipped = prefs.getBoolean("is_skipped", false)
    
    // Start at pairing screen if not paired and not skipped, otherwise home
    val startDestination = if (isPaired || isSkipped) NavigationRoutes.HOME else NavigationRoutes.PAIRING
    
    // Note: Monitoring is now started by HomeScreen when entering after successful pairing
    
    // Handle connection loss - show retry/skip screen instead of auto-navigating
    // Note: Automatic navigation disabled to prevent crashes
    // Connection loss is now handled by showing MaxRetriesReached state
    
    // Cleanup on dispose
    DisposableEffect(Unit) {
        onDispose {
            connectionMonitor.stopMonitoring()
        }
    }
    
    // Navigate to pairing screen when connection lost (not on pairing screen)
    LaunchedEffect(isConnected) {
        val currentRoute = navController.currentBackStackEntry?.destination?.route
        if (!isConnected && isPaired && !isSkipped && currentRoute != NavigationRoutes.PAIRING) {
            // Connection lost - navigate to pairing screen
            connectionMonitor.clearPairingStatus()
            connectionMonitor.stopMonitoring()
            connectionMonitor.resetFailureCounter()
            navController.navigate(NavigationRoutes.PAIRING) {
                popUpTo(NavigationRoutes.HOME) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    SwipeDismissableNavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        // Pairing screen
        composable(NavigationRoutes.PAIRING) {
            PairingScreen(
                onPairingComplete = {
                    navController.navigate(NavigationRoutes.HOME) {
                        popUpTo(NavigationRoutes.PAIRING) { inclusive = true }
                    }
                }
            )
        }
        
        // Home screen with mode arcs
        composable(NavigationRoutes.HOME) {
            HomeScreen(navController = navController)
        }

        // Practice Mode screen with real-time inference
        composable(NavigationRoutes.PRACTICE_MODE) {
            PracticeModeScreen()
        }

        // Tutorial Mode screen
        composable(NavigationRoutes.TUTORIAL_MODE) {
            TutorialModeScreen()
        }

        // Learn Mode screen
        composable(NavigationRoutes.LEARN_MODE) {
            LearnModeScreen()
        }
    }
}
