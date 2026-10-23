package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = SagePrimaryDark,
    onPrimary = OnSagePrimaryDark,
    primaryContainer = SagePrimaryContainerDark,
    onPrimaryContainer = OnSagePrimaryContainerDark,
    secondary = EucalyptusSecondaryDark,
    onSecondary = OnEucalyptusSecondaryDark,
    secondaryContainer = EucalyptusSecondaryContainerDark,
    onSecondaryContainer = OnEucalyptusSecondaryContainerDark,
    tertiary = AmberTertiaryDark,
    onTertiary = OnAmberTertiaryDark,
    tertiaryContainer = AmberTertiaryContainerDark,
    onTertiaryContainer = OnAmberTertiaryContainerDark,
    background = WellnessBackgroundDark,
    onBackground = OnWellnessBackgroundDark,
    surface = WellnessSurfaceDark,
    onSurface = OnWellnessSurfaceDark,
    surfaceVariant = WellnessSurfaceVariantDark,
    onSurfaceVariant = OnWellnessSurfaceVariantDark,
    outline = WellnessOutlineDark
)

private val LightColorScheme = lightColorScheme(
    primary = SagePrimaryLight,
    onPrimary = OnSagePrimaryLight,
    primaryContainer = SagePrimaryContainerLight,
    onPrimaryContainer = OnSagePrimaryContainerLight,
    secondary = EucalyptusSecondaryLight,
    onSecondary = OnEucalyptusSecondaryLight,
    secondaryContainer = EucalyptusSecondaryContainerLight,
    onSecondaryContainer = OnEucalyptusSecondaryContainerLight,
    tertiary = AmberTertiaryLight,
    onTertiary = OnAmberTertiaryLight,
    tertiaryContainer = AmberTertiaryContainerLight,
    onTertiaryContainer = OnAmberTertiaryContainerLight,
    background = WellnessBackgroundLight,
    onBackground = OnWellnessBackgroundLight,
    surface = WellnessSurfaceLight,
    onSurface = OnWellnessSurfaceLight,
    surfaceVariant = WellnessSurfaceVariantLight,
    onSurfaceVariant = OnWellnessSurfaceVariantLight,
    outline = WellnessOutlineLight
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
