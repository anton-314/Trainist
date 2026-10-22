package dev.antonlammers.trainist.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * Whether the resolved shade is "Ink" (dark). For the few surfaces whose design maps *different*
 * colour roles per shade (the floating navigation pill is dark in both) rather than the same role
 * in both — everything else just reads `MaterialTheme.colorScheme`.
 */
val LocalInkTheme = staticCompositionLocalOf { true }

// "Ink" — dark mode (default). Fixed monochrome surfaces; accent + data colors carry meaning.
private val DarkColorScheme = darkColorScheme(
    primary = AccentLilac,
    onPrimary = OnAccentInk,
    primaryContainer = InkSurface2,
    onPrimaryContainer = InkText,
    secondary = AccentLilac,
    onSecondary = OnAccentInk,
    secondaryContainer = InkSurface2,
    onSecondaryContainer = InkText,
    tertiary = FatColor,
    onTertiary = OnAccentInk,
    background = InkBg,
    onBackground = InkText,
    surface = InkSurface,
    onSurface = InkText,
    surfaceVariant = InkSurface2,
    onSurfaceVariant = InkText2,
    surfaceContainerLowest = InkPaper,
    surfaceContainerLow = InkBg,
    surfaceContainer = InkSurface,
    surfaceContainerHigh = InkSurface2,
    surfaceContainerHighest = InkSurface2,
    outline = InkText3,
    outlineVariant = InkLine,
    error = TagUnhealthyColor,
    onError = OnAccentInk,
    scrim = Color.Black,
)

// "Paper" — light mode. Activated automatically when the device is in light mode.
private val LightColorScheme = lightColorScheme(
    primary = AccentLilac,
    onPrimary = OnAccentInk,
    primaryContainer = PaperSurface2,
    onPrimaryContainer = PaperText,
    secondary = AccentLilac,
    onSecondary = OnAccentInk,
    secondaryContainer = PaperSurface2,
    onSecondaryContainer = PaperText,
    tertiary = FatColor,
    onTertiary = OnAccentInk,
    background = PaperBg,
    onBackground = PaperText,
    surface = PaperSurface,
    onSurface = PaperText,
    surfaceVariant = PaperSurface2,
    onSurfaceVariant = PaperText2,
    surfaceContainerLowest = PaperPaper,
    surfaceContainerLow = PaperBg,
    surfaceContainer = PaperSurface,
    surfaceContainerHigh = PaperSurface2,
    surfaceContainerHighest = PaperSurface2,
    outline = PaperText3,
    outlineVariant = PaperLine,
    error = TagUnhealthyColor,
    onError = OnAccentInk,
    scrim = Color.Black,
)

@Composable
fun TrainistTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // The app ships a fixed "Ink & Paper" color system, so Material You dynamic
    // color is off by default. The parameter is kept for previews/testing.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    CompositionLocalProvider(LocalInkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
