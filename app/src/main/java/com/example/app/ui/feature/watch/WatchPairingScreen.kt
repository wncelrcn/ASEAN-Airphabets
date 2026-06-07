package com.example.app.ui.feature.watch

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.app.R
import com.example.app.service.WatchConnectionManager
import com.example.app.ui.components.PrimaryButton
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun WatchPairingScreen(
    modifier: Modifier = Modifier,
    viewModel: WatchPairingViewModel = viewModel(),
    onProceedToDashboard: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedDeviceIndex by remember { mutableStateOf(0) }
    var isConnecting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Image(
            painter = painterResource(id = R.drawable.dis_pairing),
            contentDescription = "Watch pairing illustration",
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .padding(horizontal = 32.dp),
            contentScale = ContentScale.Fit
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Connect your\nSmartwatch",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF49A9FF),
            textAlign = TextAlign.Center,
            lineHeight = 40.sp,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Make sure that the smartwatch you want to add has Bluetooth turned on. Make sure that you have the Airphabets Wearable App installed.",
            fontSize = 14.sp,
            color = Color(0xFF2D2D2D),
            textAlign = TextAlign.Center,
            lineHeight = 20.sp,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(32.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Paired Devices",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF49A9FF)
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable(enabled = !uiState.isLoading) { 
                    viewModel.checkWatchConnection() 
                }
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = Color(0xFF49A9FF)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = Color(0xFF49A9FF),
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Refresh",
                    fontSize = 16.sp,
                    color = Color(0xFF2D2D2D)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Show connected devices or encouragement message
        if (uiState.connectedDevices.isEmpty() && uiState.hasCheckedConnection) {
            // No devices connected - show encouragement
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFF8F9FA)
                ),
                shape = MaterialTheme.shapes.medium
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_watch),
                        contentDescription = "No watch",
                        modifier = Modifier.size(48.dp),
                        tint = Color(0xFF49A9FF).copy(alpha = 0.5f)
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "No Smartwatch Connected",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF2D2D2D),
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "Connect your Galaxy Watch to track your learning progress, receive notifications, and access quick features on your wrist!",
                        fontSize = 14.sp,
                        color = Color(0xFF666666),
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )
                }
            }
        } else if (!uiState.hasCheckedConnection && uiState.isLoading) {
            // Initial loading
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color(0xFF49A9FF))
            }
        } else {
            // Show connected devices
            uiState.connectedDevices.forEachIndexed { index, device ->
                DeviceListItem(
                    deviceName = device.name,
                    isSelected = index == selectedDeviceIndex,
                    isConnected = device.isConnected,
                    batteryPercentage = device.batteryPercentage,
                    onClick = { selectedDeviceIndex = index }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        // Show error if any
        uiState.error?.let { error ->
            Text(
                text = error,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Dynamic button based on connection state
        val hasConnectedWatch = uiState.connectedDevices.isNotEmpty()
        val buttonText = if (hasConnectedWatch) "Proceed & Connect" else "Skip to Dashboard"
        val context = LocalContext.current
        
        PrimaryButton(
            text = buttonText,
            onClick = {
                if (hasConnectedWatch) {
                    // Watch is connected - request battery before proceeding
                    isConnecting = true
                    scope.launch {
                        try {
                            // Get watch manager and request battery
                            val watchManager = WatchConnectionManager.getInstance(context)
                            watchManager.requestBatteryFromConnectedWatch()
                            
                            // Wait for battery response (give it 3 seconds)
                            delay(3000L)
                            
                            // Now proceed to dashboard
                            isConnecting = false
                            onProceedToDashboard()
                        } catch (e: Exception) {
                            e.printStackTrace()
                            isConnecting = false
                            onProceedToDashboard()
                        }
                    }
                } else {
                    // No watch connected - just skip to dashboard
                    onProceedToDashboard()
                }
            },
            enabled = !isConnecting && !uiState.isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        )
        
        // Show loading text when connecting
        if (isConnecting) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Requesting battery data...",
                fontSize = 12.sp,
                color = Color(0xFF666666),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun DeviceListItem(
    deviceName: String,
    isSelected: Boolean,
    isConnected: Boolean = true,
    batteryPercentage: Int? = null,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFFE9FCFF) else Color(0xFFF8F9FA)
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_watch),
                contentDescription = "Watch",
                modifier = Modifier.size(40.dp),
                tint = if (isSelected) Color(0xFF49A9FF) else Color.Black
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = deviceName,
                    fontSize = 18.sp,
                    color = if (isSelected) Color(0xFF49A9FF) else Color(0xFF2D2D2D),
                    fontWeight = FontWeight.Medium
                )
                
                if (isConnected) {
                    Text(
                        text = "Connected",
                        fontSize = 12.sp,
                        color = Color(0xFF4CAF50),
                        fontWeight = FontWeight.Normal
                    )
                }
            }
            
            if (isConnected && batteryPercentage != null) {
                Text(
                    text = "$batteryPercentage%",
                    fontSize = 14.sp,
                    color = if (isSelected) Color(0xFF49A9FF) else Color(0xFF666666),
                    fontWeight = FontWeight.Normal
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun WatchPairingScreenPreview() {
    WatchPairingScreen()
}

