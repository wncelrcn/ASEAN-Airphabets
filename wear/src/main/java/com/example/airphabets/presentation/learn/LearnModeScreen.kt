package com.example.airphabets.presentation.learn

import android.media.MediaPlayer
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import com.example.airphabets.R
import com.example.airphabets.ml.ClassifierLoadResult
import com.example.airphabets.ml.ModelConfig
import com.example.airphabets.ml.ModelLoader
import com.example.airphabets.presentation.components.CircularModeBorder
import com.example.airphabets.presentation.service.PhoneCommunicationManager
import com.example.airphabets.presentation.theme.AppColors
import com.example.airphabets.sensors.MotionSensorManager
import com.example.airphabets.speech.TextToSpeechManager
import kotlinx.coroutines.launch

/**
 * Learn Mode screen - displays fill-in-the-blanks with gesture input
 * For "Fill in the Blank" configuration, users can tap to start gesture recognition
 * Swipe Left: Skip to next word (sends command to phone)
 * Swipe Right: Native Wear OS back gesture (preserved)
 */
@Composable
fun LearnModeScreen() {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val phoneCommunicationManager = remember { PhoneCommunicationManager(context) }
    val isPhoneInLearnMode by phoneCommunicationManager.isPhoneInLearnMode.collectAsState()
    
    // Keep screen on during Learn Mode to prevent sleep during air writing
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        LearnModeStateHolder.setWatchOnLearnScreen(true)
        phoneCommunicationManager.sendLearnModeWatchEnteredScreen()
        onDispose {
            phoneCommunicationManager.sendLearnModeWatchExitedScreen()
            LearnModeStateHolder.setWatchOnLearnScreen(false)
            view.keepScreenOn = false
        }
    }

    // Request current session state from phone on entry (handles re-entry recovery)
    LaunchedEffect(Unit) {
        phoneCommunicationManager.requestLearnModeState()
    }

    // Observe word data from LearnModeStateHolder
    val wordData by LearnModeStateHolder.wordData.collectAsState()
    val sessionData by LearnModeStateHolder.sessionData.collectAsState()
    val writeTheWordState by LearnModeStateHolder.writeTheWordState.collectAsState()

    // Track whether we're waiting for teacher to resume after re-entering the screen
    var waitingForResume by remember { mutableStateOf(false) }
    val learnModeResumeEvent by phoneCommunicationManager.learnModeResumeEvent.collectAsState()

    // Global feedback state - persists across word changes
    var showingFeedback by remember { mutableStateOf(false) }
    var feedbackIsCorrect by remember { mutableStateOf(false) }
    // Predicted letter from the phone's feedback event (reliable, not ViewModel-dependent)
    var feedbackPredictedLetter by remember { mutableStateOf("") }
    // Snapshot of letter index when feedback was received (avoids race with sendLetterResult)
    var feedbackLetterIndex by remember { mutableStateOf(-1) }

    // Listen for feedback from phone at the top level (persists across recompositions)
    LaunchedEffect(Unit) {
        phoneCommunicationManager.learnModeFeedbackEvent.collect { event ->
            if (event.timestamp > 0L) {
                android.util.Log.d("LearnModeScreen", "📥 Global feedback received: correct=${event.isCorrect}, letter=${event.predictedLetter}")
                feedbackIsCorrect = event.isCorrect
                feedbackPredictedLetter = event.predictedLetter
                feedbackLetterIndex = writeTheWordState.currentLetterIndex
                showingFeedback = true
            }
        }
    }

    // Detect if feedback is for the last letter of the word (correct)
    val isWriteTheWordOrNameThePicture = wordData.configurationType == "Write the Word" ||
            wordData.configurationType == "Name the Picture"
    val isLastLetterCorrect = feedbackIsCorrect &&
            feedbackLetterIndex >= wordData.word.length - 1 &&
            wordData.word.isNotEmpty() &&
            isWriteTheWordOrNameThePicture

    // Listen for feedback dismissal from phone at the top level
    // NOTE: Must compute per-letter check from state variables directly (not captured vals)
    // because LaunchedEffect(Unit) only launches once — plain vals would be stale.
    LaunchedEffect(Unit) {
        var lastDismissTime = 0L
        phoneCommunicationManager.learnModeFeedbackDismissed.collect { timestamp ->
            if (timestamp > lastDismissTime && timestamp > 0L && showingFeedback) {
                lastDismissTime = timestamp
                // Compute fresh from state variables (read current values inside coroutine)
                val configType = wordData.configurationType
                val isWTWOrNTP = configType == "Write the Word" || configType == "Name the Picture"
                val lastLetterCorrect = feedbackIsCorrect &&
                        feedbackLetterIndex >= wordData.word.length - 1 &&
                        wordData.word.isNotEmpty() && isWTWOrNTP
                // For per-letter feedback in Write the Word / Name the Picture,
                // ignore the phone's immediate dismissal — let the 2s auto-dismiss handle it
                // so the green/red letter is visible. For last letter (word complete) and
                // Fill in the Blank, honor the phone's dismissal normally.
                val isPerLetterFeedback = isWTWOrNTP &&
                        !writeTheWordState.isWordComplete && !lastLetterCorrect
                if (!isPerLetterFeedback) {
                    android.util.Log.d("LearnModeScreen", "👆 Global: Phone dismissed feedback")
                    showingFeedback = false
                } else {
                    android.util.Log.d("LearnModeScreen", "⏳ Per-letter feedback: ignoring phone dismissal, auto-dismiss handles it")
                }
            }
        }
    }

    // Reset feedback when word changes
    LaunchedEffect(wordData.timestamp) {
        if (wordData.timestamp > 0L) {
            android.util.Log.d("LearnModeScreen", "🔄 Word changed - resetting feedback state")
            showingFeedback = false
        }
    }

    // Auto-dismiss per-letter feedback for Write the Word / Name the Picture after 1 second
    // Don't auto-dismiss if this is the last letter correctly completed (teacher-gated)
    LaunchedEffect(showingFeedback, writeTheWordState.isWordComplete, isLastLetterCorrect) {
        if (showingFeedback && !writeTheWordState.isWordComplete && isWriteTheWordOrNameThePicture && !isLastLetterCorrect) {
            kotlinx.coroutines.delay(2000L)
            showingFeedback = false
        }
    }

    // Two-way handshake: send watch_ready when session becomes active
    LaunchedEffect(isPhoneInLearnMode) {
        if (isPhoneInLearnMode) {
            scope.launch {
                phoneCommunicationManager.sendLearnModeWatchReady()
            }
        }
    }

    // Heartbeat: keep sending watch_ready while no word data yet
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(3000)
            if (wordData.word.isEmpty() && LearnModeStateHolder.isWatchOnLearnScreen.value) {
                scope.launch {
                    phoneCommunicationManager.sendLearnModeWatchReady()
                }
            }
        }
    }

    // On screen entry: if phone is already in learn mode but we have no word data,
    // we're likely re-entering after an exit. Wait for teacher resume.
    LaunchedEffect(isPhoneInLearnMode) {
        if (isPhoneInLearnMode && wordData.word.isEmpty()) {
            waitingForResume = true
        }
    }

    // When resume event fires, clear the waiting state
    LaunchedEffect(learnModeResumeEvent) {
        if (learnModeResumeEvent > 0L && waitingForResume) {
            waitingForResume = false
        }
    }

    // "Tap to begin" wait screen - shown after handshake but before user taps
    var showWaitScreen by remember { mutableStateOf(true) }

    // Check if current word is Fill in the Blank type
    val isFillInTheBlank = wordData.configurationType == "Fill in the Blank"
    val isWriteTheWord = wordData.configurationType == "Write the Word"
    val isNameThePicture = wordData.configurationType == "Name the Picture"

    CircularModeBorder(borderColor = AppColors.LearnModeColor) {
        Scaffold {
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                when {
                    sessionData.isActivityComplete -> {
                        // Activity complete - show completion image
                        ActivityCompleteContent()
                    }
                    waitingForResume && wordData.word.isEmpty() -> {
                        // Watch re-entered screen, waiting for teacher to tap Continue
                        WaitingForResumeContent()
                    }
                    !isPhoneInLearnMode -> {
                        // Waiting state - phone hasn't started Learn Mode session yet
                        WaitingContent()
                    }
                    showWaitScreen -> {
                        // Tap to begin - after handshake, before user starts
                        WaitScreenContent(
                            onTap = {
                                showWaitScreen = false
                            }
                        )
                    }
                    isFillInTheBlank && wordData.word.isNotEmpty() -> {
                        // Fill in the Blank mode - show gesture input
                        FillInTheBlankContent(
                            wordData = wordData,
                            phoneCommunicationManager = phoneCommunicationManager,
                            showingFeedback = showingFeedback,
                            feedbackIsCorrect = feedbackIsCorrect
                        )
                    }
                    isWriteTheWord && wordData.word.isNotEmpty() -> {
                        // Write the Word mode - show letter-by-letter gesture input
                        WriteTheWordContent(
                            wordData = wordData,
                            writeTheWordState = writeTheWordState,
                            phoneCommunicationManager = phoneCommunicationManager,
                            showingFeedback = showingFeedback,
                            feedbackIsCorrect = feedbackIsCorrect,
                            feedbackPredictedLetter = feedbackPredictedLetter,
                            isLastLetterCorrect = isLastLetterCorrect
                        )
                    }
                    isNameThePicture && wordData.word.isNotEmpty() -> {
                        // Name the Picture mode - letter-by-letter gesture input without showing next letter
                        NameThePictureContent(
                            wordData = wordData,
                            writeTheWordState = writeTheWordState,
                            phoneCommunicationManager = phoneCommunicationManager,
                            showingFeedback = showingFeedback,
                            feedbackIsCorrect = feedbackIsCorrect,
                            feedbackPredictedLetter = feedbackPredictedLetter,
                            isLastLetterCorrect = isLastLetterCorrect
                        )
                    }
                    else -> {
                        // Other modes or waiting for word data
                        DefaultLearnModeContent()
                    }
                }
            }
        }
    }
}

