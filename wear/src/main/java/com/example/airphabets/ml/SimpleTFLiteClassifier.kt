package com.example.airphabets.ml

import android.content.Context
import android.util.Log
import com.example.airphabets.sensors.SensorSample
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.sqrt

/**
 * Simple TFLite classifier implementation.
 * Supports standard and Flex models (if dependencies are present).
 */
class SimpleTFLiteClassifier(
    context: Context,
    private val config: ModelConfig
) : AirWritingClassifier {

    private val interpreter: Interpreter


    // These come from the actual model, may differ from config
    private val actualWindowSize: Int
    private val actualChannels: Int
    private val actualNumClasses: Int

    override val windowSize: Int get() = actualWindowSize
    override val channels: Int get() = actualChannels
    override val numClasses: Int get() = actualNumClasses
    override val labels: List<String> get() = config.labels
    override val modelName: String get() = config.displayName

    init {
        Log.d(TAG, "Initializing SimpleTFLiteClassifier...")
        Log.d(TAG, "Config: fileName=${config.fileName}, windowSize=${config.windowSize}, channels=${config.channels}")

        // Load model file
        val modelBuffer = try {
            loadModelFile(context, config.fileName)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load model file: ${e.message}", e)
            throw IllegalStateException("Failed to load model file '${config.fileName}': ${e.message}", e)
        }
        Log.d(TAG, "Model buffer: ${modelBuffer.capacity()} bytes")

        // Create interpreter options with Flex Delegate
        val options = Interpreter.Options().apply {
            setNumThreads(2)
        }


        interpreter = Interpreter(modelBuffer, options)

        // Read actual model dimensions from the loaded model
        val inputTensor = interpreter.getInputTensor(0)
        val inputShape = inputTensor.shape()
        Log.d(TAG, "Input tensor shape: ${inputShape.contentToString()}")
        
        if (inputShape.size == 3) {
            actualWindowSize = if (inputShape[1] > 0) inputShape[1] else config.windowSize
            actualChannels = inputShape[2]
        } else {
            Log.w(TAG, "Unexpected input shape ${inputShape.contentToString()}, using config values.")
            actualWindowSize = config.windowSize
            actualChannels = config.channels
        }

        val outputTensor = interpreter.getOutputTensor(0)
        val outputShape = outputTensor.shape()
        Log.d(TAG, "Output tensor shape: ${outputShape.contentToString()}")

        if (outputShape.size == 2) {
             actualNumClasses = outputShape[1]
        } else {
             Log.w(TAG, "Unexpected output shape ${outputShape.contentToString()}, using config labels size.")
             actualNumClasses = config.labels.size
        }
        
        // Resize input if needed
        try {
             interpreter.resizeInput(0, intArrayOf(1, actualWindowSize, actualChannels))
             interpreter.allocateTensors()
        } catch (e: Exception) {
             Log.w(TAG, "Could not resize input/allocate tensors (might be fixed size model): ${e.message}")
        }

        Log.i(TAG, "✓ Model loaded: ${config.displayName}")
    }

    override fun classify(samples: List<SensorSample>): PredictionResult {
        if (samples.isEmpty()) {
            return PredictionResult.error("No samples provided")
        }
        // Prepare input array
        val input = prepareInput(samples)
        return classifyRaw(input)
    }

    override fun classifyRaw(input: FloatArray): PredictionResult {
        val expectedSize = actualWindowSize * actualChannels
        if (input.size != expectedSize) {
            return PredictionResult.error(
                "Input size mismatch: got ${input.size}, expected $expectedSize"
            )
        }

        return try {
            // Create input ByteBuffer
            val inputBuffer = ByteBuffer.allocateDirect(4 * expectedSize)
            inputBuffer.order(ByteOrder.nativeOrder())
            for (value in input) {
                inputBuffer.putFloat(value)
            }
            inputBuffer.rewind()

            // Create output ByteBuffer
            val outputBuffer = ByteBuffer.allocateDirect(4 * actualNumClasses)
            outputBuffer.order(ByteOrder.nativeOrder())

            // Run inference
            interpreter.run(inputBuffer, outputBuffer)

            // Read probabilities
            outputBuffer.rewind()
            val probs = FloatArray(actualNumClasses)
            for (i in 0 until actualNumClasses) {
                probs[i] = outputBuffer.getFloat()
            }

            // Find argmax
            var maxIdx = 0
            var maxVal = probs[0]
            for (i in 1 until actualNumClasses) {
                if (probs[i] > maxVal) {
                    maxVal = probs[i]
                    maxIdx = i
                }
            }

            // Apply index offset for specialized models
            // For example, small letter model outputs indices 26-51, but we map them to labels 0-25
            val labelIdx = maxIdx - config.indexOffset
            val label = if (labelIdx in labels.indices) {
                labels[labelIdx]
            } else {
                Log.w(TAG, "Label index out of bounds: labelIdx=$labelIdx (maxIdx=$maxIdx, offset=${config.indexOffset})")
                "?"
            }
            
            Log.d(TAG, "Model: ${config.fileName}")
            Log.d(TAG, "Output shape: actualNumClasses=$actualNumClasses, maxIdx=$maxIdx, indexOffset=${config.indexOffset}, labelIdx=$labelIdx")
            Log.d(TAG, "Labels size: ${labels.size}, Label result: $label")
            Log.d(TAG, "Top 5 probs: ${probs.withIndex().sortedByDescending { it.value }.take(5).joinToString { "(${it.index}:%.2f)".format(it.value) }}")

            PredictionResult(
                label = label,
                labelIndex = maxIdx,
                confidence = maxVal,
                allProbabilities = probs
            )
        } catch (e: Exception) {
            Log.e(TAG, "Inference failed: ${e.message}", e)
            PredictionResult.error("Inference failed: ${e.message}")
        }
    }

    /**
     * Prepare sensor samples into a float array for the model.
     * Pads or truncates to match model's expected window size.
     * Applies Per-Sample Normalization (Standardization).
     */
    private fun prepareInput(samples: List<SensorSample>): FloatArray {
        val input = FloatArray(actualWindowSize * actualChannels)

        // Take last N samples if we have more than needed (post-gesture alignment)
        val count = minOf(samples.size, actualWindowSize)
        val startIdx = if (samples.size > actualWindowSize) samples.size - actualWindowSize else 0
        
        var bufferIdx = 0
        for (i in 0 until count) {
            val sample = samples[startIdx + i]
            input[bufferIdx++] = sample.ax
            input[bufferIdx++] = sample.ay
            input[bufferIdx++] = sample.az
            input[bufferIdx++] = sample.gx
            input[bufferIdx++] = sample.gy
            input[bufferIdx++] = sample.gz
        }
        
        // Normalize the collected input
        normalizePerSample(input, actualWindowSize, actualChannels)
        
        return input
    }

    /**
     * Standardize the input array (Z-score normalization per channel).
     * (x - mean) / std_dev
     */
    private fun normalizePerSample(input: FloatArray, timeSteps: Int, channels: Int) {
        for (c in 0 until channels) {
            var mean = 0f
            for (t in 0 until timeSteps) {
                mean += input[t * channels + c]
            }
            mean /= timeSteps

            var std = 0f
            for (t in 0 until timeSteps) {
                val diff = input[t * channels + c] - mean
                std += diff * diff
            }
            std = sqrt(std / timeSteps + 1e-6f) // Add epsilon for numerical stability

            for (t in 0 until timeSteps) {
                val idx = t * channels + c
                input[idx] = (input[idx] - mean) / std
            }
        }
    }

    override fun close() {
        interpreter.close()
    }

    companion object {
        private const val TAG = "SimpleTFLite"

        private fun loadModelFile(context: Context, fileName: String): ByteBuffer {
            val assetFd = context.assets.openFd(fileName)
            val inputStream = FileInputStream(assetFd.fileDescriptor)
            val fileChannel = inputStream.channel
            val buffer = fileChannel.map(
                FileChannel.MapMode.READ_ONLY,
                assetFd.startOffset,
                assetFd.declaredLength
            )
            fileChannel.close()
            inputStream.close()
            assetFd.close()
            return buffer
        }
    }
}
