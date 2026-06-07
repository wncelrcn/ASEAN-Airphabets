package com.example.airphabets.presentation.tutorial

import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Singleton state holder for Tutorial Mode data.
 * This allows the WearMessageListenerService to update state that the ViewModel can observe.
 * All updates are posted to the main thread to ensure thread safety.
 */
object TutorialModeStateHolder {

    private const val TAG = "TutorialModeStateHolder"

    private val mainHandler = Handler(Looper.getMainLooper())

    data class LetterData(
        val letter: String = "",
        val letterCase: String = "", // "uppercase" or "lowercase"
        val currentIndex: Int = 0,
        val totalLetters: Int = 0,
        val dominantHand: String = "RIGHT", // "LEFT" or "RIGHT"
        val timestamp: Long = 0L // Used to detect new data
    )

    data class SessionData(
        val studentName: String = "",
        val lessonTitle: String = "",
        val isActive: Boolean = false,
        val timestamp: Long = 0L
    )

    private val _letterData = MutableStateFlow(LetterData())
    val letterData: StateFlow<LetterData> = _letterData.asStateFlow()

    private val _sessionData = MutableStateFlow(SessionData())
    val sessionData: StateFlow<SessionData> = _sessionData.asStateFlow()

    private val _isSessionComplete = MutableStateFlow(false)
    val isSessionComplete: StateFlow<Boolean> = _isSessionComplete.asStateFlow()
    
    // Tracks whether the watch user is currently on the TutorialModeScreen
    // Used to gate handshake replies so we don't reply when on other screens
    private val _isWatchOnTutorialScreen = MutableStateFlow(false)
    val isWatchOnTutorialScreen: StateFlow<Boolean> = _isWatchOnTutorialScreen.asStateFlow()

    fun setWatchOnTutorialScreen(onScreen: Boolean) {
        _isWatchOnTutorialScreen.value = onScreen
    }

    /**
     * Called by WearMessageListenerService when letter data is received
     */
    fun updateLetterData(letter: String, letterCase: String, currentIndex: Int, totalLetters: Int, dominantHand: String = "RIGHT") {
        runOnMainThread {
            try {
                // Deduplication guard: reject duplicate calls within 500ms for the same letter/case/index
                val current = _letterData.value
                if (current.letter == letter &&
                    current.letterCase == letterCase &&
                    current.currentIndex == currentIndex &&
                    System.currentTimeMillis() - current.timestamp < 500) {
                    Log.d(TAG, "Duplicate letter data within 500ms, skipping")
                    return@runOnMainThread
                }

                Log.d(TAG, "📝 Updating letter data: $letter ($letterCase), index: $currentIndex/$totalLetters, hand: $dominantHand")
                _letterData.value = LetterData(
                    letter = letter,
                    letterCase = letterCase,
                    currentIndex = currentIndex,
                    totalLetters = totalLetters,
                    dominantHand = dominantHand,
                    timestamp = System.currentTimeMillis()
                )
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error updating letter data", e)
            }
        }
    }

    /**
     * Called by WearMessageListenerService when session starts
     */
    fun startSession(studentName: String, lessonTitle: String) {
        runOnMainThread {
            try {
                // Guard against duplicate calls (both WearMessageListenerService and
                // PhoneCommunicationManager process /tutorial_mode_started). Without this,
                // the second call can clear letterData that arrived between the two calls.
                val current = _sessionData.value
                if (current.isActive && current.studentName == studentName && current.lessonTitle == lessonTitle) {
                    Log.d(TAG, "⏭️ Session already active for $studentName/$lessonTitle, skipping duplicate start")
                    return@runOnMainThread
                }

                Log.d(TAG, "🎯 Starting session: $lessonTitle for $studentName")
                // Clear all previous state first
                _letterData.value = LetterData()
                _isSessionComplete.value = false

                // Then set new session data
                _sessionData.value = SessionData(
                    studentName = studentName,
                    lessonTitle = lessonTitle,
                    isActive = true,
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
                    timestamp = System.currentTimeMillis()
                )
                _letterData.value = LetterData()
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error ending session", e)
            }
        }
    }

    /**
     * Called when mobile app signals that session is complete
     */
    fun markSessionComplete() {
        runOnMainThread {
            try {
                Log.d(TAG, "🎊 Session marked as complete")
                _isSessionComplete.value = true
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error marking session complete", e)
            }
        }
    }

    /**
     * Reset entire session state (called when session ends or user navigates away)
     */
    fun resetSession() {
        runOnMainThread {
            Log.d(TAG, "♻️ Resetting entire session state")
            _letterData.value = LetterData()
            _sessionData.value = SessionData()
            _isSessionComplete.value = false
        }
    }

    /**
     * Reset all state
     */
    fun reset() {
        runOnMainThread {
            _letterData.value = LetterData()
            _sessionData.value = SessionData()
            _isSessionComplete.value = false
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
