package com.smartgnss.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Light, professional field-survey palette (matches the approved design).
private val Blue = Color(0xFF2F6BED)
private val BlueDark = Color(0xFF2458CC)
private val BlueSoft = Color(0xFFEAF1FE)
private val Green = Color(0xFF1FB768)

private val LightColors = lightColorScheme(
    primary = Blue,
    onPrimary = Color.White,
    primaryContainer = BlueSoft,
    onPrimaryContainer = Color(0xFF10367F),
    secondary = Green,
    onSecondary = Color.White,
    tertiary = BlueDark,
    background = Color(0xFFEEF1F5),
    onBackground = Color(0xFF1A2330),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A2330),
    surfaceVariant = Color(0xFFF1F5FB),
    onSurfaceVariant = Color(0xFF6B7889),
    outline = Color(0xFFD6DDE7),
    error = Color(0xFFEF4746),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF6FA2FF),
    onPrimary = Color(0xFF07234F),
    primaryContainer = Color(0xFF1E4488),
    onPrimaryContainer = BlueSoft,
    secondary = Color(0xFF3FD98C),
    onSecondary = Color(0xFF00281A),
    tertiary = Color(0xFFBCD0F5),
    background = Color(0xFF0E1218),
    onBackground = Color(0xFFE4E8EE),
    surface = Color(0xFF161B22),
    onSurface = Color(0xFFE4E8EE),
    surfaceVariant = Color(0xFF222A33),
    onSurfaceVariant = Color(0xFFABB4C0),
    outline = Color(0xFF333B45),
    error = Color(0xFFFF8A82),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Semantic status colours shared across screens. */
object SurveyColors {
    val fixedGreen = Color(0xFF1FB768)
    val floatAmber = Color(0xFFF3A712)
    val dgpsBlue = Color(0xFF2F6BED)
    val singleGray = Color(0xFF8792A0)
    val noFixRed = Color(0xFFEF4746)
    val brandBlue = Blue
    val brandBlueDark = BlueDark
}

@Composable
fun SmartGnssTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        shapes = AppShapes,
        content = content,
    )
}
