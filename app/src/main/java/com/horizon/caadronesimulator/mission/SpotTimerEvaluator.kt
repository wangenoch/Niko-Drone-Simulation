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
import com.horizon.caadronesimulator.model.DroneState
import com.horizon.caadronesimulator.model.DroneSpecs
import com.horizon.caadronesimulator.model.DroneRegistry
import java.util.Locale
import kotlin.math.*

/**
 * [v1.8.36] 定點計時評測器 - 高精確度校準版
 * 修正：實施 1.0m~3.5m 高度門檻，收緊角錐偵測半徑至 1.3m。
 */
class SpotTimerEvaluator : MissionEvaluator {
    override val modeName: String = "定點計時"

    override fun update(state: DroneState, dt: Float, spec: DroneSpecs) {
        if (!state.isSpotTimerEnabled) return
        if (dt <= 0) return

        val isAirborne = state.altitude > spec.groundOffset + 0.3f
        if (state.isMotorLocked || state.isCollision || !isAirborne) {
            resetTimer(state)
            state.spotTimerMessage = when {
                state.isCollision -> "無人機損毀"
                state.isMotorLocked -> "馬達鎖定中"
                else -> "請起飛至視線高度"
            }
            return
        }

        // 1. 旋轉偵測
        var yawDiff = abs(state.yaw - state.lastYaw)
        if (yawDiff > 180f) yawDiff = 360f - yawDiff 
        val yawRate = yawDiff / dt
        val isRotating = if (state.spotTimerStable) yawRate > 25.0f else yawRate > 15.0f 

        // [v1.8.36] 空間精確校準
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
            state.spotTimerSeconds = 5.0f; state.spotTimerSuccess = false; state.spotTimerMessageTimer = 0f; state.spotTimerMessage = "尋找目標點中..."
        } else if (!isHeightOk) {
            state.spotTimerSeconds = 5.0f; state.spotTimerSuccess = false; state.spotTimerMessageTimer = 0f; state.spotTimerMessage = "⚠️ 請維持在 1.0m ~ 3.5m 高度"
        } else {
            val isHPad = inZoneId == coneTargets.size - 1
            val currentYaw = (state.yaw % 360f + 360f) % 360f
            val targets = listOf(0f, 90f, 180f, 270f, 360f)
            val yawThreshold = if (isHPad) 8f else 15f
            val isAligned = targets.any { abs(currentYaw - it) <= yawThreshold }

            if (isRotating) {
                state.spotTimerSeconds = 5.0f; state.spotTimerSuccess = false; state.spotTimerMessageTimer = 0f; state.spotTimerMessage = "轉向中...等待停穩"
            } else if (!isAligned) {
                state.spotTimerSeconds = 5.0f; state.spotTimerSuccess = false; state.spotTimerMessageTimer = 0f; state.spotTimerMessage = if(isHPad) "H點請精確對準基準面" else "請對準基準面"
            } else {
                if (!state.spotTimerSuccess) {
                    if (state.spotTimerMessageTimer <= 0f) {
                        state.spotTimerSeconds = (state.spotTimerSeconds - dt).coerceAtLeast(0f)
                        if (state.spotTimerSeconds <= 0f) {
                            state.spotTimerSuccess = true; state.spotTimerMessage = if(isHPad) "完美！H點精準懸停合格" else "恭喜！定點懸停合格"
                        } else {
                            val prefix = if(isHPad) "H點精準計時" else "定點計時"
                            state.spotTimerMessage = String.format(Locale.US, "$prefix: %.1fs", state.spotTimerSeconds)
                        }
                    }
                } else {
                    state.spotTimerMessage = if(isHPad) "完美！H點精準懸停合格" else "恭喜！定點懸停合格"
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
        
        val horizontalDist = sqrt(state.posX * state.posX + state.posZ * state.posZ)
        val isInZoomZone = state.enableZoomAssistant && horizontalDist > 10.0f && state.cameraMode != "FPV 視角" && state.cameraMode != "跟隨視角" && !state.isMenuExpanded
        val isZoomRelocated = state.autoPiPRelocate && (state.observerTilt < -5f || state.altitude > 10f)
        val isZoomInCenter = isInZoomZone && !isZoomRelocated

        val targetPadding by animateDpAsState(targetValue = when { state.isMenuExpanded -> 120.dp; isZoomInCenter -> 125.dp; isNearGround -> 30.dp; else -> 60.dp }, label = "pad")
        Box(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(top = targetPadding), contentAlignment = Alignment.TopCenter) {
            Surface(color = Color(0xCC111111), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))) {
                Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val indicatorColor = when { state.spotTimerSuccess -> Color.Green; state.spotTimerStable -> Color.Cyan; else -> Color.Red }
                    Box(modifier = Modifier.size(8.dp).background(indicatorColor, RoundedCornerShape(50)))
                    Text(text = state.spotTimerMessage ?: "", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
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
