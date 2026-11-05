package com.example.myapplication.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = ForestLight,
    onPrimary = Color(0xFF14372C),
    primaryContainer = Color(0xFF244A3B),
    onPrimaryContainer = Color(0xFFD5E7DC),
    secondary = Color(0xFFE3A27D),
    onSecondary = Color(0xFF3F2417),
    secondaryContainer = Color(0xFF52372A),
    onSecondaryContainer = Color(0xFFF2DDD0),
    tertiary = Color(0xFFE3C77F),
    onTertiary = Color(0xFF3B2B09),
    tertiaryContainer = Color(0xFF51431F),
    onTertiaryContainer = Color(0xFFF3E8C9),
    background = Color(0xFF141C18),
    onBackground = Color(0xFFE4E9E2),
    surface = Color(0xFF202923),
    onSurface = Color(0xFFE4E9E2),
    surfaceVariant = Color(0xFF39443C),
    onSurfaceVariant = Color(0xFFC0C9BF),
    surfaceContainerHigh = Color(0xFF2B362F),
    outline = Color(0xFF768278),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

private val LightColorScheme = lightColorScheme(
    primary = Forest,
    onPrimary = Color.White,
    primaryContainer = ForestContainer,
    onPrimaryContainer = Color(0xFF14372C),
    secondary = Terracotta,
    onSecondary = Color.White,
    secondaryContainer = TerracottaContainer,
    onSecondaryContainer = Color(0xFF3F2417),
    tertiary = Gold,
    onTertiary = Color.White,
    tertiaryContainer = GoldContainer,
    onTertiaryContainer = Color(0xFF3B2B09),
    background = Paper,
    onBackground = Ink,
    surface = SurfacePaper,
    onSurface = Ink,
    surfaceVariant = Sage,
    onSurfaceVariant = MutedGreen,
    surfaceContainerHigh = SurfacePaper,
    outline = Color(0xFFBCC8BD),
    error = Color(0xFFB3261E),
    errorContainer = Color(0xFFF9DEDC),
    onError = Color.White,
    onErrorContainer = Color(0xFF410E0B)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content
    )
}