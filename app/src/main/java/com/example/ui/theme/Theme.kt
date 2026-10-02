package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

enum class AppThemeMode {
    LIGHT, DARK, AMOLED, SYSTEM
}

private val NovaDarkColorScheme = darkColorScheme(
    primary = NovaPrimary,
    onPrimary = NovaOnPrimary,
    primaryContainer = NovaPrimaryContainer,
    onPrimaryContainer = NovaOnPrimaryContainer,
    secondary = NovaSecondary,
    onSecondary = NovaOnSecondary,
    secondaryContainer = NovaSecondaryContainer,
    onSecondaryContainer = NovaOnSecondaryContainer,
    tertiary = NovaTertiary,
    onTertiary = NovaOnTertiary,
    tertiaryContainer = NovaTertiaryContainer,
    onTertiaryContainer = NovaOnTertiaryContainer,
    background = NovaDarkBackground,
    onBackground = NovaTextPrimary,
    surface = NovaDarkSurface,
    onSurface = NovaTextPrimary,
    surfaceVariant = NovaDarkSurfaceVariant,
    onSurfaceVariant = NovaTextSecondary,
    outline = NovaDarkOutline
)

private val NovaAmoledColorScheme = darkColorScheme(
    primary = NovaPrimary,
    onPrimary = NovaOnPrimary,
    primaryContainer = NovaPrimaryContainer,
    onPrimaryContainer = NovaOnPrimaryContainer,
    secondary = NovaSecondary,
    onSecondary = NovaOnSecondary,
    secondaryContainer = NovaSecondaryContainer,
    onSecondaryContainer = NovaOnSecondaryContainer,
    tertiary = NovaTertiary,
    onTertiary = NovaOnTertiary,
    background = Color(0xFF000000),
    onBackground = NovaTextPrimary,
    surface = Color(0xFF111318),
    onSurface = NovaTextPrimary,
    surfaceVariant = NovaDarkSurfaceVariant,
    onSurfaceVariant = NovaTextSecondary,
    outline = NovaDarkOutline
)

private val NovaLightColorScheme = lightColorScheme(
    primary = Color(0xFF006978),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFA6EEFF),
    onPrimaryContainer = Color(0xFF001F25),
    secondary = Color(0xFF7B1FA2),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF3E8FF),
    onSecondaryContainer = Color(0xFF2E004E),
    tertiary = Color(0xFF1D4ED8),
    onTertiary = Color.White,
    background = NovaLightBackground,
    onBackground = Color(0xFF0F172A),
    surface = NovaLightSurface,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = NovaLightSurfaceVariant,
    onSurfaceVariant = Color(0xFF475569)
)

@Composable
fun NovaShareTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK, AppThemeMode.AMOLED -> true
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val context = LocalContext.current
    val colorScheme = when (themeMode) {
        AppThemeMode.AMOLED -> NovaAmoledColorScheme
        AppThemeMode.DARK -> NovaDarkColorScheme
        AppThemeMode.LIGHT -> NovaLightColorScheme
        AppThemeMode.SYSTEM -> {
            if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else if (darkTheme) {
                NovaDarkColorScheme
            } else {
                NovaLightColorScheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
