package com.example.aitalkdemo.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Central color definitions for the AI talk demo app.
 *
 * The palette leans heavily on purples and blues to mirror the gradient used on the
 * GitHub Pages demo. Additional semantic colours (e.g. danger) are also defined.
 */
object DemoColors {
    // Gradient start and end colours used for the background
    val GradientStart = Color(0xFF4e54c8)
    val GradientEnd   = Color(0xFF8f94fb)

    // Primary accent colour for buttons and highlights
    val Primary = Color(0xFF715AFF)

    // Danger colour used when the record button is active
    val Danger = Color(0xFFD32F2F)

    // On-surface colour for dark text on light backgrounds
    val OnSurface = Color(0xFF212121)
}
