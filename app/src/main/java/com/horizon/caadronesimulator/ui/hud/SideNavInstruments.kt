package com.horizon.caadronesimulator.ui.hud

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.horizon.caadronesimulator.model.DroneState
import java.util.Locale

/**
 * [v1.5.3] 專業側邊導航儀表層 (Ultra-Smooth Edition)
 * 實施「永續手勢監聽」與「阻尼解封」，達成原生 120Hz 絲滑手感。
 */
@Composable
fun SideNavInstruments(
    state: DroneState,
    onUpdateState: (DroneState.() -> Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    val isVisible = state.cameraMode.contains("站位視角") && !state.showSettings && state.showSideSliders
    if (!isVisible) return

    Box(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        // 左側拉桿 (預設高度 ALT)
        val leftX = if (state.reverseSliderSides) Alignment.CenterStart else Alignment.CenterEnd
        Box(modifier = Modifier.align(leftX).fillMaxHeight(0.35f).width(44.dp)) {
            HeightRuler(
                value = state.observerHeight,
                onValueChange = { h -> onUpdateState { 
                    observerHeight = h 
                    lastInteractionTime = System.currentTimeMillis()
                } },
                showRuler = state.showSideRulers,
                isAuto = state.cameraMode == "觀察員視角 (實驗性)"
            )
        }

        // 右側拉桿 (預設仰角 TILT)
        val rightX = if (state.reverseSliderSides) Alignment.CenterEnd else Alignment.CenterStart
        Box(modifier = Modifier.align(rightX).fillMaxHeight(0.35f).width(44.dp)) {
            PitchRuler(
                value = state.observerTilt,
                onValueChange = { t -> onUpdateState { 
                    observerTilt = t 
                    lastInteractionTime = System.currentTimeMillis()
                } },
                showRuler = state.showSideRulers,
                isAuto = state.cameraMode == "觀察員視角 (實驗性)"
            )
        }
    }
}

@Composable
private fun HeightRuler(value: Float, onValueChange: (Float) -> Unit, showRuler: Boolean, isAuto: Boolean) {
    // 使用內部 state 追蹤當前拖動中的數值，確保手勢不中斷
    var internalValue by remember(value) { mutableFloatStateOf(value) }
    
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (showRuler) {
            Text("ALT", color = if(isAuto) Color.Cyan else Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Text(String.format(Locale.US, "%.1fm", internalValue), color = Color.Cyan, fontSize = 10.sp)
        }
        
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            if (showRuler) {
                Column(modifier = Modifier.fillMaxHeight().padding(vertical = 10.dp), verticalArrangement = Arrangement.SpaceBetween) {
                    repeat(7) { i -> Box(modifier = Modifier.width(if(i%2==0) 8.dp else 4.dp).height(1.dp).background(Color.White.copy(0.3f))) }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(44.dp)
                    .pointerInput(Unit) { // [關鍵] 使用 Unit 確保手勢不因重繪中斷
                        detectVerticalDragGestures { _, dragAmount ->
                            val delta = -dragAmount * 0.05f // 解封阻尼，提升靈敏度
                            internalValue = (internalValue + delta).coerceIn(1.6f, 25.0f)
                            onValueChange(internalValue)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(modifier = Modifier.fillMaxHeight().width(4.dp).background(Color.White.copy(0.1f), RoundedCornerShape(2.dp)))
                
                Slider(
                    value = internalValue,
                    onValueChange = {},
                    valueRange = 1.6f..25.0f,
                    enabled = false,
                    modifier = Modifier.fillMaxHeight().width(20.dp).rotate(-90f),
                    colors = SliderDefaults.colors(disabledThumbColor = if(isAuto) Color.Cyan else Color.White, disabledActiveTrackColor = Color.Transparent, disabledInactiveTrackColor = Color.Transparent)
                )
            }
        }
        if (isAuto) Text("AUTO", color = Color.Cyan, fontSize = 8.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun PitchRuler(value: Float, onValueChange: (Float) -> Unit, showRuler: Boolean, isAuto: Boolean) {
    var internalValue by remember(value) { mutableFloatStateOf(value) }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (showRuler) {
            Text("TILT", color = if(isAuto) Color.Cyan else Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Text(String.format(Locale.US, "%.0f°", internalValue), color = Color.Cyan, fontSize = 10.sp)
        }

        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            if (showRuler) {
                val horizonY = 1f - (30f / 115f) 
                Box(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.align(Alignment.Center).fillMaxHeight(horizonY).fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Cyan.copy(0.1f)))))
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(44.dp)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures { _, dragAmount ->
                            val delta = -dragAmount * 0.6f // 解封仰角阻尼
                            internalValue = (internalValue + delta).coerceIn(-30f, 85f)
                            onValueChange(internalValue)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(modifier = Modifier.fillMaxHeight().width(4.dp).background(Color.White.copy(0.1f), RoundedCornerShape(2.dp)))
                
                Slider(
                    value = internalValue,
                    onValueChange = {},
                    valueRange = -30f..85f,
                    enabled = false,
                    modifier = Modifier.fillMaxHeight().width(20.dp).rotate(-90f),
                    colors = SliderDefaults.colors(disabledThumbColor = if(isAuto) Color.Cyan else Color.White, disabledActiveTrackColor = Color.Transparent, disabledInactiveTrackColor = Color.Transparent)
                )
            }
        }
        if (isAuto) Text("AUTO", color = Color.Cyan, fontSize = 8.sp, fontWeight = FontWeight.Black)
    }
}
