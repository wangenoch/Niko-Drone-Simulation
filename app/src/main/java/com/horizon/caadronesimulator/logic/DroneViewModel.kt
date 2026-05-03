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
 * [v1.2.85] 模擬器業務中樞
 * 職責：處理計時器、Logcat 採樣、硬體指令調度。
 * 將邏輯從 UI 層徹底抽離。
 */
class DroneViewModel : ViewModel() {

    private var logcatJob: Job? = null
    private var wizardJob: Job? = null

    // [v1.2.85] 教學進度狀態管理 (解耦 UI remember 遺失問題)
    var welcomeStep by mutableIntStateOf(0)
    var joystickTutorialStep by mutableIntStateOf(0)
    var climateTutorialStep by mutableIntStateOf(0)

    /**
     * [業務 1] 引導設定倒數計時器
     */
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

    /**
     * [業務 2] 系統級 Logcat 即時監測
     */
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
                    } else {
                        delay(100)
                    }
                }
                process.destroy()
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    state.logcatContent = "Logcat 啟動失敗: ${e.message}"
                }
            }
        }
    }

    /**
     * [業務 3] RadarHUD 智慧縮放運算 (10Hz)
     */
    fun updateRadarScale(state: DroneState) {
        val dx = state.posX.toDouble()
        val dz = (state.posZ + 6.0).toDouble()
        val dist = kotlin.math.sqrt(dx * dx + dz * dz).toFloat()
        
        when (state.radarZoomMode) {
            0 -> state.currentRadarScale = 1.0f // 全圖模式
            1 -> {
                // 八字自動模式 (帶滯後緩衝)
                // 12m 放大到全圖, 8m 縮回八字局部 (2.5x)
                if (dist > 12f) state.currentRadarScale = 1.0f
                else if (dist < 8f) state.currentRadarScale = 2.5f
                // 8m~12m 之間維持現狀，防止抖動
            }
            2 -> state.currentRadarScale = 4.0f // 跟隨模式 (極高倍率)
        }
    }

    override fun onCleared() {
        super.onCleared()
        logcatJob?.cancel()
        wizardJob?.cancel()
    }
}
