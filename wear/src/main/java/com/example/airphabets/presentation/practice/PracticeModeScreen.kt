package com.example.airphabets.presentation.practice

import android.media.MediaPlayer
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.wear.compose.foundation.CurvedLayout
import androidx.wear.compose.material.curvedText
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.example.airphabets.R
import com.example.airphabets.ml.ClassifierLoadResult
import com.example.airphabets.ml.ModelConfig
import com.example.airphabets.ml.ModelLoader
import com.example.airphabets.presentation.theme.AppColors
import com.example.airphabets.sensors.MotionSensorManager
import com.example.airphabets.sensors.ShakeDetector
import com.example.airphabets.speech.TextToSpeechManager

/**
 * Practice Mode screen: countdown -> record gesture -> classify -> show result
 */
@Composable
fun PracticeModeScreen() {
    val context = LocalContext.current
    val view = LocalView.current

    // Use state to track if initialization is complete
    var isInitialized by remember { mutableStateOf(false) }
    var sensorManager by remember { mutableStateOf<MotionSensorManager?>(null) }
    var classifierResult by remember { mutableStateOf<ClassifierLoadResult?>(null) }

    // Initialize TextToSpeech manager
    val ttsManager = remember { TextToSpeechManager(context) }

    // Initialize ShakeDetector for repeating questions
    val shakeDetector = remember { ShakeDetector(context) }

    // Keep screen on during Practice Mode to prevent sleep during air writing
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose {
            view.keepScreenOn = false
            ttsManager.shutdown()
            shakeDetector.release()
        }
    }

    // Initialize dependencies in LaunchedEffect to avoid blocking composition
    LaunchedEffect(Unit) {
        sensorManager = MotionSensorManager(context)
        classifierResult = try {
            ModelLoader.load(context, ModelConfig.getPracticeModeModel())
        } catch (e: Exception) {
            ClassifierLoadResult.Error("Failed to load model: ${e.message}", e)
        }
        isInitialized = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .border(8.dp, AppColors.PracticeModeColor, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (!isInitialized || sensorManager == null || classifierResult == null) {
            // Show loading while initializing
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
            // Show main content once initialized
            PracticeModeContent(
                sensorManager = sensorManager!!,
                classifierResult = classifierResult!!,
                ttsManager = ttsManager,
                shakeDetector = shakeDetector
            )
        }
    }
}

