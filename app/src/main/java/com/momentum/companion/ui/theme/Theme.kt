package com.momentum.companion.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val MomentumDarkColorScheme = darkColorScheme(
    primary = MomentumOrange,
    onPrimary = MomentumBackground,
    secondary = MomentumOrangeLight,
    background = MomentumBackground,
    surface = MomentumSurface,
    surfaceVariant = MomentumSurfaceVariant,
    onBackground = MomentumTextPrimary,
    onSurface = MomentumTextPrimary,
    onSurfaceVariant = MomentumTextSecondary,
    error = MomentumError,
    onError = MomentumTextPrimary,
)

@Composable
fun MomentumCompanionTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MomentumDarkColorScheme,
        typography = MomentumTypography,
        content = content,
    )
}
