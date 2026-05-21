package com.horizon.caadronesimulator.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * [v1.7.8] 主題模組接口 - 徹底解耦色彩定義
 */
interface ThemeModule {
    val id: String
    val nameRes: Int
    val descriptionRes: Int
    val colors: NikoColors

    @Composable
    fun RenderIcon(modifier: Modifier, isSelected: Boolean)
}
