package com.example.airphabets.presentation.screens.mode

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import com.example.airphabets.presentation.theme.AppColors

/**
 * Screen displayed when a mode is selected
 */
@Composable
fun ModeScreen(modeName: String, modeColor: Color) {
    Scaffold {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(AppColors.Background),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = modeName,
                color = modeColor,
                fontSize = 24.sp,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Swipe right to go back",
                color = AppColors.TextPrimary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
    }
}

