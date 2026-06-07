package com.example.airphabets.speech

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.util.Log
import java.util.Locale

/**
 * Manages Text-to-Speech functionality for speaking predicted letters.
 * Configured for a kid-friendly, lively voice.
 */
class TextToSpeechManager(context: Context) : TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "TextToSpeechManager"
        private const val DEFAULT_VOLUME = 1.0f // Range: 0.0 to 1.0

        // Kid-friendly voice settings
        // Slightly higher pitch sounds more friendly and engaging for children
        private const val KID_FRIENDLY_PITCH = 1.15f   // Higher pitch (1.0 is normal, 1.15 is friendlier)
        private const val KID_FRIENDLY_SPEED = 0.85f   // Slightly slower for better comprehension

        // Preferred voice names (in order of preference)
        private val PREFERRED_VOICE_NAMES = listOf(
            "en-us-x-tpf-local",    // Google's friendly female voice (current voice!)
            "en-us-x-sfg-local",    // Google's standard female
            "en-us-x-iom-local",    // Google Assistant voice
            "en-us-x-iob-local",    // Another Google voice
            "samantha",             // iOS-style friendly voice
            "karen",                // Australian female
            "female"                // Generic female indicator
        )
    }

    // Class-level properties
    private val appContext: Context = context.applicationContext
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var initRetryCount = 0
    private val maxRetries = 3

    // Queue for speech requests that come before TTS is initialized
    private val pendingSpeechQueue = mutableListOf<String>()

    /** Volume level from 0.0 (silent) to 1.0 (max) */
    var volume: Float = DEFAULT_VOLUME
        set(value) {
            field = value.coerceIn(0.0f, 1.0f)
        }

    /** Pitch level - higher values sound more child-like (0.5 to 2.0, default 1.15) */
    var pitch: Float = KID_FRIENDLY_PITCH
        set(value) {
            field = value.coerceIn(0.5f, 2.0f)
            tts?.setPitch(field)
        }

    /** Speech rate - controls how fast the voice speaks (0.5 to 2.0, default 0.85) */
    var speechRate: Float = KID_FRIENDLY_SPEED
        set(value) {
            field = value.coerceIn(0.5f, 2.0f)
            tts?.setSpeechRate(field)
        }

    init {
        initializeWithPreferredEngine()
    }

    /**
     * Initialize TTS with default engine.
     * Includes retry mechanism for timeout issues.
     */
    private fun initializeWithPreferredEngine() {
        Log.i(TAG, "Starting TTS initialization (attempt ${initRetryCount + 1}/$maxRetries)...")

        tts = TextToSpeech(appContext) { status ->
            Log.i(TAG, "TTS callback received, status: $status")

            if (status == TextToSpeech.SUCCESS) {
                onInit(status)
            } else {
                // TTS failed - try to retry
                initRetryCount++
                if (initRetryCount < maxRetries) {
                    Log.w(TAG, "TTS init failed, retrying in 2 seconds (attempt ${initRetryCount + 1}/$maxRetries)...")
                    tts?.shutdown()
                    tts = null

                    // Retry after a delay using a handler
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        initializeWithPreferredEngine()
                    }, 2000)
                } else {
                    Log.e(TAG, "TTS initialization failed after $maxRetries attempts")
                    onInit(status)
                }
            }
        }
    }

    override fun onInit(status: Int) {
        Log.i(TAG, "onInit called with status: $status")

        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.US)
            Log.i(TAG, "setLanguage result: $result")

            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e(TAG, "Language not supported")
            } else {
                isInitialized = true
                Log.i(TAG, "TTS is now initialized!")

                // Apply kid-friendly voice settings
                applyKidFriendlySettings()

                // Check SSML support
                checkSSMLSupport()

                Log.i(TAG, "TextToSpeech initialized successfully with kid-friendly settings")

                // Process any pending speech requests
                processPendingQueue()
            }
        } else {
            Log.e(TAG, "TextToSpeech initialization failed with status: $status")
        }
    }

    /**
     * Process any speech requests that were queued before TTS was initialized
     */
    private fun processPendingQueue() {
        if (pendingSpeechQueue.isNotEmpty()) {
            Log.d(TAG, "Processing ${pendingSpeechQueue.size} pending speech requests")
            val firstItem = pendingSpeechQueue.removeAt(0)
            speak(firstItem)
            // Clear remaining items (we only want to speak the most recent)
            pendingSpeechQueue.clear()
        }
    }

    /**
     * Check if the TTS engine supports SSML.
     * Logs the results for debugging purposes.
     */
    private fun checkSSMLSupport() {
        try {
            val engine = tts?.defaultEngine
            Log.i(TAG, "=== TTS Engine Info ===")
            Log.i(TAG, "Default engine: $engine")

            // Check voice features for SSML support indicators
            val currentVoice = tts?.voice
            if (currentVoice != null) {
                Log.i(TAG, "Current voice: ${currentVoice.name}")
                Log.i(TAG, "Voice locale: ${currentVoice.locale}")
                Log.i(TAG, "Voice quality: ${currentVoice.quality}")
                Log.i(TAG, "Voice features: ${currentVoice.features}")

                // Check for specific SSML-related features
                val features = currentVoice.features ?: emptySet<String>()
                @Suppress("DEPRECATION")
                val hasEmbeddedSynthesis = features.contains(TextToSpeech.Engine.KEY_FEATURE_EMBEDDED_SYNTHESIS)
                @Suppress("DEPRECATION")
                val hasNetworkSynthesis = features.contains(TextToSpeech.Engine.KEY_FEATURE_NETWORK_SYNTHESIS)

                Log.i(TAG, "Has embedded synthesis: $hasEmbeddedSynthesis")
                Log.i(TAG, "Has network synthesis: $hasNetworkSynthesis")
                Log.i(TAG, "Network connection required: ${currentVoice.isNetworkConnectionRequired}")
            } else {
                Log.w(TAG, "No voice currently selected")
            }

            // Test SSML by trying to speak a simple SSML string
            // Note: This is just for logging - actual SSML support varies by engine
            Log.i(TAG, "=== SSML Support Test ===")
            Log.i(TAG, "Note: Android TTS does NOT reliably support SSML across devices")
            Log.i(TAG, "Google TTS may support basic SSML, but <phoneme> tags are often ignored")
            Log.i(TAG, "Recommendation: Use phonetic spelling (e.g., 'buh' for /b/) instead of SSML")
            Log.i(TAG, "========================")

        } catch (e: Exception) {
            Log.e(TAG, "Error checking SSML support: ${e.message}")
        }
    }

    /**
     * Configure TTS for a kid-friendly, lively voice.
     */
    private fun applyKidFriendlySettings() {
        tts?.apply {
            // Set higher pitch for a more child-like voice
            setPitch(pitch)

            // Set speech rate for clarity
            setSpeechRate(speechRate)

            // Try to find a female voice (often sounds friendlier for kids)
            selectBestVoiceForKids()
        }
    }

    /**
     * Try to select a voice that's more suitable for children.
     * Prefers high-quality female voices as they tend to sound friendlier.
     * Your watch has: en-us-x-tpf-local (quality 400) which is excellent!
     */
    private fun selectBestVoiceForKids() {
        try {
            val voices = tts?.voices ?: return

            // Log available voices for debugging
            Log.i(TAG, "=== Available Voices ===")
            voices.filter { it.locale.language == "en" }.forEach { voice: Voice ->
                Log.d(TAG, "Voice: ${voice.name}, locale: ${voice.locale}, quality: ${voice.quality}, network: ${voice.isNetworkConnectionRequired}")
            }
            Log.i(TAG, "========================")

            // Filter to US English voices that don't require network
            val usEnglishVoices = voices.filter { voice: Voice ->
                voice.locale.language == "en" &&
                (voice.locale.country == "US" || voice.locale.country.isEmpty()) &&
                !voice.isNetworkConnectionRequired
            }

            if (usEnglishVoices.isEmpty()) {
                Log.w(TAG, "No suitable US English voices found")
                return
            }

            // First, try to find a preferred voice by name
            var selectedVoice: Voice? = null
            for (preferredName in PREFERRED_VOICE_NAMES) {
                selectedVoice = usEnglishVoices.find { voice: Voice ->
                    voice.name.lowercase().contains(preferredName.lowercase())
                }
                if (selectedVoice != null) {
                    Log.i(TAG, "Found preferred voice: ${selectedVoice.name}")
                    break
                }
            }

            // If no preferred voice found, pick the highest quality one
            if (selectedVoice == null) {
                selectedVoice = usEnglishVoices.maxByOrNull { it.quality }
                Log.i(TAG, "Using highest quality voice: ${selectedVoice?.name}")
            }

            // Apply the selected voice
            if (selectedVoice != null) {
                val result = tts?.setVoice(selectedVoice)
                if (result == TextToSpeech.SUCCESS) {
                    Log.i(TAG, "✓ Selected kid-friendly voice: ${selectedVoice.name} (quality: ${selectedVoice.quality})")
                } else {
                    Log.w(TAG, "✗ Failed to set voice: ${selectedVoice.name}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error selecting voice: ${e.message}")
        }
    }

    /**
     * Set a specific voice by name.
     * @param voiceName The name of the voice to use (e.g., "en-us-x-tpf-local")
     * @return true if voice was set successfully
     */
    fun setVoiceByName(voiceName: String): Boolean {
        try {
            val voices = tts?.voices ?: return false
            val voice = voices.find { it.name.equals(voiceName, ignoreCase = true) }

            return if (voice != null) {
                val result = tts?.setVoice(voice)
                if (result == TextToSpeech.SUCCESS) {
                    Log.i(TAG, "Voice set to: ${voice.name}")
                    true
                } else {
                    Log.w(TAG, "Failed to set voice: $voiceName")
                    false
                }
            } else {
                Log.w(TAG, "Voice not found: $voiceName")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting voice: ${e.message}")
            return false
        }
    }

    /**
     * Get current voice information
     */
    fun getCurrentVoiceInfo(): String {
        val voice = tts?.voice
        return if (voice != null) {
            "Voice: ${voice.name}, Quality: ${voice.quality}, Locale: ${voice.locale}"
        } else {
            "No voice selected"
        }
    }

    /**
     * Adjust settings for even more kid-friendly output
     * @param pitchLevel 0.5 (deep) to 2.0 (high), default 1.15
     * @param speedLevel 0.5 (slow) to 2.0 (fast), default 0.85
     */
    fun setKidFriendlySettings(pitchLevel: Float = KID_FRIENDLY_PITCH, speedLevel: Float = KID_FRIENDLY_SPEED) {
        pitch = pitchLevel
        speechRate = speedLevel
        Log.i(TAG, "Kid-friendly settings applied: pitch=$pitch, speed=$speechRate")
    }

    /**
     * Speak a complete word.
     * Perfect for when multiple letters form a word.
     */
    fun speakWord(word: String) {
        if (!isInitialized) {
            Log.w(TAG, "TTS not initialized, cannot speak")
            return
        }

        if (word.isBlank()) return

        Log.d(TAG, "Speaking word: $word (volume: $volume)")

        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume)
        }
        tts?.speak(word.lowercase(), TextToSpeech.QUEUE_FLUSH, params, "word_${System.currentTimeMillis()}")
    }

    /**
     * Speak a single letter clearly.
     * Handles special pronunciation for letters that might be unclear.
     * @param letter The letter to speak (e.g., "A", "B", "a", "b")
     */
    fun speakLetter(letter: String) {
        if (letter.isBlank()) return

        if (!isInitialized) {
            Log.w(TAG, "TTS not initialized yet, queueing letter: $letter")
            pendingSpeechQueue.add(letter)
            return
        }

        // Determine if it's uppercase or lowercase
        val isUpperCase = letter.length == 1 && letter[0].isUpperCase()
        val upperLetter = letter.uppercase().trim()

        // Special pronunciation for letters that might be unclear
        // Only say "Capital" for uppercase letters
        val textToSpeak = when (upperLetter) {
            "A" -> if (isUpperCase) "Capital A" else "A"
            "B" -> if (isUpperCase) "Capital B" else "B"
            "C" -> if (isUpperCase) "Capital C" else "C"
            "D" -> if (isUpperCase) "Capital D" else "D"
            "E" -> if (isUpperCase) "Capital E" else "E"
            "F" -> if (isUpperCase) "Capital F" else "F"
            "G" -> if (isUpperCase) "Capital G" else "G"
            "H" -> if (isUpperCase) "Capital H" else "H"
            "I" -> if (isUpperCase) "Capital I" else "I"
            "J" -> if (isUpperCase) "Capital J" else "J"
            "K" -> if (isUpperCase) "Capital K" else "K"
            "L" -> if (isUpperCase) "Capital L" else "L"
            "M" -> if (isUpperCase) "Capital M" else "M"
            "N" -> if (isUpperCase) "Capital N" else "N"
            "O" -> if (isUpperCase) "Capital O" else "O"
            "P" -> if (isUpperCase) "Capital P" else "P"
            "Q" -> if (isUpperCase) "Capital Q" else "Q"
            "R" -> if (isUpperCase) "Capital R" else "R"
            "S" -> if (isUpperCase) "Capital S" else "S"
            "T" -> if (isUpperCase) "Capital T" else "T"
            "U" -> if (isUpperCase) "Capital U" else "U"
            "V" -> if (isUpperCase) "Capital V" else "V"
            "W" -> if (isUpperCase) "Capital W" else "W"
            "X" -> if (isUpperCase) "Capital X" else "X"
            "Y" -> if (isUpperCase) "Capital Y" else "Y"
            "Z" -> if (isUpperCase) "Capital Z" else "Z"
            else -> upperLetter
        }

        Log.d(TAG, "Speaking letter: $letter as '$textToSpeak' (volume: $volume)")

        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume)
        }
        tts?.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, params, "letter_${System.currentTimeMillis()}")
    }

    /**
     * Speak encouraging phrases to motivate children.
     * Examples: "Great job!", "Keep going!", "You're doing amazing!"
     */
    fun speakEncouragement(phrase: String) {
        if (!isInitialized) {
            Log.w(TAG, "TTS not initialized, cannot speak")
            return
        }

        if (phrase.isBlank()) return

        Log.d(TAG, "Speaking encouragement: $phrase (volume: $volume)")

        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume)
        }
        tts?.speak(phrase, TextToSpeech.QUEUE_FLUSH, params, "encourage_${System.currentTimeMillis()}")
    }

    /**
     * Speak a kid-friendly "try again" message when the gesture is incorrect.
     * Randomly selects from encouraging phrases to keep it engaging.
     */
    fun speakTryAgain() {
        val tryAgainPhrases = listOf(
            "Oops! Let's try that again!",
            "Not quite! You can do it!",
            "Almost! Give it another try!",
            "Let's try one more time!",
            "Nice try! Let's do it again!"
        )
        val randomPhrase = tryAgainPhrases.random()
        speakEncouragement(randomPhrase)
    }

    /**
     * Speak custom text.
     */
    fun speak(text: String) {
        if (text.isBlank()) return

        if (!isInitialized) {
            Log.w(TAG, "TTS not initialized yet, queueing: $text")
            pendingSpeechQueue.add(text)
            return
        }

        Log.d(TAG, "Speaking: $text (volume: $volume)")

        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume)
        }
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "custom_${System.currentTimeMillis()}")
    }

    /**
     * Check if TTS is currently speaking.
     */
    fun isSpeaking(): Boolean = tts?.isSpeaking == true

    /**
     * Stop any ongoing speech.
     */
    fun stop() {
        tts?.stop()
    }

    /**
     * Release TTS resources.
     */
    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
        Log.d(TAG, "TextToSpeech shutdown")
    }
}

