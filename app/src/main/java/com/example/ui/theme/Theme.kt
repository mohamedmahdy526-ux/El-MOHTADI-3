package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val LocalIsDarkMode = staticCompositionLocalOf { true }

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF4ADE80),       // BrightGreen in Dark Mode
    onPrimary = Color.Black,
    secondary = Color(0xFF22C55E),     // SoftGreenAccent in Dark Mode
    onSecondary = Color.White,
    background = Color(0xFF0A0A0A),    // CharcoalBg in Dark Mode
    onBackground = Color(0xFFF5F5F5),  // TextWhite in Dark Mode
    surface = Color(0xFF161616),       // CardSurface in Dark Mode
    onSurface = Color(0xFFF5F5F5),
    surfaceVariant = Color(0xFF282828),
    onSurfaceVariant = Color(0xFF94A3B8), // TextGray in Dark Mode
    outline = Color(0x12FFFFFF),       // SmoothBorder in Dark Mode
    error = Color(0xFFF87171),         // AbsentColor in Dark Mode
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF16A34A),       // Vibrant Green for better reading in Light Mode
    onPrimary = Color.White,
    secondary = Color(0xFF15803D),     // Soft dark green Accent
    onSecondary = Color.White,
    background = Color(0xFFF8FAFC),    // Beautiful light slate bg
    onBackground = Color(0xFF0F172A),  // Dark slate text
    surface = Color(0xFFFFFFFF),       // Pure white cards with elevation
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF64748B),
    outline = Color(0x1A000000),       // Darker subtle margin border
    error = Color(0xFFEF4444),         // Clear red for absent
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    isDarkMode: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (isDarkMode) DarkColorScheme else LightColorScheme

    CompositionLocalProvider(
        LocalIsDarkMode provides isDarkMode,
        androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Rtl
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
