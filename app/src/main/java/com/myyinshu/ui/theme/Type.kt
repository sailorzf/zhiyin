package com.myyinshu.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

fun buildTypography(fontSizeMultiplier: Float): Typography {
    return Typography(
        displayLarge = TextStyle(
            fontWeight = FontWeight.Bold,
            fontSize = (57 * fontSizeMultiplier).sp,
            lineHeight = (64 * fontSizeMultiplier).sp,
        ),
        displayMedium = TextStyle(
            fontWeight = FontWeight.Bold,
            fontSize = (45 * fontSizeMultiplier).sp,
            lineHeight = (52 * fontSizeMultiplier).sp,
        ),
        headlineLarge = TextStyle(
            fontWeight = FontWeight.Bold,
            fontSize = (32 * fontSizeMultiplier).sp,
            lineHeight = (40 * fontSizeMultiplier).sp,
        ),
        headlineMedium = TextStyle(
            fontWeight = FontWeight.SemiBold,
            fontSize = (28 * fontSizeMultiplier).sp,
            lineHeight = (36 * fontSizeMultiplier).sp,
        ),
        bodyLarge = TextStyle(
            fontWeight = FontWeight.Normal,
            fontSize = (16 * fontSizeMultiplier).sp,
            lineHeight = (24 * fontSizeMultiplier).sp,
        ),
        bodyMedium = TextStyle(
            fontWeight = FontWeight.Normal,
            fontSize = (14 * fontSizeMultiplier).sp,
            lineHeight = (20 * fontSizeMultiplier).sp,
        ),
        labelLarge = TextStyle(
            fontWeight = FontWeight.Medium,
            fontSize = (14 * fontSizeMultiplier).sp,
            lineHeight = (20 * fontSizeMultiplier).sp,
        ),
    )
}
