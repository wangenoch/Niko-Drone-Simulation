package com.horizon.caadronesimulator.logic

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.horizon.caadronesimulator.model.DroneState
import com.horizon.caadronesimulator.model.SettingsManager
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * [v1.3.5] 模擬器業務中樞
 */
class DroneViewModel : ViewModel() {
    private var logcatJob: Job? = null
    private var wizardJob: Job? = null

    var welcomeStep by mutableIntStateOf(0)
    var joystickTutorialStep by mutableIntStateOf(0)
    var climateTutorialStep by mutableIntStateOf(0)

    fun startWizardCountdown(state: DroneState, settingsManager: SettingsManager) {
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
                    settingsManager.saveSettings(state)
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
                                state.logcatContent = (state.logcatContent + line + "\n").takeLast(3000)
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

    fun updateRadarScale(state: DroneState) {
        // [v1.3.9] 邊界預警檢查 (保留邏輯供 HUD 顯示)
        state.isNearBoundary = PhysicsEngine.isNearBoundary(state.posX, state.posZ)

        val dist = kotlin.math.sqrt(state.posX * state.posX + (state.posZ + 6f) * (state.posZ + 6f))

        when (state.radarZoomMode) {
            0 -> state.currentRadarScale = 0.65f // 全景模式 (剛好裝下 100m)
            1 -> {
                // [v1.3.9] 三階智慧平滑縮放邏輯
                state.currentRadarScale = when {
                    dist < 12f -> 2.5f  // 精確近景
                    dist < 28f -> 1.5f  // 導航中景
                    else -> 0.65f       // 安全遠景
                }
            }
            2 -> state.currentRadarScale = 4.0f // 跟隨模式
        }
    }

    fun runSmartObserver(state: DroneState) {
        if (!state.useSmartObserver || !state.cameraMode.contains("站位視角")) return
        if (System.currentTimeMillis() - state.lastManualTouchTime < 3000) return

        val dz = state.posZ - (-15f) // 站位在 Z=-15
        val distToPilot = kotlin.math.sqrt(state.posX * state.posX + dz * dz)
        
        // 1. 自動高度補償 (使用 SmoothStep 緩衝)
        val targetHeight = (1.6f + (distToPilot / 60f) * 6.4f).coerceIn(1.6f, 8.0f)
        state.observerHeight += (targetHeight - state.observerHeight) * 0.04f

        // 2. 自動視覺參數優化 (FOV, Zoom, 基準 Tilt) [v1.4.2]
        ViewportOptimizer.applyOptimization(state, smooth = true)

        // 3. 自動仰角細調 (僅在追蹤模式下疊加動態偏移，固定模式則完全遵循 Optimizer)
        if (state.cameraMode.contains("追蹤")) {
            // 計算「起降安全區」權重：距離 15m 且高度 13m 內
            val distWeight = ((18f - distToPilot) / 5f).coerceIn(0f, 1f)
            val altWeight = ((15f - state.altitude) / 4f).coerceIn(0f, 1f)
            val safetyZoneWeight = distWeight * altWeight // 1.0 代表完全在安全區，0.0 代表完全在航線區

            // 計算航線區的動態下壓偏移 (根據高度從 +12 變至 -20)
            val hFactor = (state.altitude / 30f).coerceIn(0f, 1f)
            val dynamicCruiseOffset = 12f - (hFactor * 32f)
            
            // 混合兩者：安全區強制 2 度，航線區動態偏移
            val targetOffset = (2f * safetyZoneWeight) + (dynamicCruiseOffset * (1f - safetyZoneWeight))
            
            // 雲台慣性濾波
            state.observerTilt += (targetOffset - state.observerTilt) * 0.05f
        }
        
        state.observerTilt = state.observerTilt.coerceIn(-30f, 85f)
    }

    override fun onCleared() {
        super.onCleared()
        logcatJob?.cancel()
        wizardJob?.cancel()
    }
}
