package com.example.airphabets.presentation.learn

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.airphabets.ml.AirWritingClassifier
import com.example.airphabets.ml.ClassifierLoadResult
import com.example.airphabets.sensors.MotionSensorManager
import com.example.airphabets.sensors.SensorSample
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * ViewModel for Learn Mode.
 * Handles gesture recognition for fill-in-the-blanks mode.
 */
class LearnModeViewModel(
    private val sensorManager: MotionSensorManager,
    private val classifierResult: ClassifierLoadResult,
    private val onRecordingStarted: () -> Unit = {}
) : ViewModel() {

    companion object {
        private const val TAG = "LearnModeVM"
        private const val COUNTDOWN_SECONDS = 3
        private const val RECORDING_SECONDS = 3
        private const val PREDICTION_DISPLAY_MS = 1500L // 1.5 seconds
        private const val PHONE_FEEDBACK_TIMEOUT_MS = 8000L
        private const val PROGRESS_UPDATE_INTERVAL_MS = 50L
        private const val NO_MOVEMENT_DISPLAY_SECONDS = 3

        // Motion detection thresholds
        private const val GYRO_VARIANCE_THRESHOLD = 0.3f
        private const val GYRO_RANGE_THRESHOLD = 1.0f
    }

    enum class State {
        IDLE,
        COUNTDOWN,
        RECORDING,
        PROCESSING,
        NO_MOVEMENT,
        SHOWING_PREDICTION
    }

    data class UiState(
        val title: String = "Learn Mode",
        val state: State = State.IDLE,
        val countdownSeconds: Int = 0,
        val recordingProgress: Float = 0f,
        val prediction: String? = null,
        val confidence: Float? = null,
        val statusMessage: String = "Tap to guess",
        val errorMessage: String? = null,
        val isModelLoaded: Boolean = false
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState

    private var recordingJob: Job? = null
    private val classifier: AirWritingClassifier?

    init {
        // Process classifier load result
        when (classifierResult) {
            is ClassifierLoadResult.Success -> {
                classifier = classifierResult.classifier
                Log.i(TAG, "✓ Model loaded for Learn Mode")
                _uiState.update {
                    it.copy(isModelLoaded = true, statusMessage = "Tap to guess")
                }
            }
            is ClassifierLoadResult.Error -> {
                classifier = null
                Log.e(TAG, "✗ Model failed: ${classifierResult.message}")
                _uiState.update {
                    it.copy(
                        errorMessage = classifierResult.message,
                        isModelLoaded = false,
                        statusMessage = "Model not loaded"
                    )
                }
            }
        }

        // Reset to idle when new word data arrives
        viewModelScope.launch {
            LearnModeStateHolder.wordData.collect { wordData ->
                if (wordData.word.isNotEmpty() && _uiState.value.state != State.IDLE) {
                    recordingJob?.cancel()
                    recordingJob = null
                    _uiState.update {
                        it.copy(
                            state = State.IDLE,
                            statusMessage = "Tap to guess",
                            prediction = null
                        )
                    }
                }
            }
        }
    }

    /**
     * Start the recording flow for guessing the masked letter
     */
    fun startRecording() {
        if (_uiState.value.state != State.IDLE) {
            Log.d(TAG, "Not in IDLE state, ignoring start")
            return
        }

        if (classifier == null) {
            Log.e(TAG, "Cannot start: classifier is null")
            _uiState.update { it.copy(errorMessage = "Model not loaded") }
            return
        }

        val wordData = LearnModeStateHolder.wordData.value
        if (wordData.word.isEmpty()) {
            Log.e(TAG, "Cannot start: no word data")
            _uiState.update { it.copy(errorMessage = "No word to practice") }
            return
        }

        recordingJob?.cancel()
        recordingJob = viewModelScope.launch(Dispatchers.Default) {
            try {
                // === Phase 1: Countdown ===
                Log.d(TAG, "Starting countdown...")
                for (i in COUNTDOWN_SECONDS downTo 1) {
                    if (!isActive) return@launch
                    _uiState.update {
                        it.copy(
                            state = State.COUNTDOWN,
                            countdownSeconds = i,
                            statusMessage = "Get ready...",
                            errorMessage = null,
                            prediction = null
                        )
                    }
                    delay(1000)
                }

                if (!isActive) return@launch

                // === Phase 2: Recording ===
                Log.d(TAG, "Starting recording...")
                _uiState.update {
                    it.copy(
                        state = State.RECORDING,
                        statusMessage = "Write now!",
                        recordingProgress = 0f
                    )
                }

                // Notify phone that recording has started (student is writing)
                onRecordingStarted()

                sensorManager.startRecording()

                val totalMs = RECORDING_SECONDS * 1000L
                val totalUpdates = totalMs / PROGRESS_UPDATE_INTERVAL_MS
                var updateCount = 0

                while (isActive && updateCount < totalUpdates) {
                    delay(PROGRESS_UPDATE_INTERVAL_MS)
                    updateCount++
                    _uiState.update {
                        it.copy(recordingProgress = updateCount.toFloat() / totalUpdates)
                    }
                }

                if (!isActive) return@launch

                sensorManager.stopRecording()
                Log.d(TAG, "Recording stopped")

                // === Phase 3: Processing ===
                _uiState.update {
                    it.copy(
                        state = State.PROCESSING,
                        statusMessage = "Processing...",
                        recordingProgress = 1f
                    )
                }

                val samples = sensorManager.getCollectedSamples()
                Log.d(TAG, "Collected ${samples.size} samples")

                // Check for significant wrist movement
                if (!hasSignificantMotion(samples)) {
                    Log.w(TAG, "No significant wrist movement detected — showing '?' as prediction")
                    _uiState.update {
                        it.copy(
                            state = State.SHOWING_PREDICTION,
                            prediction = "?",
                            confidence = 0f,
                            errorMessage = null,
                            recordingProgress = 0f
                        )
                    }

                    // Show prediction briefly, then wait for phone feedback
                    delay(PREDICTION_DISPLAY_MS)
                    if (!isActive) return@launch

                    // Safety timeout: if phone doesn't respond within 8 seconds, reset to idle
                    delay(PHONE_FEEDBACK_TIMEOUT_MS)
                    if (isActive && _uiState.value.state == State.SHOWING_PREDICTION) {
                        Log.w(TAG, "Phone feedback timeout — resetting to idle")
                        _uiState.update {
                            it.copy(state = State.IDLE, statusMessage = "Tap to guess")
                        }
                    }
                    return@launch
                }

                val minSamples = classifier.windowSize / 2
                if (samples.size < minSamples) {
                    Log.w(TAG, "Not enough samples: ${samples.size} < $minSamples")
                    _uiState.update {
                        it.copy(
                            state = State.IDLE,
                            errorMessage = "Not enough motion",
                            statusMessage = "Try again",
                            recordingProgress = 0f
                        )
                    }
                    return@launch
                }

                val result = classifier.classify(samples)
                Log.d(TAG, "Classification result: $result")

                if (!result.success) {
                    _uiState.update {
                        it.copy(
                            state = State.IDLE,
                            errorMessage = result.errorMessage,
                            statusMessage = "Try again",
                            recordingProgress = 0f
                        )
                    }
                    return@launch
                }

                // === Phase 4: Show the predicted letter ===
                val predictedLetter = result.label?.trim()
                Log.d(TAG, "Predicted: '$predictedLetter' (confidence: %.2f), Expected: '${wordData.word}'".format(result.confidence))
                Log.d(TAG, "Top 5 probs: ${result.allProbabilities.withIndex().sortedByDescending { it.value }.take(5).joinToString { "(${it.index}:%.2f)".format(it.value) }}")

                _uiState.update {
                    it.copy(
                        state = State.SHOWING_PREDICTION,
                        prediction = predictedLetter,
                        confidence = result.confidence,
                        errorMessage = null,
                        recordingProgress = 0f
                    )
                }

                // Show prediction briefly, then wait for phone feedback
                delay(PREDICTION_DISPLAY_MS)
                if (!isActive) return@launch

                // Safety timeout: if phone doesn't respond within 8 seconds, reset to idle
                delay(PHONE_FEEDBACK_TIMEOUT_MS)
                if (isActive && _uiState.value.state == State.SHOWING_PREDICTION) {
                    Log.w(TAG, "Phone feedback timeout — resetting to idle")
                    _uiState.update {
                        it.copy(state = State.IDLE, statusMessage = "Tap to guess")
                    }
                }

            } catch (e: CancellationException) {
                // Normal cancellation (e.g., resetToIdle cancelled the recording job) — re-throw
                Log.d(TAG, "Recording coroutine cancelled (normal)")
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Recording error: ${e.message}", e)
                _uiState.update {
                    it.copy(
                        state = State.IDLE,
                        errorMessage = "Error: ${e.message}",
                        statusMessage = "Try again",
                        recordingProgress = 0f
                    )
                }
            }
        }
    }

    /**
     * Cancel current recording/countdown
     */
    fun cancelRecording() {
        Log.d(TAG, "Cancelling recording")
        recordingJob?.cancel()
        recordingJob = null
        sensorManager.stopRecording()
        _uiState.update {
            it.copy(
                state = State.IDLE,
                statusMessage = "Tap to guess",
                errorMessage = null,
                recordingProgress = 0f,
                countdownSeconds = 0
            )
        }
    }

    /**
     * Reset to idle state, cancelling any pending recording or timeout
     */
    fun resetToIdle() {
        Log.d(TAG, "Resetting to idle")
        recordingJob?.cancel()
        recordingJob = null
        _uiState.update {
            it.copy(
                state = State.IDLE,
                statusMessage = "Tap to guess",
                errorMessage = null,
                prediction = null,
                recordingProgress = 0f,
                countdownSeconds = 0
            )
        }
    }

    /**
     * Retry after no movement was detected.
     * Cancels any pending auto-return and immediately goes back to IDLE.
     */
    fun retryAfterNoMovement() {
        if (_uiState.value.state != State.NO_MOVEMENT) return
        Log.d(TAG, "Retrying after no movement")
        recordingJob?.cancel()
        recordingJob = null
        _uiState.update {
            it.copy(
                state = State.IDLE,
                statusMessage = "Tap to guess",
                prediction = null
            )
        }
    }

    /**
     * Check if the recorded sensor samples contain significant wrist movement.
     * Uses gyroscope as the sole gate — air writing always involves rapid wrist rotation.
     */
    private fun hasSignificantMotion(samples: List<SensorSample>): Boolean {
        // TODO: Motion gate temporarily bypassed for debugging — always allow ML classification
        if (samples.size < 2) return false

        val n = samples.size.toFloat()

        val gyroMagnitudes = samples.map {
            kotlin.math.sqrt((it.gx * it.gx + it.gy * it.gy + it.gz * it.gz).toDouble()).toFloat()
        }
        val gyroMean = gyroMagnitudes.sum() / n
        val gyroVariance = gyroMagnitudes.sumOf { ((it - gyroMean) * (it - gyroMean)).toDouble() }.toFloat() / n
        val gyroRange = gyroMagnitudes.max() - gyroMagnitudes.min()

        Log.d(TAG, "Gyro: variance=%.4f (need>=%.4f), range=%.4f (need>=%.4f)".format(
            gyroVariance, GYRO_VARIANCE_THRESHOLD, gyroRange, GYRO_RANGE_THRESHOLD))

        val result = gyroVariance >= GYRO_VARIANCE_THRESHOLD && gyroRange >= GYRO_RANGE_THRESHOLD
        Log.i(TAG, "Motion detected: $result (variance=${gyroVariance >= GYRO_VARIANCE_THRESHOLD}, range=${gyroRange >= GYRO_RANGE_THRESHOLD})")
        // return result
        Log.w(TAG, "Motion gate BYPASSED — returning true regardless (debugging)")
        return true
    }

    override fun onCleared() {
        super.onCleared()
        recordingJob?.cancel()
        sensorManager.stopRecording()
        // NOTE: Do NOT close the classifier here — its lifecycle is managed by the
        // Screen composable's LaunchedEffect. Closing it here causes "Interpreter
        // has already been closed" when a new ViewModel is created with the same
        // classifier instance (e.g., when word/timestamp changes but model doesn't).
    }
}

class LearnModeViewModelFactory(
    private val sensorManager: MotionSensorManager,
    private val classifierResult: ClassifierLoadResult,
    private val onRecordingStarted: () -> Unit = {}
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LearnModeViewModel::class.java)) {
            return LearnModeViewModel(sensorManager, classifierResult, onRecordingStarted) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

