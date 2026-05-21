package com.horizon.caadronesimulator.ui.hud

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
import kotlin.math.abs

import androidx.compose.ui.res.stringResource
import com.horizon.caadronesimulator.R
import com.horizon.caadronesimulator.ui.theme.NikoTheme

/**
 * [v1.5.9] RadarHUD 專業級像素還原版 - 佈局約束修正
 */
@Composable
fun RadarHUD(
    state: DroneState,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    val themeColors = NikoTheme.colors
    val markerColor = if (state.useSimplifiedMarkers) themeColors.textPrimary.copy(0.6f) else Color(0xFFFFD600).copy(0.7f)
    val animatedScale by animateFloatAsState(targetValue = state.currentRadarScale, animationSpec = tween(800), label = "radar_scale")

    Box(
        modifier = modifier
            .size(150.dp, 100.dp)
            .background(Color(0xAA111111), RoundedCornerShape(12.dp)) // 羅盤視窗維持深色玻璃感，不受主題背景影響
            .border(1.5.dp, themeColors.primary.copy(0.6f), RoundedCornerShape(12.dp))
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(4.dp)
                .size(20.dp)
                .background(Color.Red.copy(0.4f), CircleShape)
                .clickable { state.radarZoomMode = (state.radarZoomMode + 1) % 3 },
            contentAlignment = Alignment.Center
        ) {
            Text("+", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }

        Canvas(modifier = Modifier.fillMaxSize().padding(5.dp)) {
            clipRect {
                val s = animatedScale * 1.8.dp.toPx() // 稍微縮減縮放基數，確保邊界完整
                val centerX = size.width / 2
                // 優化視圖中心：全域模式下將中心點稍微上移，以容納更長的前方場地 (Z=46)
                val centerY = if (state.radarZoomMode == 2) size.height / 2 else size.height * 0.75f

                val hRadarX = if (state.radarZoomMode == 2) centerX + state.posX * s else centerX
                val hRadarY = if (state.radarZoomMode == 2) centerY + state.posZ * s else centerY

                fun toRadar(wx: Float, wz: Float): Offset = Offset(hRadarX - wx * s, hRadarY - wz * s)

                val bL = -50f; val bR = 50f; val bF = 46f; val bB = -9f
                val tl = toRadar(bL, bF); val br = toRadar(bR, bB)
                drawRect(Color.Red.copy(0.25f), topLeft = tl, size = Size(br.x - tl.x, br.y - tl.y), style = Stroke(1.dp.toPx()))
                
                val buffer = 5f
                val wtl = toRadar(bL + buffer, bF - buffer); val wbr = toRadar(bR - buffer, bB + buffer)
                drawRect(Color.Yellow.copy(0.3f), topLeft = wtl, size = Size(wbr.x - wtl.x, wbr.y - wtl.y), 
                    style = Stroke(0.8.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))))

                listOf(16f, 8f).forEach { sz ->
                    val o = toRadar(sz/2, 6f + sz/2)
                    drawRect(markerColor, topLeft = o, size = Size(sz * s, sz * s), style = Stroke(1.dp.toPx()))
                }

                val splitX = hRadarX 
                listOf(4f, 8f).forEach { rM ->
                    val r = rM * s
                    val cL = toRadar(6f, 6f); val cR = toRadar(-6f, 6f)
                    val col = if (rM == 8f) Color.Red.copy(0.4f) else markerColor
                    clipRect(left = -5000f, top = -5000f, right = splitX, bottom = 5000f) { drawCircle(col, r, cL, style = Stroke(0.8.dp.toPx())) }
                    clipRect(left = splitX, top = -5000f, right = 5000f, bottom = 5000f) { drawCircle(col, r, cR, style = Stroke(0.8.dp.toPx())) }
                }

                val hC = toRadar(0f, 0f); val hS = 0.4f * s
                drawCircle(Color.Blue.copy(0.6f), 1.2f * s, hC)
                val hP = Path().apply { moveTo(hC.x - hS, hC.y - hS*1.2f); lineTo(hC.x - hS, hC.y + hS*1.2f); moveTo(hC.x + hS, hC.y - hS*1.2f); lineTo(hC.x + hS, hC.y + hS*1.2f); moveTo(hC.x - hS, hC.y); lineTo(hC.x + hS, hC.y) }
                drawPath(hP, themeColors.textPrimary, style = Stroke(1.5.dp.toPx()))

                val pB = toRadar(0f, -9f)
                drawLine(themeColors.textPrimary, Offset(pB.x - 0.9f * s, pB.y), Offset(pB.x + 0.9f * s, pB.y), 1.5.dp.toPx())
                drawLine(themeColors.textPrimary, pB, Offset(pB.x, pB.y + 0.5f * s), 1.5.dp.toPx())

                drawCircle(Color.Red, 1.0f * s, toRadar(13.5f, 16.0f))
                drawCircle(Color.Red, 1.0f * s, toRadar(-13.5f, 16.0f))

                // [v1.6.3] 飛行軌跡繪製：補全缺失的 Trail 渲染邏輯
                if (state.showFlightPath && state.flightPath.isNotEmpty()) {
                    val trailPath = Path()
                    state.flightPath.forEachIndexed { index, offset ->
                        val radarPos = toRadar(offset.x, offset.y)
                        if (index == 0) trailPath.moveTo(radarPos.x, radarPos.y)
                        else trailPath.lineTo(radarPos.x, radarPos.y)
                    }
                    drawPath(
                        path = trailPath,
                        color = themeColors.accent.copy(alpha = 0.5f),
                        style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f)))
                    )
                }

                val dP = if (state.radarZoomMode == 2) Offset(centerX, centerY) else toRadar(state.posX, state.posZ)
                rotate(-state.yaw, dP) {
                    val ap = Path().apply { moveTo(dP.x, dP.y - 6.dp.toPx()); lineTo(dP.x - 4.dp.toPx(), dP.y + 4.dp.toPx()); lineTo(dP.x + 4.dp.toPx(), dP.y + 4.dp.toPx()); close() }
                    drawPath(ap, themeColors.textPrimary)
                }
            }
        }
        
        val globalLabel = stringResource(R.string.radar_zoom_global)
        val autoLabel = stringResource(R.string.radar_zoom_auto)
        val preciseLabel = stringResource(R.string.radar_zoom_precise)
        val dynamicLabel = stringResource(R.string.radar_zoom_dynamic)

        Text(
            text = when(state.radarZoomMode) { 
                0 -> "[$globalLabel] 0.6X"
                1 -> "[$autoLabel] $dynamicLabel"
                else -> "[$preciseLabel] 4.0X"
            },
            color = themeColors.accent.copy(0.8f), fontSize = 8.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 8.dp, bottom = 4.dp) // 縮減底邊距
        )
    }
}
