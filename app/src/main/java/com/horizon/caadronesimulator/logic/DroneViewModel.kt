package com.horizon.caadronesimulator.logic

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.horizon.caadronesimulator.model.DroneState
import com.horizon.caadronesimulator.model.StickInputState
import com.horizon.caadronesimulator.logic.storage.ConfigurationStore
import com.horizon.caadronesimulator.model.DroneRegistry
import com.horizon.caadronesimulator.render.DroneSimulationRenderer
import com.horizon.caadronesimulator.mission.MissionManager
import com.horizon.caadronesimulator.logic.PhysicsEngine
import com.horizon.caadronesimulator.logic.CameraDirector
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.math.*

/**
 * [v1.7.6] 模擬器業務中樞 (Detoxified Logic Hub)
 * 職責：整合數據採集、任務評測與全域狀態管理。
 */
class DroneViewModel : ViewModel() {
    private var wizardJob: Job? = null

    var welcomeStep by mutableIntStateOf(0)
    var joystickTutorialStep by mutableIntStateOf(0)
    var climateTutorialStep by mutableIntStateOf(0)

    private var lastStabilityCheckTime = 0L
    private var lastResetTime = 0L
    private var physicsJob: Job? = null

    /** [v1.7.4] 物理主導權回歸 Renderer (心跳同步)，此處不再啟動高頻協程循環 */
    fun startPhysicsLoop(
        state: DroneState,
        stickInput: StickInputState,
        renderer: DroneSimulationRenderer,
        soundManager: com.horizon.caadronesimulator.audio.DroneSoundManager
    ) {
        physicsJob?.cancel()
        // [v1.7.4] 輸入橋接：僅保留 UI 輸入到 Renderer 變數的同步 (低頻即可)
        physicsJob = viewModelScope.launch(Dispatchers.Default) {
            while (isActive) {
                // 每 16ms 將當前遙控/觸控指令同步給 Renderer
                // Renderer 的 onDrawFrame 會在繪圖前讀取這些最新指令執行 Physics step
                renderer.ctrlThrottle = stickInput.stickThrottle(state)
                renderer.ctrlYaw = stickInput.stickYaw(state)
                renderer.ctrlPitch = stickInput.stickPitch(state)
                renderer.ctrlRoll = stickInput.stickRoll(state)
                
                // 背景線程音效驅動 (維持在背景，不佔用 UI)
                soundManager.updateSelfDriven(state, stickInput)
                delay(16)
            }
        }
    }

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
        // [v1.7.6] 核心修復：強制清除物理引擎緩存狀態與重置物理狀態
        com.horizon.caadronesimulator.logic.PhysicsEngine.clearState()
        renderer.resetFlight()
        
        state.apply {
            // 1. 位置與姿態徹底歸零
            val spec = DroneRegistry.getSpec(droneType)
            posX = 0f
            posZ = 0f
            altitude = spec.groundOffset
            yaw = 0f
            pitch = 0f
            roll = 0f
            lastYaw = 0f
            horizontalDist = 0f
            speed = 0f
            motorRpmFactor = 0f
            
            // 2. 狀態旗標重置
            isCollision = false
            isMotorLocked = true
            flightPath = emptyList()
            isArmSafetyPassed = false
            isHoldSafetyPassed = false
            isThrottleHoldActive = false
            
            // 3. 電池數據恢復
            batteryVoltage = 4.2f
            batteryPercent = 100
            
            // 4. 引導與考照計時重置
            spotTimerSuccess = false
            spotTimerSeconds = 5.0f
            spotTimerInZone = false
            spotTimerStable = false
            spotTimerMessage = if (isSpotTimerEnabled) "請重新起飛" else null
            systemMessage = null

            // 5. 相機導演還原
            applyCameraModeDefaults(this, cameraMode)
        }
        
