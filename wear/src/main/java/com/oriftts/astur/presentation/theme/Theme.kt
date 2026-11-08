package com.oriftts.astur.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme

/**
 * Astur 主题（固定纯黑深色，全站统一蓝色系）：
 * 纯黑底 + 电光蓝主色 + 天蓝辅色。
 * 注：本版本 wear-material3 通过 ColorScheme(...) 构造配色。
 */
private val AsturColorScheme = ColorScheme(
    primary = Color(0xFF7C9CFF),
    onPrimary = Color(0xFF0A1A4A),
    primaryContainer = Color(0xFF24397E),
    onPrimaryContainer = Color(0xFFDCE3FF),
    secondary = Color(0xFF8AB4FF),
    onSecondary = Color(0xFF0F2A5C),
    secondaryContainer = Color(0xFF1E3A6E),
    onSecondaryContainer = Color(0xFFD6E4FF),
    tertiary = Color(0xFF9BB8FF),
    onTertiary = Color(0xFF12285A),
    tertiaryContainer = Color(0xFF2A3F7E),
    onTertiaryContainer = Color(0xFFDEE7FF),
    surfaceContainer = Color(0xFF171A26),
    surfaceContainerHigh = Color(0xFF232738),
    onSurface = Color(0xFFE4E6F0),
    onSurfaceVariant = Color(0xFFC3C7D8),
    background = Color(0xFF000000),
    onBackground = Color(0xFFE4E6F0),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF5C1108)
)

@Composable
fun AsturTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AsturColorScheme,
        content = content
    )
}
