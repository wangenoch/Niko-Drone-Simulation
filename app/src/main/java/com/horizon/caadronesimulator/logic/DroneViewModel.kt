package com.horizon.caadronesimulator.logic

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.horizon.caadronesimulator.model.DroneState
import com.horizon.caadronesimulator.logic.storage.ConfigurationStore
import com.horizon.caadronesimulator.model.DroneRegistry
import com.horizon.caadronesimulator.render.DroneSimulationRenderer
import com.horizon.caadronesimulator.mission.MissionManager
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.math.*

/**
 * [v1.7.6] 模擬器業務中樞 (Detoxified Logic Hub)
 * 修正：實施最後狀態鎖定，集中距離與縮放判定，移除冗餘 UI 計算。
 * 已對接：ConfigurationStore (v1.7.5)
 */
class DroneViewModel : ViewModel() {
    private var logcatJob: Job? = null
    private var wizardJob: Job? = null

    var welcomeStep by mutableIntStateOf(0)
    var joystickTutorialStep by mutableIntStateOf(0)
    var climateTutorialStep by mutableIntStateOf(0)

    private var lastStabilityCheckTime = 0L
    private var lastResetTime = 0L

    fun startSwitchBuffer(state: DroneState, message: String) {
        viewModelScope.launch {
            state.isSwitchingMode = true
            state.switchMessage = message
            val duration = 800L
            val steps = 20
            for (i in 1..steps) {
                state.switchProgress = i.toFloat() / steps
                delay(duration / steps)
            }
            state.isSwitchingMode = false
            state.switchProgress = 0f
        }
    }

    fun resetFlight(state: DroneState, renderer: DroneSimulationRenderer) {
        lastResetTime = System.currentTimeMillis()
        renderer.resetFlight()
        
        state.apply {
            isCollision = false
            isMotorLocked = true
            flightPath = emptyList()
            observerHeight = 6.0f 
            isArmSafetyPassed = false
            isHoldSafetyPassed = false
            
            spotTimerSuccess = false
            spotTimerSeconds = 5.0f
            spotTimerInZone = false
            spotTimerStable = false
            spotTimerMessage = if (isSpotTimerEnabled) "請重新起飛" else null
            systemMessage = null
            lastYaw = 0f
            horizontalDist = 0f 
        }
        
        ViewportOptimizer.applyOptimization(state)
    }

