package com.example.airphabets.presentation.practice

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.airphabets.R
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

/**
 * Data class representing a practice question
 */
data class PracticeQuestion(
    val question: String,
    val expectedAnswer: String, // The letter or answer expected
    val category: QuestionCategory,
    val emoji: String? = null, // Optional emoji for PICTURE_MATCH questions
    val audioResId: Int? = null // Optional audio resource ID to play instead of TTS
)

enum class QuestionCategory {
    TRACING_COPYING,
    UPPERCASE_LOWERCASE,
    LETTER_SOUND,
    PICTURE_MATCH
}

/**
 * ViewModel for Practice Mode.
 * New flow: Tap to start -> Show random question -> User answers by air-writing
 */
class PracticeModeViewModel(
    private val sensorManager: MotionSensorManager,
    private val classifierResult: ClassifierLoadResult
) : ViewModel() {

    // Recording configuration
    companion object {
        private const val TAG = "PracticeModeVM"

        /** Letters whose uppercase and lowercase forms are structurally identical in air-writing */
        private val CASE_LENIENT_LETTERS = setOf('C', 'K', 'O', 'P', 'S', 'V', 'W', 'X', 'Z')
        private const val COUNTDOWN_SECONDS = 3
        private const val RECORDING_SECONDS = 3
        private const val RESULT_DISPLAY_SECONDS = 3
        private const val PROGRESS_UPDATE_INTERVAL_MS = 50L
        private const val NO_MOVEMENT_DISPLAY_SECONDS = 3

        // Motion detection thresholds (tune on device via logcat "PracticeModeVM")
        // Air writing produces rapid back-and-forth wrist rotation: high gyro variance + wide range.
        // A tilt is slow and smooth: low variance even if range is moderate. Both must pass.
        private const val GYRO_VARIANCE_THRESHOLD = 0.3f   // Gyro magnitude variance — must be high (rapid rotation)
        private const val GYRO_RANGE_THRESHOLD = 1.0f      // Gyro magnitude peak-to-peak (rad/s) — must sweep wide

        // Practice questions
        private val PRACTICE_QUESTIONS = listOf(
            // Tracing & Copying Letters
            PracticeQuestion("Can you trace the letter A?", "A", QuestionCategory.TRACING_COPYING, audioResId = R.raw.qa_trace_upper_a),
            PracticeQuestion("Can you trace the letter B?", "B", QuestionCategory.TRACING_COPYING, audioResId = R.raw.qa_trace_upper_b),
            PracticeQuestion("Can you copy the letter L?", "L", QuestionCategory.TRACING_COPYING, audioResId = R.raw.qa_trace_upper_l),
            PracticeQuestion("Can you trace the letter C?", "C", QuestionCategory.TRACING_COPYING, audioResId = R.raw.qa_trace_upper_c),
            PracticeQuestion("Can you trace the letter D?", "D", QuestionCategory.TRACING_COPYING, audioResId = R.raw.qa_trace_upper_d),
            PracticeQuestion("Can you copy the letter E?", "E", QuestionCategory.TRACING_COPYING, audioResId = R.raw.qa_copy_upper_e),
            PracticeQuestion("Can you trace the letter F?", "F", QuestionCategory.TRACING_COPYING, audioResId = R.raw.qa_trace_upper_f),
            PracticeQuestion("Can you copy the letter G?", "G", QuestionCategory.TRACING_COPYING, audioResId = R.raw.qa_copy_upper_g),
            PracticeQuestion("Can you trace the letter H?", "H", QuestionCategory.TRACING_COPYING, audioResId = R.raw.qa_trace_upper_h),
            PracticeQuestion("Can you copy the letter I?", "I", QuestionCategory.TRACING_COPYING, audioResId = R.raw.qa_copy_upper_i),
            PracticeQuestion("Can you trace the letter J?", "J", QuestionCategory.TRACING_COPYING, audioResId = R.raw.qa_trace_upper_j),
            PracticeQuestion("Can you copy the letter K?", "K", QuestionCategory.TRACING_COPYING, audioResId = R.raw.qa_copy_upper_k),
            PracticeQuestion("Can you trace the letter M?", "M", QuestionCategory.TRACING_COPYING, audioResId = R.raw.qa_trace_upper_m),
            PracticeQuestion("Can you copy the letter N?", "N", QuestionCategory.TRACING_COPYING, audioResId = R.raw.qa_copy_upper_n),
            PracticeQuestion("Can you trace the letter O?", "O", QuestionCategory.TRACING_COPYING, audioResId = R.raw.qa_trace_upper_o),
            PracticeQuestion("Can you copy the letter P?", "P", QuestionCategory.TRACING_COPYING, audioResId = R.raw.qa_copy_upper_p),
            PracticeQuestion("Can you trace the letter Q?", "Q", QuestionCategory.TRACING_COPYING, audioResId = R.raw.qa_trace_upper_q),
            PracticeQuestion("Can you copy the letter R?", "R", QuestionCategory.TRACING_COPYING, audioResId = R.raw.qa_copy_upper_r),
            PracticeQuestion("Can you trace the letter S?", "S", QuestionCategory.TRACING_COPYING, audioResId = R.raw.qa_trace_upper_s),
            PracticeQuestion("Can you copy the letter T?", "T", QuestionCategory.TRACING_COPYING, audioResId = R.raw.qa_copy_upper_t),
            PracticeQuestion("Can you trace the letter U?", "U", QuestionCategory.TRACING_COPYING, audioResId = R.raw.qa_trace_upper_u),
            PracticeQuestion("Can you copy the letter V?", "V", QuestionCategory.TRACING_COPYING, audioResId = R.raw.qa_copy_upper_v),
            PracticeQuestion("Can you trace the letter W?", "W", QuestionCategory.TRACING_COPYING, audioResId = R.raw.qa_trace_upper_w),
            PracticeQuestion("Can you copy the letter X?", "X", QuestionCategory.TRACING_COPYING, audioResId = R.raw.qa_copy_upper_x),
            PracticeQuestion("Can you trace the letter Y?", "Y", QuestionCategory.TRACING_COPYING, audioResId = R.raw.qa_trace_upper_y),
            PracticeQuestion("Can you copy the letter Z?", "Z", QuestionCategory.TRACING_COPYING, audioResId = R.raw.qa_copy_upper_z),

            // Uppercase & Lowercase Practice
            PracticeQuestion("Can you write an uppercase A?", "A", QuestionCategory.UPPERCASE_LOWERCASE, audioResId = R.raw.qa_ul_upper_a),
            PracticeQuestion("Can you write a lowercase b?", "b", QuestionCategory.UPPERCASE_LOWERCASE, audioResId = R.raw.qa_ul_lower_b),
            PracticeQuestion("Can you write an uppercase C?", "C", QuestionCategory.UPPERCASE_LOWERCASE, audioResId = R.raw.qa_ul_upper_c),
            PracticeQuestion("Can you write a lowercase d?", "d", QuestionCategory.UPPERCASE_LOWERCASE, audioResId = R.raw.qa_ul_lower_d),
            PracticeQuestion("Can you write an uppercase E?", "E", QuestionCategory.UPPERCASE_LOWERCASE, audioResId = R.raw.qa_ul_upper_e),
            PracticeQuestion("Can you write a lowercase f?", "f", QuestionCategory.UPPERCASE_LOWERCASE, audioResId = R.raw.qa_ul_lower_f),
            PracticeQuestion("Can you write an uppercase G?", "G", QuestionCategory.UPPERCASE_LOWERCASE, audioResId = R.raw.qa_ul_upper_g),
            PracticeQuestion("Can you write a lowercase h?", "h", QuestionCategory.UPPERCASE_LOWERCASE, audioResId = R.raw.qa_ul_lower_h),
            PracticeQuestion("Can you write an uppercase I?", "I", QuestionCategory.UPPERCASE_LOWERCASE, audioResId = R.raw.qa_ul_upper_i),
            PracticeQuestion("Can you write a lowercase j?", "j", QuestionCategory.UPPERCASE_LOWERCASE, audioResId = R.raw.qa_ul_lower_j),
            PracticeQuestion("Can you write an uppercase K?", "K", QuestionCategory.UPPERCASE_LOWERCASE, audioResId = R.raw.qa_ul_upper_k),
            PracticeQuestion("Can you write a lowercase l?", "l", QuestionCategory.UPPERCASE_LOWERCASE, audioResId = R.raw.qa_ul_lower_l),
            PracticeQuestion("Can you write an uppercase M?", "M", QuestionCategory.UPPERCASE_LOWERCASE, audioResId = R.raw.qa_ul_upper_m),
            PracticeQuestion("Can you write a lowercase n?", "n", QuestionCategory.UPPERCASE_LOWERCASE, audioResId = R.raw.qa_ul_lower_n),
            PracticeQuestion("Can you write an uppercase O?", "O", QuestionCategory.UPPERCASE_LOWERCASE, audioResId = R.raw.qa_ul_upper_o),
            PracticeQuestion("Can you write a lowercase p?", "p", QuestionCategory.UPPERCASE_LOWERCASE, audioResId = R.raw.qa_ul_lower_p),

            // Letter Sound Match (Phonics) - TTS friendly (sound + example word)
            PracticeQuestion("Which letter makes the sound \"b\" as in \"ball\"?", "B", QuestionCategory.LETTER_SOUND, audioResId = R.raw.qa_sound_b),
            PracticeQuestion("Which letter makes the sound \"a\" as in \"apple\"?", "A", QuestionCategory.LETTER_SOUND, audioResId = R.raw.qa_sound_a),
            PracticeQuestion("Which letter makes the sound \"s\" as in \"sun\"?", "S", QuestionCategory.LETTER_SOUND, audioResId = R.raw.qa_sound_s),
            PracticeQuestion("Which letter makes the sound \"m\" as in \"moon\"?", "M", QuestionCategory.LETTER_SOUND, audioResId = R.raw.qa_sound_m),
            PracticeQuestion("Which letter makes the sound \"d\" as in \"dog\"?", "D", QuestionCategory.LETTER_SOUND, audioResId = R.raw.qa_sound_d),
            PracticeQuestion("Which letter makes the sound \"f\" as in \"fish\"?", "F", QuestionCategory.LETTER_SOUND, audioResId = R.raw.qa_sound_f),
            PracticeQuestion("Which letter makes the sound \"z\" as in \"zebra\"?", "Z", QuestionCategory.LETTER_SOUND, audioResId = R.raw.qa_sound_z),
            PracticeQuestion("Which letter makes the sound \"k\" as in \"kite\"?", "K", QuestionCategory.LETTER_SOUND, audioResId = R.raw.qa_sound_k),
            PracticeQuestion("Which letter makes the sound \"t\" as in \"top\"?", "T", QuestionCategory.LETTER_SOUND, audioResId = R.raw.qa_sound_t),
            PracticeQuestion("Which letter makes the sound \"h\" as in \"hat\"?", "H", QuestionCategory.LETTER_SOUND, audioResId = R.raw.qa_sound_h),
            PracticeQuestion("Which letter makes the sound \"p\" as in \"pen\"?", "P", QuestionCategory.LETTER_SOUND, audioResId = R.raw.qa_sound_p),
            PracticeQuestion("Which letter makes the sound \"r\" as in \"run\"?", "R", QuestionCategory.LETTER_SOUND, audioResId = R.raw.qa_sound_r),
            PracticeQuestion("Which letter makes the sound \"n\" as in \"nose\"?", "N", QuestionCategory.LETTER_SOUND, audioResId = R.raw.qa_sound_n),
            PracticeQuestion("Which letter makes the sound \"j\" as in \"jump\"?", "J", QuestionCategory.LETTER_SOUND, audioResId = R.raw.qa_sound_j),
            PracticeQuestion("Which letter makes the sound \"l\" as in \"lion\"?", "L", QuestionCategory.LETTER_SOUND, audioResId = R.raw.qa_sound_l),
            PracticeQuestion("Which letter makes the sound \"g\" as in \"goat\"?", "G", QuestionCategory.LETTER_SOUND, audioResId = R.raw.qa_sound_g),

            // Picture → Letter Match
            PracticeQuestion("What letter does cat start with?", "C", QuestionCategory.PICTURE_MATCH, "🐱", audioResId = R.raw.qa_picture_cat),
            PracticeQuestion("What letter does apple start with?", "A", QuestionCategory.PICTURE_MATCH, "🍎", audioResId = R.raw.qa_picture_apple),
            PracticeQuestion("What letter does elephant start with?", "E", QuestionCategory.PICTURE_MATCH, "🐘", audioResId = R.raw.qa_picture_elephant),
            PracticeQuestion("What letter does dog start with?", "D", QuestionCategory.PICTURE_MATCH, "🐕", audioResId = R.raw.qa_picture_dog),
            PracticeQuestion("What letter does fish start with?", "F", QuestionCategory.PICTURE_MATCH, "🐟", audioResId = R.raw.qa_picture_fish),
            PracticeQuestion("What letter does lion start with?", "L", QuestionCategory.PICTURE_MATCH, "🦁", audioResId = R.raw.qa_picture_lion),
            PracticeQuestion("What letter does monkey start with?", "M", QuestionCategory.PICTURE_MATCH, "🐵", audioResId = R.raw.qa_picture_monkey),
            PracticeQuestion("What letter does snake start with?", "S", QuestionCategory.PICTURE_MATCH, "🐍", audioResId = R.raw.qa_picture_snake),
            PracticeQuestion("What letter does turtle start with?", "T", QuestionCategory.PICTURE_MATCH, "🐢", audioResId = R.raw.qa_picture_turtle),
            PracticeQuestion("What letter does bear start with?", "B", QuestionCategory.PICTURE_MATCH, "🐻", audioResId = R.raw.qa_picture_bear),
            PracticeQuestion("What letter does frog start with?", "F", QuestionCategory.PICTURE_MATCH, "🐸", audioResId = R.raw.qa_picture_frog),
            PracticeQuestion("What letter does rabbit start with?", "R", QuestionCategory.PICTURE_MATCH, "🐰", audioResId = R.raw.qa_picture_rabbit),
            PracticeQuestion("What letter does giraffe start with?", "G", QuestionCategory.PICTURE_MATCH, "🦒", audioResId = R.raw.qa_picture_giraffe),
            PracticeQuestion("What letter does pig start with?", "P", QuestionCategory.PICTURE_MATCH, "🐷", audioResId = R.raw.qa_picture_pig),
            PracticeQuestion("What letter does fox start with?", "F", QuestionCategory.PICTURE_MATCH, "🦊", audioResId = R.raw.qa_picture_fox),
            PracticeQuestion("What letter does butterfly start with?", "B", QuestionCategory.PICTURE_MATCH, "🦋", audioResId = R.raw.qa_picture_butterfly),
            PracticeQuestion("What letter does banana start with?", "B", QuestionCategory.PICTURE_MATCH, "🍌", audioResId = R.raw.qa_picture_banana),
            PracticeQuestion("What letter does sunflower start with?", "S", QuestionCategory.PICTURE_MATCH, "🌻", audioResId = R.raw.qa_picture_sunflower),
            PracticeQuestion("What letter does grapes start with?", "G", QuestionCategory.PICTURE_MATCH, "🍇", audioResId = R.raw.qa_picture_grapes),
            PracticeQuestion("What letter does car start with?", "C", QuestionCategory.PICTURE_MATCH, "🚗", audioResId = R.raw.qa_picture_car),

            )
    }

    enum class State {
        IDLE,
        QUESTION,      // Show the question to the user
        COUNTDOWN,
        RECORDING,
        PROCESSING,
        NO_MOVEMENT,            // User didn't move wrist during recording
        SHOWING_PREDICTION,  // Show the user's predicted letter
        RESULT
    }

    data class UiState(
        val state: State = State.IDLE,
        val countdownSeconds: Int = 0,
        val recordingProgress: Float = 0f,
        val prediction: String? = null,
        val confidence: Float? = null,
        val statusMessage: String = "Tap to Start",
        val errorMessage: String? = null,
        val modelInfo: String? = null,
        val isModelLoaded: Boolean = false,
        // Question-based practice fields
        val currentQuestion: PracticeQuestion? = null,
        val isAnswerCorrect: Boolean? = null,
        val questionsAnswered: Int = 0,
        val correctAnswers: Int = 0
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState

    private var recordingJob: Job? = null
    private val classifier: AirWritingClassifier?

    // Track used questions to avoid repetition
    private val usedQuestionIndices = mutableSetOf<Int>()

    init {
        // Process classifier load result
        when (classifierResult) {
            is ClassifierLoadResult.Success -> {
                classifier = classifierResult.classifier
                val info = "${classifier.modelName}: ${classifier.windowSize}×${classifier.channels} → ${classifier.numClasses} classes"
                Log.i(TAG, "✓ Model loaded: $info")
                _uiState.update {
                    it.copy(
                        modelInfo = info,
                        isModelLoaded = true,
                        statusMessage = "Tap to Start"
                    )
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
    }

    /**
     * Get a random question that hasn't been used recently
     */
    private fun getRandomQuestion(): PracticeQuestion {
        // Reset if all questions have been used
        if (usedQuestionIndices.size >= PRACTICE_QUESTIONS.size) {
            usedQuestionIndices.clear()
        }

        var index: Int
        do {
            index = (0 until PRACTICE_QUESTIONS.size).random()
        } while (index in usedQuestionIndices)

        usedQuestionIndices.add(index)
        return PRACTICE_QUESTIONS[index]
    }

    /**
     * Start the practice flow: show question -> countdown -> record -> classify -> show result
     */
    fun startRecording() {
        if (_uiState.value.state != State.IDLE) {
            Log.d(TAG, "Not in IDLE state, ignoring start")
            return
        }

        if (classifier == null) {
            Log.e(TAG, "Cannot start: classifier is null")
            _uiState.update {
                it.copy(errorMessage = "Model not loaded")
            }
            return
        }

        // Get a random question and show it
        val question = getRandomQuestion()
        Log.d(TAG, "Selected question: ${question.question}")

        _uiState.update {
            it.copy(
                state = State.QUESTION,
                currentQuestion = question,
                statusMessage = question.question,
                errorMessage = null,
                prediction = null,
                confidence = null,
                isAnswerCorrect = null
            )
        }
    }

    /**
     * Called when user taps to start answering after seeing the question
     */
    fun startAnswering() {
        if (_uiState.value.state != State.QUESTION) {
            Log.d(TAG, "Not in QUESTION state, ignoring")
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
                        statusMessage = "Write now!",
                        recordingProgress = 0f
                    )
                }

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
                    Log.w(TAG, "No significant wrist movement detected")
                    _uiState.update {
                        it.copy(
                            state = State.NO_MOVEMENT,
                            statusMessage = "Oops! You did not air write!",
                            recordingProgress = 0f
                        )
                    }

                    // Auto-return to QUESTION state after delay
                    delay(NO_MOVEMENT_DISPLAY_SECONDS * 1000L)
                    if (isActive && _uiState.value.state == State.NO_MOVEMENT) {
                        returnToCurrentQuestion()
                    }
                    return@launch
                }

                // Check if we have enough data
                val minSamples = classifier!!.windowSize / 2
                if (samples.size < minSamples) {
                    Log.w(TAG, "Not enough samples: ${samples.size} < $minSamples")
                    _uiState.update {
                        it.copy(
                            state = State.IDLE,
                            errorMessage = "Not enough motion (${samples.size} samples)",
                            statusMessage = "Move more during recording",
                            recordingProgress = 0f,
                            currentQuestion = null
                        )
                    }
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
                            recordingProgress = 0f,
                            currentQuestion = null
                        )
                    }
                    return@launch
                }

                // === Phase 4: Show Prediction ===
                val predictedLetter = result.label
                val currentQuestion = _uiState.value.currentQuestion

                // TRACING_COPYING & UPPERCASE_LOWERCASE: case-insensitive for the 9
                // structurally identical letters (C K O P S V W X Z), strict for all others.
                // Other categories (LETTER_SOUND, PICTURE_MATCH): always case-insensitive.
                val isCorrect = when (currentQuestion?.category) {
                    QuestionCategory.TRACING_COPYING,
                    QuestionCategory.UPPERCASE_LOWERCASE -> {
                        val expected = currentQuestion.expectedAnswer
                        if (expected.length == 1 && expected[0].uppercaseChar() in CASE_LENIENT_LETTERS) {
                            predictedLetter?.uppercase() == expected.uppercase()
                        } else {
                            predictedLetter == expected
                        }
                    }
                    else -> {
                        // Case-insensitive comparison for other categories
                        predictedLetter?.uppercase() == currentQuestion?.expectedAnswer?.uppercase()
                    }
                }

                Log.d(TAG, "Predicted: $predictedLetter, Expected: ${currentQuestion?.expectedAnswer}, Correct: $isCorrect")

                // First show the predicted letter
                _uiState.update {
                    it.copy(
                        state = State.SHOWING_PREDICTION,
                        prediction = predictedLetter,
                        confidence = result.confidence,
                        statusMessage = "You wrote:",
                        errorMessage = null,
                        recordingProgress = 0f,
                        isAnswerCorrect = isCorrect,
                        questionsAnswered = it.questionsAnswered + 1,
                        correctAnswers = if (isCorrect) it.correctAnswers + 1 else it.correctAnswers
                    )
                }

                // Show prediction for 2 seconds, then show result
                delay(2000L)
                if (isActive && _uiState.value.state == State.SHOWING_PREDICTION) {
                    _uiState.update {
                        it.copy(
                            state = State.RESULT,
                            statusMessage = if (isCorrect) "Correct! ✓" else "Try again!"
                        )
                    }
                }

                // Auto-reset after showing result
                delay(RESULT_DISPLAY_SECONDS * 1000L)
                if (isActive && _uiState.value.state == State.RESULT) {
                    val wasCorrect = _uiState.value.isAnswerCorrect == true
                    val sameQuestion = _uiState.value.currentQuestion
                    
                    if (wasCorrect) {
                        // Correct answer: Move to IDLE, ready for next question
                        _uiState.update {
                            it.copy(
                                state = State.IDLE,
                                statusMessage = "Tap to Start",
                                currentQuestion = null,
                                isAnswerCorrect = null,
                                prediction = null,
                                confidence = null
                            )
                        }
                    } else {
                        // Wrong answer: Stay on same question, go back to QUESTION state
                        _uiState.update {
                            it.copy(
                                state = State.QUESTION,
                                statusMessage = sameQuestion?.question ?: "Try again",
                                isAnswerCorrect = null,
                                prediction = null,
                                confidence = null
                            )
                        }
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Recording error: ${e.message}", e)
                _uiState.update {
                    it.copy(
                        state = State.IDLE,
                        errorMessage = "Error: ${e.message}",
                        statusMessage = "Try again",
                        recordingProgress = 0f,
                        currentQuestion = null
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
                statusMessage = "Tap to Start",
                errorMessage = null,
                recordingProgress = 0f,
                countdownSeconds = 0,
                currentQuestion = null
            )
        }
    }

    /**
     * Reset from result state back to idle
     */
    fun resetToIdle() {
        Log.d(TAG, "Resetting to idle")
        _uiState.update {
            it.copy(
                state = State.IDLE,
                statusMessage = "Tap to Start",
                errorMessage = null,
                recordingProgress = 0f,
                countdownSeconds = 0,
                currentQuestion = null,
                isAnswerCorrect = null
            )
        }
    }

    /**
     * Retry the same question after no movement was detected.
     * Cancels any pending auto-return and immediately goes back to QUESTION.
     */
    fun retryAfterNoMovement() {
        if (_uiState.value.state != State.NO_MOVEMENT) return
        Log.d(TAG, "Retrying after no movement")
        recordingJob?.cancel()
        recordingJob = null
        returnToCurrentQuestion()
    }

    /**
     * Return to QUESTION state with the current question preserved.
     */
    private fun returnToCurrentQuestion() {
        val sameQuestion = _uiState.value.currentQuestion
        _uiState.update {
            it.copy(
                state = State.QUESTION,
                statusMessage = sameQuestion?.question ?: "Try again",
                isAnswerCorrect = null,
                prediction = null,
                confidence = null
            )
        }
    }

    /**
     * Check if the recorded sensor samples contain significant wrist movement.
     *
     * Uses gyroscope as the sole gate — air writing always involves rapid wrist rotation.
     * Both variance AND range must exceed their thresholds (AND logic):
     * - Variance catches rapid back-and-forth rotation (writing strokes)
     * - Range catches overall rotational sweep (pen-like motion)
     *
     * A slight tilt is slow and smooth — it may have moderate range but very low variance,
     * so it fails the AND check. Holding still fails both.
     */
    private fun hasSignificantMotion(samples: List<SensorSample>): Boolean {
        // TODO: Motion gate temporarily bypassed for debugging — always allow ML classification
        if (samples.size < 2) return false

        val n = samples.size.toFloat()

        // Compute per-sample gyroscope magnitude: sqrt(gx² + gy² + gz²)
        val gyroMagnitudes = samples.map {
            kotlin.math.sqrt((it.gx * it.gx + it.gy * it.gy + it.gz * it.gz).toDouble()).toFloat()
        }
        val gyroMean = gyroMagnitudes.sum() / n
        val gyroVariance = gyroMagnitudes.sumOf { ((it - gyroMean) * (it - gyroMean)).toDouble() }.toFloat() / n
        val gyroRange = gyroMagnitudes.max() - gyroMagnitudes.min()

        Log.d(TAG, "Gyro: variance=%.4f (need>=%.4f), range=%.4f (need>=%.4f)".format(
            gyroVariance, GYRO_VARIANCE_THRESHOLD, gyroRange, GYRO_RANGE_THRESHOLD))

        // Both must pass: rapid rotation (high variance) with meaningful sweep (high range)
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
        classifier?.close()
    }
}

/**
 * Factory for PracticeModeViewModel
 */
class PracticeModeViewModelFactory(
    private val sensorManager: MotionSensorManager,
    private val classifierResult: ClassifierLoadResult
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PracticeModeViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return PracticeModeViewModel(sensorManager, classifierResult) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: $modelClass")
    }
}
