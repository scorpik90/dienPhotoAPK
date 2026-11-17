package ru.delivery.photo.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Оформление в духе iOS: системные цвета, скруглённые группы, крупные
 * заголовки и airgap вместо жёстких разделителей.
 *
 * Шрифт — системный sans-serif: SF Pro на Android недоступен, поэтому
 * используется шрифт устройства, а метрики и интервалы повторяют iOS.
 */
private val LightColors = lightColorScheme(
    primary = Color(0xFF007AFF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E9FF),
    onPrimaryContainer = Color(0xFF003A70),
    secondary = Color(0xFF8E8E93),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE5E5EA),
    onSecondaryContainer = Color(0xFF1C1C1E),
    tertiary = Color(0xFF34C759),
    onTertiary = Color.White,
    error = Color(0xFFFF3B30),
    onError = Color.White,
    background = Color(0xFFF2F2F7),
    onBackground = Color(0xFF000000),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF000000),
    surfaceVariant = Color(0xFFFFFFFF),
    onSurfaceVariant = Color(0xFF3C3C43),
    outline = Color(0xFFC6C6C8),
    outlineVariant = Color(0xFFE5E5EA),
    surfaceContainerHighest = Color(0xFFFFFFFF),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF0A84FF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF003A70),
    onPrimaryContainer = Color(0xFFD6E9FF),
    secondary = Color(0xFF8E8E93),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF2C2C2E),
    onSecondaryContainer = Color(0xFFE5E5EA),
    tertiary = Color(0xFF30D158),
    onTertiary = Color.White,
    error = Color(0xFFFF453A),
    onError = Color.White,
    background = Color(0xFF000000),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF1C1C1E),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF2C2C2E),
    onSurfaceVariant = Color(0xFFE5E5EA),
    outline = Color(0xFF48484A),
    outlineVariant = Color(0xFF3A3A3C),
    surfaceContainerHighest = Color(0xFF48484A),
)

/** Метрики текста в духе iOS (SF Pro: 34/28/22/17/13 pt). */
private val AppleTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 41.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 25.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 22.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
    ),
)

/** Скругления в духе iOS: группы 12 pt, кнопки 10 pt, диалоги 14 pt. */
private val AppleShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(14.dp),
    extraLarge = RoundedCornerShape(20.dp),
)

@Composable
fun DeliveryPhotoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppleTypography,
        shapes = AppleShapes,
        content = content,
    )
}
