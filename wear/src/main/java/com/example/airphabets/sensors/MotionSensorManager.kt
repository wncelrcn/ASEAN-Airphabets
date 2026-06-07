package com.example.airphabets.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

/**
 * Manages motion sensors (accelerometer + gyroscope) on Wear OS.
 * Records data the same way as the data collection app:
 * - Accelerometer events drive the sampling
 * - Latest gyroscope values are paired with each accelerometer reading
 */
class MotionSensorManager(private val context: Context) : SensorEventListener {

    private val sensorManager: SensorManager? =
        context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null

    // Latest readings
    private var accelerometerValues: FloatArray? = null
    private var gyroscopeValues: FloatArray? = null

    // Collected samples during recording
    private val gestureData = mutableListOf<SensorSample>()
    private var startTime = 0L

    @Volatile
    private var isRecording: Boolean = false

    /** Start listening to motion sensors and begin recording. */
    fun startRecording() {
        if (isRecording) return

        isRecording = true
        startTime = System.currentTimeMillis()
        gestureData.clear()
        accelerometerValues = null
        gyroscopeValues = null

        val sm = sensorManager ?: return

        accelerometer = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscope = sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        // If either sensor is missing, stop early.
        if (accelerometer == null || gyroscope == null) {
            isRecording = false
            return
        }

        // Use specific sampling rate of 100Hz (10,000 microseconds = 0.01 seconds = 100Hz)
        val samplingPeriodUs = 10000 // 10,000 microseconds = 100Hz
        sm.registerListener(this, accelerometer, samplingPeriodUs)
        sm.registerListener(this, gyroscope, samplingPeriodUs)
    }

    /** Stop listening to motion sensors. */
    fun stopRecording() {
        if (!isRecording) return
        isRecording = false
        sensorManager?.unregisterListener(this)
        accelerometerValues = null
        gyroscopeValues = null
    }

    /** Get all collected samples from the current recording session. */
    fun getCollectedSamples(): List<SensorSample> {
        return synchronized(gestureData) {
            gestureData.toList()
        }
    }

    /** Get the number of samples collected so far. */
    fun getSampleCount(): Int {
        return synchronized(gestureData) {
            gestureData.size
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (!isRecording) return

        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                accelerometerValues = event.values.clone()
                // When we get a new accelerometer event, record a new data row
                // using the latest gyroscope data. This makes accelerometer events the
                // "driver" for our sampling rate (same as data collection app).
                gyroscopeValues?.let { gyro ->
                    val acc = accelerometerValues!!
                    val timestamp = System.currentTimeMillis() - startTime

                    val sample = SensorSample(
                        timestampNanos = timestamp * 1_000_000, // Convert ms to nanoseconds for consistency
                        ax = acc[0],
                        ay = acc[1],
                        az = acc[2],
                        gx = gyro[0],
                        gy = gyro[1],
                        gz = gyro[2]
                    )

                    synchronized(gestureData) {
                        gestureData.add(sample)
                    }
                }
            }
            Sensor.TYPE_GYROSCOPE -> {
                gyroscopeValues = event.values.clone()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op but kept for interface completeness
    }
}
