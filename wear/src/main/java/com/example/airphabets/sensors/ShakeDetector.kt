package com.example.airphabets.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlin.math.sqrt

/**
 * Detects shake/tilt gestures on the watch.
 * Used in Practice Mode to allow users to repeat the current question.
 */
class ShakeDetector(private val context: Context) : SensorEventListener {

    companion object {
        private const val TAG = "ShakeDetector"
        
        // Shake detection thresholds
        private const val SHAKE_THRESHOLD_GRAVITY = 2.5f  // Acceleration threshold (in G)
        private const val SHAKE_SLOP_TIME_MS = 1500        // Minimum time between shakes
        private const val SHAKE_COUNT_RESET_TIME_MS = 2000 // Reset shake count after this time
        
        // Tilt detection thresholds  
        private const val TILT_THRESHOLD_DEGREES = 45f    // Tilt angle threshold
        private const val TILT_COOLDOWN_MS = 1200         // Cooldown between tilt detections
    }

    private val sensorManager: SensorManager? =
        context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    private var accelerometer: Sensor? = null
    
    // Shake detection state
    private var shakeTimestamp: Long = 0
    private var shakeCount: Int = 0
    
    // Tilt detection state
    private var lastTiltTimestamp: Long = 0
    private var initialGravity: FloatArray? = null
    private var gravityValues = FloatArray(3)
    
    // Listener for shake/tilt events
    private var onShakeListener: (() -> Unit)? = null
    
    @Volatile
    private var isListening: Boolean = false

    /**
     * Set the callback for when a shake/tilt gesture is detected.
     */
    fun setOnShakeListener(listener: () -> Unit) {
        onShakeListener = listener
    }

    /**
     * Start listening for shake/tilt gestures.
     */
    fun startListening() {
        if (isListening) return
        
        val sm = sensorManager ?: run {
            Log.e(TAG, "SensorManager not available")
            return
        }

        accelerometer = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (accelerometer == null) {
            Log.e(TAG, "Accelerometer not available")
            return
        }

        isListening = true
        shakeCount = 0
        initialGravity = null
        
        // Use a slower sampling rate since we don't need high precision
        sm.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI)
        Log.d(TAG, "Started listening for shake/tilt gestures")
    }

    /**
     * Stop listening for shake/tilt gestures.
     */
    fun stopListening() {
        if (!isListening) return
        
        isListening = false
        sensorManager?.unregisterListener(this)
        shakeCount = 0
        initialGravity = null
        Log.d(TAG, "Stopped listening for shake/tilt gestures")
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (!isListening) return
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        // Apply low-pass filter to isolate gravity
        val alpha = 0.8f
        gravityValues[0] = alpha * gravityValues[0] + (1 - alpha) * x
        gravityValues[1] = alpha * gravityValues[1] + (1 - alpha) * y
        gravityValues[2] = alpha * gravityValues[2] + (1 - alpha) * z

        // Check for shake (sudden acceleration)
        checkForShake(x, y, z)
        
        // Check for tilt (orientation change)
        checkForTilt()
    }

    /**
     * Detect shake by measuring acceleration magnitude above threshold.
     */
    private fun checkForShake(x: Float, y: Float, z: Float) {
        // Calculate acceleration magnitude relative to gravity
        val gX = x / SensorManager.GRAVITY_EARTH
        val gY = y / SensorManager.GRAVITY_EARTH
        val gZ = z / SensorManager.GRAVITY_EARTH

        // Calculate total G-force
        val gForce = sqrt(gX * gX + gY * gY + gZ * gZ)

        if (gForce > SHAKE_THRESHOLD_GRAVITY) {
            val now = System.currentTimeMillis()
            
            // Check if enough time has passed since last shake
            if (shakeTimestamp + SHAKE_SLOP_TIME_MS > now) {
                return
            }

            // Reset shake count if too much time has passed
            if (shakeTimestamp + SHAKE_COUNT_RESET_TIME_MS < now) {
                shakeCount = 0
            }

            shakeTimestamp = now
            shakeCount++

            Log.d(TAG, "Shake detected! Count: $shakeCount, G-force: $gForce")
            
            // Trigger callback on first shake (no need to wait for multiple shakes)
            if (shakeCount >= 1) {
                onShakeListener?.invoke()
                shakeCount = 0 // Reset after triggering
            }
        }
    }

    /**
     * Detect tilt by measuring significant change in device orientation.
     */
    private fun checkForTilt() {
        val now = System.currentTimeMillis()
        
        // Check cooldown
        if (now - lastTiltTimestamp < TILT_COOLDOWN_MS) {
            return
        }

        // Store initial gravity reference
        if (initialGravity == null) {
            initialGravity = gravityValues.clone()
            return
        }

        val initial = initialGravity!!
        
        // Calculate the angle difference using dot product
        val magnitude1 = sqrt(initial[0] * initial[0] + initial[1] * initial[1] + initial[2] * initial[2])
        val magnitude2 = sqrt(gravityValues[0] * gravityValues[0] + gravityValues[1] * gravityValues[1] + gravityValues[2] * gravityValues[2])
        
        if (magnitude1 < 0.1f || magnitude2 < 0.1f) return
        
        val dotProduct = (initial[0] * gravityValues[0] + initial[1] * gravityValues[1] + initial[2] * gravityValues[2])
        val cosAngle = dotProduct / (magnitude1 * magnitude2)
        
        // Clamp to valid range for acos
        val clampedCos = cosAngle.coerceIn(-1f, 1f)
        val angleDegrees = Math.toDegrees(kotlin.math.acos(clampedCos).toDouble()).toFloat()

        if (angleDegrees > TILT_THRESHOLD_DEGREES) {
            Log.d(TAG, "Tilt detected! Angle: $angleDegrees degrees")
            lastTiltTimestamp = now
            initialGravity = gravityValues.clone() // Update reference
            onShakeListener?.invoke()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op
    }

    /**
     * Clean up resources.
     */
    fun release() {
        stopListening()
        onShakeListener = null
    }
}
