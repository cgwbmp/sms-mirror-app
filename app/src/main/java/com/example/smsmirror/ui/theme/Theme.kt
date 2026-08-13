package com.example.smsmirror.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Colours the M3 scheme has no slot for. Passed down explicitly so screens don't
 * hard-code hexes.
 */
data class StatusColors(
    val queued: Color,
    val queuedContainer: Color,
    val failed: Color,
    val failedContainer: Color,
)

val LocalStatusColors: ProvidableCompositionLocal<StatusColors> = staticCompositionLocalOf {
    StatusColors(Amber, AmberContainer, Danger, DangerContainer)
}

private val LightScheme = lightColorScheme(
    primary = SignalGreen,
    onPrimary = Color.White,
    primaryContainer = SignalGreenContainer,
    onPrimaryContainer = Color(0xFF06301B),
    secondary = InkSoft,
    onSecondary = Color.White,
    // NavigationBarItem draws its selected pill from secondaryContainer — leaving it
    // unset hands the most visible element on screen back to Material's default purple.
    secondaryContainer = SignalGreenContainer,
    onSecondaryContainer = Color(0xFF06301B),
    background = SurfaceLight,
    onBackground = Ink,
    surface = SurfaceLight,
    onSurface = Ink,
    surfaceVariant = Color(0xFFE6EEE9),
    onSurfaceVariant = InkSoft,
    surfaceContainer = SurfaceContainerLight,
    surfaceContainerHigh = Color(0xFFFFFFFF),
    outline = OutlineLight,
    outlineVariant = Color(0xFFE1E9E4),
    error = Danger,
    onError = Color.White,
    errorContainer = DangerContainer,
    onErrorContainer = Color(0xFF4A1113),
)

private val DarkScheme = darkColorScheme(
    primary = SignalGreenDark,
    onPrimary = Color(0xFF00311A),
    primaryContainer = SignalGreenContainerDark,
    onPrimaryContainer = Color(0xFFB4EBCC),
    secondary = InkSoftOnDark,
    onSecondary = Color(0xFF12201A),
    secondaryContainer = SignalGreenContainerDark,
    onSecondaryContainer = Color(0xFFB4EBCC),
    background = SurfaceDark,
    onBackground = InkOnDark,
    surface = SurfaceDark,
    onSurface = InkOnDark,
    surfaceVariant = Color(0xFF222D27),
    onSurfaceVariant = InkSoftOnDark,
    surfaceContainer = SurfaceContainerDark,
    surfaceContainerHigh = Color(0xFF1E2823),
    outline = OutlineDark,
    outlineVariant = Color(0xFF25302A),
    error = Color(0xFFFF8A8F),
    onError = Color(0xFF3A0A0C),
    errorContainer = DangerContainerDark,
    onErrorContainer = Color(0xFFFFD9DA),
)

@Composable
fun SmsMirrorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val statusColors = if (darkTheme) {
        StatusColors(Amber, AmberContainerDark, Color(0xFFFF8A8F), DangerContainerDark)
    } else {
        StatusColors(Amber, AmberContainer, Danger, DangerContainer)
    }

    CompositionLocalProvider(LocalStatusColors provides statusColors) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            typography = SmsMirrorTypography,
            content = content,
        )
    }
}
