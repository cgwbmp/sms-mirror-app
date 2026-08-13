package com.example.smsmirror.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp

val SmsMirrorTypography = Typography(
    headlineMedium = Typography().headlineMedium.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = Typography().titleMedium.copy(fontWeight = FontWeight.SemiBold),
)

/**
 * Monospace for every piece of telecom data — phone numbers, timestamps, counters.
 * They line up in columns, which is what this data wants, and it gives the app a quiet
 * technical character without any custom font to ship.
 */
val DataStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 14.sp,
    letterSpacing = 0.sp,
)

/** Big tabular numbers for the counters row. */
val StatNumberStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 28.sp,
    fontWeight = FontWeight.Medium,
    textAlign = TextAlign.Start,
)