@Composable
private fun WaitingContent() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(36.dp))

        // Text at the top
        Text(
            text = "Waiting...",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

         // Mascot below the text
        Image(
            painter = painterResource(id = R.drawable.dis_watch_learn_start),
            contentDescription = "Learn Mode waiting mascot",
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 14.dp),
            contentScale = ContentScale.Crop
        )
    }
}

@Composable
private fun WaitingForResumeContent() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(32.dp),
            strokeWidth = 3.dp
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Waiting for\nteacher to resume...",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun WaitScreenContent(
    onTap: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onTap
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Tap to begin!",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Image(
                painter = painterResource(id = R.drawable.dis_watch_wait),
                contentDescription = "Tap to start",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }
    }
}

@Composable
private fun ActivityCompleteContent() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.dis_watch_complete),
            contentDescription = "Activity Complete",
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
private fun DefaultLearnModeContent() {
    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Learn Mode",
                color = AppColors.LearnModeColor,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Waiting for teacher...",
                color = AppColors.LearnModeColor.copy(alpha = 0.7f),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun FillInTheBlankContent(
    wordData: LearnModeStateHolder.WordData,
    phoneCommunicationManager: PhoneCommunicationManager,
    showingFeedback: Boolean,
    feedbackIsCorrect: Boolean
) {
    val context = LocalContext.current

    // Initialize dependencies
    var isInitialized by remember { mutableStateOf(false) }
    var sensorManager by remember { mutableStateOf<MotionSensorManager?>(null) }
    var classifierResult by remember { mutableStateOf<ClassifierLoadResult?>(null) }

    // Initialize TextToSpeech manager
    val ttsManager = remember { TextToSpeechManager(context) }

    // Cleanup TTS when disposed
    DisposableEffect(Unit) {
        onDispose {
            ttsManager.shutdown()
        }
    }

    // Determine letter case from the actual target letter (at maskedIndex), not the first letter of the word
    val targetChar = wordData.word.getOrNull(wordData.maskedIndex)
    val letterCase = if (targetChar?.isUpperCase() == true) "uppercase" else "lowercase"

    // Track which model is currently loaded to prevent composing MainContent with a stale classifier.
    // When letterCase changes, the LaunchedEffect hasn't run yet during the first recomposition frame,
    // so classifierResult still holds the old model. Without this guard, MainContent would be composed
    // and a ViewModel created with the wrong (soon-to-be-closed) classifier.
    val expectedModelKey = "$letterCase-${wordData.dominantHand}"
    var loadedModelKey by remember { mutableStateOf("") }

    // Initialize dependencies and load hand+case-specific model
    LaunchedEffect(letterCase, wordData.dominantHand) {
        sensorManager = sensorManager ?: MotionSensorManager(context)
        // Close old model if switching
        (classifierResult as? ClassifierLoadResult.Success)?.classifier?.close()
        classifierResult = try {
            val modelConfig = ModelConfig.getModelForSession(letterCase, wordData.dominantHand)
            android.util.Log.d("LearnModeScreen", "Loading model: ${modelConfig.fileName}")
            ModelLoader.load(context, modelConfig)
        } catch (e: Exception) {
            ClassifierLoadResult.Error("Failed to load model: ${e.message}", e)
        }
        loadedModelKey = expectedModelKey
        isInitialized = true
    }

    if (!isInitialized || sensorManager == null || classifierResult == null || loadedModelKey != expectedModelKey) {
        // Loading state
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(40.dp),
                strokeWidth = 4.dp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Loading...",
                color = AppColors.TextSecondary,
                fontSize = 12.sp
            )
        }
    } else {
        FillInTheBlankMainContent(
            wordData = wordData,
            sensorManager = sensorManager!!,
            classifierResult = classifierResult!!,
            ttsManager = ttsManager,
            phoneCommunicationManager = phoneCommunicationManager,
            showingFeedback = showingFeedback,
            feedbackIsCorrect = feedbackIsCorrect
        )
    }
}

