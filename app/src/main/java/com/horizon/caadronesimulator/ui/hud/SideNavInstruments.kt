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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.horizon.caadronesimulator.R
import com.horizon.caadronesimulator.model.AppConfig
import com.horizon.caadronesimulator.model.DroneState
import com.horizon.caadronesimulator.ui.theme.NikoTheme
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

    val config = LocalConfiguration.current
    val isSmallDevice = config.screenWidthDp < 600 || config.screenHeightDp < 400
    val rulerWidth = if (isSmallDevice) 30.dp else 40.dp
    val innerPadding = if (isSmallDevice) 2.dp else 4.dp

    val isReversed = state.reverseSliderSides
    val isFpvMode = state.cameraMode == AppConfig.CAM_MODE_FPV
    
    // [v1.7.6] 佈局精修：將拉桿垂直縮短並置中，確保不會干涉位於 TopStart 或 BottomStart 的雷達視窗
    Box(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp).padding(top = 130.dp, bottom = 130.dp)) {
        if (isFpvMode) {
            // FPV 模式：僅顯示鏡頭仰角 (Gimbal Tilt)
            Box(modifier = Modifier.align(if(isReversed) Alignment.CenterEnd else Alignment.CenterStart).fillMaxHeight().width(rulerWidth)) {
                PitchRuler(
                    label = stringResource(R.string.visual_label_fpv_tilt),
                    value = state.cameraTilt,
                    onValueChange = { t -> onUpdateState { cameraTilt = t } },
                    range = -90f..20f, // FPV 雲台常見範圍
                    showRuler = state.showSideRulers,
                    isAuto = (state.mappingFpvTilt.axis != -1),
                    innerPadding = innerPadding
                )
            }
        } else {
            // 站位/追蹤模式：顯示站位高度與抬頭角度
            Box(modifier = Modifier.align(if(isReversed) Alignment.CenterStart else Alignment.CenterEnd).fillMaxHeight().width(rulerWidth)) {
                HeightRuler(
                    value = state.observerHeight,
                    onValueChange = { h -> onUpdateState { observerHeight = h; if(cameraMode == AppConfig.CAM_MODE_OBS) lastManualTouchTime = System.currentTimeMillis() } },
                    showRuler = state.showSideRulers,
                    isAuto = (state.mappingObsHeight.axis != -1),
                    innerPadding = innerPadding
                )
            }

            Box(modifier = Modifier.align(if(isReversed) Alignment.CenterEnd else Alignment.CenterStart).fillMaxHeight().width(rulerWidth)) {
                PitchRuler(
                    label = stringResource(R.string.hud_tilt_short),
                    value = state.observerTilt,
                    onValueChange = { t -> onUpdateState { observerTilt = t; if(cameraMode == AppConfig.CAM_MODE_OBS) lastManualTouchTime = System.currentTimeMillis() } },
                    range = -30f..85f,
                    showRuler = state.showSideRulers,
                    isAuto = (state.mappingObsTilt.axis != -1),
                    innerPadding = innerPadding
                )
            }
        }
    }
}

@Composable
private fun HeightRuler(value: Float, onValueChange: (Float) -> Unit, showRuler: Boolean, isAuto: Boolean, innerPadding: Dp) {
    var internalValue by remember(value) { mutableFloatStateOf(value) }
    val themeColors = NikoTheme.colors
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .background(themeColors.panel.copy(alpha = 0.75f), RoundedCornerShape(10.dp)) // 使用主題背板，增加透明度
            .padding(horizontal = innerPadding, vertical = 12.dp)
    ) {
        if (showRuler) {
            Text(stringResource(R.string.hud_altitude_short), color = if(isAuto) themeColors.primary else themeColors.textPrimary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Text(String.format(Locale.US, "%.1fm", internalValue), color = themeColors.primary, fontSize = 10.sp)
        }
        
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            if (showRuler) {
                Column(modifier = Modifier.fillMaxHeight().padding(vertical = 10.dp), verticalArrangement = Arrangement.SpaceBetween) {
                    repeat(7) { i -> Box(modifier = Modifier.width(if(i%2==0) 8.dp else 4.dp).height(1.dp).background(themeColors.textPrimary.copy(0.3f))) }
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
                Box(modifier = Modifier.fillMaxHeight().width(4.dp).background(themeColors.textPrimary.copy(0.15f), RoundedCornerShape(2.dp)))
                
                Slider(
                    value = internalValue,
                    onValueChange = {},
                    valueRange = 1.6f..25.0f,
                    enabled = false,
                    modifier = Modifier.fillMaxHeight().width(20.dp).rotate(-90f),
                    colors = SliderDefaults.colors(disabledThumbColor = if(isAuto) themeColors.primary else themeColors.textPrimary, disabledActiveTrackColor = Color.Transparent, disabledInactiveTrackColor = Color.Transparent)
                )
            }
        }
        if (isAuto) Text(stringResource(R.string.visual_label_auto), color = themeColors.primary, fontSize = 8.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun PitchRuler(
    label: String,
    value: Float, 
    onValueChange: (Float) -> Unit, 
    range: ClosedFloatingPointRange<Float> = -30f..85f,
    showRuler: Boolean, 
    isAuto: Boolean, 
    innerPadding: Dp
) {
    var internalValue by remember(value) { mutableFloatStateOf(value) }
    val themeColors = NikoTheme.colors
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .background(themeColors.panel.copy(alpha = 0.75f), RoundedCornerShape(10.dp)) // 使用主題背板
            .padding(horizontal = innerPadding, vertical = 12.dp)
    ) {
        if (showRuler) {
            Text(label, color = if(isAuto) themeColors.primary else themeColors.textPrimary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Text(String.format(Locale.US, "%.0f°", internalValue), color = themeColors.primary, fontSize = 10.sp)
        }
        
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            if (showRuler) {
                Column(modifier = Modifier.fillMaxHeight().padding(vertical = 10.dp), verticalArrangement = Arrangement.SpaceBetween) {
                    repeat(7) { i -> Box(modifier = Modifier.width(if(i%2==0) 8.dp else 4.dp).height(1.dp).background(themeColors.textPrimary.copy(0.3f))) }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(44.dp)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures { _, dragAmount ->
                            val delta = -dragAmount * 0.25f
                            internalValue = (internalValue + delta).coerceIn(range)
                            onValueChange(internalValue)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(modifier = Modifier.fillMaxHeight().width(4.dp).background(themeColors.textPrimary.copy(0.15f), RoundedCornerShape(2.dp)))
                
                Slider(
                    value = internalValue,
                    onValueChange = {},
                    valueRange = range,
                    enabled = false,
                    modifier = Modifier.fillMaxHeight().width(20.dp).rotate(-90f),
                    colors = SliderDefaults.colors(disabledThumbColor = if(isAuto) themeColors.primary else themeColors.textPrimary, disabledActiveTrackColor = Color.Transparent, disabledInactiveTrackColor = Color.Transparent)
                )
            }
        }
        if (isAuto) Text(stringResource(R.string.visual_label_auto), color = themeColors.primary, fontSize = 8.sp, fontWeight = FontWeight.Black)
    }
}