@Composable
private fun PracticeModeContent(
    sensorManager: MotionSensorManager,
    classifierResult: ClassifierLoadResult,
    ttsManager: TextToSpeechManager,
    shakeDetector: ShakeDetector
) {
    val context = LocalContext.current
    val viewModel: PracticeModeViewModel = viewModel(
        factory = PracticeModeViewModelFactory(sensorManager, classifierResult)
    )

    val uiState by viewModel.uiState.collectAsState()

    // Playback guard: prevent tilt from re-triggering audio while playing
    var isAudioPlaying by remember { mutableStateOf(false) }
    var lastPlaybackEndTime by remember { mutableStateOf(0L) }
    var activeMediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    // Play countdown voice audio (3, 2, 1)
    LaunchedEffect(uiState.countdownSeconds) {
        val resId = when (uiState.countdownSeconds) {
            3 -> R.raw.voice_3
            2 -> R.raw.voice_2
            1 -> R.raw.voice_1
            else -> null
        }
        if (resId != null && uiState.state == PracticeModeViewModel.State.COUNTDOWN) {
            val mp = MediaPlayer.create(context, resId)
            mp?.start()
            mp?.setOnCompletionListener { it.release() }
        }
    }

    // Play "go" voice when recording starts
    LaunchedEffect(uiState.state) {
        if (uiState.state == PracticeModeViewModel.State.RECORDING) {
            val mp = MediaPlayer.create(context, R.raw.voice_go)
            mp?.start()
            mp?.setOnCompletionListener { it.release() }
        }
    }

    // Play the question audio or speak it with TTS when entering QUESTION state
    LaunchedEffect(uiState.state, uiState.currentQuestion) {
        if (uiState.state == PracticeModeViewModel.State.QUESTION && uiState.currentQuestion != null) {
            val question = uiState.currentQuestion!!
            isAudioPlaying = true
            if (question.audioResId != null) {
                // Play audio file if available
                try {
                    activeMediaPlayer?.release()
                    val mediaPlayer = MediaPlayer.create(context, question.audioResId)
                    activeMediaPlayer = mediaPlayer
                    mediaPlayer?.setOnCompletionListener {
                        it.release()
                        activeMediaPlayer = null
                        isAudioPlaying = false
                        lastPlaybackEndTime = System.currentTimeMillis()
                    }
                    mediaPlayer?.setOnErrorListener { mp, _, _ ->
                        mp.release()
                        activeMediaPlayer = null
                        isAudioPlaying = false
                        lastPlaybackEndTime = System.currentTimeMillis()
                        true
                    }
                    mediaPlayer?.start()
                } catch (e: Exception) {
                    // Fallback to TTS if audio playback fails
                    activeMediaPlayer = null
                    ttsManager.speak(question.question)
                    // isAudioPlaying stays true — TTS guard in shake listener will check ttsManager.isSpeaking()
                }
            } else {
                // Use TTS if no audio file
                ttsManager.speak(question.question)
                // isAudioPlaying stays true — TTS guard in shake listener will check ttsManager.isSpeaking()
            }
        } else {
            // Reset when leaving QUESTION state
            isAudioPlaying = false
        }
    }

    // Manage shake detector for repeating questions in QUESTION state
    DisposableEffect(uiState.state, uiState.currentQuestion) {
        if (uiState.state == PracticeModeViewModel.State.QUESTION && uiState.currentQuestion != null) {
            // Set up shake listener to repeat the question
            shakeDetector.setOnShakeListener {
                // Guard: ignore tilt if audio is playing or within post-playback cooldown
                if (isAudioPlaying || ttsManager.isSpeaking()) return@setOnShakeListener
                if (System.currentTimeMillis() - lastPlaybackEndTime < 2500) return@setOnShakeListener

                uiState.currentQuestion?.let { question ->
                    isAudioPlaying = true
                    if (question.audioResId != null) {
                        // Play audio file if available
                        try {
                            activeMediaPlayer?.release()
                            val mediaPlayer = MediaPlayer.create(context, question.audioResId)
                            activeMediaPlayer = mediaPlayer
                            mediaPlayer?.setOnCompletionListener {
                                it.release()
                                activeMediaPlayer = null
                                isAudioPlaying = false
                                lastPlaybackEndTime = System.currentTimeMillis()
                            }
                            mediaPlayer?.setOnErrorListener { mp, _, _ ->
                                mp.release()
                                activeMediaPlayer = null
                                isAudioPlaying = false
                                lastPlaybackEndTime = System.currentTimeMillis()
                                true
                            }
                            mediaPlayer?.start()
                        } catch (e: Exception) {
                            // Fallback to TTS if audio playback fails
                            activeMediaPlayer = null
                            ttsManager.speak(question.question)
                        }
                    } else {
                        // Use TTS if no audio file
                        ttsManager.speak(question.question)
                    }
                }
            }
            shakeDetector.startListening()
        } else {
            // Stop listening when not in QUESTION state
            shakeDetector.stopListening()
        }

        onDispose {
            shakeDetector.stopListening()
            activeMediaPlayer?.release()
            activeMediaPlayer = null
            isAudioPlaying = false
        }
    }

    // Random affirmations for correct and wrong answers
    val correctAffirmations = remember {
        listOf(
            "You are Correct!",
            "Great job!",
            "Awesome!",
            "Well done!",
            "Perfect!",
            "Excellent!",
            "Amazing!",
            "You got it!",
            "Fantastic!",
            "Super!"
        )
    }

    val wrongAffirmations = remember {
        listOf(
            "Try again!",
            "Not quite, try again!",
            "Almost there!",
            "Keep trying!",
            "You can do it!",
            "Give it another shot!",
            "Let's try once more!",
            "Don't give up!"
        )
    }

    // Track last spoken prediction to avoid double TTS
    var lastSpokenPrediction by remember { mutableStateOf<String?>(null) }

    // Speak the predicted letter when entering SHOWING_PREDICTION state
    LaunchedEffect(uiState.state, uiState.prediction) {
        if (uiState.state == PracticeModeViewModel.State.SHOWING_PREDICTION && uiState.prediction != null) {
            if (lastSpokenPrediction != uiState.prediction) {
                lastSpokenPrediction = uiState.prediction
                ttsManager.speakLetter(uiState.prediction!!)
            }
        }
    }

    // Reset last spoken prediction when starting a new attempt
    LaunchedEffect(uiState.state) {
        if (uiState.state == PracticeModeViewModel.State.IDLE ||
            uiState.state == PracticeModeViewModel.State.COUNTDOWN) {
            lastSpokenPrediction = null
        }
    }

    // Speak "oops" message when entering NO_MOVEMENT state
    LaunchedEffect(uiState.state) {
        if (uiState.state == PracticeModeViewModel.State.NO_MOVEMENT) {
            ttsManager.speak("Oops, you did not air write!")
        }
    }

    // Speak feedback when we enter RESULT state
    LaunchedEffect(uiState.state, uiState.isAnswerCorrect) {
        if (uiState.state == PracticeModeViewModel.State.RESULT) {
            val isCorrect = uiState.isAnswerCorrect == true
            if (isCorrect) {
                // Play random correct audio file
                val correctAudioFiles = listOf(
                    R.raw.correct_keep_it_up,
                    R.raw.correct_you_nailed_it,
                    R.raw.correct_impressive,
                    R.raw.correct_way_to_go,
                    R.raw.correct_smart_thinking,
                    R.raw.correct_thats_correct,
                    R.raw.correct_youre_improving,
                    R.raw.correct_outstanding,
                    R.raw.correct_perfect,
                    R.raw.correct_nice_work,
                    R.raw.correct_brilliant,
                    R.raw.correct_you_did_it,
                    R.raw.correct_fantastic,
                    R.raw.correct_awesome,
                    R.raw.correct_excellent,
                    R.raw.correct_well_done,
                    R.raw.correct_great_work,
                    R.raw.correct_good_job,
                    R.raw.correct_amazing_effort
                )
                val randomAudio = correctAudioFiles.random()
                try {
                    val mediaPlayer = MediaPlayer.create(context, randomAudio)
                    mediaPlayer?.start()
                    mediaPlayer?.setOnCompletionListener {
                        it.release()
                    }
                } catch (e: Exception) {
                    // Fallback to TTS if audio playback fails
                    ttsManager.speak(correctAffirmations.random())
                }
            } else {
                // Play random wrong audio file
                val wrongAudioFiles = listOf(
                    R.raw.wrong_keep_pushing_forward,
                    R.raw.wrong_lets_try_again,
                    R.raw.wrong_think_it_through,
                    R.raw.wrong_keep_practicing,
                    R.raw.wrong_mistakes_help_us_learn,
                    R.raw.wrong_try_one_more_time,
                    R.raw.wrong_take_your_time,
                    R.raw.wrong_practice_makes_perfect,
                    R.raw.wrong_give_it_another_shot,
                    R.raw.wrong_keep_trying
                )
                val randomAudio = wrongAudioFiles.random()
                try {
                    val mediaPlayer = MediaPlayer.create(context, randomAudio)
                    mediaPlayer?.start()
                    mediaPlayer?.setOnCompletionListener {
                        it.release()
                    }
                } catch (e: Exception) {
                    // Fallback to TTS if audio playback fails
                    ttsManager.speak(wrongAffirmations.random())
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        when (uiState.state) {
            PracticeModeViewModel.State.IDLE -> IdleContent(uiState, viewModel)
            PracticeModeViewModel.State.QUESTION -> QuestionContent(uiState, viewModel)
            PracticeModeViewModel.State.COUNTDOWN -> CountdownContent(uiState, viewModel)
            PracticeModeViewModel.State.RECORDING -> RecordingContent(uiState, viewModel)
            PracticeModeViewModel.State.PROCESSING -> ProcessingContent(uiState)
            PracticeModeViewModel.State.NO_MOVEMENT -> NoMovementContent(viewModel)
            PracticeModeViewModel.State.SHOWING_PREDICTION -> PredictionContent(uiState)
            PracticeModeViewModel.State.RESULT -> ResultContent(uiState, viewModel)
        }
    }
}

@Composable
private fun IdleContent(
    uiState: PracticeModeViewModel.UiState,
    viewModel: PracticeModeViewModel
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                indication = null,
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            ) { viewModel.startRecording() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // "Tap to Start" text
            Text(
                text = "Tap to Start",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 32.dp)
            )

            // Practice mascot image
            Image(
                painter = painterResource(id = R.drawable.dis_practice),
                contentDescription = "Practice mascot",
                modifier = Modifier
                    .size(150.dp)
                    .padding(bottom = 32.dp),
                contentScale = ContentScale.Fit
            )

            // Show score if any questions have been answered
            if (uiState.questionsAnswered > 0) {
                Text(
                    text = "Score: ${uiState.correctAnswers}/${uiState.questionsAnswered}",
                    color = AppColors.TextSecondary,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    // Show error message if present
    uiState.errorMessage?.let { error ->
        Spacer(modifier = Modifier.height(8.dp))
        ErrorBox(error)
    }
}

@Composable
private fun QuestionContent(
    uiState: PracticeModeViewModel.UiState,
    viewModel: PracticeModeViewModel
) {
    val question = uiState.currentQuestion
    val isPictureMatch = question?.category == QuestionCategory.PICTURE_MATCH
    val isTracingCopying = question?.category == QuestionCategory.TRACING_COPYING
    val isUppercaseLowercase = question?.category == QuestionCategory.UPPERCASE_LOWERCASE
    val isLetterSound = question?.category == QuestionCategory.LETTER_SOUND
    val emoji = question?.emoji

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                indication = null,
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            ) { viewModel.startAnswering() },
        contentAlignment = Alignment.Center
    ) {
        // Curved question text along the top arc of the watch face
        if (question != null) {
            CurvedLayout(anchor = 270f) {
                curvedText(
                    text = question.question,
                    fontSize = 12.sp,
                    color = Color.White
                )
            }
        }

        if (isPictureMatch && emoji != null) {
            // For Picture Match: Show ONLY the emoji centered, no text
            Text(
                text = emoji,
                fontSize = 80.sp,
                textAlign = TextAlign.Center
            )
        } else if (isTracingCopying || isUppercaseLowercase) {
            // For Tracing & Copying and Uppercase/Lowercase: Show ONLY the letter centered, no text
            Text(
                text = question?.expectedAnswer ?: "",
                color = AppColors.PracticeModeColor,
                fontSize = 80.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        } else if (isLetterSound) {
            // For Letter Sound Match (Phonics): Show ONLY the question avatar
            Image(
                painter = painterResource(id = R.drawable.dis_question),
                contentDescription = "Question avatar",
                modifier = Modifier.size(120.dp),
                contentScale = ContentScale.Fit
            )
        } else {
            // For other categories: Show question text with expected letter
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(16.dp)
            ) {
                // Question text
                Text(
                    text = question?.question ?: "",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Expected letter (hint)
                Text(
                    text = question?.expectedAnswer ?: "",
                    color = AppColors.PracticeModeColor,
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Tap to continue instruction
                Text(
                    text = "Tap to answer",
                    color = AppColors.TextSecondary,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun CountdownContent(
    uiState: PracticeModeViewModel.UiState,
    viewModel: PracticeModeViewModel
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "${uiState.countdownSeconds}",
            color = AppColors.PracticeModeColor,
            fontSize = 80.sp,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.display1
        )
    }
}

@Composable
private fun RecordingContent(
    uiState: PracticeModeViewModel.UiState,
    viewModel: PracticeModeViewModel
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            progress = uiState.recordingProgress,
            modifier = Modifier.fillMaxSize(),
            strokeWidth = 8.dp,
            indicatorColor = AppColors.PracticeModeColor
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
private fun ProcessingContent(uiState: PracticeModeViewModel.UiState) {
    CircularProgressIndicator(
        modifier = Modifier.size(60.dp),
        strokeWidth = 6.dp
    )

    Spacer(modifier = Modifier.height(16.dp))

    Text(
        text = uiState.statusMessage,
        color = AppColors.TextPrimary,
        fontSize = 14.sp,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun PredictionContent(uiState: PracticeModeViewModel.UiState) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Show the user's predicted letter in white
        Text(
            text = uiState.prediction ?: "?",
            color = Color.White,
            fontSize = 80.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ResultContent(
    uiState: PracticeModeViewModel.UiState,
    viewModel: PracticeModeViewModel
) {
    val isCorrect = uiState.isAnswerCorrect == true

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { viewModel.resetToIdle() },
        contentAlignment = Alignment.Center
    ) {
        // Show correct or wrong mascot image based on result
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

@Composable
private fun NoMovementContent(
    viewModel: PracticeModeViewModel
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

@Composable
private fun ErrorBox(message: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .background(Color.DarkGray, RoundedCornerShape(4.dp))
            .padding(8.dp)
    ) {
        Text(
            text = "⚠️ Error",
            color = Color.Red,
            fontSize = 10.sp
        )
        Text(
            text = message,
            color = Color.Yellow,
            fontSize = 9.sp,
            lineHeight = 11.sp,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
