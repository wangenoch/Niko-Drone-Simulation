package com.horizon.caadronesimulator.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.horizon.caadronesimulator.model.DroneState
import java.util.Locale

/**
 * [v1.4.0] 側邊導航儀表層
 * 將左右拉桿升級為具備刻度與地平線參考的精密儀表
 */
@Composable
fun SideNavInstruments(
    state: DroneState,
    onUpdateState: (DroneState.() -> Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    // 僅在站位視角且未顯示設定時呈現
    val isVisible = state.cameraMode.contains("站位視角") && !state.showSettings
    if (!isVisible) return

    Box(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        // 左側：高度拉桿 (1.6m - 8.0m)
        val leftX = if (state.reverseSliderSides) Alignment.CenterEnd else Alignment.CenterStart
        Box(modifier = Modifier.align(leftX).fillMaxHeight(0.5f).width(40.dp)) {
            HeightRuler(
                value = state.observerHeight,
                onValueChange = { h -> onUpdateState { observerHeight = h } },
                showRuler = state.showSideRulers,
                isAuto = state.useSmartObserver
            )
        }

        // 右側：仰角拉桿 (-30 - 45)
        val rightX = if (state.reverseSliderSides) Alignment.CenterStart else Alignment.CenterEnd
        Box(modifier = Modifier.align(rightX).fillMaxHeight(0.5f).width(40.dp)) {
            PitchRuler(
                value = state.observerTilt,
                onValueChange = { t -> onUpdateState { observerTilt = t } },
                showRuler = state.showSideRulers,
                isAuto = state.useSmartObserver
            )
        }
    }
}

@Composable
private fun HeightRuler(
    value: Float,
    onValueChange: (Float) -> Unit,
    showRuler: Boolean,
    isAuto: Boolean
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (showRuler) {
            Text("ALT", color = if(isAuto) Color.Cyan else Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Text(String.format(Locale.US, "%.1fm", value), color = Color.Cyan, fontSize = 10.sp)
        }
        
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            // 背景刻度
            if (showRuler) {
                Column(modifier = Modifier.fillMaxHeight().padding(vertical = 10.dp), verticalArrangement = Arrangement.SpaceBetween) {
                    repeat(7) { i ->
                        Box(modifier = Modifier.width(if(i%2==0) 8.dp else 4.dp).height(1.dp).background(Color.White.copy(0.3f)))
                    }
                }
            }

            // 滑桿
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = 1.6f..8.0f,
                modifier = Modifier
                    .fillMaxHeight()
                    .width(20.dp)
                    .rotate(-90f),
                colors = SliderDefaults.colors(
                    thumbColor = if(isAuto) Color.Cyan else Color.White,
                    activeTrackColor = Color.Transparent,
                    inactiveTrackColor = Color.Transparent
                )
            )
        }
        
        if (isAuto) {
            Text("AUTO", color = Color.Cyan, fontSize = 8.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun PitchRuler(
    value: Float,
    onValueChange: (Float) -> Unit,
    showRuler: Boolean,
    isAuto: Boolean
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (showRuler) {
            Text("TILT", color = if(isAuto) Color.Cyan else Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Text(String.format(Locale.US, "%.0f°", value), color = Color.Cyan, fontSize = 10.sp)
        }

        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            if (showRuler) {
                // 地平線參考 (0度線)
                // 計算 0 度在拉桿中的百分比位置：(-30 to 45) 範圍中，0 在 30/75 = 40% 處
                val horizonY = 1f - (30f / 75f) 
                Box(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.align(Alignment.Center).fillMaxHeight(horizonY).fillMaxWidth().background(
                        Brush.verticalGradient(listOf(Color.Transparent, Color.Cyan.copy(0.1f)))
                    ))
                }
            }

            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = -30f..85f, // 擴大仰角至 85 度
                modifier = Modifier
                    .fillMaxHeight()
                    .width(20.dp)
                    .rotate(-90f),
                colors = SliderDefaults.colors(
                    thumbColor = if(isAuto) Color.Cyan else Color.White,
                    activeTrackColor = Color.Transparent,
                    inactiveTrackColor = Color.Transparent
                )
            )
        }

        if (isAuto) {
            Text("AUTO", color = Color.Cyan, fontSize = 8.sp, fontWeight = FontWeight.Black)
        }
    }
}
