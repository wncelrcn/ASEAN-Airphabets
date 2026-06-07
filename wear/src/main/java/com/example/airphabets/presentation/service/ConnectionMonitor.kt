package com.example.airphabets.presentation.service

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.NodeClient
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.*
import com.example.airphabets.presentation.pairing.PairingViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

/**
 * Connection failure reasons
 */
enum class DisconnectionReason {
    BLUETOOTH_DISCONNECTED,  // Physical Bluetooth connection lost
    APP_NOT_RESPONDING,      // Bluetooth OK, but app not running/responding
    NONE                     // Connected
}

/**
 * Get user-friendly message for disconnection reason
 */
fun DisconnectionReason.toUserMessage(): String {
    return when (this) {
        DisconnectionReason.BLUETOOTH_DISCONNECTED -> "Phone disconnected via Bluetooth"
        DisconnectionReason.APP_NOT_RESPONDING -> "Kusho app not running on phone"
        DisconnectionReason.NONE -> "Connected"
    }
}

/**
 * Global connection monitor that continuously checks if mobile app is connected
 * Two-layer detection: 1) Physical Bluetooth 2) App status via PING-PONG
 * Provides a shared state that can be observed throughout the app
 */
class ConnectionMonitor private constructor(private val context: Context) {
    
    private val nodeClient: NodeClient = Wearable.getNodeClient(context)
    private val messageClient: MessageClient = Wearable.getMessageClient(context)
    private val capabilityClient: CapabilityClient = Wearable.getCapabilityClient(context)
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private val _isConnected = MutableStateFlow(true) // Start optimistic
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    
    private val _consecutiveFailures = MutableStateFlow(0)
    val consecutiveFailures: StateFlow<Int> = _consecutiveFailures.asStateFlow()
    
    private val _disconnectionReason = MutableStateFlow(DisconnectionReason.NONE)
    val disconnectionReason: StateFlow<DisconnectionReason> = _disconnectionReason.asStateFlow()
    
    private val _receivedPong = MutableStateFlow(false)
    
    private var monitoringJob: Job? = null
    private var lastKnownNodeId: String? = null
    
