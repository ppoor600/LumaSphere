package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = CyberCyan,
    secondary = SolarAmber,
    tertiary = DeepCoral,
    background = DarkBackground,
    surface = CardSurface,
    onPrimary = Color(0xFF381E72),
    onSecondary = Color(0xFF1D192B),
    onTertiary = Color(0xFF601410),
    onBackground = PureWhite,
    onSurface = PureWhite,
    outline = BorderGray
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    // Explicitly enforce our rich dark creative typography theme
    // for professional, eye-safe VFX environments.
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
