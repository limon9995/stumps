package com.mdlimonhossain.stumps.ui.theme

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

/**
 * This file wraps the raw colours from Color.kt into two complete "colour schemes" — one for
 * light mode, one for dark mode — using Material3's naming convention (primary = the main
 * brand colour, background/surface = what's behind/under content, onXxx = what colour TEXT
 * should be when placed on top of that Xxx colour, so it stays readable, "xxxContainer" = a
 * softer background shade of that same colour family, and "surfaceContainerXxx" = the ladder
 * of five card/panel background shades described in Color.kt).
 *
 * Every one of Material3's ~25 colour roles is set below (earlier this file only set 7, so
 * every component using one of the other 18 was silently falling back to Compose's own generic
 * purple defaults instead of an on-brand colour — that mismatch is what this change fixes).
 */

private val LightColors = lightColorScheme(
    primary = PitchGreen,
    onPrimary = Cream,
    primaryContainer = PrimaryContainerLight,
    onPrimaryContainer = OnPrimaryContainerLight,
    secondary = SecondaryGreen,
    onSecondary = OnSecondaryLight,
    secondaryContainer = SecondaryContainerLight,
    onSecondaryContainer = OnSecondaryContainerLight,
    tertiary = TrophyGold,
    onTertiary = OnTertiaryLight,
    tertiaryContainer = TertiaryContainerLight,
    onTertiaryContainer = OnTertiaryContainerLight,
    error = BallRed,
    onError = Cream,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerLight,
    background = Cream,
    onBackground = InkGreen,
    surface = Cream,
    onSurface = InkGreen,
    surfaceVariant = SurfaceContainerHighLight,
    onSurfaceVariant = InkGreen,
    surfaceContainerLowest = SurfaceContainerLowestLight,
    surfaceContainerLow = SurfaceContainerLowLight,
    surfaceContainer = SurfaceContainerLight,
    surfaceContainerHigh = SurfaceContainerHighLight,
    surfaceContainerHighest = SurfaceContainerHighestLight,
    outline = OutlineGreen,
    outlineVariant = OutlineVariantGreen,
    inverseSurface = InverseSurfaceLight,
    inverseOnSurface = InverseOnSurfaceLight,
    inversePrimary = PitchGreenDark,
    scrim = Color.Black
)

private val DarkColors = darkColorScheme(
    primary = PitchGreenDark,
    onPrimary = NightBg,
    primaryContainer = PrimaryContainerDark,
    onPrimaryContainer = OnPrimaryContainerDark,
    secondary = SecondaryGreenDark,
    onSecondary = OnSecondaryDark,
    secondaryContainer = SecondaryContainerDark,
    onSecondaryContainer = OnSecondaryContainerDark,
    tertiary = TrophyGoldDark,
    onTertiary = OnTertiaryDark,
    tertiaryContainer = TertiaryContainerDark,
    onTertiaryContainer = OnTertiaryContainerDark,
    error = BallRed,
    onError = Cream,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark,
    background = NightBg,
    onBackground = Cream,
    surface = NightSurface,
    onSurface = Cream,
    surfaceVariant = SurfaceContainerHighDark,
    onSurfaceVariant = Cream,
    surfaceContainerLowest = SurfaceContainerLowestDark,
    surfaceContainerLow = SurfaceContainerLowDark,
    surfaceContainer = SurfaceContainerDark,
    surfaceContainerHigh = SurfaceContainerHighDark,
    surfaceContainerHighest = SurfaceContainerHighestDark,
    outline = OutlineGreenDark,
    outlineVariant = OutlineVariantGreenDark,
    inverseSurface = InverseSurfaceDark,
    inverseOnSurface = InverseOnSurfaceDark,
    inversePrimary = PitchGreen,
    scrim = Color.Black
)

/**
 * Wrap the WHOLE app in this once (see MainActivity.kt) so every screen automatically picks up
 * the right colours and text styles — that's why every screen can just write
 * "MaterialTheme.colorScheme.primary" or "MaterialTheme.typography.headlineLarge" without
 * needing to know the actual colour/size values themselves.
 */
@Composable
fun StumpsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(), // defaults to matching the phone's own dark/light mode setting
    dynamicColor: Boolean = false, // "dynamic colour" = Android 12+'s feature that themes apps to match the phone's wallpaper — turned off here so the app always looks the same brand colours
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = StumpsShapes, // see Shape.kt — how rounded cards/buttons/etc. should be
        content = content
    )
}
