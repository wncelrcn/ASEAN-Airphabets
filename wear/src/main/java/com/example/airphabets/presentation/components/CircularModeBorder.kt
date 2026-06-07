package com.example.airphabets.presentation.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A composable that wraps content with a circular border.
 * Used to visually indicate the current mode.
 *
 * @param borderColor The color of the circular border
 * @param borderWidth The width of the border stroke
 * @param content The content to be displayed inside the border
 */
@Composable
fun CircularModeBorder(
    borderColor: Color,
    borderWidth: Dp = 8.dp,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .border(
                width = borderWidth,
                color = borderColor,
                shape = CircleShape
            )
    ) {
        content()
    }
}

