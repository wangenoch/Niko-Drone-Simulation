package com.horizon.caadronesimulator.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.horizon.caadronesimulator.R
import com.horizon.caadronesimulator.model.AppConfig

/**
 * [v1.7.8] 明亮專業主題模組
 */
object LightTheme : ThemeModule {
    override val id: String = AppConfig.THEME_LIGHT
    override val nameRes: Int = R.string.settings_theme_light
    override val descriptionRes: Int = R.string.settings_theme_light_desc

    override val colors = NikoColors(
        background = Color(0xFFF5F7FA), // 稍微偏暖的淺灰
        surface = Color(0xFFFFFFFF),
        panel = Color(0xFFFFFFFF),    // 明亮模式下面板不宜太透明
        primary = Color(0xFF0056B3),  // 深藍
        accent = Color(0xFFE65100),   // 深橘
        textPrimary = Color(0xFF1A1C1E),
        textSecondary = Color(0xFF5D6679), // 加深副標題顏色
        divider = Color.Black.copy(alpha = 0.08f),
        warning = Color(0xFFC62828),  // 更深且清晰的紅色
        success = Color(0xFF2E7D32),  // 更深且清晰的綠色
        safety = Color(0xFFEF6C00),   // 深橘
        status = Color(0xFF1B5E20),   // 明亮模式下改用深森林綠代替螢光綠
        isLight = true
    )

    @Composable
    override fun RenderIcon(modifier: Modifier, isSelected: Boolean) {
        Canvas(modifier = modifier) {
            val w = size.width; val h = size.height
            drawRect(Color(0xFFF0F2F5))
            drawRect(Color(0xFFFFFFFF), Offset(w * 0.2f, h * 0.2f), size * 0.6f)
            drawCircle(Color(0xFF0056B3), radius = w * 0.1f, center = center)
        }
    }
}