@Composable
private fun FillInTheBlankMainContent(
    wordData: LearnModeStateHolder.WordData,
    sensorManager: MotionSensorManager,
    classifierResult: ClassifierLoadResult,
    ttsManager: TextToSpeechManager,
    phoneCommunicationManager: PhoneCommunicationManager,
    showingFeedback: Boolean,
    feedbackIsCorrect: Boolean
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Include timestamp in key to force fresh ViewModel on screen re-entry (matches Tutorial Mode pattern)
    val viewModel: LearnModeViewModel = viewModel(
        factory = LearnModeViewModelFactory(
            sensorManager = sensorManager,
            classifierResult = classifierResult,
            onRecordingStarted = {
                scope.launch {
                    phoneCommunicationManager.sendLearnModeGestureRecording()
                }
            }
        ),
        key = "${wordData.word}-${wordData.maskedIndex}-${wordData.timestamp}"
    )

    val uiState by viewModel.uiState.collectAsState()

    // Play countdown voice audio (3, 2, 1)
    LaunchedEffect(uiState.countdownSeconds) {
        val resId = when (uiState.countdownSeconds) {
            3 -> R.raw.voice_3
            2 -> R.raw.voice_2
            1 -> R.raw.voice_1
            else -> null
        }
        if (resId != null && uiState.state == LearnModeViewModel.State.COUNTDOWN) {
            val mp = MediaPlayer.create(context, resId)
            mp?.start()
            mp?.setOnCompletionListener { it.release() }
        }
    }

    // Play "go" voice when recording starts
    LaunchedEffect(uiState.state) {
        if (uiState.state == LearnModeViewModel.State.RECORDING) {
            val mp = MediaPlayer.create(context, R.raw.voice_go)
            mp?.start()
            mp?.setOnCompletionListener { it.release() }
        }
    }

    // Auto-start only for the very first question after the WaitScreen tap.
    // Subsequent questions (skip / correct answer) require the user to tap to start.
    val isFirstQuestion by LearnModeStateHolder.isFirstQuestion.collectAsState()
    LaunchedEffect(Unit) {
        if (isFirstQuestion) {
            LearnModeStateHolder.consumeFirstQuestion()
            viewModel.startRecording()
        }
    }

    // Track last spoken prediction to avoid double TTS
    var lastSpokenPrediction by remember { mutableStateOf<String?>(null) }

    // Speak the prediction and send to phone when prediction is shown
    LaunchedEffect(uiState.state, uiState.prediction) {
        if (uiState.state == LearnModeViewModel.State.SHOWING_PREDICTION && uiState.prediction != null) {
            // Only speak if we haven't already spoken this prediction
            if (lastSpokenPrediction != uiState.prediction) {
                lastSpokenPrediction = uiState.prediction
                if (uiState.prediction == "?") {
                    ttsManager.speak("Oops, you did not air write!")
                } else {
                    ttsManager.speakLetter(uiState.prediction!!)
                }
            }

            // Send letter input to phone immediately for validation
            android.util.Log.d("LearnModeScreen", "📤 Sending letter input to phone: ${uiState.prediction} at index ${wordData.maskedIndex}")
            phoneCommunicationManager.sendLetterInput(uiState.prediction!!, wordData.maskedIndex)
        }
    }

    // Reset last spoken prediction when going back to idle
    LaunchedEffect(uiState.state) {
        if (uiState.state == LearnModeViewModel.State.IDLE) {
            lastSpokenPrediction = null
        }
    }

    // Reset ViewModel only when feedback is actually dismissed (true -> false),
    // not on initial composition when showingFeedback starts as false
    var previousShowingFeedback by remember { mutableStateOf(false) }
    LaunchedEffect(showingFeedback) {
        if (previousShowingFeedback && !showingFeedback) {
            viewModel.resetToIdle()
        }
        previousShowingFeedback = showingFeedback
    }

    // Build masked word display
    val maskedWord = buildMaskedWordDisplay(wordData.word, wordData.maskedIndex)

    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Show feedback image if we're in feedback state (synced with phone)
            if (showingFeedback) {
                LearnModeFeedbackContent(
                    isCorrect = feedbackIsCorrect
                )
            } else {
                when (uiState.state) {
                    LearnModeViewModel.State.IDLE -> IdleContent(
                        maskedWord = maskedWord,
                        viewModel = viewModel
                    )
                    LearnModeViewModel.State.COUNTDOWN -> CountdownContent(uiState)
                    LearnModeViewModel.State.RECORDING -> RecordingContent(uiState)
                    LearnModeViewModel.State.PROCESSING -> ProcessingContent()
                    LearnModeViewModel.State.NO_MOVEMENT -> { /* Unused in Learn mode */ }
                    LearnModeViewModel.State.SHOWING_PREDICTION -> {
                        if (previousShowingFeedback) {
                            // Feedback just dismissed; resetToIdle() hasn't propagated yet.
                            // Show idle to avoid flashing the stale prediction for one frame.
                            IdleContent(maskedWord = maskedWord, viewModel = viewModel)
                        } else {
                            ShowingPredictionContent(uiState)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IdleContent(
    maskedWord: String,
    viewModel: LearnModeViewModel
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { viewModel.startRecording() }
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))
            
            Text(
                text = "Tap to begin!",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            
            Image(
                painter = painterResource(id = R.drawable.dis_watch_wait),
                contentDescription = "Tap to start",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }
    }
}

@Composable
private fun CountdownContent(uiState: LearnModeViewModel.UiState) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "${uiState.countdownSeconds}",
            color = AppColors.LearnModeColor,
            fontSize = 80.sp,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.display1
        )
    }
}

