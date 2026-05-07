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

class SpotTimerEvaluator : MissionEvaluator {
    override val modeName: String = "定點計時"

    override fun update(state: DroneState, dt: Float, spec: DroneSpecs) {
        if (!state.isSpotTimerEnabled) return
        if (dt <= 0) return

        // 0. 基礎狀態檢查
        val isAirborne = state.altitude > spec.groundOffset + 0.3f
        if (state.isMotorLocked || state.isCollision || !isAirborne) {
            state.spotTimerTargetId = -1
            state.spotTimerInZone = false
            state.spotTimerStable = false
            state.spotTimerSeconds = 5.0f
            state.spotTimerMessageTimer = 0f
            state.spotTimerMessage = when {
                state.isCollision -> "無人機損毀"
                state.isMotorLocked -> "馬達鎖定中"
                else -> "請起飛至視線高度"
            }
            return
        }

        // 1. 旋轉偵測
        val yawRate = abs(state.yaw - state.lastYaw) / dt
        val isRotating = if (state.spotTimerStable) yawRate > 12.0f else yawRate > 6.0f

        // 2. 航向對準檢查
        val currentYaw = (state.yaw % 360f + 360f) % 360f
        val targets = listOf(0f, 90f, 180f, 270f, 360f)
        val isAligned = targets.any { abs(currentYaw - it) <= 15f }

        // 3. 區域偵測
        val coneTargets = Constants.CONE_POSITIONS + arrayOf(floatArrayOf(0f, -6f))
        var inZoneId = -1
        val targetCircleRadius = 1.2f 
        val outOfLimitThreshold = targetCircleRadius - (spec.collisionRadius * 0.66f)
        
        var currentDist = 0f
        for (idx in coneTargets.indices) {
            val dist = sqrt((state.posX - coneTargets[idx][0]).toDouble().pow(2) + (state.posZ - coneTargets[idx][1]).toDouble().pow(2)).toFloat()
            if (dist < 1.5f) { inZoneId = idx; currentDist = dist; break }
        }
        
        if (inZoneId == -1) {
            state.spotTimerSeconds = 5.0f
            state.spotTimerSuccess = false
            state.spotTimerMessageTimer = 0f
            state.spotTimerMessage = "尋找目標點中..."
        } else {
            val isDeepInZone = currentDist <= outOfLimitThreshold
            if (!isDeepInZone) {
                state.spotTimerSeconds = 5.0f; state.spotTimerSuccess = false; state.spotTimerMessageTimer = 0f
                state.spotTimerMessage = "超出邊界！計時重置"
            } else if (isRotating) {
                state.spotTimerSeconds = 5.0f; state.spotTimerSuccess = false; state.spotTimerMessageTimer = 0f
                state.spotTimerMessage = "轉向中...等待停穩"
            } else if (!isAligned) {
                state.spotTimerSeconds = 5.0f; state.spotTimerSuccess = false; state.spotTimerMessageTimer = 0f
                state.spotTimerMessage = "請對準基準面"
            } else {
                if (inZoneId == 7) {
                    val yawDiff = abs(state.yaw - state.spotTimerLastYaw)
                    val normalizedDiff = if (yawDiff > 180f) 360f - yawDiff else yawDiff
                    if (normalizedDiff > 70f) {
                        state.spotTimerSeconds = 5.0f; state.spotTimerSuccess = false; state.spotTimerLastYaw = state.yaw
                        state.spotTimerMessage = "發現新航向，重新計時"; state.spotTimerMessageTimer = 1.5f
                    }
                }

                if (!state.spotTimerSuccess) {
                    if (state.spotTimerMessageTimer <= 0f) {
                        state.spotTimerSeconds = (state.spotTimerSeconds - dt).coerceAtLeast(0f)
                        if (state.spotTimerSeconds <= 0f) {
                            state.spotTimerSuccess = true
                            state.spotTimerMessage = "恭喜！定點懸停合格"
                        } else {
                            state.spotTimerMessage = String.format(Locale.US, "定點計時: %.1fs", state.spotTimerSeconds)
                        }
                    }
                } else {
                    state.spotTimerMessage = "恭喜！定點懸停合格"
                }
            }
        }

        state.spotTimerTargetId = inZoneId
        state.spotTimerInZone = inZoneId != -1
        state.spotTimerStable = inZoneId != -1 && currentDist <= outOfLimitThreshold && !isRotating && isAligned
        state.spotTimerMessageTimer = (state.spotTimerMessageTimer - dt).coerceAtLeast(0f)
    }

    @Composable
    override fun OverlayUI(state: DroneState, onUpdateState: (DroneState.() -> Unit) -> Unit) {
        if (!state.isSpotTimerEnabled || state.isCollision || state.showSettings) return
        val spec = remember(state.droneType) { DroneRegistry.getSpec(state.droneType) }
        val isNearGround = state.altitude <= (spec.groundOffset + 0.15f)
        
        // [v1.4.2] 偵測姿態視窗是否正在中央擋路 (與 DroneHUD/FlightInteractionLayer 邏輯同步)
        val horizontalDist = sqrt(state.posX * state.posX + (state.posZ + 6f) * (state.posZ + 6f))
        val isInZoomZone = state.enableZoomAssistant && horizontalDist > 10.0f && state.cameraMode != "FPV 視角" && state.cameraMode != "跟隨視角" && !state.isMenuExpanded
        val isZoomRelocated = state.autoPiPRelocate && (state.observerTilt < -5f || state.altitude > 10f)
        val isZoomInCenter = isInZoomZone && !isZoomRelocated

        val targetPadding by animateDpAsState(
            targetValue = when {
                state.isMenuExpanded -> 120.dp
                isZoomInCenter -> 125.dp     // 移至輔助視窗下方 (16 + 100 + 間距)
                isNearGround -> 30.dp        // 移至起槳按鈕上方 (按鈕在 85)
                else -> 60.dp
            }, 
            label = "pad"
        )
        Box(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(top = targetPadding), contentAlignment = Alignment.TopCenter) {
            Surface(color = Color(0xCC111111), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))) {
                Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val indicatorColor = when {
                        state.spotTimerSuccess -> Color.Green
                        state.spotTimerStable -> Color.Cyan
                        else -> Color.Red
                    }
                    Box(modifier = Modifier.size(8.dp).background(indicatorColor, RoundedCornerShape(50)))
                    Text(text = state.spotTimerMessage ?: "", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    if (state.spotTimerInZone || state.spotTimerSuccess) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(28.dp)) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                drawCircle(Color.White.copy(alpha = 0.1f), style = Stroke(2.dp.toPx()))
                                if (state.spotTimerSeconds < 5f) drawArc(color = if(state.spotTimerSuccess) Color.Green else Color.Cyan, startAngle = -90f, sweepAngle = (1f - state.spotTimerSeconds / 5.0f) * 360f, useCenter = false, style = Stroke(3.dp.toPx()))
                            }
                            if (state.spotTimerSuccess) Icon(Icons.Default.Check, null, tint = Color.Green, modifier = Modifier.size(16.dp))
                            else Text(text = String.format(Locale.US, "%.0f", state.spotTimerSeconds), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