    fun syncFlightData(
        state: DroneState,
        alt: Float, x: Float, z: Float,
        yaw: Float, pitch: Float, roll: Float,
        speed: Float, isImpact: Boolean,
        volt: Float, perc: Int,
        physicsResult: PhysicsEngine.PhysicsResult?
    ) {
        val now = System.currentTimeMillis()
        val isProtecting = (now - lastResetTime < 500)
        if ((state.showSettings || state.isCollision) && !isProtecting) return

        state.apply {
            this.lastYaw = this.yaw
            batteryVoltage = volt
            batteryPercent = perc
            altitude = alt
            posX = x
            posZ = z
            this.yaw = yaw
            this.pitch = pitch
            this.roll = roll
            
            // [v1.8.19] 撞擊速度保留
            this.speed = if (isImpact && physicsResult != null) physicsResult.impactSpeed else speed
            this.horizontalDist = sqrt(x.pow(2) + z.pow(2))
            
            val distToAnchor = sqrt(x.pow(2) + (z + 6f).pow(2))
            this.lastInZoomZone = distToAnchor > 10.0f && cameraMode != "FPV 視角" && cameraMode != "跟隨視角"
        }

        // [v1.8.25] 統一消息翻譯層：防止 UI 消息阻塞控制邏輯
        if (!state.isCollision && physicsResult?.systemMessage != null) {
            val msg = when(physicsResult.systemMessage) {
                "CRASH_EXTREME" -> "❌ 嚴重撞擊損毀 (速度: %.1f m/s)".format(physicsResult.impactSpeed)
                "CRASH_STRUCTURAL" -> "⚠️ 結構衝擊損毀 (速度: %.1f m/s)".format(physicsResult.impactSpeed)
                else -> physicsResult.systemMessage
            }
            if (state.systemMessage == null) state.systemMessage = msg
        }
        
        if (isImpact || (state.useFlightLimit && perc <= 0)) {
            state.isCollision = true
            state.isMotorLocked = true
        } else {
            // [v1.8.25] 分級落地警告優化：確保不覆蓋關鍵停槳條件
            val spec = DroneRegistry.getSpec(state.droneType)
            if (state.useStrictLanding && alt <= spec.groundOffset + 0.05f && physicsResult != null) {
                val impactV = physicsResult.impactSpeed
                // 僅在非停槳嘗試時顯示警告
                if (impactV in 1.2f..2.2f && state.systemMessage == null) {
                    state.systemMessage = "⚠️ 落地過重 (%.1f m/s)，請練習平穩著陸".format(impactV)
                }
            }

            val relAlt = alt - spec.groundOffset
            if (relAlt < 29.5f && state.systemMessage == "已達限高 30m") {
                state.systemMessage = null
            }
            
            val stabilityNow = System.currentTimeMillis()
            val dt = if (lastStabilityCheckTime == 0L) 0f else (stabilityNow - lastStabilityCheckTime) / 1000f
            lastStabilityCheckTime = stabilityNow
            
            MissionManager.update(state, dt, spec)

            if (!spec.isHoldSupported) state.isThrottleHoldActive = false

            if (state.showFlightPath && !state.isMotorLocked) {
                val path = state.flightPath
                val pos = androidx.compose.ui.geometry.Offset(x, z)
                val last = path.lastOrNull()
                if (last == null || sqrt((pos.x - last.x).pow(2) + (pos.y - last.y).pow(2)) > 0.15f) {
                    state.flightPath = (path + pos).takeLast(5400)
                }
            } else if (!state.showFlightPath && state.flightPath.isNotEmpty()) {
                state.flightPath = emptyList()
            }
        }
    }

    fun updateRadarScale(state: DroneState) {
        state.isNearBoundary = PhysicsEngine.isNearBoundary(state.posX, state.posZ)
        val dist = state.horizontalDist 
        when (state.radarZoomMode) {
            0 -> state.currentRadarScale = 0.65f 
            1 -> {
                state.currentRadarScale = when {
                    dist < 12f -> 2.5f  
                    dist < 28f -> 1.5f  
                    else -> 0.65f       
                }
            }
            2 -> state.currentRadarScale = 4.0f 
        }
    }

    fun startWizardCountdown(state: DroneState, configStore: ConfigurationStore) {
        wizardJob?.cancel()
        wizardJob = viewModelScope.launch(Dispatchers.Main) {
            if (state.setupWizardStep > 0 && state.wizardWaitingForNeutral) {
                for (i in 2 downTo 1) {
                    state.wizardCountdown = i
                    delay(1000)
                }
                if (state.setupWizardStep < 4) {
                    state.setupWizardStep += 1
                    state.wizardWaitingForNeutral = false
                    state.wizardCountdown = 0
                } else {
                    state.setupWizardStep = 0
                    state.wizardWaitingForNeutral = false
                    state.systemMessage = "引導設定完成"
                    configStore.saveSettings(state)
                }
            }
        }
    }

    fun toggleLogcat(state: DroneState) {
        logcatJob?.cancel()
        if (!state.isLogcatEnabled) return
        logcatJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val process = Runtime.getRuntime().exec("logcat -v time")
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                while (state.isLogcatEnabled && isActive) {
                    if (reader.ready()) {
                        val line = reader.readLine() ?: break
                        if (line.contains("Niko") || line.contains("Serial") || line.contains("Drone")) {
                            withContext(Dispatchers.Main) {
                                val lines = state.logcatContent.lines().takeLast(100)
                                state.logcatContent = (lines + line).joinToString("\n")
                            }
                        }
                    } else delay(100)
                }
                process.destroy()
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { state.logcatContent = "Logcat 啟動失敗: ${e.message}" }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        logcatJob?.cancel()
        wizardJob?.cancel()
    }
}
