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

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * [v1.5.9] 獨立飛行儀表層 (Optimized Display)
 */
@Composable
fun InstrumentsLayer(
    state: DroneState,
    onUpdatePipRect: (android.graphics.Rect?) -> Unit,
    onUpdateState: (DroneState.() -> Unit) -> Unit,
    onUpdateTutorialTargets: (String, androidx.compose.ui.geometry.Rect) -> Unit = { _, _ -> }
) {
    val themeColors = NikoTheme.colors
    val haptic = LocalHapticFeedback.current

    val radarAlign = if (state.showVirtualJoysticks) Alignment.TopStart else Alignment.BottomStart
    val radarPad = if (state.showVirtualJoysticks) Modifier.padding(top = 16.dp, start = 16.dp) else Modifier.padding(bottom = 16.dp, start = 16.dp)
    
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = radarPad
                .align(radarAlign)
                .offset(x = state.radarOffset.x.dp, y = state.radarOffset.y.dp) // [v1.7.12] 應用自由拖拽偏移 (全儀表通用)
                .onGloballyPositioned { onUpdateTutorialTargets("radar", it.positionInWindow().let { pos -> androidx.compose.ui.geometry.Rect(pos.x, pos.y, pos.x + it.size.width, pos.y + it.size.height) }) }
                .border(
                    1.5.dp, 
                    if(state.isRadarUnlocked) themeColors.primary else Color.Transparent, // [v1.7.12] 解鎖時顯示全儀表邊框
                    if(state.hudMode == 3) CircleShape else RoundedCornerShape(12.dp) // [v1.7.12] 自動適配內容形狀，解決最小化時框不對位問題
                )
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDrag = { change, dragAmount ->
                            if (state.isRadarUnlocked) {
                                change.consume()
                                state.radarOffset += Offset(dragAmount.x / density, dragAmount.y / density)
                            }
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { 
                            // 點擊區域時，循環切換模式 (模式 3 為最小化，所以回到 0)
                            val nextMode = (state.hudMode + 1) % 4
                            // [v1.7.12] 關鍵修復：從模式 1 (OSD/FPV) 切換出時，主動清除 FBO 渲染區域，防止 FPV 殘留
                            if (state.hudMode == 1) onUpdatePipRect(null)
                            onUpdateState { hudMode = nextMode }
                        },
                        onLongPress = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (state.isRadarUnlocked) {
                                // 再次長按：歸位並鎖定
                                state.radarOffset = Offset.Zero
                                state.isRadarUnlocked = false
                            } else {
                                // 長按：解鎖移動
                                state.isRadarUnlocked = true
                            }
                        }
                    )
                }
        ) {
            when (state.hudMode) {
                0 -> RadarHUD(state, modifier = Modifier.size(150.dp, 100.dp))
                1 -> OsdView(state, onUpdatePipRect, modifier = Modifier.size(150.dp, 100.dp))
                2 -> AttitudeView(state, modifier = Modifier.size(120.dp))
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
            .background(Color(0x44111111))
            .border(1.5.dp, Color(0xFFFF9800).copy(0.6f), RoundedCornerShape(12.dp))
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
fun OsdView(state: DroneState, onUpdatePipRect: (android.graphics.Rect?) -> Unit, modifier: Modifier = Modifier) {
    val spec = DroneRegistry.getSpec(state.droneType)
    val theme = NikoTheme
    Box(modifier = modifier.clip(theme.shapes.medium).background(Color.Transparent).border(2.dp, theme.colors.primary.copy(0.6f), theme.shapes.medium)) {
        Box(modifier = Modifier.fillMaxSize().padding(3.dp).onGloballyPositioned { coords ->
            val pos = coords.positionInWindow(); val size = coords.size
            // [v1.7.6] 修正：在 Compose 坐標系轉換為 Android Graphics Rect 時加入邊界緩衝
            onUpdatePipRect(android.graphics.Rect(pos.x.toInt() + 2, pos.y.toInt() + 2, (pos.x + size.width).toInt() - 2, (pos.y + size.height).toInt() - 2))
        })
        Box(modifier = Modifier.fillMaxSize().padding(10.dp)) {
            val vText = String.format(Locale.US, "%.1f", state.speed)
            val hText = String.format(Locale.US, "%.1f", (state.altitude - spec.groundOffset))
            val dText = String.format(Locale.US, "%.1f", state.horizontalDist)

            Column(modifier = Modifier.align(Alignment.CenterStart)) {
                Text("${stringResource(R.string.hud_speed_short)}: $vText", color = theme.colors.status, style = theme.typography.caption.copy(fontWeight = FontWeight.Bold))
                Text("${stringResource(R.string.hud_pitch_short)}: ${state.pitch.toInt()}°", color = theme.colors.status, style = theme.typography.caption.copy(fontSize = 7.sp))
            }
            Column(modifier = Modifier.align(Alignment.CenterEnd), horizontalAlignment = Alignment.End) {
                Text("${stringResource(R.string.hud_altitude_short)}: $hText", color = theme.colors.status, style = theme.typography.caption.copy(fontWeight = FontWeight.Bold))
                Text("${stringResource(R.string.hud_tilt_short)}: ${state.cameraTilt.toInt()}°", color = theme.colors.primary, style = theme.typography.caption.copy(fontSize = 7.sp))
            }
            Text("${stringResource(R.string.hud_distance_short)}: ${dText}${stringResource(R.string.hud_unit_m)}", modifier = Modifier.align(Alignment.BottomCenter), color = theme.colors.status, style = theme.typography.caption)
            Text("${state.yaw.toInt()}°", modifier = Modifier.align(Alignment.TopCenter), color = theme.colors.status, style = theme.typography.label)
        }
    }
}

@Composable
fun AttitudeView(state: DroneState, modifier: Modifier = Modifier) {
    val textMeasurer = rememberTextMeasurer()
    Box(modifier = modifier.background(Color(0xAA111111), CircleShape).border(1.5.dp, Color(0xFF00BFFF), CircleShape)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2, size.height / 2); val r = size.minDimension / 2
            rotate(-state.yaw, c) {
                for (i in 0 until 360 step 30) {
                    val aR = Math.toRadians(i.toDouble() - 90.0).toFloat(); val isMain = i % 90 == 0; val len = if (isMain) 8.dp.toPx() else 4.dp.toPx(); val color = if (i == 0) Color.Red else Color.White.copy(0.6f)
                    drawLine(color, Offset(c.x + cos(aR) * (r - 2.dp.toPx()), c.y + sin(aR) * (r - 2.dp.toPx())), Offset(c.x + cos(aR) * (r - 2.dp.toPx() - len), c.y + sin(aR) * (r - 2.dp.toPx() - len)), if (isMain) 2.dp.toPx() else 1.dp.toPx())
                    if (isMain) {
                        val label = when(i) { 0 -> "N"; 90 -> "E"; 180 -> "S"; 270 -> "W"; else -> "" }
                        val textLayoutResult = textMeasurer.measure(text = label, style = TextStyle(color = if (i == 0) Color.Red else Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold))
                        val textPos = Offset(c.x + cos(aR) * (r - 14.dp.toPx()), c.y + sin(aR) * (r - 14.dp.toPx()))
                        rotate(state.yaw, textPos) { drawText(textLayoutResult = textLayoutResult, topLeft = Offset(textPos.x - textLayoutResult.size.width / 2, textPos.y - textLayoutResult.size.height / 2)) }
                    }
                }
            }
            clipPath(Path().apply { addOval(androidx.compose.ui.geometry.Rect(c, r - 20.dp.toPx())) }) {
                // [關鍵修復] 俯仰極性校準：抬頭 (Pitch > 0) 時地平線應下降 (+)，露出天空
                val pOff = (state.pitch / 45f) * (r - 20.dp.toPx())
                rotate(state.roll, c) {
                    drawRect(Color(0xFF5D4037), Offset(-size.width, size.height / 2 + pOff), Size(size.width * 3, size.height * 2))
                    drawRect(Color(0xFF0288D1), Offset(-size.width, -size.height * 2 + size.height / 2 + pOff), Size(size.width * 3, size.height * 2))
                    drawLine(Color.White, Offset(-size.width, size.height / 2 + pOff), Offset(size.width * 2, size.height / 2 + pOff), 1.5.dp.toPx())
                }
            }
            val tipPath = Path().apply { moveTo(c.x, 2.dp.toPx()); lineTo(c.x - 5.dp.toPx(), 12.dp.toPx()); lineTo(c.x + 5.dp.toPx(), 12.dp.toPx()); close() }
            drawPath(tipPath, Color.Red)
            
            // [v1.7.6] 儀表文字標籤與高度位置修正
            val heading = ((state.yaw % 360 + 360) % 360).toInt(); val hLayout = textMeasurer.measure("$heading°", TextStyle(color = Color.Cyan, fontSize = 10.sp, fontWeight = FontWeight.Bold))
            drawRect(Color.Black.copy(0.4f), Offset(c.x - hLayout.size.width/2 - 2.dp.toPx(), size.height - 18.dp.toPx()), Size(hLayout.size.width.toFloat() + 4.dp.toPx(), 14.dp.toPx()))
            drawText(textLayoutResult = hLayout, topLeft = Offset(c.x - hLayout.size.width / 2, size.height - 18.dp.toPx()))
        }
    }
}
