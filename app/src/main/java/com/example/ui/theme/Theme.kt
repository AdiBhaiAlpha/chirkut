package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = RealmeYellow,
    onPrimary = RealmeOnYellow,
    primaryContainer = Color(0xFF453600),
    onPrimaryContainer = RealmeYellowLight,
    secondary = AmberGold,
    onSecondary = RealmeOnYellow,
    secondaryContainer = Color(0xFF3E3100),
    onSecondaryContainer = Color(0xFFFFE082),
    tertiary = Color(0xFFFFD54F),
    onTertiary = RealmeOnYellow,
    background = BackgroundDark,
    onBackground = OnSurfaceDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceContainerDark,
    onSurfaceVariant = Color(0xFFCAC4D0),
    outline = Color(0xFF938F99),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

private val LightColorScheme = lightColorScheme(
    primary = RealmeYellow,
    onPrimary = RealmeOnYellow,
    primaryContainer = RealmeYellowContainer,
    onPrimaryContainer = Color(0xFF261900),
    secondary = RealmeYellowDark,
    onSecondary = RealmeOnYellow,
    secondaryContainer = Color(0xFFFFECB3),
    onSecondaryContainer = Color(0xFF261900),
    tertiary = AmberDark,
    onTertiary = Color.White,
    background = BackgroundLight,
    onBackground = OnSurfaceLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceContainerLight,
    onSurfaceVariant = Color(0xFF49454F),
    outline = Color(0xFF79747E),
    error = Color(0xFFBA1A1A),
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Keep false to strictly enforce Yellow branding across all pages
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
