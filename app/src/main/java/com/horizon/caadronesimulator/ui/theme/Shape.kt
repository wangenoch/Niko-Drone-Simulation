package com.horizon.caadronesimulator.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

/**
 * [v1.7.7] 工業級設計系統 - 統一形狀定義 (Shapes)
 */
data class NikoShapes(
    val small: RoundedCornerShape = RoundedCornerShape(6.dp),   // 小型按鈕、標籤
    val medium: RoundedCornerShape = RoundedCornerShape(12.dp), // 一般卡片、彈窗
    val large: RoundedCornerShape = RoundedCornerShape(24.dp),  // 大型選單、首次引導
    val panel: RoundedCornerShape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp) // HUD 底部面板
)

val LocalNikoShapes = staticCompositionLocalOf { NikoShapes() }
