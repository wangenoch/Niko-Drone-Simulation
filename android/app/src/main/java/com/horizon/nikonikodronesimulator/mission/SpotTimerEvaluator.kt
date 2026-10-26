package com.horizon.nikonikodronesimulator.mission

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.horizon.nikonikodronesimulator.model.Constants
import com.horizon.nikonikodronesimulator.model.AppConfig
import com.horizon.nikonikodronesimulator.model.DroneState
import com.horizon.nikonikodronesimulator.model.DroneSpecs
import com.horizon.nikonikodronesimulator.model.DroneRegistry
import com.horizon.nikonikodronesimulator.ui.theme.NikoTheme
import com.horizon.nikonikodronesimulator.R
import java.util.Locale
import kotlin.math.*

import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.layout.onGloballyPositioned

/**
 * [v1.5.9] 定點計時評測器 - 高精確度校準版
 */
class SpotTimerEvaluator : MissionEvaluator {
    override val modeName: String = "SPOT" // 使用 ID，由 UI 轉譯

    override fun update(state: DroneState, dt: Float, spec: DroneSpecs) {
        if (!state.isSpotTimerEnabled) return
        if (dt <= 0) return

        // [v1.7.6] 降低起飛偵測門檻至 0.1m，提升計時器啟動靈敏度
        val isAirborne = state.altitude > spec.groundOffset + 0.1f
        if (state.isMotorLocked || state.isCollision || !isAirborne) {
            resetTimer(state)
            state.spotTimerMessage = "IDLE" // 使用 ID 占位，由 UI 轉譯
            return
        }

        // 1. 旋轉偵測
        var yawDiff = abs(state.yaw - state.lastYaw)
        if (yawDiff > 180f) yawDiff = 360f - yawDiff 
        val yawRate = yawDiff / dt
        val isRotating = if (state.spotTimerStable) yawRate > 25.0f else yawRate > 15.0f 

        // [v1.5.9] 空間精確校準
        val relAlt = state.altitude - spec.groundOffset
        val isHeightOk = relAlt in 1.0f..3.5f

        // 2. 區域偵測 (DATUM: H-Pad)
        val hPadPos = floatArrayOf(0f, 0f)
        val coneTargets = Constants.CONE_POSITIONS + arrayOf(hPadPos)
        var inZoneId = -1
        
        for (idx in coneTargets.indices) {
            val tx = coneTargets[idx][0]
            val tz = coneTargets[idx][1]
            val dist = sqrt((state.posX - tx).toDouble().pow(2) + (state.posZ - tz).toDouble().pow(2)).toFloat()
            
            // 角錐半徑縮緊至 1.3m, H 坪維持 1.0m
            val isHPad = idx == coneTargets.size - 1
            val threshold = if (isHPad) 1.0f else 1.3f
            
            if (dist < threshold) { inZoneId = idx; break }
        }
        
        if (inZoneId == -1) {
            state.spotTimerSeconds = 5.0f; state.spotTimerSuccess = false; state.spotTimerMessageTimer = 0f; state.spotTimerMessage = "SEARCHING"
        } else if (!isHeightOk) {
            state.spotTimerSeconds = 5.0f; state.spotTimerSuccess = false; state.spotTimerMessageTimer = 0f
            state.spotTimerMessage = if (relAlt < 1.0f) "TOO_LOW|$relAlt" else "TOO_HIGH|$relAlt"
        } else {
            val isHPad = inZoneId == coneTargets.size - 1
            val currentYaw = (state.yaw % 360f + 360f) % 360f
            val targets = listOf(0f, 90f, 180f, 270f, 360f)
            val yawThreshold = if (isHPad) 8f else 15f
            
            // 計算最小偏角
            var minDiff = 360f
            targets.forEach { target ->
                var diff = abs(currentYaw - target)
                if (diff > 180f) diff = 360f - diff
                if (diff < minDiff) minDiff = diff
            }
            val isAligned = minDiff <= yawThreshold

            if (isRotating) {
                state.spotTimerSeconds = 5.0f; state.spotTimerSuccess = false; state.spotTimerMessageTimer = 0f; state.spotTimerMessage = "ROTATING"
            } else if (!isAligned) {
                state.spotTimerSeconds = 5.0f; state.spotTimerSuccess = false; state.spotTimerMessageTimer = 0f
                state.spotTimerMessage = if(isHPad) "YAW_ERROR_H|$minDiff" else "YAW_ERROR_G|$minDiff"
            } else {
                if (!state.spotTimerSuccess) {
                    if (state.spotTimerMessageTimer <= 0f) {
                        state.spotTimerSeconds = (state.spotTimerSeconds - dt).coerceAtLeast(0f)
                        if (state.spotTimerSeconds <= 0f) {
                            state.spotTimerSuccess = true; state.spotTimerMessage = if(isHPad) "PERFECT_H" else "SUCCESS_G"
                        } else {
                            state.spotTimerMessage = if(isHPad) "COUNTING_H|${state.spotTimerSeconds}" else "COUNTING_G|${state.spotTimerSeconds}"
                        }
                    }
                } else {
                    state.spotTimerMessage = if(isHPad) "PERFECT_H" else "SUCCESS_G"
                }
            }
            state.spotTimerStable = !isRotating && isAligned
        }

        state.spotTimerTargetId = inZoneId
        state.spotTimerInZone = inZoneId != -1
        state.spotTimerMessageTimer = (state.spotTimerMessageTimer - dt).coerceAtLeast(0f)
    }

