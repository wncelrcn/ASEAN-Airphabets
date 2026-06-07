package com.example.airphabets.presentation.pairing

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.Text
import com.example.airphabets.R
import com.example.airphabets.presentation.service.ConnectionMonitor
import kotlinx.coroutines.delay

/**
 * Watch Pairing Screen
 * Shows different states: Prompt, Checking, Success, Error
 */
@Composable
fun PairingScreen(
    onPairingComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val viewModel: PairingViewModel = viewModel(
        factory = PairingViewModelFactory(context)
    )
    
    // Get connection monitor and stop global monitoring while on pairing screen
    val connectionMonitor = ConnectionMonitor.getInstance(context)
    
    val pairingState by viewModel.pairingState.collectAsState()
    
    // Stop global monitoring while on pairing screen
    LaunchedEffect(Unit) {
        connectionMonitor.stopMonitoring()
    }
    
    // Navigate to home when pairing is successful
    LaunchedEffect(pairingState) {
        if (pairingState is PairingState.Success) {
            // Wait a bit to show success animation
            delay(3000)
            // Reset connection monitor state after successful pairing
            connectionMonitor.setConnected()
            // Navigate to home - monitoring will start there
            onPairingComplete()
        }
    }
    
    // No auto-monitoring needed - user initiates via button
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        when (pairingState) {
            is PairingState.Prompt -> {
                PromptContent(onConnectToPhone = { viewModel.connectToPhone() })
            }
            is PairingState.Checking -> {
                CheckingContent()
            }
            is PairingState.Success -> {
                SuccessContent()
            }
            is PairingState.Error -> {
                ErrorContent(
                    message = (pairingState as PairingState.Error).message,
                    onRetry = { viewModel.retry() },
                    onSkip = {
                        viewModel.skipPairing()
                        onPairingComplete()
                    }
                )
            }
            is PairingState.BluetoothOff -> {
                BluetoothOffContent()
            }
            is PairingState.MaxRetriesReached -> {
                MaxRetriesContent(
                    attemptCount = (pairingState as PairingState.MaxRetriesReached).attemptCount,
                    onTryAgain = { viewModel.restartMonitoring() },
                    onSkip = { 
                        viewModel.skipPairing()
                        onPairingComplete()
                    }
                )
            }
            is PairingState.WaitingForAcceptance -> {
                WaitingForAcceptanceContent()
            }
            is PairingState.Declined -> {
                DeclinedContent(onRetry = { viewModel.retry() })
            }
        }
    }
}

/**
 * Prompt state - "Connect to Phone" button
 */
