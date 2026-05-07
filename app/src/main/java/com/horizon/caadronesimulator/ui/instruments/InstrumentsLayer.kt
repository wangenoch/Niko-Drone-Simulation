package com.horizon.caadronesimulator.ui.instruments

import androidx.compose.foundation.* // 基礎 UI 元件 (Canvas, Background, Clickable)
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.* // 佈局定位工具 (Box, Row, Column)
import androidx.compose.foundation.shape.CircleShape // 圓形輪廓
import androidx.compose.foundation.shape.RoundedCornerShape // 圓角矩形輪廓
import androidx.compose.material.icons.Icons // Material 圖示庫
import androidx.compose.material.icons.filled.Radar // 雷達圖示
import androidx.compose.material3.* // Material 3 UI 組件 (IconButton)
import androidx.compose.runtime.* // 狀態管理 (remember, mutableIntStateOf)
import androidx.compose.ui.Alignment // 元件對齊
import androidx.compose.ui.Modifier // 組件修飾符
import androidx.compose.ui.draw.clip // 裁切處理
import androidx.compose.ui.draw.drawWithCache // 繪圖快取優化 (v1.2.68)
import androidx.compose.ui.geometry.Offset // 幾何座標
import androidx.compose.ui.geometry.Size // 幾幾尺寸
import androidx.compose.ui.graphics.Color // 顏色類型
import androidx.compose.ui.graphics.Path // 繪圖路徑
import androidx.compose.ui.graphics.PathEffect // 路徑樣式 (虛線)
import androidx.compose.ui.graphics.drawscope.Stroke // 線條描邊
import androidx.compose.ui.graphics.drawscope.clipPath // 畫布裁切
import androidx.compose.ui.graphics.drawscope.rotate // 畫布旋轉
import androidx.compose.ui.input.pointer.pointerInput // 觸控輸入優化 (v1.2.71)
import androidx.compose.ui.layout.onGloballyPositioned // 取得全域位置
import androidx.compose.ui.layout.positionInWindow // 計算視窗相對座標
import androidx.compose.ui.unit.dp // 密度無關像素
import androidx.compose.ui.unit.sp // 文字縮放單位
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight // 字體粗細
import com.horizon.caadronesimulator.model.DroneState // 飛機狀態模型
import com.horizon.caadronesimulator.model.DroneRegistry // 無人機規格註冊表
import java.util.Locale // 地區格式化
import kotlin.math.* // 數學運算 (abs, sin, cos)

/**
 * [v1.2.68] 獨立飛行儀表層 (效能優化版)
 * 實作 Project B: 繪圖圖層硬體加速快取
 */
