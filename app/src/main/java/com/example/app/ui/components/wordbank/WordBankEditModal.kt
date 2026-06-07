package com.example.app.ui.components.wordbank

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.example.app.ui.components.DeleteConfirmationDialog
import com.example.app.ui.components.DeleteType
import java.io.File

/**
 * Word Bank Edit Modal component for viewing, editing, and deleting existing words.
 * Displays a dialog with the word's image/upload area, editable word text, and action buttons.
 *
 * @param isVisible Whether the modal is currently visible
 * @param wordInput Current text in the word input field
 * @param existingImagePath Path to the word's existing image (null if no image)
 * @param selectedImageUri URI of newly selected image for update (null if no new selection)
 * @param inputError Error message to display for word input, if any
 * @param imageError Error message for image upload, if any
 * @param isSaveEnabled Whether the save button is enabled
 * @param isLoading Whether the modal is in a loading state
 * @param onWordInputChanged Callback when word input changes
 * @param onMediaUploadClick Callback when upload/update media area is clicked
 * @param onRemoveImage Callback when remove image button is clicked
 * @param onSaveClick Callback when "Save" button is clicked
 * @param onDeleteClick Callback when "Delete" button is clicked
 * @param onDismiss Callback when modal is dismissed
 */
@Composable
fun WordBankEditModal(
    isVisible: Boolean,
    wordInput: String,
    existingImagePath: String?,
    selectedImageUri: Uri?,
    inputError: String?,
    imageError: String?,
    isSaveEnabled: Boolean,
    isLoading: Boolean,
    onWordInputChanged: (String) -> Unit,
    onMediaUploadClick: () -> Unit,
    onRemoveImage: () -> Unit,
    onSaveClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onDismiss: () -> Unit,
    dictionarySuggestions: List<String> = emptyList(),
    onSuggestionClick: (String) -> Unit = {}
) {
    if (!isVisible) return

    val focusManager = LocalFocusManager.current
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    // Delete confirmation dialog
    DeleteConfirmationDialog(
        isVisible = showDeleteConfirmation,
        deleteType = DeleteType.WORD,
        onConfirm = {
            showDeleteConfirmation = false
            onDeleteClick()
        },
        onDismiss = { showDeleteConfirmation = false }
    )

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
                // Media section - shows existing image or upload area
                EditMediaSection(
                    existingImagePath = existingImagePath,
                    selectedImageUri = selectedImageUri,
                    imageError = imageError,
                    isLoading = isLoading,
                    onMediaUploadClick = onMediaUploadClick,
                    onRemoveImage = onRemoveImage
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Word input section
                EditWordInputSection(
                    wordInput = wordInput,
                    inputError = inputError,
                    isLoading = isLoading,
                    isSaveEnabled = isSaveEnabled,
                    onWordInputChanged = onWordInputChanged,
                    onSubmit = {
                        focusManager.clearFocus()
                        onSaveClick()
                    },
                    dictionarySuggestions = dictionarySuggestions,
                    onSuggestionClick = onSuggestionClick
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Action buttons row
                ActionButtonsRow(
                    isLoading = isLoading,
                    isSaveEnabled = isSaveEnabled,
                    onSaveClick = {
                        focusManager.clearFocus()
                        onSaveClick()
                    },
                    onDeleteClick = { showDeleteConfirmation = true }
                )
            }
        }
    }
}

/**
 * Media section for editing - shows existing image, new selection, or upload area.
 */
@Composable
private fun EditMediaSection(
    existingImagePath: String?,
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
        when {
            // Show newly selected image if available
            selectedImageUri != null -> {
                SelectedImagePreview(
                    imageUri = selectedImageUri,
                    onRemoveClick = onRemoveImage,
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                )
            }
            // Show existing image from file path
            existingImagePath != null -> {
                ExistingImagePreview(
                    imagePath = existingImagePath,
                    onUpdateClick = onMediaUploadClick,
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                )
            }
            // Show upload area if no image
            else -> {
                UploadMediaArea(
                    onClick = onMediaUploadClick,
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                )
            }
        }

        // Image error message
        if (imageError != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = imageError,
                fontSize = 12.sp,
                color = Color(0xFF49A9FF),
                modifier = Modifier.align(Alignment.Start)
            )
        }
    }
}

/**
 * Word input section for editing with label and text field.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditWordInputSection(
    wordInput: String,
    inputError: String?,
    isLoading: Boolean,
    isSaveEnabled: Boolean,
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
                    if (isSaveEnabled && !isLoading) {
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
 * Row containing Save and Delete action buttons.
 */
@Composable
private fun ActionButtonsRow(
    isLoading: Boolean,
    isSaveEnabled: Boolean,
    onSaveClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Save button
        Button(
            onClick = onSaveClick,
            enabled = isSaveEnabled && !isLoading,
            modifier = Modifier
                .weight(1f)
                .height(56.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF49A9FF),
                disabledContainerColor = Color(0xFFB0D9FF)
            )
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Text(
                    text = "Save",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
        }

        // Delete button
        Button(
            onClick = onDeleteClick,
            enabled = !isLoading,
            modifier = Modifier
                .weight(1f)
                .height(56.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Red,
                disabledContainerColor = Color(0xFFFFB0B0)
            )

        ) {
            Text(
                text = "Delete",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }
    }
}

/**
 * Preview showing an existing image with update option.
 */
@Composable
fun ExistingImagePreview(
    imagePath: String,
    onUpdateClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val file = File(imagePath)
    val uri = if (file.exists()) Uri.fromFile(file) else null

    if (uri != null) {
        // Reuse SelectedImagePreview but with "Update" action
        SelectedImagePreviewWithUpdate(
            imageUri = uri,
            onUpdateClick = onUpdateClick,
            enabled = enabled,
            modifier = modifier
        )
    } else {
        // File doesn't exist, show upload area
        UploadMediaArea(
            onClick = onUpdateClick,
            enabled = enabled,
            modifier = modifier
        )
    }
}

/**
 * Image preview with update button instead of remove.
 */
@Composable
private fun SelectedImagePreviewWithUpdate(
    imageUri: Uri,
    onUpdateClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFFE8F4FD)),
        contentAlignment = Alignment.Center
    ) {
        // Display the image
        coil.compose.AsyncImage(
            model = imageUri,
            contentDescription = "Selected media",
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(16.dp)),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop
        )

        // Update button overlay at top-right
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
        ) {
            Button(
                onClick = onUpdateClick,
                enabled = enabled,
                modifier = Modifier.height(32.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF49A9FF),
                    disabledContainerColor = Color(0xFFB0D9FF)
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "Update Media",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun WordBankEditModalPreview() {
    WordBankEditModal(
        isVisible = true,
        wordInput = "Dog",
        existingImagePath = null,
        selectedImageUri = null,
        inputError = null,
        imageError = null,
        isSaveEnabled = true,
        isLoading = false,
        onWordInputChanged = {},
        onMediaUploadClick = {},
        onRemoveImage = {},
        onSaveClick = {},
        onDeleteClick = {},
        onDismiss = {}
    )
}

