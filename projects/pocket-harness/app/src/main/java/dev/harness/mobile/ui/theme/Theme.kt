package dev.harness.mobile.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

private val HarnessColors = darkColorScheme(
    primary = Color(0xFFB7A6FF),
    onPrimary = Color(0xFF1B1033),
    primaryContainer = Color(0xFF33265E),
    onPrimaryContainer = Color(0xFFEAE3FF),
    secondary = Color(0xFF7BE0C4),
    onSecondary = Color(0xFF00382B),
    secondaryContainer = Color(0xFF0F4A3B),
    onSecondaryContainer = Color(0xFFB9F5E3),
    tertiary = Color(0xFFFFC1A6),
    onTertiary = Color(0xFF3A1404),
    background = Color(0xFF0E0F13),
    onBackground = Color(0xFFE6E6EA),
    surface = Color(0xFF14161C),
    onSurface = Color(0xFFE6E6EA),
    surfaceVariant = Color(0xFF23262F),
    onSurfaceVariant = Color(0xFFB4B8C4),
    outline = Color(0xFF3A3F4C),
    outlineVariant = Color(0xFF272B34),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF3B0906),
    errorContainer = Color(0xFF5C1A15),
    onErrorContainer = Color(0xFFFFDAD5)
)

private val HarnessTypography = Typography(
    bodyLarge = TextStyle(fontFamily = FontFamily.Default, fontSize = 15.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.Default, fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.Default, fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.Default, fontSize = 13.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.Default, fontSize = 11.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.Default, fontSize = 16.sp),
    titleSmall = TextStyle(fontFamily = FontFamily.Default, fontSize = 14.sp)
)

@Composable
fun PocketHarnessTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = HarnessColors,
        typography = HarnessTypography,
        content = content
    )
}