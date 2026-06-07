package com.example.airphabets.ml

import com.example.airphabets.sensors.SensorSample

/**
 * Interface for air writing classifiers.
 * Allows swapping different model implementations.
 */
interface AirWritingClassifier {

    /** Model's expected window size (number of timesteps) */
    val windowSize: Int

    /** Model's expected number of channels (e.g., 6 for ax,ay,az,gx,gy,gz) */
    val channels: Int

    /** Number of output classes */
    val numClasses: Int

    /** Labels for each class */
    val labels: List<String>

    /** Model display name for debugging */
    val modelName: String

    /**
     * Classify sensor samples directly.
     * Handles preprocessing internally.
     */
    fun classify(samples: List<SensorSample>): PredictionResult

    /**
     * Classify a pre-processed float array.
     * Array should be of size windowSize * channels.
     */
    fun classifyRaw(input: FloatArray): PredictionResult

    /**
     * Release model resources.
     */
    fun close()
}

/**
 * Result of a classification.
 */
data class PredictionResult(
    val label: String,
    val labelIndex: Int,
    val confidence: Float,
    val allProbabilities: FloatArray,
    val success: Boolean = true,
    val errorMessage: String? = null
) {
    companion object {
        fun error(message: String) = PredictionResult(
            label = "?",
            labelIndex = -1,
            confidence = 0f,
            allProbabilities = floatArrayOf(),
            success = false,
            errorMessage = message
        )
    }

    override fun toString(): String {
        return if (success) {
            "Prediction: '$label' (${(confidence * 100).toInt()}%)"
        } else {
            "Error: $errorMessage"
        }
    }
}