    private fun resetTimer(state: DroneState) {
        state.spotTimerTargetId = -1; state.spotTimerInZone = false; state.spotTimerStable = false
        state.spotTimerSeconds = 5.0f; state.spotTimerMessageTimer = 0f
    }

    @Composable
    override fun OverlayUI(state: DroneState, onUpdateState: (DroneState.() -> Unit) -> Unit) {
        if (!state.isSpotTimerEnabled || state.isCollision || state.showSettings) return
        
        // [v1.7.15] 定位策略：飛機上方浮動跟隨邏輯 (Floating Tag)
        val dronePos = state.droneScreenPos
        var boxWidth by remember { mutableIntStateOf(0) }

        val rawMsg = state.spotTimerMessage ?: ""
        val parts = rawMsg.split("|")
        val msgId = parts[0]
        val param = parts.getOrNull(1)?.toFloatOrNull() ?: 0f

        val translatedMessage = when(msgId) {
            "IDLE" -> if (state.isCollision) stringResource(R.string.status_crash) else if (state.isMotorLocked) stringResource(R.string.status_motor_locked) else stringResource(R.string.mission_spot_timer_takeoff)
            "SEARCHING" -> stringResource(R.string.mission_spot_timer_searching)
            "TOO_LOW" -> stringResource(R.string.mission_spot_timer_too_low, String.format(java.util.Locale.US, "%.1f", param))
            "TOO_HIGH" -> stringResource(R.string.mission_spot_timer_too_high, String.format(java.util.Locale.US, "%.1f", param))
            "ROTATING" -> stringResource(R.string.mission_spot_timer_wait_stable)
            "YAW_ERROR_H" -> stringResource(R.string.mission_spot_timer_yaw_error, String.format(java.util.Locale.US, "%.0f", param))
            "YAW_ERROR_G" -> stringResource(R.string.mission_spot_timer_yaw_error_generic, String.format(java.util.Locale.US, "%.0f", param))
            "PERFECT_H" -> stringResource(R.string.mission_spot_timer_perfect)
            "SUCCESS_G" -> stringResource(R.string.mission_spot_timer_success)
            "COUNTING_H" -> stringResource(R.string.mission_spot_timer_prefix_h) + ": " + "%.1fs".format(param)
            "COUNTING_G" -> stringResource(R.string.mission_spot_timer_prefix_generic) + ": " + "%.1fs".format(param)
            else -> rawMsg
        }
        
        Box(modifier = Modifier.fillMaxSize()) {
            val themeColors = NikoTheme.colors
            
            // 只有在抓到飛機位置時才顯示浮動標籤
            if (dronePos != null) {
                val baseOffset = IntOffset(
                    x = dronePos.x.toInt(),
                    y = (dronePos.y - 160).toInt() // [v1.7.15] 提升至飛機上方 160 像素處
                )

                // 使用平滑動畫防止座標抖動
                val animatedOffset by animateIntOffsetAsState(
                    targetValue = baseOffset,
                    animationSpec = spring(stiffness = Spring.StiffnessLow),
                    label = "timer_float"
                )

                Surface(
                    modifier = Modifier
                        .onGloballyPositioned { boxWidth = it.size.width } // 即時測量寬度
                        .offset { 
                            // [v1.7.15] 精確居中修正：X 座標減去組件寬度的一半
                            IntOffset(animatedOffset.x - (boxWidth / 2), animatedOffset.y) 
                        },
                    color = themeColors.panel.copy(alpha = 0.75f),
                    shape = RoundedCornerShape(8.dp), 
                    border = BorderStroke(1.dp, themeColors.divider.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), 
                        verticalAlignment = Alignment.CenterVertically, 
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val indicatorColor = when { state.spotTimerSuccess -> Color.Green; state.spotTimerStable -> Color.Cyan; else -> Color.Red }
                        Box(modifier = Modifier.size(6.dp).background(indicatorColor, CircleShape))
                        Text(text = translatedMessage, color = themeColors.textPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        
                        if (state.spotTimerInZone || state.spotTimerSuccess) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(20.dp)) {
                                Canvas(modifier = Modifier.fillMaxSize()) { 
                                    drawCircle(themeColors.textPrimary.copy(alpha = 0.1f), style = Stroke(1.5.dp.toPx()))
                                    if (state.spotTimerSeconds < 5f) {
                                        drawArc(color = if(state.spotTimerSuccess) Color.Green else themeColors.primary, startAngle = -90f, sweepAngle = (1f - state.spotTimerSeconds / 5.0f) * 360f, useCenter = false, style = Stroke(2.dp.toPx())) 
                                    }
                                }
                                if (state.spotTimerSuccess) Icon(Icons.Default.Check, null, tint = Color.Green, modifier = Modifier.size(12.dp))
                                else Text(text = String.format(Locale.US, "%.0f", state.spotTimerSeconds), color = themeColors.textPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else {
                // 回退模式：若無投影座標，顯示在頂部中央
                Box(modifier = Modifier.fillMaxSize().padding(top = 100.dp), contentAlignment = Alignment.TopCenter) {
                    Surface(
                        color = themeColors.panel.copy(alpha = 0.85f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, themeColors.divider)
                    ) {
                        Text(text = translatedMessage, color = themeColors.textPrimary, modifier = Modifier.padding(10.dp))
                    }
                }
            }
        }
    }
}
