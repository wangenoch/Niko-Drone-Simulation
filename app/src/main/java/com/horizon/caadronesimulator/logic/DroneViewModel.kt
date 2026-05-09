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
        // [v1.6.0] 職責遷移：智慧觀察員邏輯現在由 CameraDirector 統籌管理
        // 此處僅保留與 UI 觸發相關的延時判定
        if (!state.useSmartObserver || !state.cameraMode.contains("站位視角")) return
        if (System.currentTimeMillis() - state.lastManualTouchTime < 3000) {
            // 手動覆蓋期間，僅進行基礎狀態同步，不執行自動運鏡
            return
        }
        
        // 具體運算將在渲染循環中透過 CameraDirector.update() 執行
    }

    override fun onCleared() {
        super.onCleared()
        logcatJob?.cancel()
        wizardJob?.cancel()
    }
}
