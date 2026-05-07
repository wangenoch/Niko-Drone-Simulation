package com.horizon.caadronesimulator.ui.instruments

import androidx.compose.animation.core.*
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
import com.horizon.caadronesimulator.model.Constants
import kotlin.math.acos

/**
 * [v1.3.9] RadarHUD 座標系物理校正版 (Final Fix)
 * 解決上下/左右相反問題，修正 H 圖標與跟隨模式下的 8 字裁切。
 */
@Composable
fun RadarHUD(
    state: DroneState,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val borderColor = when {
        state.isNearBoundary -> Color.Red
        state.radarZoomMode == 0 -> Color(0xFFFF9800)
        state.radarZoomMode == 1 -> Color.Cyan
        else -> Color.Green
    }

    val animatedScale by animateFloatAsState(targetValue = state.currentRadarScale, animationSpec = tween(800), label = "radar_scale")
    val markerColor = if (state.useSimplifiedMarkers) Color.White.copy(0.6f) else Color(0xFFFFD600).copy(0.7f)

    Box(
        modifier = modifier
            .size(150.dp, 100.dp)
            .background(Color(0xAA111111), RoundedCornerShape(12.dp))
            .border(1.5.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier.align(Alignment.TopStart).padding(4.dp).size(20.dp).background(Color.Red.copy(0.4f), CircleShape)
                .clickable { state.radarZoomMode = (state.radarZoomMode + 1) % 3 },
            contentAlignment = Alignment.Center
        ) {
            Text("+", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }

        Canvas(modifier = Modifier.fillMaxSize().padding(5.dp)) {
            clipRect {
                val scale = animatedScale * 2.0.dp.toPx()
                val centerX = size.width / 2
                // H 坪 (Z=-6) 在畫布上的基準高度
                val centerY = if (state.radarZoomMode == 2) size.height / 2 else size.height * 0.82f

                // 【核心幾何定錨】計算 H 坪中心點在畫布上的實際座標
                val hRadarX = if (state.radarZoomMode == 2) centerX + state.posX * scale else centerX
                val hRadarY = if (state.radarZoomMode == 2) centerY + (state.posZ + 6f) * scale else centerY

                // 統一座標映射函數：Radar_X = hX - World_X*s, Radar_Y = hY - (World_Z+6)*s
                // +X 為物理左側 -> 映射至畫布左側 (-X)
                // +Z 為物理前方 -> 映射至畫布上方 (-Y)
                fun toRadar(wx: Float, wz: Float): Offset {
                    return Offset(hRadarX - wx * scale, hRadarY - (wz + 6f) * scale)
                }

                // 1. 繪製 100m 物理邊界 (紅實) 與 警告線 (黃虛)
                val bL = -50f; val bR = 50f; val bF = 40f; val bB = -15f
                val tl = toRadar(bL, bF); val br = toRadar(bR, bB)
                drawRect(Color.Red.copy(0.25f), topLeft = tl, size = Size(br.x - tl.x, br.y - tl.y), style = Stroke(1.dp.toPx()))
                
                val buffer = 5f
                val wtl = toRadar(bL + buffer, bF - buffer); val wbr = toRadar(bR - buffer, bB + buffer)
                drawRect(Color.Yellow.copy(0.3f), topLeft = wtl, size = Size(wbr.x - wtl.x, wbr.y - wtl.y), 
                    style = Stroke(0.8.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))))

                // 2. 繪製矩形框 (16m / 8m)
                listOf(16f, 8f).forEach { s ->
                    val o = toRadar(s/2, s/2)
                    drawRect(markerColor, topLeft = o, size = Size(s * scale, s * scale), style = Stroke(1.dp.toPx()))
                }

                // 3. 繪製 8 字圓圈 (動態對齊裁切，解決 Mode 2 破損)
                val splitX = hRadarX 
                listOf(4f, 8f).forEach { rMeter ->
                    val r = rMeter * scale
                    val cL = toRadar(6f, 0f); val cR = toRadar(-6f, 0f)
                    val color = if (rMeter == 8f) Color.Red.copy(0.4f) else markerColor
                    
                    clipRect(left = -5000f, top = -5000f, right = splitX, bottom = 5000f) {
                        drawCircle(color, r, cL, style = Stroke(0.8.dp.toPx()))
                    }
                    clipRect(left = splitX, top = -5000f, right = 5000f, bottom = 5000f) {
                        drawCircle(color, r, cR, style = Stroke(0.8.dp.toPx()))
                    }
                }

                // 4. 繪製 H 坪圖標 (修正比例與正立 H)
                val hC = toRadar(0f, -6f); val hS = 0.4f * scale
                drawCircle(Color.Blue.copy(0.6f), 1.2f * scale, hC)
                val hPath = Path().apply {
                    // 正確比例的 H
                    moveTo(hC.x - hS, hC.y - hS*1.2f); lineTo(hC.x - hS, hC.y + hS*1.2f) 
                    moveTo(hC.x + hS, hC.y - hS*1.2f); lineTo(hC.x + hS, hC.y + hS*1.2f) 
                    moveTo(hC.x - hS, hC.y); lineTo(hC.x + hS, hC.y)           
                }
                drawPath(hPath, Color.White, style = Stroke(1.5.dp.toPx()))

                // 5. 繪製倒 T 站位線
                val pB = toRadar(0f, -15f)
                drawLine(Color.White, Offset(pB.x - 0.9f * scale, pB.y), Offset(pB.x + 0.9f * scale, pB.y), 1.5.dp.toPx())
                drawLine(Color.White, pB, Offset(pB.x, pB.y + 0.5f * scale), 1.5.dp.toPx())

                // 6. 興趣點 (紅圈)
                drawCircle(Color.Red, 1.0f * scale, toRadar(13.5f, 10f)) // 左 (World +X)
                drawCircle(Color.Red, 1.0f * scale, toRadar(-13.5f, 10f)) // 右 (World -X)

                // 7. 軌跡
                if (state.showFlightPath && state.flightPath.size > 1) {
                    val p = Path()
                    state.flightPath.forEachIndexed { i, pt ->
                        val rP = toRadar(pt.x, pt.y)
                        if (i == 0) p.moveTo(rP.x, rP.y) else p.lineTo(rP.x, rP.y)
                    }
                    drawPath(p, Color.Cyan.copy(0.5f), style = Stroke(0.8.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f))))
                }

                // 8. 無人機三角標誌 (模式 2 固定在中心，其餘隨位置移動)
                val dronePos = if (state.radarZoomMode == 2) Offset(centerX, centerY) else toRadar(state.posX, state.posZ)
                
                rotate(-state.yaw, dronePos) {
                    val ap = Path().apply { 
                        moveTo(dronePos.x, dronePos.y - 6.dp.toPx()) // 尖端朝上
                        lineTo(dronePos.x - 4.dp.toPx(), dronePos.y + 4.dp.toPx())
                        lineTo(dronePos.x + 4.dp.toPx(), dronePos.y + 4.dp.toPx())
                        close() 
                    }
                    drawPath(ap, Color.White)
                }
            }
        }
    }
}
