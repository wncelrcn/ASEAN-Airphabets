package com.example.airphabets.sensors

/**
 * Combined accelerometer and gyroscope sample used for inference.
 */
data class SensorSample(
    val timestampNanos: Long,
    val ax: Float,
    val ay: Float,
    val az: Float,
    val gx: Float,
    val gy: Float,
    val gz: Float
)

