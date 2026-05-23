package com.horizon.caadronesimulator.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.horizon.caadronesimulator.ui.theme.NikoTheme

/**
 * [v1.7.9.14] 統一覆蓋層卡片 (Internal Overlay Card)
 * 職責：取代系統 AlertDialog，確保在主視窗內繪製以維持沈浸模式。
 */
@Composable
fun NikoOverlayCard(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f))
            .zIndex(2000f)
            .clickable { onDismiss() }, // 點擊背景關閉
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 340.dp) // [v1.7.9.16] 進一步縮小寬度
                .fillMaxWidth(0.55f)   // [v1.7.9.16] 降低佔比
                .clickable(enabled = false) { },
            color = NikoTheme.colors.panel,
            shape = RoundedCornerShape(12.dp), // 稍微減小圓角以顯得更精簡
            border = BorderStroke(1.dp, NikoTheme.colors.primary.copy(alpha = 0.4f)),
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp), // [v1.7.9.16] 減少內邊距 (24 -> 16)
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = title,
                    color = NikoTheme.colors.textPrimary,
                    fontSize = 15.sp, // [v1.7.9.16] 縮小標題字體 (18 -> 15)
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(10.dp)) // [v1.7.9.16] 縮短間距 (16 -> 10)
                
                content()
            }
        }
    }
}
