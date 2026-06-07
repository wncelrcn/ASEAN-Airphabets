package com.example.airphabets.presentation.screens.home

import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Text
import com.example.airphabets.R
import com.example.airphabets.presentation.service.ConnectionMonitor
import com.example.airphabets.presentation.components.drawModeArc
import com.example.airphabets.presentation.navigation.NavigationRoutes
import com.example.airphabets.presentation.theme.AppColors
import com.example.airphabets.presentation.pairing.PairingViewModel
import com.example.airphabets.presentation.utils.ArcConstants
import com.example.airphabets.presentation.utils.ArcHelper

/**
 * Home screen displaying the Kusho logo with three mode arcs
 */
@Composable
fun HomeScreen(navController: NavHostController) {
    val density = LocalDensity.current
    val context = LocalContext.current
    
    // Check if pairing was skipped
    val prefs = context.getSharedPreferences(PairingViewModel.PREFS_NAME, android.content.Context.MODE_PRIVATE)
    val isSkipped = prefs.getBoolean("is_skipped", false)
    val isPaired = prefs.getBoolean("is_paired", false)
    
    // Monitor connection state if paired and not skipped
    val connectionMonitor = remember { ConnectionMonitor.getInstance(context) }
    val isConnected by connectionMonitor.isConnected.collectAsState()
    
    // Start monitoring when entering home screen if paired
    LaunchedEffect(isPaired, isSkipped) {
        if (isPaired && !isSkipped) {
            Log.d("HomeScreen", "Starting connection monitoring")
            connectionMonitor.startMonitoring()
        }
    }
    

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Background)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val centerX = size.width / 2f
                    val centerY = size.height / 2f
                    val radius = size.width / ArcConstants.RADIUS_FACTOR

                    // Calculate which arc was tapped
                    val tappedMode = ArcHelper.getTappedMode(
                        tapX = offset.x,
                        tapY = offset.y,
                        centerX = centerX,
                        centerY = centerY,
                        radius = radius
                    )

                    // Navigate to the appropriate mode screen
                    when (tappedMode) {
                        "practice" -> navController.navigate(NavigationRoutes.PRACTICE_MODE)
                        "tutorial" -> {
                            // Only allow if not skipped
                            if (!isSkipped) {
                                navController.navigate(NavigationRoutes.TUTORIAL_MODE)
                            }
                        }
                        "learn" -> {
                            // Only allow if not skipped
                            if (!isSkipped) {
                                navController.navigate(NavigationRoutes.LEARN_MODE)
                            }
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // Show pairing button at top if skipped
        if (isSkipped) {
            PairingIndicator(
                modifier = Modifier.align(Alignment.TopCenter),
                onClick = {
                    // Clear skipped status and navigate to pairing
                    prefs.edit().putBoolean("is_skipped", false).apply()
                    navController.navigate(NavigationRoutes.PAIRING) {
                        popUpTo(NavigationRoutes.HOME) { inclusive = true }
                    }
                }
            )
        }
        // Center logo
        Image(
            painter = painterResource(id = R.drawable.ic_kusho),
            contentDescription = "Kusho Logo",
            modifier = Modifier.size(ArcConstants.LOGO_SIZE_DP.dp),
            contentScale = ContentScale.Fit
        )

        // Draw mode arcs
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerX = size.width / 2f
            val centerY = size.height / 2f
            val radius = size.width / ArcConstants.RADIUS_FACTOR

            // Draw all three mode arcs
            drawModeArc(
                centerX = centerX,
                centerY = centerY,
                radius = radius,
                centerAngle = ArcConstants.PRACTICE_MODE_ANGLE,
                text = "Practice Mode",
                color = AppColors.PracticeModeColor,
                density = density
            )

            drawModeArc(
                centerX = centerX,
                centerY = centerY,
                radius = radius,
                centerAngle = ArcConstants.TUTORIAL_MODE_ANGLE,
                text = "Tutorial Mode",
                color = if (isSkipped) Color(0xFF555555) else AppColors.TutorialModeColor,
                density = density
            )

            drawModeArc(
                centerX = centerX,
                centerY = centerY,
                radius = radius,
                centerAngle = ArcConstants.LEARN_MODE_ANGLE,
                text = "Learn Mode",
                color = if (isSkipped) Color(0xFF555555) else AppColors.LearnModeColor,
                density = density
            )
        }

    }
}

/**
 * Pairing indicator shown when user skipped pairing
 */
@Composable
private fun PairingIndicator(
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .padding(top = 8.dp)
            .size(44.dp),
        colors = ButtonDefaults.buttonColors(
            backgroundColor = Color(0xFF4A90E2)
        )
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_refresh),
            contentDescription = "Pair device",
            modifier = Modifier.size(24.dp),
            contentScale = ContentScale.Fit
        )
    }
}
