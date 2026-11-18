package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = ShopNavy,
    onPrimary = Color.White,
    primaryContainer = ShopNavyLight,
    onPrimaryContainer = ShopNavyDark,
    secondary = ShopTerracotta,
    onSecondary = Color.White,
    secondaryContainer = ShopTerracottaLight,
    onSecondaryContainer = Color(0xFF5D1D0D),
    tertiary = ShopEmerald,
    onTertiary = Color.White,
    tertiaryContainer = ShopEmeraldLight,
    onTertiaryContainer = Color(0xFF0A3D24),
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,
    outline = LightOutline,
    error = UrgentRed,
    onError = Color.White,
    errorContainer = UrgentRedContainer,
    onErrorContainer = Color(0xFF7F1D1D)
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF8EA8FF),
    onPrimary = Color(0xFF0B1C42),
    primaryContainer = ShopNavy,
    onPrimaryContainer = ShopNavyLight,
    secondary = Color(0xFFFF8F70),
    onSecondary = Color(0xFF471406),
    secondaryContainer = Color(0xFF7A2D18),
    onSecondaryContainer = ShopTerracottaLight,
    tertiary = Color(0xFF4ADE80),
    onTertiary = Color(0xFF06331C),
    tertiaryContainer = Color(0xFF0F5232),
    onTertiaryContainer = ShopEmeraldLight,
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkOutline,
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = UrgentRedContainer
)

private val ShopKeepShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun ShopKeepTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = ShopKeepTypography,
        shapes = ShopKeepShapes,
        content = content
    )
}