    companion object {
        @Volatile
        private var INSTANCE: ConnectionMonitor? = null
        private const val TAG = "ConnectionMonitor"
        private const val MESSAGE_PATH_PING = "/airphabets/ping"
        private const val MESSAGE_PATH_PONG = "/airphabets/pong"
        private const val PING_TIMEOUT_MS = 3000L
        private const val CHECK_INTERVAL_MS = 5000L // Check every 5 seconds
        
        fun getInstance(context: Context): ConnectionMonitor {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ConnectionMonitor(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }
    
    private val messageListener = MessageClient.OnMessageReceivedListener { messageEvent ->
        if (messageEvent.path == MESSAGE_PATH_PONG) {
            Log.d(TAG, "🏓 Connection verified - PONG received")
            _receivedPong.value = true
        }
    }
    
    // Real-time node listener for instant Bluetooth disconnection detection
    private val nodeListener = object : CapabilityClient.OnCapabilityChangedListener {
        override fun onCapabilityChanged(capabilityInfo: com.google.android.gms.wearable.CapabilityInfo) {
            // Capability changed - check if nodes are still available
            scope.launch {
                checkNodesStatus()
            }
        }
    }
    
    init {
        messageClient.addListener(messageListener)
        // Add capability listener for real-time node changes
        capabilityClient.addListener(nodeListener, "*")
        Log.d(TAG, "🔍 ConnectionMonitor initialized with real-time node listeners")
    }
    
    /**
     * Check nodes status - called by capability listener for real-time detection
     */
    private suspend fun checkNodesStatus() {
        try {
            val connectedNodes = nodeClient.connectedNodes.await()
            if (connectedNodes.isEmpty() && _isConnected.value) {
                Log.w(TAG, "⚡ REAL-TIME: Bluetooth disconnection detected!")
                updateConnectionState(false, DisconnectionReason.BLUETOOTH_DISCONNECTED)
            } else if (connectedNodes.isNotEmpty() && !_isConnected.value) {
                Log.d(TAG, "⚡ REAL-TIME: Phone reconnected via Bluetooth")
                // Don't set to connected yet - wait for PING-PONG verification
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking nodes status", e)
        }
    }
    
    /**
     * Start monitoring connection continuously
     */
    fun startMonitoring() {
        if (monitoringJob?.isActive == true) {
            Log.d(TAG, "⚠️ Monitoring already active")
            return
        }
        
        Log.d(TAG, "🚀 Starting continuous connection monitoring")
        monitoringJob = scope.launch {
            while (isActive) {
                checkConnection()
                delay(CHECK_INTERVAL_MS)
            }
        }
    }
    
    /**
     * Stop monitoring connection
     */
    fun stopMonitoring() {
        Log.d(TAG, "🛑 Stopping connection monitoring")
        monitoringJob?.cancel()
        monitoringJob = null
    }
    
    /**
     * Check connection with two-layer detection:
     * Layer 1: Check Bluetooth physical connection
     * Layer 2: Verify app is running via PING-PONG
     */
    private suspend fun checkConnection() {
        try {
            // LAYER 1: Check physical Bluetooth connection
            Log.d(TAG, "🔍 Layer 1: Checking Bluetooth connection...")
            val connectedNodes = nodeClient.connectedNodes.await()
            
            // Filter for nearby (actively connected) nodes only
            val nearbyNodes = connectedNodes.filter { it.isNearby }
            Log.d(TAG, "📊 Total paired nodes: ${connectedNodes.size}, Nearby (active): ${nearbyNodes.size}")
            
            if (nearbyNodes.isEmpty()) {
                Log.w(TAG, "❌ LAYER 1 FAILED: No phone connected via Bluetooth")
                updateConnectionState(false, DisconnectionReason.BLUETOOTH_DISCONNECTED)
                lastKnownNodeId = null
                return
            }
            
            val phoneNode = nearbyNodes.first()
            lastKnownNodeId = phoneNode.id
            Log.d(TAG, "✅ Layer 1 PASSED: Phone connected via Bluetooth (${phoneNode.displayName})")
            
            // LAYER 2: Verify Kusho app is running
            Log.d(TAG, "🔍 Layer 2: Verifying Kusho app is running...")
            _receivedPong.value = false
            
            messageClient.sendMessage(phoneNode.id, MESSAGE_PATH_PING, "ping".toByteArray()).await()
            Log.d(TAG, "📤 PING sent to ${phoneNode.displayName}")
            
            // Wait for pong with timeout
            val startTime = System.currentTimeMillis()
            while (!_receivedPong.value && System.currentTimeMillis() - startTime < PING_TIMEOUT_MS) {
                delay(100)
            }
            
            if (_receivedPong.value) {
                val responseTime = System.currentTimeMillis() - startTime
                Log.d(TAG, "✅ LAYER 2 PASSED: Kusho app responding (${responseTime}ms)")
                Log.d(TAG, "🟢 BOTH LAYERS PASSED: Fully connected")
                updateConnectionState(true, DisconnectionReason.NONE)
            } else {
                Log.w(TAG, "❌ LAYER 2 FAILED: Kusho app not responding (timeout after ${PING_TIMEOUT_MS}ms)")
                Log.w(TAG, "📱 Bluetooth: ✅ Connected | Kusho App: ❌ Not running/responding")
                updateConnectionState(false, DisconnectionReason.APP_NOT_RESPONDING)
            }
            
        } catch (e: kotlinx.coroutines.CancellationException) {
            Log.d(TAG, "⚠️ Connection check cancelled (monitoring stopped)")
            // Don't update state on cancellation - this is expected when stopping monitoring
            throw e  // Re-throw to properly cancel the coroutine
        } catch (e: com.google.android.gms.common.api.ApiException) {
            // Handle TARGET_NODE_NOT_CONNECTED (4000) - node disconnected while sending message
            when (e.statusCode) {
                4000 -> {
                    Log.w(TAG, "❌ TARGET_NODE_NOT_CONNECTED: Phone disconnected during message send")
                    updateConnectionState(false, DisconnectionReason.BLUETOOTH_DISCONNECTED)
                }
                else -> {
                    Log.e(TAG, "💥 ApiException during connection check: ${e.statusCode}", e)
                    updateConnectionState(false, DisconnectionReason.BLUETOOTH_DISCONNECTED)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "💥 Error during connection check", e)
            updateConnectionState(false, DisconnectionReason.APP_NOT_RESPONDING)
        }
    }
    
    /**
     * Update connection state with reason tracking
     * Note: Just updates state, navigation handled by screens
     */
    private fun updateConnectionState(connected: Boolean, reason: DisconnectionReason) {
        val previousState = _isConnected.value
        _isConnected.value = connected
        _disconnectionReason.value = reason
        
        if (previousState != connected) {
            if (connected) {
                Log.d(TAG, "🟢 CONNECTION RESTORED")
                _consecutiveFailures.value = 0 // Reset failure counter
            } else {
                _consecutiveFailures.value += 1
                val reasonText = when (reason) {
                    DisconnectionReason.BLUETOOTH_DISCONNECTED -> "Bluetooth disconnected"
                    DisconnectionReason.APP_NOT_RESPONDING -> "App not responding"
                    DisconnectionReason.NONE -> "Unknown"
                }
                Log.w(TAG, "🔴 CONNECTION LOST - Reason: $reasonText")
                Log.w(TAG, "🔴 Consecutive failures: ${_consecutiveFailures.value}")
                Log.w(TAG, "⚠️ User will be redirected to pairing screen")
            }
        }
    }
    
    /**
     * Clear pairing status from SharedPreferences
     */
    fun clearPairingStatus() {
        Log.d(TAG, "🗑️ Clearing pairing status")
        val prefs = context.getSharedPreferences(PairingViewModel.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean("is_paired", false).apply()
    }
    
    /**
     * Reset failure counter (used when user successfully pairs again)
     */
    fun resetFailureCounter() {
        Log.d(TAG, "🔄 Resetting failure counter")
        _consecutiveFailures.value = 0
    }
    
    /**
     * Set connection state to connected (used after successful pairing)
     */
    fun setConnected() {
        Log.d(TAG, "✅ Setting connection state to connected")
        _isConnected.value = true
        _consecutiveFailures.value = 0
        _disconnectionReason.value = DisconnectionReason.NONE
    }
    
    /**
     * Cleanup resources
     */
    fun cleanup() {
        Log.d(TAG, "🧹 Cleaning up ConnectionMonitor")
        stopMonitoring()
        messageClient.removeListener(messageListener)
        capabilityClient.removeListener(nodeListener)
    }
}
