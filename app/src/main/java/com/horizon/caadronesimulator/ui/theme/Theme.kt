package com.horizon.caadronesimulator.ui.theme

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import com.horizon.caadronesimulator.model.AppConfig

/**
 * [v1.7.8] Niko 模擬器語義化色彩架構 - 模組化驅動版
 */
data class NikoColors(
    val background: Color,
    val surface: Color,
    val panel: Color,
    val primary: Color,
    val accent: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val divider: Color,
    val warning: Color,
    val success: Color,
    val safety: Color,
    val status: Color,
    val isLight: Boolean
)

private val LocalNikoColors = staticCompositionLocalOf { ClassicTheme.colors }

@Composable
fun NikoTheme(
    themeId: String = AppConfig.THEME_CLASSIC,
    content: @Composable () -> Unit
) {
    val colors = remember(themeId) {
        ThemeRegistry.getColors(themeId)
    }
    
    CompositionLocalProvider(LocalNikoColors provides colors) {
        content()
    }
}

/**
 * 全域訪問物件
 */
object NikoTheme {
    val colors: NikoColors
        @Composable
        @ReadOnlyComposable
        get() = LocalNikoColors.current
}
