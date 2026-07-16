package com.autodoctor.aipro.ui.design

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF2F80FF),
    secondary = Color(0xFF42D392),
    tertiary = Color(0xFFFFD166),
    background = Color(0xFF07111F),
    surface = Color(0xFF101827),
)

@Composable
fun AutoDoctorTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkScheme,
        content = content,
    )
}
