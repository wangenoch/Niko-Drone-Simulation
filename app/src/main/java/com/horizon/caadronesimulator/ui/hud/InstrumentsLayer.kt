package com.horizon.caadronesimulator.ui.hud

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.horizon.caadronesimulator.R
import com.horizon.caadronesimulator.model.DroneState
import com.horizon.caadronesimulator.model.DroneRegistry
import com.horizon.caadronesimulator.ui.theme.NikoTheme
import java.util.Locale
import kotlin.math.*

/**
 * [v1.5.9] 獨立飛行儀表層 (Optimized Display)
 */
@Composable
fun InstrumentsLayer(
    state: DroneState,
    onUpdatePipRect: (android.graphics.Rect?) -> Unit,
    onUpdateState: (DroneState.() -> Unit) -> Unit
) {
    val radarAlign = if (state.showVirtualJoysticks) Alignment.TopStart else Alignment.BottomStart
    val radarPad = if (state.showVirtualJoysticks) Modifier.padding(top = 16.dp, start = 16.dp) else Modifier.padding(bottom = 16.dp, start = 16.dp)
    
    Box(modifier = Modifier.fillMaxSize()) {
        Box(modifier = radarPad.align(radarAlign)) {
            when (state.hudMode) {
                0 -> RadarHUD(state, modifier = Modifier.size(150.dp, 100.dp)) { onUpdateState { hudMode = 1 } }
                1 -> OsdView(state, onUpdatePipRect, modifier = Modifier.size(150.dp, 100.dp)) { onUpdatePipRect(null); onUpdateState { hudMode = 2 } }
                2 -> AttitudeView(state, modifier = Modifier.size(120.dp)) { onUpdateState { hudMode = 3 } }
                3 -> IconButton(
                    onClick = { onUpdateState { hudMode = 0 } }, 
                    modifier = Modifier.size(44.dp).background(Color(0xAA111111), CircleShape).border(1.dp, Color(0xFFFF9800), CircleShape)
                ) { 
                    Icon(Icons.Default.Radar, null, tint = Color.White, modifier = Modifier.size(22.dp)) 
                }
            }
        }
    }
}

@Composable
fun PrecisionZoomView(
    state: com.horizon.caadronesimulator.model.DroneState,
    modifier: Modifier = Modifier,
    onUpdateRect: (androidx.compose.ui.geometry.Rect?) -> Unit
) {
    Box(
        modifier = modifier
            .size(150.dp, 100.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x44111111)) // 恢復深色半透明背景，確保不會遮擋 3D 渲染
            .border(1.5.dp, NikoTheme.colors.primary.copy(0.6f), RoundedCornerShape(12.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp)
                .onGloballyPositioned { coords ->
                    val pos = coords.positionInWindow(); val size = coords.size
                    // [v1.7.6] 加入邊界緩衝，防止 FBO 覆蓋 Compose 邊框
                    onUpdateRect(androidx.compose.ui.geometry.Rect(pos.x + 2, pos.y + 2, pos.x + size.width - 2, pos.y + size.height - 2))
                }
        )
    }
}

