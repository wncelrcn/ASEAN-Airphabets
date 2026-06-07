package com.example.app.ui.components.wordbank

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.IconButton
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.app.R
import com.example.app.data.repository.GenerationPhase
import androidx.compose.foundation.border
import androidx.compose.ui.text.style.TextOverflow

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WordBankGenerationModal(
    isVisible: Boolean,
    promptInput: String,
    isLoading: Boolean,
    generationPhase: GenerationPhase = GenerationPhase.Idle,
    error: String? = null,
    wordCount: Int = 5,
    onWordCountChanged: (Int) -> Unit = {},
    suggestedPrompts: List<String> = emptyList(),
    isSuggestionsLoading: Boolean = false,
    onSuggestionClick: (String) -> Unit = {},
    onPromptInputChanged: (String) -> Unit,
    onGenerate: () -> Unit,
    onDismiss: () -> Unit,
    onBackClick: () -> Unit = onDismiss,
    generatedWords: List<String> = emptyList(),
    requestedCount: Int = 0,
    onDone: () -> Unit = {}
) {
    if (!isVisible) return

    val focusManager = LocalFocusManager.current
    val isSubmitEnabled = promptInput.isNotBlank()
    val isSuccess = generatedWords.isNotEmpty() && !isLoading

    Dialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = !isLoading,
            dismissOnClickOutside = !isLoading,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .wrapContentHeight(),
            contentAlignment = Alignment.TopCenter
        ) {
            // Back button positioned outside the modal card at top-left
            IconButton(
                onClick = onBackClick,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = (1).dp, y = 26.5.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color(0xFF3FA9F8)
                )
            }

            // Main card content (positioned below the mascot)
            Column(
                modifier = Modifier
                    .padding(top = 80.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.White)
            ) {
                // Blue header section
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(70.dp)
                        .background(Color(0xFF49A9FF))
                )

                // White content section
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(top = 24.dp, bottom = 32.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (isSuccess) {
                        // === SUCCESS STATE ===
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Success",
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(48.dp)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "${generatedWords.size} words added!",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0B0B0B),
                            textAlign = TextAlign.Center
                        )

                        if (generatedWords.size < requestedCount) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Some words were duplicates or invalid and were skipped",
                                fontSize = 14.sp,
                                color = Color(0xFF888888),
                                textAlign = TextAlign.Center
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        generatedWords.forEach { word ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .background(
                                        color = Color(0xFFF0F7FF),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                Text(
                                    text = word,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF0B0B0B)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(32.dp))

                        Button(
                            onClick = onDone,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(28.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF49A9FF)
                            )
                        ) {
                            Text(
                                text = "Done",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }
                    } else {
                        // === INPUT STATE ===
                        // Title
                        Text(
                            text = "Let Kuu Generate CVC Words!",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0B0B0B),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // Prompt input section
                        Column(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "What kind of CVC words should Kuu create?",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF0B0B0B),
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedTextField(
                                value = promptInput,
                                onValueChange = onPromptInputChanged,
                                textStyle = TextStyle(
                                    color = Color(0xFF49A9FF),
                                    fontSize = 16.sp
                                ),
                                placeholder = {
                                    Text(
                                        text = "Describe what CVC words you want...",
                                        color = Color.Gray
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(120.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF49A9FF),
                                    unfocusedBorderColor = Color(0xFFE0E0E0),
                                    focusedContainerColor = Color.White,
                                    unfocusedContainerColor = Color.White,
                                    cursorColor = Color(0xFF49A9FF),
                                    disabledBorderColor = Color(0xFFE0E0E0),
                                    disabledContainerColor = Color(0xFFF5F5F5),
                                    focusedTextColor = Color(0xFF49A9FF),
                                    unfocusedTextColor = Color(0xFF49A9FF),
                                    disabledTextColor = Color(0xFF49A9FF).copy(alpha = 0.5f)
                                ),
                                enabled = !isLoading,
                                keyboardOptions = KeyboardOptions(
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = {
                                        focusManager.clearFocus()
                                        if (isSubmitEnabled) {
                                            onGenerate()
                                        }
                                    }
                                ),
                                maxLines = 4
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Word count stepper section
                        Text(
                            text = "How many words should Kuu generate?",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF0B0B0B),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Stepper row: minus, count display, plus
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Minus button
                            val minusEnabled = wordCount > 1 && !isLoading
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .shadow(2.dp, CircleShape)
                                    .background(
                                        color = if (minusEnabled) Color(0xFF49A9FF) else Color(0xFFB0D9FF),
                                        shape = CircleShape
                                    )
                                    .then(
                                        if (minusEnabled) Modifier.clickable {
                                            onWordCountChanged(wordCount - 1)
                                        } else Modifier
                                    )
                                    .semantics { contentDescription = "Decrease word count" },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "−",
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }

                            Spacer(modifier = Modifier.width(32.dp))

                            // Count display
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "$wordCount",
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0B0B0B)
                                )
                                Text(
                                    text = "words",
                                    fontSize = 14.sp,
                                    color = Color(0xFF888888)
                                )
                            }

                            Spacer(modifier = Modifier.width(32.dp))

                            // Plus button
                            val plusEnabled = wordCount < 20 && !isLoading
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .shadow(2.dp, CircleShape)
                                    .background(
                                        color = if (plusEnabled) Color(0xFF49A9FF) else Color(0xFFB0D9FF),
                                        shape = CircleShape
                                    )
                                    .then(
                                        if (plusEnabled) Modifier.clickable {
                                            onWordCountChanged(wordCount + 1)
                                        } else Modifier
                                    )
                                    .semantics { contentDescription = "Increase word count" },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "+",
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Preset chips
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            listOf(5, 10, 15, 20).forEach { preset ->
                                val isSelected = wordCount == preset
                                Box(
                                    modifier = Modifier
                                        .height(44.dp)
                                        .widthIn(min = 48.dp)
                                        .background(
                                            color = if (isSelected) Color(0xFF49A9FF) else Color(0xFFF0F0F0),
                                            shape = RoundedCornerShape(24.dp)
                                        )
                                        .then(
                                            if (!isLoading) Modifier.clickable {
                                                onWordCountChanged(preset)
                                            } else Modifier
                                        )
                                        .padding(horizontal = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "$preset",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (isSelected) Color.White else Color(0xFF666666)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Suggested prompts section
                        if (isSuggestionsLoading || suggestedPrompts.isNotEmpty()) {
                            Text(
                                text = "Need ideas? Start with these!",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF0B0B0B),
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            if (isSuggestionsLoading) {
                                // Loading spinner
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(60.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        color = Color(0xFF49A9FF),
                                        strokeWidth = 2.dp
                                    )
                                }
                            } else {
                                // Suggestion chips - horizontal flow rows
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    suggestedPrompts.forEach { prompt ->
                                        Box(
                                            modifier = Modifier
                                                .heightIn(min = 44.dp)
                                                .background(
                                                    color = Color(0xFFF0F7FF),
                                                    shape = RoundedCornerShape(16.dp)
                                                )
                                                .border(
                                                    width = 1.dp,
                                                    color = Color(0xFF49A9FF),
                                                    shape = RoundedCornerShape(16.dp)
                                                )
                                                .then(
                                                    if (!isLoading) Modifier.clickable {
                                                        onSuggestionClick(prompt)
                                                    } else Modifier
                                                )
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                            contentAlignment = Alignment.CenterStart
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    painter = painterResource(id = R.drawable.ic_wand),
                                                    contentDescription = null,
                                                    tint = Color(0xFF49A9FF),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = prompt,
                                                    fontSize = 14.sp,
                                                    color = Color(0xFF333333),
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(32.dp))

                        // Generate button
                        Button(
                            onClick = {
                                focusManager.clearFocus()
                                onGenerate()
                            },
                            enabled = isSubmitEnabled && !isLoading,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(28.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF49A9FF),
                                disabledContainerColor = Color(0xFFB0D9FF)
                            )
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = when (generationPhase) {
                                        is GenerationPhase.Filtering -> "Filtering words... (1/3)"
                                        is GenerationPhase.Grouping -> "Grouping sets... (2/3)"
                                        is GenerationPhase.Configuring -> "Configuring... (3/3)"
                                        else -> "Generating..."
                                    },
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White
                                )
                            } else {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_wand),
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Generate CVC Words",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                            }
                        }

                        // Error message
                        if (error != null) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = error,
                                color = Color.Red,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            // Mascot image overlapping the top
            Image(
                painter = painterResource(id = R.drawable.dis_wand_sit),
                contentDescription = "Kuu with wand",
                modifier = Modifier
                    .size(160.dp)
                    .offset(y = 0.dp),
                contentScale = ContentScale.Fit
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun WordBankGenerationModalPreview() {
    WordBankGenerationModal(
        isVisible = true,
        promptInput = "",
        isLoading = false,
        wordCount = 5,
        onWordCountChanged = {},
        suggestedPrompts = listOf(
            "Short vowel 'a' words like cat, bat, hat",
            "Animal-themed CVC words for beginners"
        ),
        isSuggestionsLoading = false,
        onSuggestionClick = {},
        onPromptInputChanged = {},
        onGenerate = {},
        onDismiss = {}
    )
}

@Preview(showBackground = true)
@Composable
fun WordBankGenerationModalWithInputPreview() {
    WordBankGenerationModal(
        isVisible = true,
        promptInput = "Animal-themed CVC words for beginners",
        isLoading = false,
        wordCount = 10,
        onWordCountChanged = {},
        suggestedPrompts = emptyList(),
        isSuggestionsLoading = false,
        onSuggestionClick = {},
        onPromptInputChanged = {},
        onGenerate = {},
        onDismiss = {}
    )
}

@Preview(showBackground = true)
@Composable
fun WordBankGenerationModalLoadingPreview() {
    WordBankGenerationModal(
        isVisible = true,
        promptInput = "Animal-themed CVC words",
        isLoading = true,
        wordCount = 15,
        onWordCountChanged = {},
        suggestedPrompts = emptyList(),
        isSuggestionsLoading = true,
        onSuggestionClick = {},
        onPromptInputChanged = {},
        onGenerate = {},
        onDismiss = {}
    )
}

@Preview(showBackground = true)
@Composable
fun WordBankGenerationModalSuccessPreview() {
    WordBankGenerationModal(
        isVisible = true,
        promptInput = "",
        isLoading = false,
        generatedWords = listOf("cat", "bat", "hat", "mat", "sat"),
        requestedCount = 5,
        onPromptInputChanged = {},
        onGenerate = {},
        onDismiss = {},
        onDone = {}
    )
}

@Preview(showBackground = true)
@Composable
fun WordBankGenerationModalPartialSuccessPreview() {
    WordBankGenerationModal(
        isVisible = true,
        promptInput = "",
        isLoading = false,
        generatedWords = listOf("cat", "bat", "hat"),
        requestedCount = 5,
        onPromptInputChanged = {},
        onGenerate = {},
        onDismiss = {},
        onDone = {}
    )
}
