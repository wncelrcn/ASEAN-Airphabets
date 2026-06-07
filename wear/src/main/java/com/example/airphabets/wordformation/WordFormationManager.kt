package com.example.airphabets.wordformation

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages word formation state during practice mode.
 * Tracks letter predictions and detects when words are formed.
 * Auto-resets after a configurable number of letters (default: 3).
 */
class WordFormationManager(
    private val engine: WordFormationEngine = WordFormationEngine()
) {

    companion object {
        private const val TAG = "WordFormationManager"
        private const val DEFAULT_LETTERS_BEFORE_RESET = 3
    }

    // Configuration
    private var wordFormationEnabled = true
    private var minWordLength = 2
    private var maxWordLength = 10
    private var lettersBeforeReset = DEFAULT_LETTERS_BEFORE_RESET

    // Letter buffer - stores predicted letters
    private val letterBuffer = mutableListOf<LetterPrediction>()

    // Formed words tracking
    private val formedWords = mutableListOf<String>()

    // Track the last formed word (for display purposes)
    private var lastFormedWord: String? = null
    private var wordJustFormed = false

    // Total letters written in this session
    private var totalLettersInSession = 0

    // State flow for UI updates
    private val _state = MutableStateFlow(WordFormationState())
    val state: StateFlow<WordFormationState> = _state.asStateFlow()

    /**
     * Configure word formation settings
     */
    fun configure(
        enabled: Boolean = true,
        minLength: Int = 2,
        maxLength: Int = 10,
        lettersUntilReset: Int = DEFAULT_LETTERS_BEFORE_RESET
    ) {
        wordFormationEnabled = enabled
        minWordLength = minLength
        maxWordLength = maxLength
        lettersBeforeReset = lettersUntilReset
        Log.d(TAG, "Configured: minLen=$minLength, maxLen=$maxLength, resetAfter=$lettersUntilReset letters")
    }

    /**
     * Load word bank for matching
     */
    fun loadWordBank(words: List<String>) {
        engine.loadWordBank(words.filter { it.length in minWordLength..maxWordLength })
        Log.d(TAG, "Loaded ${engine.getWordCount()} words into word bank")
        updateState()
    }

    /**
     * Add a predicted letter to the buffer
     */
    fun addLetter(letter: String, confidence: Float = 1.0f) {
        if (!wordFormationEnabled) return

        // Normalize using LetterMatchingUtils - handles similar case letters
        val normalizedLetter = letter.uppercase().firstOrNull()?.toString() ?: return

        letterBuffer.add(LetterPrediction(
            letter = normalizedLetter,
            confidence = confidence,
            timestamp = System.currentTimeMillis()
        ))

        // Track total letters in session
        totalLettersInSession++

        Log.d(TAG, "Added letter: $normalizedLetter (original: $letter), buffer: ${letterBuffer.map { it.letter }.joinToString("")}, total letters: $totalLettersInSession")

        // Limit buffer size
        if (letterBuffer.size > maxWordLength) {
            letterBuffer.removeAt(0)
        }

        // Check if we formed a complete word
        val result = engine.analyzeLetterBuffer(letterBuffer.map { it.letter })
        if (result is WordFormationResult.CompleteWord) {
            // Auto-confirm word if it's complete and can't continue to a longer word
            // OR if user has typed maxWordLength letters
            if (!result.canContinue || letterBuffer.size >= maxWordLength) {
                autoConfirmWord(result.word)
            }
        }

        // Check if we need to reset after N letters
        if (totalLettersInSession >= lettersBeforeReset) {
            Log.d(TAG, "Reached $lettersBeforeReset letters, marking session complete")
        }

        updateState()
    }

    /**
     * Auto-confirm a completed word
     */
    private fun autoConfirmWord(word: String) {
        Log.d(TAG, "Auto-confirming word: $word")
        formedWords.add(word)
        lastFormedWord = word
        wordJustFormed = true
        letterBuffer.clear()

        updateState()
    }

    /**
     * Manually confirm the current word
     */
    fun confirmWord(): String? {
        val result = engine.analyzeLetterBuffer(letterBuffer.map { it.letter })

        return when (result) {
            is WordFormationResult.CompleteWord -> {
                val word = result.word
                formedWords.add(word)
                lastFormedWord = word
                wordJustFormed = true
                letterBuffer.clear()

                Log.d(TAG, "Manually confirmed word: $word, total words: ${formedWords.size}")

                updateState()
                word
            }
            else -> null
        }
    }

    /**
     * Remove the last letter from buffer (undo)
     */
    fun removeLast() {
        if (letterBuffer.isNotEmpty()) {
            val removed = letterBuffer.removeAt(letterBuffer.lastIndex)
            Log.d(TAG, "Removed letter: ${removed.letter}")
            updateState()
        }
    }

    /**
     * Clear the letter buffer (not formed words)
     */
    fun clearBuffer() {
        letterBuffer.clear()
        Log.d(TAG, "Buffer cleared")
        updateState()
    }

    /**
     * Reset entire session (buffer + formed words + letter count)
     */
    fun resetSession() {
        letterBuffer.clear()
        formedWords.clear()
        totalLettersInSession = 0
        lastFormedWord = null
        wordJustFormed = false
        Log.d(TAG, "Session reset")
        updateState()
    }

    /**
     * Acknowledge that the formed word has been displayed
     */
    fun acknowledgeFormedWord() {
        wordJustFormed = false
        updateState()
    }

    /**
     * Get the last formed word (for display)
     */
    fun getLastFormedWord(): String? = lastFormedWord

    /**
     * Check if a word was just formed
     */
    fun wasWordJustFormed(): Boolean = wordJustFormed

    /**
     * Get current letter sequence as string
     */
    fun getCurrentSequence(): String {
        return letterBuffer.joinToString("") { it.letter }
    }

    /**
     * Check if current sequence forms a complete word
     */
    fun isCurrentWordComplete(): Boolean {
        val result = engine.analyzeLetterBuffer(letterBuffer.map { it.letter })
        return result is WordFormationResult.CompleteWord
    }

    /**
     * Check if session is complete (reached letter limit)
     */
    fun isSessionComplete(): Boolean {
        return totalLettersInSession >= lettersBeforeReset
    }

    /**
     * Get formed words in this session
     */
    fun getFormedWords(): List<String> = formedWords.toList()

    /**
     * Get suggestions for current prefix
     */
    fun getSuggestions(): List<String> {
        val result = engine.analyzeLetterBuffer(letterBuffer.map { it.letter })
        return when (result) {
            is WordFormationResult.ValidPrefix -> result.possibleWords
            is WordFormationResult.CompleteWord -> result.possibleContinuations
            else -> emptyList()
        }
    }

    private fun updateState() {
        val letters = letterBuffer.map { it.letter }
        val result = engine.analyzeLetterBuffer(letters)

        _state.value = WordFormationState(
            letterBuffer = letterBuffer.toList(),
            currentSequence = letters.joinToString(""),
            formationResult = result,
            isWordComplete = result is WordFormationResult.CompleteWord,
            suggestions = when (result) {
                is WordFormationResult.ValidPrefix -> result.possibleWords
                is WordFormationResult.CompleteWord -> result.possibleContinuations
                else -> emptyList()
            },
            wordBankSize = engine.getWordCount(),
            formedWords = formedWords.toList(),
            wordsFormedCount = formedWords.size,
            totalLettersInSession = totalLettersInSession,
            lettersUntilReset = lettersBeforeReset - totalLettersInSession,
            isSessionComplete = totalLettersInSession >= lettersBeforeReset,
            wordJustFormed = wordJustFormed,
            lastFormedWord = lastFormedWord
        )
    }
}

/**
 * Represents a single letter prediction
 */
data class LetterPrediction(
    val letter: String,
    val confidence: Float,
    val timestamp: Long
)

/**
 * Current state of word formation
 */
data class WordFormationState(
    val letterBuffer: List<LetterPrediction> = emptyList(),
    val currentSequence: String = "",
    val formationResult: WordFormationResult = WordFormationResult.Empty,
    val isWordComplete: Boolean = false,
    val suggestions: List<String> = emptyList(),
    val wordBankSize: Int = 0,
    // Session tracking
    val formedWords: List<String> = emptyList(),
    val wordsFormedCount: Int = 0,
    val totalLettersInSession: Int = 0,
    val lettersUntilReset: Int = 3,
    val isSessionComplete: Boolean = false,
    // Word just formed tracking
    val wordJustFormed: Boolean = false,
    val lastFormedWord: String? = null
) {
    val isEmpty: Boolean get() = letterBuffer.isEmpty()
    val letterCount: Int get() = letterBuffer.size
}
