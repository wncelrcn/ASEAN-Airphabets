package com.example.app.ui.components.wordbank

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Word Bank Modal component for adding new words.
 * Displays a dialog with upload media area, word input, and add button.
 *
 * @param isVisible Whether the modal is currently visible
 * @param wordInput Current text in the word input field
 * @param selectedImageUri URI of the selected image (optional)
 * @param inputError Error message to display for word input, if any
 * @param imageError Error message for image upload, if any
 * @param isSubmitEnabled Whether the submit button is enabled
 * @param isLoading Whether the modal is in a loading state
 * @param onWordInputChanged Callback when word input changes
 * @param onMediaUploadClick Callback when upload media area is clicked
 * @param onRemoveImage Callback when remove image button is clicked
 * @param onAddClick Callback when "Add to Word Bank" button is clicked
 * @param onDismiss Callback when modal is dismissed
 * @param dictionarySuggestions List of suggested words from dictionary validation
 * @param onSuggestionClick Callback when a suggestion chip is clicked
 */
@Composable
fun WordBankModal(
    isVisible: Boolean,
    wordInput: String,
    selectedImageUri: Uri?,
    inputError: String?,
    imageError: String?,
    isSubmitEnabled: Boolean,
    isLoading: Boolean,
    onWordInputChanged: (String) -> Unit,
    onMediaUploadClick: () -> Unit,
    onRemoveImage: () -> Unit,
    onAddClick: () -> Unit,
    onDismiss: () -> Unit,
    dictionarySuggestions: List<String> = emptyList(),
    onSuggestionClick: (String) -> Unit = {}
) {
    if (!isVisible) return

    val focusManager = LocalFocusManager.current

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
                .wrapContentHeight()
                .clip(RoundedCornerShape(24.dp))
                .background(Color.White)
                .padding(32.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Media upload section
                MediaUploadSection(
                    selectedImageUri = selectedImageUri,
                    imageError = imageError,
                    isLoading = isLoading,
                    onMediaUploadClick = onMediaUploadClick,
                    onRemoveImage = onRemoveImage
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Word input section
                WordInputSection(
                    wordInput = wordInput,
                    inputError = inputError,
                    isLoading = isLoading,
                    isSubmitEnabled = isSubmitEnabled,
                    onWordInputChanged = onWordInputChanged,
                    onSubmit = {
                        focusManager.clearFocus()
                        onAddClick()
                    },
                    dictionarySuggestions = dictionarySuggestions,
                    onSuggestionClick = onSuggestionClick
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Submit button
                SubmitButton(
                    isLoading = isLoading,
                    isEnabled = isSubmitEnabled,
                    onClick = {
                        focusManager.clearFocus()
                        onAddClick()
                    }
                )
            }
        }
    }
}

/**
 * Media upload section containing either the upload area or image preview.
 */
@Composable
private fun MediaUploadSection(
    selectedImageUri: Uri?,
    imageError: String?,
    isLoading: Boolean,
    onMediaUploadClick: () -> Unit,
    onRemoveImage: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (selectedImageUri != null) {
            SelectedImagePreview(
                imageUri = selectedImageUri,
                onRemoveClick = onRemoveImage,
                enabled = !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
            )
        } else {
            UploadMediaArea(
                onClick = onMediaUploadClick,
                enabled = !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
            )
        }

        // Image error message
        if (imageError != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = imageError,
                fontSize = 12.sp,
                color = Color.Red,
                modifier = Modifier.align(Alignment.Start)
            )
        }
    }
}

/**
 * Word input section with label and text field.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WordInputSection(
    wordInput: String,
    inputError: String?,
    isLoading: Boolean,
    isSubmitEnabled: Boolean,
    onWordInputChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    dictionarySuggestions: List<String> = emptyList(),
    onSuggestionClick: (String) -> Unit = {}
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Word Label
        Text(
            text = "Word",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF0B0B0B)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Word Input Field
        OutlinedTextField(
            value = wordInput,
            onValueChange = onWordInputChanged,
            placeholder = {
                Text(
                    text = "E.g, dog",
                    color = Color.Gray
                )
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF49A9FF),
                unfocusedBorderColor = Color(0xFFE0E0E0),
                errorBorderColor = Color(0xFF49A9FF),
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                cursorColor = Color(0xFF49A9FF),
                disabledBorderColor = Color(0xFFE0E0E0),
                disabledContainerColor = Color(0xFFF5F5F5),
                focusedTextColor = Color(0xFF49A9FF),
                unfocusedTextColor = if (wordInput.isNotEmpty()) Color(0xFF49A9FF) else Color.Gray
            ),
            singleLine = true,
            isError = inputError != null,
            enabled = !isLoading,
            supportingText = if (inputError != null) {
                { Text(text = inputError, color = Color(0xFF49A9FF)) }
            } else null,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    if (isSubmitEnabled && !isLoading) {
                        onSubmit()
                    }
                }
            )
        )

        // Dictionary suggestion chips
        if (dictionarySuggestions.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Did you mean:",
                fontSize = 12.sp,
                color = Color.Gray,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(4.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                dictionarySuggestions.forEach { suggestion ->
                    SuggestionChip(
                        onClick = { onSuggestionClick(suggestion) },
                        label = {
                            Text(
                                text = suggestion,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        },
                        shape = RoundedCornerShape(20.dp),
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = Color(0xFFE8F4FD),
                            labelColor = Color(0xFF49A9FF)
                        ),
                        border = SuggestionChipDefaults.suggestionChipBorder(
                            enabled = true,
                            borderColor = Color(0xFF49A9FF),
                            borderWidth = 1.dp
                        )
                    )
                }
            }
        }
    }
}

/**
 * Submit button with loading state.
 */
@Composable
private fun SubmitButton(
    isLoading: Boolean,
    isEnabled: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = isEnabled && !isLoading,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(18.dp),
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
        } else {
            Text(
                text = "Add to Word Bank",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun WordBankModalPreview() {
    WordBankModal(
        isVisible = true,
        wordInput = "",
        selectedImageUri = null,
        inputError = null,
        imageError = null,
        isSubmitEnabled = false,
        isLoading = false,
        onWordInputChanged = {},
        onMediaUploadClick = {},
        onRemoveImage = {},
        onAddClick = {},
        onDismiss = {}
    )
}

@Preview(showBackground = true)
@Composable
fun WordBankModalWithInputPreview() {
    WordBankModal(
        isVisible = true,
        wordInput = "dog",
        selectedImageUri = null,
        inputError = null,
        imageError = null,
        isSubmitEnabled = true,
        isLoading = false,
        onWordInputChanged = {},
        onMediaUploadClick = {},
        onRemoveImage = {},
        onAddClick = {},
        onDismiss = {}
    )
}

@Preview(showBackground = true)
@Composable
fun WordBankModalLoadingPreview() {
    WordBankModal(
        isVisible = true,
        wordInput = "dog",
        selectedImageUri = null,
        inputError = null,
        imageError = null,
        isSubmitEnabled = true,
        isLoading = true,
        onWordInputChanged = {},
        onMediaUploadClick = {},
        onRemoveImage = {},
        onAddClick = {},
        onDismiss = {}
    )
}

