package com.example.aitalkdemo.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Defines a simple typography scheme for the AI talk demo. If you wish to use
 * custom fonts (e.g. Inter or Montserrat) you can add the font files under
 * `res/font/` and refer to them here via `FontFamily`.
 */
val Typography = Typography(
    headlineMedium = TextStyle(
        fontFamily   = FontFamily.Default,
        fontWeight   = FontWeight.Bold,
        fontSize     = 24.sp,
        letterSpacing = 0.0.sp
    ),
    bodyLarge = TextStyle(
        fontFamily   = FontFamily.Default,
        fontWeight   = FontWeight.Normal,
        fontSize     = 16.sp,
        letterSpacing = 0.25.sp
    )
)
