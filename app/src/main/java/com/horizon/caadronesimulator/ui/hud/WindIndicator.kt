package com.horizon.caadronesimulator.ui.hud

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * [v1.3.9] 獨立風力與風向視覺化組件
 */
@Composable
fun WindIndicator(
    level: Int,
    direction: String,
    modifier: Modifier = Modifier
) {
    val isNoWind = level == 0 || direction == "無"
    // [v1.6.1] 物理驅動角度：直接從 state 讀取物理引擎計算出的真實角度
    val physicalAngle = com.horizon.caadronesimulator.model.DroneState.getInstance().env.currentWindAngle
    
    // 計算旋轉角度 (指向風吹去的方向)
    val rotationAngle by animateFloatAsState(
        targetValue = if (!isNoWind) physicalAngle + 180f else 0f,
        animationSpec = spring(dampingRatio = 0.6f),
        label = "wind_rotate"
    )

    val color = when {
        level == 0 -> Color.Gray
        level <= 2 -> Color.White
        level <= 4 -> Color(0xFFFFA000) // 琥珀色
        else -> Color.Red
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.padding(horizontal = 4.dp)
    ) {
        // 視覺化羅盤/箭頭
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(Color.Black.copy(0.2f), CircleShape)
                .border(1.dp, color.copy(alpha = 0.3f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (!isNoWind) {
                Icon(
                    imageVector = Icons.Default.Navigation,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier
                        .size(14.dp)
                        .rotate(rotationAngle)
                )
            } else {
                Box(modifier = Modifier.size(4.dp).background(Color.Gray, CircleShape))
            }
        }

        Spacer(modifier = Modifier.width(6.dp))

        // 文字資訊
        Column(horizontalAlignment = Alignment.Start) {
            Text(
                text = if (isNoWind) "無風" else "L$level",
                color = color,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 10.sp
            )
            if (!isNoWind) {
                Text(
                    text = direction,
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 8.sp
                )
            }
        }
    }
}
