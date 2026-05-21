package com.horizon.caadronesimulator.mission

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
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
import com.horizon.caadronesimulator.model.Constants
import com.horizon.caadronesimulator.model.AppConfig
import com.horizon.caadronesimulator.model.DroneState
import com.horizon.caadronesimulator.model.DroneSpecs
import com.horizon.caadronesimulator.model.DroneRegistry
import java.util.Locale
import kotlin.math.*

import androidx.compose.ui.res.stringResource
import com.horizon.caadronesimulator.R

/**
 * [v1.5.9] 定點計時評測器 - 高精確度校準版
 */
class SpotTimerEvaluator : MissionEvaluator {
    override val modeName: String = "定點計時" // 這裡通常由模式選擇器顯示，若需翻譯可由資源獲取

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
        val spec = remember(state.droneType) { DroneRegistry.getSpec(state.droneType) }
        val isNearGround = state.altitude <= (spec.groundOffset + 0.15f)
        
        // [v1.7.6] 校準：作業中心與觸發基準
        val distToOpsCenter = sqrt(state.posX * state.posX + (state.posZ - 6f) * (state.posZ - 6f))
        val isInZoomZone = state.enableZoomAssistant && distToOpsCenter > 10.0f && state.cameraMode != AppConfig.CAM_MODE_FPV && state.cameraMode != AppConfig.CAM_MODE_FOLLOW && !state.isMenuExpanded
        val isZoomRelocated = state.autoPiPRelocate && (state.observerTilt < -5f || state.altitude > 10f)
        val isZoomInCenter = isInZoomZone && !isZoomRelocated

        // [v1.7.6] 佈局優化：調整與「起槳」按鈕的避讓間距，防止重疊
        val targetPadding by animateDpAsState(targetValue = when { 
            state.isMenuExpanded -> 120.dp 
            isZoomInCenter -> 125.dp 
            isNearGround -> 140.dp // 移動至「起槳 (85dp)」按鈕下方，防止遮擋
            else -> 60.dp 
        }, label = "pad")

        val rawMsg = state.spotTimerMessage ?: ""
        val parts = rawMsg.split("|")
        val msgId = parts[0]
        val param = parts.getOrNull(1)?.toFloatOrNull() ?: 0f

        val translatedMessage = when(msgId) {
            "IDLE" -> if (state.isCollision) stringResource(R.string.status_crash) else if (state.isMotorLocked) stringResource(R.string.status_motor_locked) else stringResource(R.string.mission_spot_timer_takeoff)
            "SEARCHING" -> stringResource(R.string.mission_spot_timer_searching)
            "TOO_LOW" -> stringResource(R.string.mission_spot_timer_too_low, param)
            "TOO_HIGH" -> stringResource(R.string.mission_spot_timer_too_high, param)
            "ROTATING" -> stringResource(R.string.mission_spot_timer_wait_stable)
            "YAW_ERROR_H" -> stringResource(R.string.mission_spot_timer_yaw_error, param)
            "YAW_ERROR_G" -> stringResource(R.string.mission_spot_timer_yaw_error_generic, param)
            "PERFECT_H" -> stringResource(R.string.mission_spot_timer_perfect)
            "SUCCESS_G" -> stringResource(R.string.mission_spot_timer_success)
            "COUNTING_H" -> stringResource(R.string.mission_spot_timer_prefix_h) + ": " + "%.1fs".format(param)
            "COUNTING_G" -> stringResource(R.string.mission_spot_timer_prefix_generic) + ": " + "%.1fs".format(param)
            else -> rawMsg
        }

        Box(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(top = targetPadding), contentAlignment = Alignment.TopCenter) {
            Surface(color = Color(0xCC111111), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))) {
                Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val indicatorColor = when { state.spotTimerSuccess -> Color.Green; state.spotTimerStable -> Color.Cyan; else -> Color.Red }
                    Box(modifier = Modifier.size(8.dp).background(indicatorColor, RoundedCornerShape(50)))
                    Text(text = translatedMessage, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    if (state.spotTimerInZone || state.spotTimerSuccess) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(28.dp)) {
                            Canvas(modifier = Modifier.fillMaxSize()) { drawCircle(Color.White.copy(alpha = 0.1f), style = Stroke(2.dp.toPx())); if (state.spotTimerSeconds < 5f) drawArc(color = if(state.spotTimerSuccess) Color.Green else Color.Cyan, startAngle = -90f, sweepAngle = (1f - state.spotTimerSeconds / 5.0f) * 360f, useCenter = false, style = Stroke(3.dp.toPx())) }
                            if (state.spotTimerSuccess) Icon(Icons.Default.Check, null, tint = Color.Green, modifier = Modifier.size(16.dp))
                            else Text(text = String.format(Locale.US, "%.0f", state.spotTimerSeconds), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