@Composable
fun InstrumentsLayer(
    state: DroneState,
    onUpdatePipRect: (android.graphics.Rect?) -> Unit,
    onUpdateState: (DroneState.() -> Unit) -> Unit
) {
    var radarMode by remember { mutableIntStateOf(0) }
    val radarAlign = if (state.showVirtualJoysticks) Alignment.TopStart else Alignment.BottomStart
    val radarPad = if (state.showVirtualJoysticks) Modifier.padding(top = 16.dp, start = 20.dp) else Modifier.padding(bottom = 16.dp, start = 16.dp)
    
    Box(modifier = Modifier.fillMaxSize()) {
        Box(modifier = radarPad.align(radarAlign)) {
            when (radarMode) {
                0 -> RadarHUD(state) { radarMode = 1 }
                1 -> OsdView(state, onUpdatePipRect) { onUpdatePipRect(null); radarMode = 2 }
                2 -> AttitudeView(state) { radarMode = 3 }
                3 -> IconButton(onClick = { radarMode = 0 }, modifier = Modifier.size(44.dp).background(Color(0xAA111111), CircleShape).border(1.dp, Color(0xFFFF9800), CircleShape)) { 
                    Icon(Icons.Default.Radar, null, tint = Color.White, modifier = Modifier.size(22.dp)) 
                }
            }
        }
        
        // [v1.3.8] 互動式相機控制條 - 對稱佈局與雙拉桿支援
        val isFPV = state.cameraMode == "FPV 視角"
        val isStation = state.cameraMode.contains("站位視角")

        if (isFPV) {
            // FPV 模式：單一鏡頭仰角拉桿 (預設在右側)
            val isOnLeft = state.reverseSliderSides
            InteractiveCameraSlider(
                label = "${state.cameraTilt.toInt()}°",
                value = (state.cameraTilt + 30f) / 75f,
                alignment = if (isOnLeft) Alignment.CenterStart else Alignment.CenterEnd,
                modifier = if (isOnLeft) Modifier.padding(start = 16.dp) else Modifier.padding(end = 16.dp),
                onDrag = { drag -> 
                    val newTilt = (state.cameraTilt - drag * 0.5f).coerceIn(-30f, 45f)
                    onUpdateState { cameraTilt = newTilt }
                }
            )
        } else if (isStation) {
            // 站位模式：對稱雙拉桿 (高度 + 觀察仰角)
            
            // 1. 觀察者高度拉桿 (預設在左側)
            val isHeightOnLeft = !state.reverseSliderSides
            InteractiveCameraSlider(
                label = String.format(Locale.US, "%.1fm", state.observerHeight),
                value = (state.observerHeight - 1.6f) / (8.0f - 1.6f),
                alignment = if (isHeightOnLeft) Alignment.CenterStart else Alignment.CenterEnd,
                modifier = if (isHeightOnLeft) Modifier.padding(start = 16.dp) else Modifier.padding(end = 16.dp),
                onDrag = { drag -> 
                    val newHeight = (state.observerHeight - drag * 0.02f).coerceIn(1.6f, 8.0f)
                    onUpdateState { observerHeight = newHeight }
                }
            )

            // 2. 觀察者仰角拉桿 (預設在右側)
            val isTiltOnLeft = state.reverseSliderSides
            InteractiveCameraSlider(
                label = "${state.observerTilt.toInt()}°",
                value = (state.observerTilt + 30f) / 75f,
                alignment = if (isTiltOnLeft) Alignment.CenterStart else Alignment.CenterEnd,
                modifier = if (isTiltOnLeft) Modifier.padding(start = 16.dp) else Modifier.padding(end = 16.dp),
                onDrag = { drag -> 
                    val newTilt = (state.observerTilt - drag * 0.4f).coerceIn(-30f, 45f)
                    onUpdateState { observerTilt = newTilt }
                },
                color = Color.Yellow // 黃色區分仰角，青色區分高度
            )
        }
    }
}

/**
 * [v1.3.8] 通用相機調整拉桿組件
 */
@Composable
fun InteractiveCameraSlider(
    label: String,
    value: Float,
    alignment: Alignment,
    modifier: Modifier = Modifier,
    color: Color = Color.Cyan,
    onDrag: (Float) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(modifier),
        contentAlignment = alignment
    ) {
        Box(
            modifier = Modifier
                .width(40.dp)
                .fillMaxHeight(0.4f)
                .pointerInput(Unit) {
                    detectVerticalDragGestures { _, dragAmount -> onDrag(dragAmount) }
                },
            contentAlignment = Alignment.Center
        ) {
            // 視覺導軌
            Box(modifier = Modifier.width(6.dp).fillMaxHeight().background(Color(0x44000000), RoundedCornerShape(3.dp)).border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(3.dp)))
            
            // 動態位置圓點
            Canvas(modifier = Modifier.fillMaxSize()) {
                val dotY = size.height * (1f - value.coerceIn(0f, 1f))
                drawCircle(color = color, radius = 4.dp.toPx(), center = Offset(size.width / 2, dotY), style = Stroke(2.dp.toPx()))
                drawCircle(color = color.copy(alpha = 0.5f), radius = 2.dp.toPx(), center = Offset(size.width / 2, dotY))
            }
            
            Text(
                text = label,
                color = color.copy(0.7f),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp)
            )
        }
    }
}