@Composable
private fun RecordingContent(uiState: LearnModeViewModel.UiState) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            progress = uiState.recordingProgress,
            modifier = Modifier.fillMaxSize(),
            strokeWidth = 8.dp,
            indicatorColor = AppColors.LearnModeColor
        )
        Image(
            painter = painterResource(id = R.drawable.ic_kusho_hand),
            contentDescription = "Air write now",
            modifier = Modifier.size(85.dp),
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
private fun ProcessingContent() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(60.dp),
            strokeWidth = 6.dp,
            indicatorColor = AppColors.LearnModeColor
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Processing...",
            color = AppColors.TextPrimary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ShowingPredictionContent(uiState: LearnModeViewModel.UiState) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Display the predicted letter as the user inputted it
        Text(
            text = uiState.prediction ?: "",
            color = Color.White,
            fontSize = 80.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.display1
        )
    }
}

@Composable
private fun LearnNoMovementContent(
    viewModel: LearnModeViewModel
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { viewModel.retryAfterNoMovement() },
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.dis_ops),
            contentDescription = "Oops, no movement detected",
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentScale = ContentScale.Fit
        )
    }
}

/**
 * Build a display string with the masked letter shown as underscore
 */
private fun buildMaskedWordDisplay(word: String, maskedIndex: Int): String {
    return word.mapIndexed { index, char ->
        if (index == maskedIndex) "_" else char.toString()
    }.joinToString(" ")
}

