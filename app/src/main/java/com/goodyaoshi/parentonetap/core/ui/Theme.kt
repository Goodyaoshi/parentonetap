package com.goodyaoshi.parentonetap.core.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
    primary = Color(0xFF1B8A3A),
    background = Color(0xFFF6F8F6),
    surface = Color(0xFFF6F8F6)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF4CC274),
    background = Color(0xFF101410),
    surface = Color(0xFF101410)
)

// 老年友好字号：全局 26sp 起步、高对比（方案 3.1）
private val AppTypography = Typography(
    displaySmall = TextStyle(fontSize = 40.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold),
    bodyLarge = TextStyle(fontSize = 26.sp),
    bodyMedium = TextStyle(fontSize = 24.sp),
    bodySmall = TextStyle(fontSize = 22.sp),
    labelLarge = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Medium)
)

@Composable
fun ParentOneTapTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content
    )
}
