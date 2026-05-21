package com.horizon.caadronesimulator.ui.hud

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.horizon.caadronesimulator.R
import com.horizon.caadronesimulator.model.AppConfig
import com.horizon.caadronesimulator.model.DroneState
import java.util.Locale

/**
 * [v1.5.8] 側邊導航儀表 - 智慧觸控優化版
 */
@Composable
fun SideNavInstruments(
    state: DroneState,
    onUpdateState: (DroneState.() -> Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!state.showSideSliders || state.showSettings) return

    val isReversed = state.reverseSliderSides
    
    // [v1.7.6] 佈局精修：將拉桿垂直縮短並置中，確保不會干涉位於 TopStart 或 BottomStart 的雷達視窗
    Box(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp).padding(top = 130.dp, bottom = 130.dp)) {
        // [v1.7.6] 位置對調：高度標尺改為左側 (預設)，抬頭標尺改為右側 (預設)
        Box(modifier = Modifier.align(if(isReversed) Alignment.CenterStart else Alignment.CenterEnd).fillMaxHeight().width(50.dp)) {
            HeightRuler(
                value = state.observerHeight,
                onValueChange = { h -> onUpdateState { observerHeight = h; if(cameraMode == AppConfig.CAM_MODE_OBS) lastManualTouchTime = System.currentTimeMillis() } },
                showRuler = state.showSideRulers,
                isAuto = (state.mappingObsHeight.axis != -1)
            )
        }

        // [v1.7.6] 位置對調：抬頭標尺改為右側 (預設)
        Box(modifier = Modifier.align(if(isReversed) Alignment.CenterEnd else Alignment.CenterStart).fillMaxHeight().width(50.dp)) {
            PitchRuler(
                value = state.observerTilt,
                onValueChange = { t -> onUpdateState { observerTilt = t; if(cameraMode == AppConfig.CAM_MODE_OBS) lastManualTouchTime = System.currentTimeMillis() } },
                showRuler = state.showSideRulers,
                isAuto = (state.mappingObsTilt.axis != -1)
            )
        }
    }
}

@Composable
private fun HeightRuler(value: Float, onValueChange: (Float) -> Unit, showRuler: Boolean, isAuto: Boolean) {
    var internalValue by remember(value) { mutableFloatStateOf(value) }
    
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (showRuler) {
            Text(stringResource(R.string.hud_altitude_short), color = if(isAuto) Color.Cyan else Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
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
                    .pointerInput(Unit) {
                        detectVerticalDragGestures { _, dragAmount ->
                            val delta = -dragAmount * 0.05f
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
        if (isAuto) Text(stringResource(R.string.visual_label_auto), color = Color.Cyan, fontSize = 8.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun PitchRuler(value: Float, onValueChange: (Float) -> Unit, showRuler: Boolean, isAuto: Boolean) {
    var internalValue by remember(value) { mutableFloatStateOf(value) }
    
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (showRuler) {
            Text(stringResource(R.string.hud_tilt_short), color = if(isAuto) Color.Cyan else Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Text(String.format(Locale.US, "%.0f°", internalValue), color = Color.Cyan, fontSize = 10.sp)
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
                    .pointerInput(Unit) {
                        detectVerticalDragGestures { _, dragAmount ->
                            val delta = -dragAmount * 0.25f
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
        if (isAuto) Text(stringResource(R.string.visual_label_auto), color = Color.Cyan, fontSize = 8.sp, fontWeight = FontWeight.Black)
    }
}