/**
 * Write the Word mode content - letter-by-letter gesture input
 * User inputs each letter of the word sequentially using gestures
 */
@Composable
private fun WriteTheWordContent(
    wordData: LearnModeStateHolder.WordData,
    writeTheWordState: LearnModeStateHolder.WriteTheWordState,
    phoneCommunicationManager: PhoneCommunicationManager,
    showingFeedback: Boolean,
    feedbackIsCorrect: Boolean,
    feedbackPredictedLetter: String,
    isLastLetterCorrect: Boolean
) {
    val context = LocalContext.current

    // Initialize dependencies
    var isInitialized by remember { mutableStateOf(false) }
    var sensorManager by remember { mutableStateOf<MotionSensorManager?>(null) }
    var classifierResult by remember { mutableStateOf<ClassifierLoadResult?>(null) }

    // Initialize TextToSpeech manager
    val ttsManager = remember { TextToSpeechManager(context) }

    // Cleanup TTS when disposed
    DisposableEffect(Unit) {
        onDispose {
            ttsManager.shutdown()
        }
    }

    // Determine letter case from the actual current letter being written, not the first letter of the word
    val targetChar = wordData.word.getOrNull(writeTheWordState.currentLetterIndex)
    val letterCase = if (targetChar?.isUpperCase() == true) "uppercase" else "lowercase"

    // Track which model is currently loaded to prevent composing MainContent with a stale classifier.
    // When letterCase changes mid-word (e.g., "PiG": P->i), the LaunchedEffect hasn't run yet during
    // the first recomposition frame, so classifierResult still holds the old model. Without this guard,
    // MainContent would be composed and a ViewModel created with the wrong (soon-to-be-closed) classifier.
    val expectedModelKey = "$letterCase-${wordData.dominantHand}"
    var loadedModelKey by remember { mutableStateOf("") }

    // Load hand+case-specific model; reloads when letterCase or dominantHand changes
    LaunchedEffect(letterCase, wordData.dominantHand) {
        sensorManager = sensorManager ?: MotionSensorManager(context)
        // Close old model before loading new one
        (classifierResult as? ClassifierLoadResult.Success)?.classifier?.close()
        classifierResult = try {
            val modelConfig = ModelConfig.getModelForSession(letterCase, wordData.dominantHand)
            android.util.Log.d("LearnModeScreen", "Loading model: ${modelConfig.fileName}")
            ModelLoader.load(context, modelConfig)
        } catch (e: Exception) {
            ClassifierLoadResult.Error("Failed to load model: ${e.message}", e)
        }
        loadedModelKey = expectedModelKey
        isInitialized = true
    }

    if (!isInitialized || sensorManager == null || classifierResult == null || loadedModelKey != expectedModelKey) {
        // Loading state
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(40.dp),
                strokeWidth = 4.dp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Loading...",
                color = AppColors.TextSecondary,
                fontSize = 12.sp
            )
        }
    } else {
        WriteTheWordMainContent(
            wordData = wordData,
            writeTheWordState = writeTheWordState,
            sensorManager = sensorManager!!,
            classifierResult = classifierResult!!,
            ttsManager = ttsManager,
            phoneCommunicationManager = phoneCommunicationManager,
            showingFeedback = showingFeedback,
            feedbackIsCorrect = feedbackIsCorrect,
            feedbackPredictedLetter = feedbackPredictedLetter,
            isLastLetterCorrect = isLastLetterCorrect
        )
    }
}

