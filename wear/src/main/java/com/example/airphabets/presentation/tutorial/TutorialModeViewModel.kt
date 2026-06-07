package com.example.airphabets.presentation.tutorial

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.airphabets.ml.AirWritingClassifier
import com.example.airphabets.ml.ClassifierLoadResult
import com.example.airphabets.sensors.MotionSensorManager
import com.example.airphabets.sensors.SensorSample
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * ViewModel for Tutorial Mode gesture recognition.
 * Mirrors LearnModeViewModel pattern: IDLE -> countdown -> recording -> classification -> SHOWING_PREDICTION -> (wait for phone)
 * Phone is single source of truth for feedback. ViewModel stops at SHOWING_PREDICTION.
 */
class TutorialModeViewModel(
    private val sensorManager: MotionSensorManager,
    private val classifierResult: ClassifierLoadResult,
    private val targetLetter: String,
    private val letterCase: String,
    private val onGestureResult: (isCorrect: Boolean, predictedLetter: String) -> Unit,
    private val onRecordingStarted: () -> Unit = {}
) : ViewModel() {

    companion object {
        private const val TAG = "TutorialModeVM"
        private const val COUNTDOWN_SECONDS = 3
        private const val RECORDING_SECONDS = 3
        private const val PROGRESS_UPDATE_INTERVAL_MS = 50L
        private const val GYRO_VARIANCE_THRESHOLD = 0.3f
        private const val GYRO_RANGE_THRESHOLD = 1.0f

        // Letters with similar shapes in uppercase and lowercase
        private val SIMILAR_SHAPE_LETTERS = setOf(
            'C', 'K', 'O', 'P', 'S', 'V', 'W', 'X', 'Z'
        )
    }

    enum class State {
        IDLE,
        COUNTDOWN,
        RECORDING,
        PROCESSING,
        SHOWING_PREDICTION
    }

    data class UiState(
        val state: State = State.IDLE,
        val countdownSeconds: Int = 0,
        val recordingProgress: Float = 0f,
        val prediction: String? = null,
        val isCorrect: Boolean = false,
        val statusMessage: String = "Tap to begin",
        val errorMessage: String? = null
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState

    private var recordingJob: Job? = null
    private val classifier: AirWritingClassifier?

    init {
        // Process classifier load result
        classifier = when (classifierResult) {
            is ClassifierLoadResult.Success -> {
                Log.i(TAG, "✓ Model loaded for Tutorial Mode")
                classifierResult.classifier
            }
            is ClassifierLoadResult.Error -> {
                Log.e(TAG, "✗ Model failed: ${classifierResult.message}")
                null
            }
        }
    }

    /**
     * Start recording from IDLE state (user tapped to begin).
     * Mirrors LearnModeViewModel.startRecording().
     */
    fun startRecording() {
        if (_uiState.value.state != State.IDLE) {
            Log.d(TAG, "Not in IDLE state, ignoring start")
            return
        }

        if (classifier == null) {
            Log.e(TAG, "Cannot start: classifier is null")
            _uiState.update {
                it.copy(
                    errorMessage = "Model not loaded",
                    statusMessage = "Error"
                )
            }
            onGestureResult(false, "")
            return
        }

        recordingJob?.cancel()

        recordingJob = viewModelScope.launch(Dispatchers.Default) {
            try {
                // === Phase 1: Countdown ===
                Log.d(TAG, "Starting countdown for letter: $targetLetter")
                for (i in COUNTDOWN_SECONDS downTo 1) {
                    if (!isActive) return@launch
                    _uiState.update {
                        it.copy(
                            state = State.COUNTDOWN,
                            countdownSeconds = i,
                            statusMessage = "Get ready...",
                            errorMessage = null
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
                        countdownSeconds = 0,
                        statusMessage = "Write now!",
                        recordingProgress = 0f
                    )
                }

                // Notify phone that recording has started (student is writing)
                onRecordingStarted()

                // Start sensor recording
                sensorManager.startRecording()

                // Wait for recording duration with progress updates
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

                // Stop sensor recording
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

                // Get collected samples
                val samples = sensorManager.getCollectedSamples()
                Log.d(TAG, "Collected ${samples.size} samples")

                // Check for significant wrist movement
                if (!hasSignificantMotion(samples)) {
                    Log.w(TAG, "No significant wrist movement detected — showing '?' as prediction")
                    _uiState.update {
                        it.copy(
                            state = State.SHOWING_PREDICTION,
                            prediction = "?",
                            isCorrect = false,
                            statusMessage = "?",
                            errorMessage = null,
                            recordingProgress = 0f
                        )
                    }
                    onGestureResult(false, "?")
                    return@launch
                }

                // Check if we have enough data
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
                    onGestureResult(false, "")
                    return@launch
                }

                // Run classification
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
                    onGestureResult(false, "")
                    return@launch
                }

                // === Phase 4: Show prediction and send result to phone ===
                val predictedLetter = result.label  // Small model outputs lowercase (a-z), capital model outputs uppercase (A-Z)

                // Determine the expected letter case
                val expectedLetter = when (letterCase.lowercase()) {
                    "small", "lowercase" -> targetLetter.lowercase()
                    else -> targetLetter.uppercase()
                }

                // Check if this letter has similar shapes in both cases
                val targetUppercase = targetLetter.uppercase().firstOrNull()
                val isSimilarShape = targetUppercase != null && targetUppercase in SIMILAR_SHAPE_LETTERS

                val isCorrect = if (isSimilarShape) {
                    predictedLetter.equals(expectedLetter, ignoreCase = true)
                } else {
                    predictedLetter.equals(expectedLetter, ignoreCase = false)
                }

                Log.d(TAG, "Comparison: predicted='$predictedLetter' expected='$expectedLetter' " +
                    "case=$letterCase similar=$isSimilarShape result=$isCorrect")

                // Show the predicted letter (stay in this state until phone sends feedback)
                _uiState.update {
                    it.copy(
                        state = State.SHOWING_PREDICTION,
                        prediction = predictedLetter,
                        isCorrect = isCorrect,
                        statusMessage = predictedLetter,
                        errorMessage = null,
                        recordingProgress = 0f
                    )
                }

                // Send result to phone — phone will send feedback back
                onGestureResult(isCorrect, predictedLetter)

                // ViewModel stops here. Phone-driven feedback takes over in the composable.

            } catch (e: CancellationException) {
                // Job was cancelled (e.g., ViewModel cleared during letter transition).
                // Do NOT send onGestureResult — this is not a real gesture attempt.
                Log.d(TAG, "Recording job cancelled, not sending gesture result")
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Recognition error: ${e.message}", e)
                _uiState.update {
                    it.copy(
                        state = State.IDLE,
                        errorMessage = "Error: ${e.message}",
                        statusMessage = "Try again",
                        recordingProgress = 0f
                    )
                }
                onGestureResult(false, "")
            }
        }
    }

    /**
     * Reset to idle state, cancelling any pending recording.
     * Called when phone-driven feedback is dismissed.
     * Mirrors LearnModeViewModel.resetToIdle().
     */
    fun resetToIdle() {
        Log.d(TAG, "Resetting to idle")
        recordingJob?.cancel()
        recordingJob = null
        _uiState.update {
            it.copy(
                state = State.IDLE,
                statusMessage = "Tap to begin",
                errorMessage = null,
                prediction = null,
                isCorrect = false,
                recordingProgress = 0f,
                countdownSeconds = COUNTDOWN_SECONDS
            )
        }
    }

    /**
     * Cancel current recording
     */
    fun cancelRecognition() {
        Log.d(TAG, "Cancelling recognition")
        recordingJob?.cancel()
        recordingJob = null
        sensorManager.stopRecording()
    }

    override fun onCleared() {
        super.onCleared()
        recordingJob?.cancel()
        sensorManager.stopRecording()
    }

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
}

/**
 * Factory for TutorialModeViewModel
 */
class TutorialModeViewModelFactory(
    private val sensorManager: MotionSensorManager,
    private val classifierResult: ClassifierLoadResult,
    private val targetLetter: String,
    private val letterCase: String,
    private val onGestureResult: (Boolean, String) -> Unit,
    private val onRecordingStarted: () -> Unit = {}
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TutorialModeViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TutorialModeViewModel(sensorManager, classifierResult, targetLetter, letterCase, onGestureResult, onRecordingStarted) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: $modelClass")
    }
}
