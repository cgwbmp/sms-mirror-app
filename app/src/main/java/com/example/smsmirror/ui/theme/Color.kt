package com.example.smsmirror.ui.theme

import androidx.compose.ui.graphics.Color

// "Signal green" — relay / active / go. Chosen over Material's default purple-blue so
// the app reads as a piece of telecom plumbing rather than a generic utility.
val SignalGreen = Color(0xFF12A55F)
val SignalGreenDark = Color(0xFF2ED47F)
val SignalGreenContainer = Color(0xFFCFEFDD)
val SignalGreenContainerDark = Color(0xFF10432A)

// Green-biased neutrals, picked rather than inherited grey.
val Ink = Color(0xFF0F1613)
val InkSoft = Color(0xFF4A5A52)
val SurfaceLight = Color(0xFFF5F8F6)
val SurfaceContainerLight = Color(0xFFFFFFFF)
val OutlineLight = Color(0xFFD3DED7)

val SurfaceDark = Color(0xFF0F1613)
val SurfaceContainerDark = Color(0xFF18211D)
val InkOnDark = Color(0xFFE4EDE7)
val InkSoftOnDark = Color(0xFFA5B5AC)
val OutlineDark = Color(0xFF2E3A34)

// Semantic states, deliberately distinct from the accent so "queued" can never be
// mistaken for "active". Always paired with a text chip, never colour alone.
val Amber = Color(0xFFC77E00)
val AmberContainer = Color(0xFFFFEFD1)
val AmberContainerDark = Color(0xFF4A3200)
val Danger = Color(0xFFD3454A)
val DangerContainer = Color(0xFFFFDDDE)
val DangerContainerDark = Color(0xFF54171A)
