package dev.harness.mobile.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

/**
 * 深色高对比配色。
 * 原则：正文接近纯白（对比度 > 12:1），次级文字也保持在 7:1 以上，
 * 面上层级用背景明度差区分，而不是靠降低文字亮度来"弱化"。
 */
private val HarnessColors = darkColorScheme(
    primary = Color(0xFFD6C9FF),
    onPrimary = Color(0xFF1A1033),
    primaryContainer = Color(0xFF3E2F73),
    onPrimaryContainer = Color(0xFFF3EEFF),
    secondary = Color(0xFF9DF0D8),
    onSecondary = Color(0xFF00382B),
    secondaryContainer = Color(0xFF0F4A3B),
    onSecondaryContainer = Color(0xFFCBFFF1),
    tertiary = Color(0xFFFFCBA8),
    onTertiary = Color(0xFF3A1404),
    background = Color(0xFF08090C),
    onBackground = Color(0xFFF7F8FC),
    surface = Color(0xFF15171E),
    onSurface = Color(0xFFF7F8FC),
    surfaceVariant = Color(0xFF272C38),
    onSurfaceVariant = Color(0xFFDDE2ED),
    outline = Color(0xFF5C6478),
    outlineVariant = Color(0xFF353C4A),
    error = Color(0xFFFF9E94),
    onError = Color(0xFF3B0906),
    errorContainer = Color(0xFF6A1F19),
    onErrorContainer = Color(0xFFFFE3DF)
)

private val HarnessTypography = Typography(
    bodyLarge = TextStyle(fontFamily = FontFamily.Default, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.Default, fontSize = 15.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.Default, fontSize = 13.sp, lineHeight = 19.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.Default, fontSize = 14.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.Default, fontSize = 12.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.Default, fontSize = 17.sp),
    titleSmall = TextStyle(fontFamily = FontFamily.Default, fontSize = 15.sp)
)

@Composable
fun PocketHarnessTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = HarnessColors,
        typography = HarnessTypography,
        content = content
    )
}