@Composable
fun OsdView(state: DroneState, onUpdatePipRect: (android.graphics.Rect?) -> Unit, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val spec = DroneRegistry.getSpec(state.droneType)
    Box(modifier = modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xAA111111)).border(2.dp, NikoTheme.colors.primary.copy(0.6f), RoundedCornerShape(12.dp)).clickable { onClick() }) {
        Box(modifier = Modifier.fillMaxSize().padding(3.dp).onGloballyPositioned { coords ->
            val pos = coords.positionInWindow(); val size = coords.size
            // [v1.7.6] 修正：在 Compose 坐標系轉換為 Android Graphics Rect 時加入邊界緩衝
            onUpdatePipRect(android.graphics.Rect(pos.x.toInt() + 2, pos.y.toInt() + 2, (pos.x + size.width).toInt() - 2, (pos.y + size.height).toInt() - 2))
        })
        Box(modifier = Modifier.fillMaxSize().padding(10.dp)) {
            val vText = String.format(Locale.US, "%.1f", state.speed)
            val hText = String.format(Locale.US, "%.1f", (state.altitude - spec.groundOffset))
            val dText = String.format(Locale.US, "%.1f", state.horizontalDist)

            val color = if(NikoTheme.colors.isLight) NikoTheme.colors.primary else Color.Green

            Column(modifier = Modifier.align(Alignment.CenterStart)) {
                Text("${stringResource(R.string.hud_speed_short)}: $vText", color = color, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                Text("${stringResource(R.string.hud_pitch_short)}: ${state.pitch.toInt()}°", color = color, fontSize = 7.sp)
            }
            Column(modifier = Modifier.align(Alignment.CenterEnd), horizontalAlignment = Alignment.End) {
                Text("${stringResource(R.string.hud_altitude_short)}: $hText", color = color, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                Text("${stringResource(R.string.hud_tilt_short)}: ${state.cameraTilt.toInt()}°", color = NikoTheme.colors.accent, fontSize = 7.sp)
            }
            Text("${stringResource(R.string.hud_distance_short)}: ${dText}${stringResource(R.string.hud_unit_m)}", modifier = Modifier.align(Alignment.BottomCenter), color = color, fontSize = 8.sp)
            Text("${state.yaw.toInt()}°", modifier = Modifier.align(Alignment.TopCenter), color = color, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun AttitudeView(state: DroneState, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val textMeasurer = rememberTextMeasurer()
    val themeColors = NikoTheme.colors
    Box(modifier = modifier.background(themeColors.panel, CircleShape).border(1.5.dp, themeColors.primary, CircleShape).clickable { onClick() }) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2, size.height / 2); val r = size.minDimension / 2
            rotate(-state.yaw, c) {
                for (i in 0 until 360 step 30) {
                    val aR = Math.toRadians(i.toDouble() - 90.0).toFloat(); val isMain = i % 90 == 0; val len = if (isMain) 8.dp.toPx() else 4.dp.toPx(); val color = if (i == 0) Color.Red else themeColors.textPrimary.copy(0.6f)
                    drawLine(color, Offset(c.x + cos(aR) * (r - 2.dp.toPx()), c.y + sin(aR) * (r - 2.dp.toPx())), Offset(c.x + cos(aR) * (r - 2.dp.toPx() - len), c.y + sin(aR) * (r - 2.dp.toPx() - len)), if (isMain) 2.dp.toPx() else 1.dp.toPx())
                    if (isMain) {
                        val label = when(i) { 0 -> "N"; 90 -> "E"; 180 -> "S"; 270 -> "W"; else -> "" }
                        val textLayoutResult = textMeasurer.measure(text = label, style = TextStyle(color = if (i == 0) Color.Red else themeColors.textPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold))
                        val textPos = Offset(c.x + cos(aR) * (r - 14.dp.toPx()), c.y + sin(aR) * (r - 14.dp.toPx()))
                        rotate(state.yaw, textPos) { drawText(textLayoutResult = textLayoutResult, topLeft = Offset(textPos.x - textLayoutResult.size.width / 2, textPos.y - textLayoutResult.size.height / 2)) }
                    }
                }
            }
            clipPath(Path().apply { addOval(androidx.compose.ui.geometry.Rect(c, r - 20.dp.toPx())) }) {
                // [關鍵修復] 俯仰極性校準：抬頭 (Pitch > 0) 時地平線應下降 (+)，露出天空
                val pOff = (state.pitch / 45f) * (r - 20.dp.toPx())
                rotate(-state.roll, c) {
                    drawRect(Color(0xFF5D4037), Offset(-size.width, size.height / 2 + pOff), Size(size.width * 3, size.height * 2))
                    drawRect(Color(0xFF0288D1), Offset(-size.width, -size.height * 2 + size.height / 2 + pOff), Size(size.width * 3, size.height * 2))
                    drawLine(Color.White, Offset(-size.width, size.height / 2 + pOff), Offset(size.width * 2, size.height / 2 + pOff), 1.5.dp.toPx())
                }
            }
            val tipPath = Path().apply { moveTo(c.x, 2.dp.toPx()); lineTo(c.x - 5.dp.toPx(), 12.dp.toPx()); lineTo(c.x + 5.dp.toPx(), 12.dp.toPx()); close() }
            drawPath(tipPath, Color.Red)
            
            // [v1.7.6] 儀表文字標籤與高度位置修正
            val heading = ((state.yaw % 360 + 360) % 360).toInt(); val hLayout = textMeasurer.measure("$heading°", TextStyle(color = themeColors.accent, fontSize = 10.sp, fontWeight = FontWeight.Bold))
            drawRect(themeColors.panel.copy(0.4f), Offset(c.x - hLayout.size.width/2 - 2.dp.toPx(), size.height - 18.dp.toPx()), Size(hLayout.size.width.toFloat() + 4.dp.toPx(), 14.dp.toPx()))
            drawText(textLayoutResult = hLayout, topLeft = Offset(c.x - hLayout.size.width / 2, size.height - 18.dp.toPx()))
        }
    }
}