@Composable
fun OsdView(state: DroneState, onUpdatePipRect: (android.graphics.Rect?) -> Unit, onClick: () -> Unit) {
    val spec = DroneRegistry.getSpec(state.droneType)
    Box(modifier = Modifier.size(150.dp, 100.dp).clip(RoundedCornerShape(12.dp)).background(Color(0x55111111)).border(2.dp, Color(0xFFFF9800), RoundedCornerShape(12.dp)).clickable { onClick() }) {
        Box(modifier = Modifier.fillMaxSize().padding(3.dp).onGloballyPositioned { coords ->
            val pos = coords.positionInWindow(); val size = coords.size
            onUpdatePipRect(android.graphics.Rect(pos.x.toInt(), pos.y.toInt(), (pos.x + size.width).toInt(), (pos.y + size.height).toInt()))
        })
        Box(modifier = Modifier.fillMaxSize().padding(10.dp)) {
            Column(modifier = Modifier.align(Alignment.CenterStart)) {
                Text("V: ${String.format(Locale.US, "%.1f", state.speed)}", color = Color.Green, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                Text("P: ${state.pitch.toInt()}°", color = Color.Green, fontSize = 7.sp)
            }
            Column(modifier = Modifier.align(Alignment.CenterEnd), horizontalAlignment = Alignment.End) {
                Text("H: ${String.format(Locale.US, "%.1f", (state.altitude - spec.groundOffset))}", color = Color.Green, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                Text("T: ${state.cameraTilt.toInt()}°", color = Color.Cyan, fontSize = 7.sp)
            }
            Text("D: ${String.format(Locale.US, "%.1f", sqrt(state.posX.pow(2) + (state.posZ + 6f).pow(2)))}m", modifier = Modifier.align(Alignment.BottomCenter), color = Color.Green, fontSize = 8.sp)
            Text("${state.yaw.toInt()}°", modifier = Modifier.align(Alignment.TopCenter), color = Color.Green, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun AttitudeView(state: DroneState, onClick: () -> Unit) {
    val textMeasurer = rememberTextMeasurer()
    Box(modifier = Modifier.size(100.dp).background(Color(0xAA111111), CircleShape).border(1.5.dp, Color(0xFF00BFFF), CircleShape).clickable { onClick() }) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2, size.height / 2); val r = size.minDimension / 2
            rotate(-state.yaw, c) {
                for (i in 0 until 360 step 30) {
                    val aR = Math.toRadians(i.toDouble() - 90.0).toFloat(); val isMain = i % 90 == 0; val len = if (isMain) 8.dp.toPx() else 4.dp.toPx(); val color = if (i == 0) Color.Red else Color.White.copy(0.6f)
                    drawLine(color, Offset(c.x + cos(aR) * (r - 2.dp.toPx()), c.y + sin(aR) * (r - 2.dp.toPx())), Offset(c.x + cos(aR) * (r - 2.dp.toPx() - len), c.y + sin(aR) * (r - 2.dp.toPx() - len)), if (isMain) 2.dp.toPx() else 1.dp.toPx())
                    if (isMain) {
                        val label = when(i) { 0 -> "N"; 90 -> "E"; 180 -> "S"; 270 -> "W"; else -> "" }
                        val textLayoutResult = textMeasurer.measure(text = label, style = TextStyle(color = if (i == 0) Color.Red else Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold))
                        val textPos = Offset(c.x + cos(aR) * (r - 14.dp.toPx()), c.y + sin(aR) * (r - 14.dp.toPx()))
                        rotate(state.yaw, textPos) { drawText(textLayoutResult = textLayoutResult, topLeft = Offset(textPos.x - textLayoutResult.size.width / 2, textPos.y - textLayoutResult.size.height / 2)) }
                    }
                }
            }
            clipPath(Path().apply { addOval(androidx.compose.ui.geometry.Rect(c, r - 20.dp.toPx())) }) {
                val pOff = -(state.pitch / 45f) * (r - 20.dp.toPx())
                rotate(-state.roll, c) {
                    drawRect(Color(0xFF5D4037), Offset(-size.width, size.height / 2 + pOff), Size(size.width * 3, size.height * 2))
                    drawRect(Color(0xFF0288D1), Offset(-size.width, -size.height * 2 + size.height / 2 + pOff), Size(size.width * 3, size.height * 2))
                    drawLine(Color.White, Offset(-size.width, size.height / 2 + pOff), Offset(size.width * 2, size.height / 2 + pOff), 1.5.dp.toPx())
                }
            }
            val tipPath = Path().apply { moveTo(c.x, 2.dp.toPx()); lineTo(c.x - 5.dp.toPx(), 12.dp.toPx()); lineTo(c.x + 5.dp.toPx(), 12.dp.toPx()); close() }
            drawPath(tipPath, Color.Red)
            val heading = ((state.yaw % 360 + 360) % 360).toInt(); val hLayout = textMeasurer.measure("$heading°", TextStyle(color = Color.Cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold))
            drawRect(Color.Black.copy(0.4f), Offset(c.x - hLayout.size.width/2 - 2.dp.toPx(), size.height - 18.dp.toPx()), Size(hLayout.size.width.toFloat() + 4.dp.toPx(), 14.dp.toPx()))
            drawText(textLayoutResult = hLayout, topLeft = Offset(c.x - hLayout.size.width / 2, size.height - 18.dp.toPx()))
        }
    }
}
