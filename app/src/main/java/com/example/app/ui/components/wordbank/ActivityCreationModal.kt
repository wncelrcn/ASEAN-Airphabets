package com.example.app.ui.components.wordbank

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.app.R
import com.example.app.data.entity.Word
import com.example.app.data.repository.GenerationPhase

/**
 * Activity Creation Modal component for creating activities using the entire word bank.
 * Displays a dialog with activity input, suggested prompts, and magic creation button.
 * Shows an empty state when the word bank has no words.
 */
@Composable
fun ActivityCreationModal(
    isVisible: Boolean,
    activityInput: String,
    words: List<Word>,
    isLoading: Boolean,
    generationPhase: GenerationPhase = GenerationPhase.Idle,
    error: String? = null,
    suggestedPrompts: List<String> = emptyList(),
    isSuggestionsLoading: Boolean = false,
    onActivityInputChanged: (String) -> Unit,
    onSuggestionClick: (String) -> Unit = {},
    onCreateActivity: () -> Unit,
    onDismiss: () -> Unit,
    onBackClick: () -> Unit = onDismiss
) {
    if (!isVisible) return

    val focusManager = LocalFocusManager.current
    val isSubmitEnabled = activityInput.isNotBlank()
    val isWordBankEmpty = words.isEmpty()

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
                    .offset(x = (1).dp, y = 0.dp)
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
                    // Title
                    Text(
                        text = "Let Kuu Create your Activities!",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0B0B0B),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    if (isWordBankEmpty) {
                        // Empty word bank state
                        EmptyWordBankMessage()
                    } else {
                        // Activity input section
                        ActivityInputSection(
                            activityInput = activityInput,
                            isLoading = isLoading,
                            onActivityInputChanged = onActivityInputChanged,
                            onSubmit = {
                                focusManager.clearFocus()
                                if (isSubmitEnabled) {
                                    onCreateActivity()
                                }
                            }
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // Suggested prompts section
                        SuggestedPromptsSection(
                            suggestedPrompts = suggestedPrompts,
                            isSuggestionsLoading = isSuggestionsLoading,
                            isLoading = isLoading,
                            onSuggestionClick = onSuggestionClick
                        )

                        Spacer(modifier = Modifier.height(32.dp))

                        // Magic button
                        MagicButton(
                            isLoading = isLoading,
                            generationPhase = generationPhase,
                            isEnabled = isSubmitEnabled,
                            onClick = {
                                focusManager.clearFocus()
                                onCreateActivity()
                            }
                        )

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

/**
 * Activity input section with label and text field.
 */
@Composable
private fun ActivityInputSection(
    activityInput: String,
    isLoading: Boolean,
    onActivityInputChanged: (String) -> Unit,
    onSubmit: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Label
        Text(
            text = "What kind of Activity should Kuu Create?",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF0B0B0B),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Input Field
        OutlinedTextField(
            value = activityInput,
            onValueChange = onActivityInputChanged,
            textStyle = TextStyle(
                color = Color(0xFF49A9FF),
                fontSize = 16.sp
            ),
            placeholder = {
                Text(
                    text = "Describe your activity...",
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
                onDone = { onSubmit() }
            ),
            maxLines = 4
        )
    }
}

/**
 * Message shown when the word bank is empty.
 */
@Composable
private fun EmptyWordBankMessage() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Your Word Bank is empty!",
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF0B0B0B),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Add words to your Word Bank first to create activities.",
            fontSize = 14.sp,
            color = Color(0xFF888888),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Suggested prompts section with loading state and clickable chips.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SuggestedPromptsSection(
    suggestedPrompts: List<String>,
    isSuggestionsLoading: Boolean,
    isLoading: Boolean,
    onSuggestionClick: (String) -> Unit
) {
    if (!isSuggestionsLoading && suggestedPrompts.isEmpty()) return

    Text(
        text = "Not sure? Try these Activity Ideas!",
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium,
        color = Color(0xFF0B0B0B),
        textAlign = TextAlign.Center
    )

    Spacer(modifier = Modifier.height(12.dp))

    if (isSuggestionsLoading) {
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

/**
 * "Do the magic!" button with wand icon.
 */
@Composable
private fun MagicButton(
    isLoading: Boolean,
    generationPhase: GenerationPhase = GenerationPhase.Idle,
    isEnabled: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = isEnabled && !isLoading,
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
                    is GenerationPhase.Grouping -> "Grouping activities... (2/3)"
                    is GenerationPhase.Configuring -> "Configuring... (3/3)"
                    else -> "Generating..."
                },
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
        } else {
            // Wand icon
            Icon(
                painter = painterResource(id = R.drawable.ic_wand),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Generate Activity",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ActivityCreationModalPreview() {
    val sampleWords = listOf(
        Word(id = 1L, userId = 1L, word = "cat"),
        Word(id = 2L, userId = 1L, word = "dog"),
        Word(id = 3L, userId = 1L, word = "bat")
    )

    ActivityCreationModal(
        isVisible = true,
        activityInput = "",
        words = sampleWords,
        isLoading = false,
        suggestedPrompts = listOf(
            "Practice the -at word family with rhyming words",
            "Group words by short vowel sounds"
        ),
        onActivityInputChanged = {},
        onCreateActivity = {},
        onDismiss = {}
    )
}

@Preview(showBackground = true)
@Composable
fun ActivityCreationModalEmptyWordBankPreview() {
    ActivityCreationModal(
        isVisible = true,
        activityInput = "",
        words = emptyList(),
        isLoading = false,
        onActivityInputChanged = {},
        onCreateActivity = {},
        onDismiss = {}
    )
}

@Preview(showBackground = true)
@Composable
fun ActivityCreationModalLoadingPreview() {
    val sampleWords = listOf(
        Word(id = 1L, userId = 1L, word = "cat"),
        Word(id = 2L, userId = 1L, word = "dog")
    )

    ActivityCreationModal(
        isVisible = true,
        activityInput = "Create a story",
        words = sampleWords,
        isLoading = true,
        onActivityInputChanged = {},
        onCreateActivity = {},
        onDismiss = {}
    )
}
