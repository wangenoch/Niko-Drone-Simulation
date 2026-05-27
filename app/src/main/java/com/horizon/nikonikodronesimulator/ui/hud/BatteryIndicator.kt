package com.horizon.nikonikodronesimulator.ui.hud

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.horizon.nikonikodronesimulator.ui.theme.NikoTheme
import java.util.Locale

/**
 * [v1.3.9] 獨立電池狀態顯示組件
 */
@Composable
fun BatteryIndicator(
    percent: Int,
    voltage: Float,
    modifier: Modifier = Modifier
) {
    val isLow = percent <= 30
    val isCritical = percent <= 10

    // 低電量閃爍動畫
    val infiniteTransition = rememberInfiniteTransition(label = "battery_flash")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isLow) 0.3f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isCritical) 400 else 800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    val color = when {
        isCritical -> NikoTheme.colors.warning
        isLow -> NikoTheme.colors.safety
        else -> NikoTheme.colors.status
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.padding(horizontal = 4.dp)
    ) {
        // 電池圖示
        Box(
            modifier = Modifier
                .size(20.dp, 10.dp)
                .border(1.dp, color.copy(alpha = if (isLow) alpha else 1f), RoundedCornerShape(2.dp))
                .padding(1.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(percent / 100f)
                    .clip(RoundedCornerShape(1.dp))
                    .background(color.copy(alpha = if (isLow) alpha else 1f))
            )
        }
        
        Spacer(modifier = Modifier.width(6.dp))

        // 電壓與百分比
        val theme = NikoTheme
        val textCol = if(theme.colors.isLight) theme.colors.textPrimary else Color.White.copy(alpha = 0.9f)
        Column(horizontalAlignment = Alignment.Start) {
            Text(
                text = String.format(Locale.US, "%.1fV", voltage),
                color = color,
                style = theme.typography.label,
                lineHeight = 10.sp
            )
            Text(
                text = "$percent%",
                color = textCol,
                style = theme.typography.caption.copy(fontWeight = FontWeight.Medium),
                lineHeight = 8.sp
            )
        }
    }
}