@Composable
private fun WriteTheWordMainContent(
    wordData: LearnModeStateHolder.WordData,
    writeTheWordState: LearnModeStateHolder.WriteTheWordState,
    sensorManager: MotionSensorManager,
    classifierResult: ClassifierLoadResult,
    ttsManager: TextToSpeechManager,
    phoneCommunicationManager: PhoneCommunicationManager,
    showingFeedback: Boolean,
    feedbackIsCorrect: Boolean,
    feedbackPredictedLetter: String,
    isLastLetterCorrect: Boolean
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Current letter to input - keep exact case (uppercase or lowercase)
    val currentLetterIndex = writeTheWordState.currentLetterIndex
    val expectedLetter = wordData.word.getOrNull(currentLetterIndex)

    // Include currentLetterIndex in the key so each letter position gets a fresh ViewModel.
    // This prevents key collisions when the same case appears at different positions
    // (e.g., "PiG" — P at index 0 and G at index 2 are both uppercase but need separate ViewModels
    // because the classifier from index 0 gets closed when switching to lowercase for index 1).
    val viewModel: LearnModeViewModel = viewModel(
        factory = LearnModeViewModelFactory(
            sensorManager = sensorManager,
            classifierResult = classifierResult,
            onRecordingStarted = {
                scope.launch {
                    phoneCommunicationManager.sendLearnModeGestureRecording()
                }
            }
        ),
        key = "${wordData.word}-${wordData.maskedIndex}-${wordData.timestamp}-${currentLetterIndex}"
    )

    val uiState by viewModel.uiState.collectAsState()

    // Play countdown voice audio (3, 2, 1)
    LaunchedEffect(uiState.countdownSeconds) {
        val resId = when (uiState.countdownSeconds) {
            3 -> R.raw.voice_3
            2 -> R.raw.voice_2
            1 -> R.raw.voice_1
            else -> null
        }
        if (resId != null && uiState.state == LearnModeViewModel.State.COUNTDOWN) {
            val mp = MediaPlayer.create(context, resId)
            mp?.start()
            mp?.setOnCompletionListener { it.release() }
        }
    }

    // Play "go" voice when recording starts
    LaunchedEffect(uiState.state) {
        if (uiState.state == LearnModeViewModel.State.RECORDING) {
            val mp = MediaPlayer.create(context, R.raw.voice_go)
            mp?.start()
            mp?.setOnCompletionListener { it.release() }
        }
    }

    // Track last spoken prediction to avoid double TTS
    var lastSpokenPrediction by remember { mutableStateOf<String?>(null) }

    // Send letter input to phone when prediction is made
    LaunchedEffect(uiState.state, uiState.prediction) {
        if (uiState.state == LearnModeViewModel.State.SHOWING_PREDICTION && uiState.prediction != null) {
            // Only speak if we haven't already spoken this prediction
            if (lastSpokenPrediction != uiState.prediction) {
                lastSpokenPrediction = uiState.prediction
                if (uiState.prediction == "?") {
                    ttsManager.speak("Oops, you did not air write!")
                } else {
                    ttsManager.speakLetter(uiState.prediction!!)
                }
            }

            // Send letter input to phone for validation (preserve exact case)
            phoneCommunicationManager.sendLetterInput(uiState.prediction!!, currentLetterIndex)
        }
    }

    // Reset last spoken prediction when going back to idle
    LaunchedEffect(uiState.state) {
        if (uiState.state == LearnModeViewModel.State.IDLE) {
            lastSpokenPrediction = null
        }
    }

    // Reset ViewModel only when feedback is actually dismissed (true -> false),
    // not on initial composition when showingFeedback starts as false
    var previousShowingFeedback by remember { mutableStateOf(false) }
    LaunchedEffect(showingFeedback) {
        if (previousShowingFeedback && !showingFeedback) {
            viewModel.resetToIdle()
        }
        previousShowingFeedback = showingFeedback
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Show feedback based on word-complete vs per-letter
            if (showingFeedback) {
                if (writeTheWordState.isWordComplete || isLastLetterCorrect) {
                    WordCompleteFeedbackContent(ttsManager = ttsManager)
                } else {
                    PerLetterFeedbackContent(
                        prediction = feedbackPredictedLetter,
                        isCorrect = feedbackIsCorrect
                    )
                }
            } else {
                when (uiState.state) {
                    LearnModeViewModel.State.IDLE -> WriteTheWordIdleContent(
                        wordData = wordData,
                        writeTheWordState = writeTheWordState,
                        viewModel = viewModel
                    )
                    LearnModeViewModel.State.COUNTDOWN -> CountdownContent(uiState)
                    LearnModeViewModel.State.RECORDING -> RecordingContent(uiState)
                    LearnModeViewModel.State.PROCESSING -> ProcessingContent()
                    LearnModeViewModel.State.NO_MOVEMENT -> { /* Unused in Learn mode */ }
                    LearnModeViewModel.State.SHOWING_PREDICTION -> ShowingPredictionContent(uiState)
                }
            }
        }
    }
}

@Composable
private fun WriteTheWordIdleContent(
    wordData: LearnModeStateHolder.WordData,
    writeTheWordState: LearnModeStateHolder.WriteTheWordState,
    viewModel: LearnModeViewModel
) {
    val currentLetterIndex = writeTheWordState.currentLetterIndex
    val expectedLetter = wordData.word.getOrNull(currentLetterIndex)?.toString() ?: ""

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { viewModel.startRecording() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Tap to begin!",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Display only the current letter to write - large and purple like in the UI image
            Text(
                text = expectedLetter,
                color = AppColors.LearnModeColor,
                fontSize = 80.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.display1
            )
        }
    }
}

@Composable
private fun WriteTheWordLetterDisplay(
    word: String,
    completedIndices: Set<Int>,
    currentIndex: Int
) {
    val completedColor = Color(0xFFAE8EFB) // Purple for completed
    val currentColor = Color.White // White for current letter
    val pendingColor = Color(0xFF808080) // Gray for pending

    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        word.forEachIndexed { index, letter ->
            Text(
                text = letter.toString(),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = when {
                    index in completedIndices -> completedColor
                    index == currentIndex -> currentColor
                    else -> pendingColor
                }
            )
        }
    }
}

/**
 * Per-letter feedback for Write the Word / Name the Picture modes.
 * Shows the predicted letter in green (correct) or red (wrong), large and centered.
 * Auto-dismissed by the parent after ~1 second.
 */
@Composable
private fun PerLetterFeedbackContent(
    prediction: String?,
    isCorrect: Boolean
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = prediction ?: "",
            color = if (isCorrect) Color(0xFF4CAF50) else Color(0xFFF44336),
            fontSize = 80.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.display1
        )
    }
}

/**
 * Word-complete feedback for Write the Word / Name the Picture modes.
 * Shows dis_watch_correct image + TTS affirmation, then transitions to "Waiting for teacher...".
 * Stays visible until teacher dismisses from phone.
 */
