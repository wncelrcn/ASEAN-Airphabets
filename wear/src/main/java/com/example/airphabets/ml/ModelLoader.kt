package com.example.airphabets.ml

import android.content.Context
import android.util.Log

/**
 * Result of attempting to load a classifier.
 */
sealed class ClassifierLoadResult {
    data class Success(val classifier: AirWritingClassifier) : ClassifierLoadResult()
    data class Error(val message: String, val exception: Exception? = null) : ClassifierLoadResult()
}

/**
 * Loads and manages TFLite models for air writing classification.
 */
object ModelLoader {
    private const val TAG = "ModelLoader"

    /**
     * Load a classifier with the default model configuration (model.tflite).
     */
    fun loadDefault(context: Context): ClassifierLoadResult {
        return load(context, ModelConfig.getDefault())
    }

    /**
     * Load a classifier with a specific model configuration.
     */
    fun load(context: Context, config: ModelConfig): ClassifierLoadResult {
        Log.i(TAG, "Loading model: ${config.displayName} (${config.fileName})")

        // Check if model file exists
        if (!modelFileExists(context, config.fileName)) {
            val error = "Model file '${config.fileName}' not found in assets folder."
            Log.e(TAG, error)
            return ClassifierLoadResult.Error(error)
        }

        // Try to create the classifier
        return try {
            val classifier = SimpleTFLiteClassifier(context, config)
            Log.i(TAG, "Classifier created successfully.")
            ClassifierLoadResult.Success(classifier)
        } catch (e: Exception) {
            val error = "Failed to load model '${config.fileName}': ${e.message}"
            Log.e(TAG, error, e)
            ClassifierLoadResult.Error(error, e)
        }
    }

    private fun modelFileExists(context: Context, fileName: String): Boolean {
        return try {
            context.assets.openFd(fileName).close()
            true
        } catch (e: Exception) {
            false
        }
    }
}
