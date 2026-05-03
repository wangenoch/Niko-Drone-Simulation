package com.horizon.caadronesimulator.ui.instruments

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.horizon.caadronesimulator.model.DroneState
import kotlin.math.acos

/**
 * [v1.2.86] RadarHUD 智慧縮放系統
 * 具備三階段模式：全圖 (橘)、八字自動 (藍)、跟隨模式 (綠)
 */
@Composable
fun RadarHUD(
    state: DroneState,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val borderColor = when (state.radarZoomMode) {
        0 -> Color(0xFFFF9800) // 橘色
        1 -> Color.Cyan        // 藍色
        else -> Color.Green    // 綠色
    }

    Box(
        modifier = modifier
            .size(150.dp, 100.dp)
            .background(Color(0xAA111111), RoundedCornerShape(12.dp))
            .border(1.5.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable { onClick() }
    ) {
        // --- 1. 左上角紅色切換按鈕 ---
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(4.dp)
                .size(24.dp)
                .background(Color.Red.copy(0.4f), CircleShape)
                .clickable {
                    state.radarZoomMode = (state.radarZoomMode + 1) % 3
                },
            contentAlignment = Alignment.Center
        ) {
            Text("+", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        // --- 2. 雷達畫布 ---
        Canvas(modifier = Modifier
            .fillMaxSize()
            .padding(5.dp)
        ) {
            // [v1.2.86] 關鍵修正：實施畫布裁切，防止線條溢出雷達框外
            clipRect {
                val scale = state.currentRadarScale * 2.0.dp.toPx()
                val centerX = size.width / 2
                // [v1.2.86] 動態中心點：跟隨模式使用正中心 (0.5)，其餘模式使用偏下方位 (0.83)
                val centerY = if (state.radarZoomMode == 2) size.height / 2 else size.height * 0.83f
                
                rotate(180f, Offset(centerX, centerY)) {
                    // 若為跟隨模式，平移畫布中心至無人機位置
                    val transX = if (state.radarZoomMode == 2) -state.posX * scale else 0f
                    val transY = if (state.radarZoomMode == 2) -(state.posZ + 6f) * scale else 0f
                    
                    translate(transX, transY) {
                        val fieldCenter = Offset(centerX, centerY + 6f * scale)
                        
                        drawRect(
                            Color.Red.copy(0.3f), 
                            topLeft = Offset(fieldCenter.x - 35f * scale, fieldCenter.y - 13f * scale), 
                            size = Size(70f * scale, 43f * scale), 
                            style = Stroke(1.5.dp.toPx())
                        )
                        
                        listOf(4f, 8f).forEach { rMeter ->
                            val r = rMeter * scale
                            val lC = Offset(fieldCenter.x - 6f * scale, fieldCenter.y)
                            val rC = Offset(fieldCenter.x + 6f * scale, fieldCenter.y)
                            if (rMeter == 8f) {
                                val angle = Math.toDegrees(acos(6f / 8f).toDouble()).toFloat()
                                val sweep = angle * 2f
                                drawArc(Color.Red.copy(0.4f), angle, 360f - sweep, false, Offset(lC.x - r, lC.y - r), Size(r*2, r*2), style = Stroke(1.dp.toPx()))
                                drawArc(Color.Red.copy(0.4f), 180f + angle, 360f - sweep, false, Offset(rC.x - r, rC.y - r), Size(r*2, r*2), style = Stroke(1.dp.toPx()))
                            } else {
                                drawCircle(Color.Red.copy(0.4f), r, lC, style = Stroke(1.dp.toPx()))
                                drawCircle(Color.Red.copy(0.4f), r, rC, style = Stroke(1.dp.toPx()))
                            }
                        }

                        if (state.showFlightPath && state.flightPath.size > 1) {
                            val p = Path()
                            state.flightPath.forEachIndexed { i, pt ->
                                val px = centerX + pt.x * scale
                                val py = centerY + (pt.y + 6f) * scale
                                if (i == 0) p.moveTo(px, py) else p.lineTo(px, py)
                            }
                            drawPath(p, Color.Cyan.copy(0.6f), style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f))))
                        }
                    }
                }

                // 繪製無人機標誌
                rotate(180f, Offset(centerX, centerY)) {
                    val droneX = if (state.radarZoomMode == 2) centerX else centerX + state.posX * scale
                    val droneY = if (state.radarZoomMode == 2) centerY else centerY + (state.posZ + 6f) * scale
                    val dronePos = Offset(droneX, droneY)
                    
                    rotate(-state.yaw, dronePos) {
                        val ap = Path().apply { 
                            moveTo(dronePos.x, dronePos.y + 6.dp.toPx())
                            lineTo(dronePos.x - 4.dp.toPx(), dronePos.y - 4.dp.toPx())
                            lineTo(dronePos.x + 4.dp.toPx(), dronePos.y - 4.dp.toPx())
                            close() 
                        }
                        drawPath(ap, Color.White)
                    }
                }
            }
        }
    }
}