@Composable
private fun WordCompleteFeedbackContent(
    ttsManager: TextToSpeechManager
) {
    var showWaiting by remember { mutableStateOf(false) }

    // Play TTS affirmation and transition to waiting after 2 seconds
    LaunchedEffect(Unit) {
        ttsManager.speakEncouragement("Great job!")
        kotlinx.coroutines.delay(2000L)
        showWaiting = true
    }

    if (showWaiting) {
        // Waiting for teacher state
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(32.dp))

                Text(
                    text = "Waiting for teacher...",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Image(
                    painter = painterResource(id = R.drawable.dis_watch_wait),
                    contentDescription = "Waiting for teacher",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
        }
    } else {
        // Correct celebration image
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.dis_watch_correct),
                contentDescription = "Word complete - correct",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentScale = ContentScale.Fit
            )
        }
    }
}

/**
 * Feedback content for Learn Mode - briefly shows correct/wrong image,
 * then transitions to a waiting state until the teacher continues on the phone.
 */
@Composable
private fun LearnModeFeedbackContent(isCorrect: Boolean) {
    var showWaiting by remember { mutableStateOf(false) }

    // After 2 seconds, transition from feedback to waiting state
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(2000L)
        showWaiting = true
    }

    if (showWaiting) {
        // Waiting for teacher state
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(32.dp))

                Text(
                    text = "Waiting for teacher...",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Image(
                    painter = painterResource(id = R.drawable.dis_watch_wait),
                    contentDescription = "Waiting for teacher",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
        }
    } else {
        // Brief correct/wrong feedback flash
        Box(
            modifier = Modifier
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(
                    id = if (isCorrect) R.drawable.dis_watch_correct else R.drawable.dis_watch_wrong
                ),
                contentDescription = if (isCorrect) "Correct" else "Wrong",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentScale = ContentScale.Fit
            )
        }
    }
}

/**
 * Name the Picture mode content - letter-by-letter gesture input
 * Similar to Write the Word but does NOT show the next letter to input
 */
@Composable
private fun NameThePictureContent(
    wordData: LearnModeStateHolder.WordData,
    writeTheWordState: LearnModeStateHolder.WriteTheWordState,
    phoneCommunicationManager: PhoneCommunicationManager,
    showingFeedback: Boolean,
    feedbackIsCorrect: Boolean,
    feedbackPredictedLetter: String,
    isLastLetterCorrect: Boolean
) {
    val context = LocalContext.current

    // Initialize dependencies
    var isInitialized by remember { mutableStateOf(false) }
    var sensorManager by remember { mutableStateOf<MotionSensorManager?>(null) }
    var classifierResult by remember { mutableStateOf<ClassifierLoadResult?>(null) }

    // Initialize TextToSpeech manager
    val ttsManager = remember { TextToSpeechManager(context) }

    // Cleanup TTS when disposed
    DisposableEffect(Unit) {
        onDispose {
            ttsManager.shutdown()
        }
    }

    // Determine letter case from the actual current letter being written, not the first letter of the word
    val targetChar = wordData.word.getOrNull(writeTheWordState.currentLetterIndex)
    val letterCase = if (targetChar?.isUpperCase() == true) "uppercase" else "lowercase"

    // Track which model is currently loaded to prevent composing MainContent with a stale classifier.
    // When letterCase changes mid-word (e.g., "PiG": P->i), the LaunchedEffect hasn't run yet during
    // the first recomposition frame, so classifierResult still holds the old model. Without this guard,
    // MainContent would be composed and a ViewModel created with the wrong (soon-to-be-closed) classifier.
    val expectedModelKey = "$letterCase-${wordData.dominantHand}"
    var loadedModelKey by remember { mutableStateOf("") }

    // Load hand+case-specific model; reloads when letterCase or dominantHand changes
    LaunchedEffect(letterCase, wordData.dominantHand) {
        sensorManager = sensorManager ?: MotionSensorManager(context)
        // Close old model before loading new one
        (classifierResult as? ClassifierLoadResult.Success)?.classifier?.close()
        classifierResult = try {
            val modelConfig = ModelConfig.getModelForSession(letterCase, wordData.dominantHand)
            android.util.Log.d("LearnModeScreen", "Loading model: ${modelConfig.fileName}")
            ModelLoader.load(context, modelConfig)
        } catch (e: Exception) {
            ClassifierLoadResult.Error("Failed to load model: ${e.message}", e)
        }
        loadedModelKey = expectedModelKey
        isInitialized = true
    }

    if (!isInitialized || sensorManager == null || classifierResult == null || loadedModelKey != expectedModelKey) {
        // Loading state
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(40.dp),
                strokeWidth = 4.dp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Loading...",
                color = AppColors.TextSecondary,
                fontSize = 12.sp
            )
        }
    } else {
        NameThePictureMainContent(
            wordData = wordData,
            writeTheWordState = writeTheWordState,
            sensorManager = sensorManager!!,
            classifierResult = classifierResult!!,
            ttsManager = ttsManager,
            phoneCommunicationManager = phoneCommunicationManager,
            showingFeedback = showingFeedback,
            feedbackIsCorrect = feedbackIsCorrect,
            feedbackPredictedLetter = feedbackPredictedLetter,
            isLastLetterCorrect = isLastLetterCorrect
        )
    }
}

