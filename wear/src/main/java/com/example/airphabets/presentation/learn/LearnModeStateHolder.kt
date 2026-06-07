package com.example.airphabets.presentation.learn

import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Singleton state holder for Learn Mode data.
 * This allows the WearMessageListenerService to update state that the ViewModel can observe.
 * All updates are posted to the main thread to ensure thread safety.
 */
object LearnModeStateHolder {

    private const val TAG = "LearnModeStateHolder"

    private val mainHandler = Handler(Looper.getMainLooper())

    data class WordData(
        val word: String = "",
        val maskedIndex: Int = -1,
        val configurationType: String = "",
        val dominantHand: String = "RIGHT", // "LEFT" or "RIGHT"
        val timestamp: Long = 0L // Used to detect new data
    )

    data class SessionData(
        val setTitle: String = "",
        val totalWords: Int = 0,
        val isActive: Boolean = false,
        val isActivityComplete: Boolean = false,
        val timestamp: Long = 0L
    )

    /**
     * State for Write the Word mode - tracks letter input progress
     */
    data class WriteTheWordState(
        val currentLetterIndex: Int = 0,
        val completedIndices: Set<Int> = emptySet(),
        val lastResultCorrect: Boolean? = null,
        val isWordComplete: Boolean = false,
        val timestamp: Long = 0L
    )

    private val _wordData = MutableStateFlow(WordData())
    val wordData: StateFlow<WordData> = _wordData.asStateFlow()

    private val _sessionData = MutableStateFlow(SessionData())
    val sessionData: StateFlow<SessionData> = _sessionData.asStateFlow()

    private val _writeTheWordState = MutableStateFlow(WriteTheWordState())
    val writeTheWordState: StateFlow<WriteTheWordState> = _writeTheWordState.asStateFlow()

    // Tracks whether the watch user is currently on the LearnModeScreen
    // Used to gate handshake replies so we don't reply when on other screens
    private val _isWatchOnLearnScreen = MutableStateFlow(false)
    val isWatchOnLearnScreen: StateFlow<Boolean> = _isWatchOnLearnScreen.asStateFlow()

    // True only for the first question after session start.
    // Used so Fill-in-the-Blank auto-starts the countdown once (after the WaitScreen tap)
    // but NOT on subsequent question transitions (skip / correct answer).
    private val _isFirstQuestion = MutableStateFlow(true)
    val isFirstQuestion: StateFlow<Boolean> = _isFirstQuestion.asStateFlow()

    fun consumeFirstQuestion() {
        _isFirstQuestion.value = false
    }

    fun setWatchOnLearnScreen(onScreen: Boolean) {
        _isWatchOnLearnScreen.value = onScreen
    }

    /**
     * Called by WearMessageListenerService when word data is received
     */
    fun updateWordData(word: String, maskedIndex: Int, configurationType: String, dominantHand: String = "RIGHT") {
        runOnMainThread {
            try {
                Log.d(TAG, "📚 Updating word data: $word, masked: $maskedIndex, type: $configurationType, hand: $dominantHand")
                _wordData.value = WordData(
                    word = word,
                    maskedIndex = maskedIndex,
                    configurationType = configurationType,
                    dominantHand = dominantHand,
                    timestamp = System.currentTimeMillis()
                )
                // Reset Write the Word state for new word
                _writeTheWordState.value = WriteTheWordState(timestamp = System.currentTimeMillis())
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error updating word data", e)
            }
        }
    }

    /**
     * Called by WearMessageListenerService when session starts
     */
    fun startSession(setTitle: String, totalWords: Int) {
        runOnMainThread {
            try {
                Log.d(TAG, "🎯 Starting session: $setTitle ($totalWords words)")
                _isFirstQuestion.value = true
                _sessionData.value = SessionData(
                    setTitle = setTitle,
                    totalWords = totalWords,
                    isActive = true,
                    isActivityComplete = false,
                    timestamp = System.currentTimeMillis()
                )
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error starting session", e)
            }
        }
    }

    /**
     * Called by WearMessageListenerService when session ends
     */
    fun endSession() {
        runOnMainThread {
            try {
                Log.d(TAG, "🏁 Ending session")
                _sessionData.value = SessionData(
                    isActive = false,
                    isActivityComplete = false,
                    timestamp = System.currentTimeMillis()
                )
                _wordData.value = WordData()
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error ending session", e)
            }
        }
    }

    /**
     * Reset all state
     */
    fun reset() {
        runOnMainThread {
            _wordData.value = WordData()
            _sessionData.value = SessionData()
            _writeTheWordState.value = WriteTheWordState()
            _isWatchOnLearnScreen.value = false
            _isFirstQuestion.value = true
        }
    }

    /**
     * Called when phone sends letter validation result for Write the Word mode
     */
    fun onLetterResult(isCorrect: Boolean, currentIndex: Int, totalLetters: Int) {
        runOnMainThread {
            try {
                Log.d(TAG, "📝 Letter result: correct=$isCorrect, index=$currentIndex/$totalLetters")
                val currentState = _writeTheWordState.value
                _writeTheWordState.value = currentState.copy(
                    currentLetterIndex = currentIndex,
                    completedIndices = if (isCorrect && currentIndex > 0) {
                        currentState.completedIndices + (currentIndex - 1)
                    } else {
                        currentState.completedIndices
                    },
                    lastResultCorrect = isCorrect,
                    timestamp = System.currentTimeMillis()
                )
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error updating letter result", e)
            }
        }
    }

    /**
     * Called when phone notifies word is complete
     */
    fun onWordComplete() {
        runOnMainThread {
            try {
                Log.d(TAG, "✅ Word complete!")
                val currentWord = _wordData.value.word
                _writeTheWordState.value = _writeTheWordState.value.copy(
                    isWordComplete = true,
                    completedIndices = currentWord.indices.toSet(),
                    lastResultCorrect = true,
                    timestamp = System.currentTimeMillis()
                )
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error updating word complete", e)
            }
        }
    }

    /**
     * Called when phone notifies the entire activity (all items in set) is complete
     */
    fun onActivityComplete() {
        runOnMainThread {
            try {
                Log.d(TAG, "🎉 Activity complete!")
                _sessionData.value = _sessionData.value.copy(
                    isActivityComplete = true,
                    timestamp = System.currentTimeMillis()
                )
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error updating activity complete", e)
            }
        }
    }

    /**
     * Run a block on the main thread safely
     */
    private fun runOnMainThread(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }
}