        ViewportOptimizer.applyOptimization(state)
    }

    /** [v1.6.1] 恢復原廠設定：邏輯執行層 */
    fun restoreFactorySettings(state: DroneState, renderer: DroneSimulationRenderer) {
        // 1. 先執行基礎飛行重置
        resetFlight(state, renderer)

        // 2. 還原全域設定
        state.apply {
            // 手感還原
            globalRate = com.horizon.caadronesimulator.model.AppConfig.JoystickDefaults.RATE
            globalExpo = com.horizon.caadronesimulator.model.AppConfig.JoystickDefaults.EXPO
            joystickDeadzone = com.horizon.caadronesimulator.model.AppConfig.JoystickDefaults.DEADZONE
            useGlobalRates = true
            showIndividualRates = false
            
            // 各別軸向還原
            rateT = com.horizon.caadronesimulator.model.AppConfig.JoystickDefaults.RATE
            expoT = com.horizon.caadronesimulator.model.AppConfig.JoystickDefaults.EXPO
            rateY = com.horizon.caadronesimulator.model.AppConfig.JoystickDefaults.RATE
            expoY = com.horizon.caadronesimulator.model.AppConfig.JoystickDefaults.EXPO
            rateP = com.horizon.caadronesimulator.model.AppConfig.JoystickDefaults.RATE
            expoP = com.horizon.caadronesimulator.model.AppConfig.JoystickDefaults.EXPO
            rateR = com.horizon.caadronesimulator.model.AppConfig.JoystickDefaults.RATE
            expoR = com.horizon.caadronesimulator.model.AppConfig.JoystickDefaults.EXPO

            // 環境還原
            windLevel = com.horizon.caadronesimulator.model.AppConfig.EnvironmentDefaults.WIND_LEVEL
            windDirection = com.horizon.caadronesimulator.model.AppConfig.EnvironmentDefaults.WIND_DIRECTION
            isSunSimEnabled = com.horizon.caadronesimulator.model.AppConfig.EnvironmentDefaults.SUN_ENABLED
            sunPosition = com.horizon.caadronesimulator.model.AppConfig.EnvironmentDefaults.SUN_POSITION
            shadowIntensity = com.horizon.caadronesimulator.model.AppConfig.EnvironmentDefaults.SHADOW_INTENSITY
            cloudDensity = com.horizon.caadronesimulator.model.AppConfig.EnvironmentDefaults.CLOUD_DENSITY
            weatherMode = com.horizon.caadronesimulator.model.AppConfig.EnvironmentDefaults.WEATHER_MODE
            showClouds = com.horizon.caadronesimulator.model.AppConfig.EnvironmentDefaults.SHOW_CLOUDS
            showMountains = com.horizon.caadronesimulator.model.AppConfig.EnvironmentDefaults.SHOW_MOUNTAINS
            useHardcorePhysics = com.horizon.caadronesimulator.model.AppConfig.EnvironmentDefaults.HARDCORE_PHYSICS

            // 視覺還原
            mainFOV = com.horizon.caadronesimulator.model.AppConfig.VisualDefaults.MAIN_FOV
            // 視覺還原 (依照 AppConfig 預設初始模式進行對接)
            cameraMode = com.horizon.caadronesimulator.model.AppConfig.VisualDefaults.CAMERA_MODE
            mainFOV = com.horizon.caadronesimulator.model.AppConfig.VisualDefaults.MAIN_FOV
            
            if (cameraMode == "站位視角 (固定)") {
                observerHeight = com.horizon.caadronesimulator.model.AppConfig.VisualDefaults.OBSERVER_HEIGHT
                observerTilt = com.horizon.caadronesimulator.model.AppConfig.VisualDefaults.OBSERVER_TILT
                zoomFactor = com.horizon.caadronesimulator.model.AppConfig.VisualDefaults.ZOOM_FACTOR_FIXED
            } else {
                observerHeight = com.horizon.caadronesimulator.model.AppConfig.VisualDefaults.OBSERVER_HEIGHT_TRACKING
                observerTilt = com.horizon.caadronesimulator.model.AppConfig.VisualDefaults.OBSERVER_TILT_TRACKING
                zoomFactor = com.horizon.caadronesimulator.model.AppConfig.VisualDefaults.ZOOM_FACTOR_TRACKING
            }

            showGroundAnchor = com.horizon.caadronesimulator.model.AppConfig.VisualDefaults.SHOW_GROUND_ANCHOR
            autoPiPRelocate = com.horizon.caadronesimulator.model.AppConfig.VisualDefaults.AUTO_PIP_RELOCATE
            enableZoomAssistant = com.horizon.caadronesimulator.model.AppConfig.VisualDefaults.ENABLE_ZOOM_ASSISTANT
            showFlightPath = com.horizon.caadronesimulator.model.AppConfig.VisualDefaults.SHOW_FLIGHT_PATH

            // 安全還原
            useFlightLimit = com.horizon.caadronesimulator.model.AppConfig.SystemDefaults.USE_FLIGHT_LIMIT
            useStrictLanding = com.horizon.caadronesimulator.model.AppConfig.SystemDefaults.USE_STRICT_LANDING
            applyPhysicalSpecs = com.horizon.caadronesimulator.model.AppConfig.SystemDefaults.APPLY_PHYSICAL_SPECS
            isThrottleHoldActive = com.horizon.caadronesimulator.model.AppConfig.SystemDefaults.IS_THROTTLE_HOLD_ACTIVE
            
            // 系統與介面還原
            isMuted = com.horizon.caadronesimulator.model.AppConfig.SystemDefaults.IS_MUTED
            showShadow = com.horizon.caadronesimulator.model.AppConfig.SystemDefaults.SHOW_SHADOW
            showObstacles = com.horizon.caadronesimulator.model.AppConfig.SystemDefaults.SHOW_OBSTACLES
            hideStatusBar = com.horizon.caadronesimulator.model.AppConfig.SystemDefaults.HIDE_STATUS_BAR
            pauseInSettings = com.horizon.caadronesimulator.model.AppConfig.SystemDefaults.PAUSE_IN_SETTINGS
            isAutoConnectEnabled = com.horizon.caadronesimulator.model.AppConfig.SystemDefaults.AUTO_CONNECT_ENABLED
            isExpertModeLocked = com.horizon.caadronesimulator.model.AppConfig.SystemDefaults.IS_EXPERT_MODE_LOCKED
            hudMode = com.horizon.caadronesimulator.model.AppConfig.VisualDefaults.HUD_MODE
            radarZoomMode = com.horizon.caadronesimulator.model.AppConfig.VisualDefaults.RADAR_ZOOM_MODE
            
            // 任務評測還原
            isSpotTimerEnabled = com.horizon.caadronesimulator.model.AppConfig.SystemDefaults.IS_SPOT_TIMER_ENABLED
            spotTimerSeconds = com.horizon.caadronesimulator.model.AppConfig.SystemDefaults.SPOT_TIMER_SECONDS

            // 介面細節還原
            showSpecialTitle = com.horizon.caadronesimulator.model.AppConfig.VisualDefaults.SHOW_SPECIAL_TITLE
            showSideSliders = com.horizon.caadronesimulator.model.AppConfig.VisualDefaults.SHOW_SPECIAL_TITLE // 使用同一基準
            showSideRulers = com.horizon.caadronesimulator.model.AppConfig.VisualDefaults.SHOW_SIDE_RULERS
            reverseSliderSides = com.horizon.caadronesimulator.model.AppConfig.VisualDefaults.REVERSE_SLIDERS

            systemMessage = "已恢復原廠預設值"
        }
    }

    /** [v1.6.1] 視角模式切換自動歸位邏輯 */
    fun applyCameraModeDefaults(state: DroneState, mode: String) {
        state.apply {
            when (mode) {
                "站位視角 (固定)" -> {
                    observerHeight = com.horizon.caadronesimulator.model.AppConfig.VisualDefaults.OBSERVER_HEIGHT
                    observerTilt = com.horizon.caadronesimulator.model.AppConfig.VisualDefaults.OBSERVER_TILT
                    zoomFactor = com.horizon.caadronesimulator.model.AppConfig.VisualDefaults.ZOOM_FACTOR_FIXED
                }
                "站位視角 (追蹤)" -> {
                    observerHeight = com.horizon.caadronesimulator.model.AppConfig.VisualDefaults.OBSERVER_HEIGHT_TRACKING
                    observerTilt = com.horizon.caadronesimulator.model.AppConfig.VisualDefaults.OBSERVER_TILT_TRACKING
                    zoomFactor = com.horizon.caadronesimulator.model.AppConfig.VisualDefaults.ZOOM_FACTOR_TRACKING
                }
                "站位視角 (智慧)" -> {
                    observerHeight = com.horizon.caadronesimulator.model.AppConfig.VisualDefaults.OBSERVER_HEIGHT_TRACKING
                    observerTilt = com.horizon.caadronesimulator.model.AppConfig.VisualDefaults.OBSERVER_TILT_TRACKING
                    zoomFactor = 1.2f
                }
            }
        }
    }

    fun syncFlightData(
        state: DroneState,
        alt: Float, x: Float, z: Float,
        yaw: Float, pitch: Float, roll: Float,
        speed: Float, isImpact: Boolean,
        volt: Float, perc: Int,
        physicsResult: com.horizon.caadronesimulator.logic.PhysicsEngine.PhysicsResult?
    ) {
        // [v1.7.4] 深度效能優化：實施 20Hz (50ms) 降頻同步攔截
        val now = System.currentTimeMillis()
        if (now - lastStabilityCheckTime < 50) return
        
        // [v1.7.6] 正確計算 dt：必須在更新基準時間前計算
        val dt = if (lastStabilityCheckTime == 0L) 0.05f else (now - lastStabilityCheckTime) / 1000f
        lastStabilityCheckTime = now

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
            
            // [v1.5.9] 撞擊速度保留
            this.speed = if (isImpact && physicsResult != null) physicsResult.impactSpeed else speed
            this.horizontalDist = sqrt(x.pow(2) + z.pow(2))
            
            val distToAnchor = sqrt(x.pow(2) + (z + 6f).pow(2))
            this.lastInZoomZone = distToAnchor > 10.0f && cameraMode != "FPV 視角" && cameraMode != "跟隨視角"
        }

        // [v1.5.9] 統一消息翻譯層：防止 UI 消息阻塞控制邏輯
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
            // [v1.5.9] 分級落地警告優化：確保不覆蓋關鍵停槳條件
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
        
        // [v1.7.6] 每幀同步時更新雷達縮放 (確保 Mode 1 自動縮放生效)
        updateRadarScale(state)
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

    override fun onCleared() {
        super.onCleared()
        wizardJob?.cancel()
    }
}