@Composable
private fun NameThePictureMainContent(
    wordData: LearnModeStateHolder.WordData,
    writeTheWordState: LearnModeStateHolder.WriteTheWordState,
    sensorManager: MotionSensorManager,
    classifierResult: ClassifierLoadResult,
    ttsManager: TextToSpeechManager,
    phoneCommunicationManager: PhoneCommunicationManager,
    showingFeedback: Boolean,
    feedbackIsCorrect: Boolean,
    feedbackPredictedLetter: String,
    isLastLetterCorrect: Boolean
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Current letter to input - keep exact case (uppercase or lowercase)
    val currentLetterIndex = writeTheWordState.currentLetterIndex
    val expectedLetter = wordData.word.getOrNull(currentLetterIndex)

    // Include currentLetterIndex in the key so each letter position gets a fresh ViewModel.
    // This prevents key collisions when the same case appears at different positions
    // (e.g., "PiG" — P at index 0 and G at index 2 are both uppercase but need separate ViewModels
    // because the classifier from index 0 gets closed when switching to lowercase for index 1).
    val viewModel: LearnModeViewModel = viewModel(
        factory = LearnModeViewModelFactory(
            sensorManager = sensorManager,
            classifierResult = classifierResult,
            onRecordingStarted = {
                scope.launch {
                    phoneCommunicationManager.sendLearnModeGestureRecording()
                }
            }
        ),
        key = "${wordData.word}-${wordData.maskedIndex}-${wordData.timestamp}-${currentLetterIndex}"
    )

    val uiState by viewModel.uiState.collectAsState()

    // Play countdown voice audio (3, 2, 1)
    LaunchedEffect(uiState.countdownSeconds) {
        val resId = when (uiState.countdownSeconds) {
            3 -> R.raw.voice_3
            2 -> R.raw.voice_2
            1 -> R.raw.voice_1
            else -> null
        }
        if (resId != null && uiState.state == LearnModeViewModel.State.COUNTDOWN) {
            val mp = MediaPlayer.create(context, resId)
            mp?.start()
            mp?.setOnCompletionListener { it.release() }
        }
    }

    // Play "go" voice when recording starts
    LaunchedEffect(uiState.state) {
        if (uiState.state == LearnModeViewModel.State.RECORDING) {
            val mp = MediaPlayer.create(context, R.raw.voice_go)
            mp?.start()
            mp?.setOnCompletionListener { it.release() }
        }
    }

    // Track last spoken prediction to avoid double TTS
    var lastSpokenPrediction by remember { mutableStateOf<String?>(null) }

    // Send letter input to phone when prediction is made
    LaunchedEffect(uiState.state, uiState.prediction) {
        if (uiState.state == LearnModeViewModel.State.SHOWING_PREDICTION && uiState.prediction != null) {
            // Only speak if we haven't already spoken this prediction
            if (lastSpokenPrediction != uiState.prediction) {
                lastSpokenPrediction = uiState.prediction
                if (uiState.prediction == "?") {
                    ttsManager.speak("Oops, you did not air write!")
                } else {
                    ttsManager.speakLetter(uiState.prediction!!)
                }
            }

            // Send letter input to phone for validation (preserve exact case)
            phoneCommunicationManager.sendLetterInput(uiState.prediction!!, currentLetterIndex)
        }
    }

    // Reset last spoken prediction when going back to idle
    LaunchedEffect(uiState.state) {
        if (uiState.state == LearnModeViewModel.State.IDLE) {
            lastSpokenPrediction = null
        }
    }

    // Reset ViewModel only when feedback is actually dismissed (true -> false),
    // not on initial composition when showingFeedback starts as false
    var previousShowingFeedback by remember { mutableStateOf(false) }
    LaunchedEffect(showingFeedback) {
        if (previousShowingFeedback && !showingFeedback) {
            viewModel.resetToIdle()
        }
        previousShowingFeedback = showingFeedback
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Show feedback based on word-complete vs per-letter
            if (showingFeedback) {
                if (writeTheWordState.isWordComplete || isLastLetterCorrect) {
                    WordCompleteFeedbackContent(ttsManager = ttsManager)
                } else {
                    PerLetterFeedbackContent(
                        prediction = feedbackPredictedLetter,
                        isCorrect = feedbackIsCorrect
                    )
                }
            } else {
                when (uiState.state) {
                    LearnModeViewModel.State.IDLE -> NameThePictureIdleContent(
                        wordData = wordData,
                        writeTheWordState = writeTheWordState,
                        viewModel = viewModel
                    )
                    LearnModeViewModel.State.COUNTDOWN -> CountdownContent(uiState)
                    LearnModeViewModel.State.RECORDING -> RecordingContent(uiState)
                    LearnModeViewModel.State.PROCESSING -> ProcessingContent()
                    LearnModeViewModel.State.NO_MOVEMENT -> { /* Unused in Learn mode */ }
                    LearnModeViewModel.State.SHOWING_PREDICTION -> ShowingPredictionContent(uiState)
                }
            }
        }
    }
}

/**
 * Idle content for Name the Picture mode - shows mascot image with letter progress
 * Completed letters are revealed in purple, pending letters show as underlines
 */
@Composable
private fun NameThePictureIdleContent(
    wordData: LearnModeStateHolder.WordData,
    writeTheWordState: LearnModeStateHolder.WriteTheWordState,
    viewModel: LearnModeViewModel
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { viewModel.startRecording() }
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))
            
            Text(
                text = "Tap to begin!",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            
            Image(
                painter = painterResource(id = R.drawable.dis_watch_wait),
                contentDescription = "Tap to start",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }
    }
}


