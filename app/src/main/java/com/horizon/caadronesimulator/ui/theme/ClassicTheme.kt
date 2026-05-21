package com.horizon.caadronesimulator.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.horizon.caadronesimulator.R
import com.horizon.caadronesimulator.model.AppConfig

/**
 * [v1.7.8] 經典深色主題模組
 */
object ClassicTheme : ThemeModule {
    override val id: String = AppConfig.THEME_CLASSIC
    override val nameRes: Int = R.string.settings_theme_classic
    override val descriptionRes: Int = R.string.settings_theme_classic_desc

    override val colors = NikoColors(
        background = Color(0xFF0A0E14),
        surface = Color(0xFF1B2535),
        panel = Color(0xAA111111),
        primary = Color(0xFF00BFFF), // 深天藍
        accent = Color(0xFF00E5FF),  // 青色
        textPrimary = Color(0xFFFFFFFF),
        textSecondary = Color(0xFFB0BEC5),
        divider = Color.White.copy(alpha = 0.1f),
        warning = Color(0xFFD32F2F),
        success = Color(0xFF388E3C),
        safety = Color(0xFFFFA000),
        status = Color(0xFFC6FF00),
        isLight = false
    )

    @Composable
    override fun RenderIcon(modifier: Modifier, isSelected: Boolean) {
        Canvas(modifier = modifier) {
            val w = size.width; val h = size.height
            drawRect(Color(0xFF0A0E14))
            drawRect(Color(0xFF1B2535), Offset(w * 0.2f, h * 0.2f), size * 0.6f)
            drawCircle(Color(0xFF00BFFF), radius = w * 0.1f, center = center)
        }
    }
}
