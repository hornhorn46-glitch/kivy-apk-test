package com.autodoctor.aipro.ui.design

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFB7FF2A),
    secondary = Color(0xFF27F4FF),
    tertiary = Color(0xFFFF2D1F),
    background = Color(0xFF050506),
    surface = Color(0xFF141518),
    onPrimary = Color(0xFF050506),
    onSecondary = Color(0xFF050506),
    onSurface = Color.White,
)

@Composable
fun AutoDoctorTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkScheme,
        content = content,
    )
}
