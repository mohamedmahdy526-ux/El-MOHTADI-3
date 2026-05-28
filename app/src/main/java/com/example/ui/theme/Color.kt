package com.example.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush

// Sophisticated Dark-Mode and elegant Light-Mode Counterpart (al-Mhtdi Brand)

val CharcoalBg: Color
    @Composable
    get() = if (LocalIsDarkMode.current) Color(0xFF0A0A0A) else Color(0xFFF8FAFC)

val CardSurface: Color
    @Composable
    get() = if (LocalIsDarkMode.current) Color(0xFF161616) else Color(0xFFFFFFFF)

val BrightGreen: Color
    @Composable
    get() = if (LocalIsDarkMode.current) Color(0xFF4ADE80) else Color(0xFF16A34A)

val SoftGreenAccent: Color
    @Composable
    get() = if (LocalIsDarkMode.current) Color(0xFF22C55E) else Color(0xFF15803D)

val SmoothBorder: Color
    @Composable
    get() = if (LocalIsDarkMode.current) Color(0x12FFFFFF) else Color(0x1A000000)

val TextWhite: Color
    @Composable
    get() = if (LocalIsDarkMode.current) Color(0xFFF5F5F5) else Color(0xFF0F172A)

val TextGray: Color
    @Composable
    get() = if (LocalIsDarkMode.current) Color(0xFF94A3B8) else Color(0xFF64748B)

val AccentGold: Color
    @Composable
    get() = if (LocalIsDarkMode.current) Color(0xFFFBBF24) else Color(0xFFD97706)

val PremiumCardGradient: Brush
    @Composable
    get() = if (LocalIsDarkMode.current) {
        Brush.linearGradient(colors = listOf(Color(0xFF1A1A1A), Color(0xFF111111)))
    } else {
        Brush.linearGradient(colors = listOf(Color(0xFFFFFEFE), Color(0xFFF1F5F9)))
    }

val LightGreenBox: Color
    @Composable
    get() = if (LocalIsDarkMode.current) Color(0x1F4ADE80) else Color(0x1F16A34A)

val TransparentGreen: Color
    @Composable
    get() = if (LocalIsDarkMode.current) Color(0x124ADE80) else Color(0x1216A34A)

val PresentColor: Color
    @Composable
    get() = if (LocalIsDarkMode.current) Color(0xFF4ADE80) else Color(0xFF16A34A)

val AbsentColor: Color
    @Composable
    get() = if (LocalIsDarkMode.current) Color(0xFFF87171) else Color(0xFFEF4444)