@Composable
private fun PromptContent(
    onConnectToPhone: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Phone icon with mascot
        Image(
            painter = painterResource(id = R.drawable.dis_pairing_m),
            contentDescription = "Pairing prompt",
            modifier = Modifier
                .size(80.dp)
                .padding(bottom = 8.dp),
            contentScale = ContentScale.Fit
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        // Instructions
        Text(
            text = "Pair with Phone",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Connect to Phone button
        Button(
            onClick = onConnectToPhone,
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(40.dp),
            colors = ButtonDefaults.buttonColors(
                backgroundColor = Color(0xFF4A90E2)
            )
        ) {
            Text(
                text = "Connect to Phone",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
        }
    }
}

/**
 * Checking state - Loading animation
 */
@Composable
private fun CheckingContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Circular progress indicator
        CircularProgressIndicator(
            modifier = Modifier.size(60.dp),
            strokeWidth = 4.dp,
            indicatorColor = Color(0xFF49A9FF)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Connecting...",
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal,
            color = Color.White,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Success state - "Paired Successfully!"
 */
@Composable
private fun SuccessContent() {
    // Create sparkle animation
    val infiniteTransition = rememberInfiniteTransition(label = "sparkle")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Success mascot with sparkles
        Box(
            contentAlignment = Alignment.Center
        ) {
            // Sparkles background (you can add actual sparkle decorations here)
            Image(
                painter = painterResource(id = R.drawable.dis_success),
                contentDescription = "Success",
                modifier = Modifier
                    .size(100.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    },
                contentScale = ContentScale.Fit
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Paired\nSuccessfully!",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF49A9FF),
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )
    }
}

/**
 * Error state - Connection lost or error
 */
@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit,
    onSkip: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Error icon
        Image(
            painter = painterResource(id = R.drawable.dis_remove),
            contentDescription = "Error",
            modifier = Modifier.size(50.dp),
            contentScale = ContentScale.Fit
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Connection Lost",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFFF6B6B),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = message,
            fontSize = 10.sp,
            color = Color(0xFFAAAAAA),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Retry button
        Button(
            onClick = onRetry,
            modifier = Modifier
                .fillMaxWidth(0.75f)
                .height(38.dp),
            colors = ButtonDefaults.buttonColors(
                backgroundColor = Color(0xFF4A90E2)
            )
        ) {
            Text(
                text = "Retry",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Skip button
        Button(
            onClick = onSkip,
            modifier = Modifier
                .fillMaxWidth(0.75f)
                .height(32.dp),
            colors = ButtonDefaults.buttonColors(
                backgroundColor = Color(0xFF3A3A3A)
            )
        ) {
            Text(
                text = "Skip",
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal,
                color = Color(0xFFBBBBBB)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Practice Mode only",
            fontSize = 10.sp,
            fontWeight = FontWeight.Normal,
            color = Color(0xFFBBBBBB)
        )
    }
}

/**
 * Max Retries Reached state - Show skip and try again options
 */
@Composable
private fun MaxRetriesContent(
    attemptCount: Int,
    onTryAgain: () -> Unit,
    onSkip: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Error icon
        Image(
            painter = painterResource(id = R.drawable.dis_remove),
            contentDescription = "Connection failed",
            modifier = Modifier.size(50.dp),
            contentScale = ContentScale.Fit
        )
        
        Spacer(modifier = Modifier.height(4.dp))

        // Main error message
        Text(
            text = "Unable to Pair",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFFF6B6B),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(2.dp))

        // Helpful hint
        Text(
            text = "Open Airphabets on phone",
            fontSize = 10.sp,
            color = Color(0xFFAAAAAA),
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(10.dp))

        // Primary action - Retry (larger, more prominent)
        Button(
            onClick = onTryAgain,
            modifier = Modifier
                .fillMaxWidth(0.75f)
                .height(38.dp),
            colors = ButtonDefaults.buttonColors(
                backgroundColor = Color(0xFF4A90E2)
            )
        ) {
            Text(
                text = "Try Again",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Secondary action - Skip (smaller, less prominent)
        Button(
            onClick = onSkip,
            modifier = Modifier
                .fillMaxWidth(0.75f)
                .height(32.dp),
            colors = ButtonDefaults.buttonColors(
                backgroundColor = Color(0xFF3A3A3A)
            )
        ) {
            Text(
                text = "Skip",
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal,
                color = Color(0xFFBBBBBB)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Practice Mode only",
            fontSize = 10.sp,
            fontWeight = FontWeight.Normal,
            color = Color(0xFFBBBBBB)
        )

    }
}

/**
 * Bluetooth Off state
 */
@Composable
private fun BluetoothOffContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.dis_remove),
            contentDescription = "Bluetooth Off",
            modifier = Modifier
                .size(100.dp)
                .padding(bottom = 16.dp),
            contentScale = ContentScale.Fit
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Bluetooth is Off",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFFF6B6B),
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Turn on Bluetooth\nto connect",
            fontSize = 12.sp,
            fontWeight = FontWeight.Normal,
            color = Color.White,
            textAlign = TextAlign.Center,
            lineHeight = 16.sp
        )
    }
}

/**
 * Waiting for phone to accept pairing request
 */
@Composable
private fun WaitingForAcceptanceContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(50.dp),
            strokeWidth = 4.dp,
            indicatorColor = Color(0xFF49A9FF)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Waiting for\nphone to accept...",
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal,
            color = Color.White,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp
        )
    }
}

/**
 * Phone declined the pairing request
 */
@Composable
private fun DeclinedContent(
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.dis_remove),
            contentDescription = "Declined",
            modifier = Modifier.size(80.dp),
            contentScale = ContentScale.Fit
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Request Declined",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFFF6B6B),
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Button(
            onClick = onRetry,
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .height(36.dp),
            colors = ButtonDefaults.buttonColors(
                backgroundColor = Color(0xFF4A90E2)
            )
        ) {
            Text(
                text = "Try Again",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
        }
    }
}
