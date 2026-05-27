package com.horizon.nikonikodronesimulator.logic

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.horizon.nikonikodronesimulator.model.AppConfig
import com.horizon.nikonikodronesimulator.model.DroneState
import com.horizon.nikonikodronesimulator.model.StickInputState
import com.horizon.nikonikodronesimulator.logic.storage.ConfigurationStore
import com.horizon.nikonikodronesimulator.model.DroneRegistry
import com.horizon.nikonikodronesimulator.render.DroneSimulationRenderer
import com.horizon.nikonikodronesimulator.mission.MissionManager
import kotlinx.coroutines.*
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
        soundManager: com.horizon.nikonikodronesimulator.audio.DroneSoundManager
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
        com.horizon.nikonikodronesimulator.logic.PhysicsEngine.clearState()
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
            crashReason = com.horizon.nikonikodronesimulator.model.CrashReason.NONE
            sessionFlightTime = 0f
            isNearBoundary = false
            flightPath = emptyList()
            isArmSafetyPassed = false
            isHoldSafetyPassed = false
            isThrottleHoldActive = false
            systemMessage = null // [關鍵修復] 確保重置後立即清除所有系統訊息與警告
            
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
            
            // [v1.7.12] 物理同步：強行抹除相機平滑器的記憶，確保畫面瞬間跳回預設位置
            CameraDirector.snapToDefaults(observerHeight, observerTilt, zoomFactor, mainFOV)

            // [v1.7.7] 重置雷達位置
            radarOffset = androidx.compose.ui.geometry.Offset.Zero
            isRadarUnlocked = false
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
            globalRate = com.horizon.nikonikodronesimulator.model.AppConfig.JoystickDefaults.RATE
            globalExpo = com.horizon.nikonikodronesimulator.model.AppConfig.JoystickDefaults.EXPO
            joystickDeadzone = com.horizon.nikonikodronesimulator.model.AppConfig.JoystickDefaults.DEADZONE
            useGlobalRates = true
            showIndividualRates = false
            
            // 各別軸向還原
            rateT = com.horizon.nikonikodronesimulator.model.AppConfig.JoystickDefaults.RATE
            expoT = com.horizon.nikonikodronesimulator.model.AppConfig.JoystickDefaults.EXPO
            rateY = com.horizon.nikonikodronesimulator.model.AppConfig.JoystickDefaults.RATE
            expoY = com.horizon.nikonikodronesimulator.model.AppConfig.JoystickDefaults.EXPO
            rateP = com.horizon.nikonikodronesimulator.model.AppConfig.JoystickDefaults.RATE
            expoP = com.horizon.nikonikodronesimulator.model.AppConfig.JoystickDefaults.EXPO
            rateR = com.horizon.nikonikodronesimulator.model.AppConfig.JoystickDefaults.RATE
            expoR = com.horizon.nikonikodronesimulator.model.AppConfig.JoystickDefaults.EXPO

            // 環境還原
            windLevel = com.horizon.nikonikodronesimulator.model.AppConfig.EnvironmentDefaults.WIND_LEVEL
            windDirection = com.horizon.nikonikodronesimulator.model.AppConfig.EnvironmentDefaults.WIND_DIRECTION
            isSunSimEnabled = com.horizon.nikonikodronesimulator.model.AppConfig.EnvironmentDefaults.SUN_ENABLED
            sunPosition = com.horizon.nikonikodronesimulator.model.AppConfig.EnvironmentDefaults.SUN_POSITION
            shadowIntensity = com.horizon.nikonikodronesimulator.model.AppConfig.EnvironmentDefaults.SHADOW_INTENSITY
            cloudDensity = com.horizon.nikonikodronesimulator.model.AppConfig.EnvironmentDefaults.CLOUD_DENSITY
            weatherMode = com.horizon.nikonikodronesimulator.model.AppConfig.EnvironmentDefaults.WEATHER_MODE
            showClouds = com.horizon.nikonikodronesimulator.model.AppConfig.EnvironmentDefaults.SHOW_CLOUDS
            showMountains = com.horizon.nikonikodronesimulator.model.AppConfig.EnvironmentDefaults.SHOW_MOUNTAINS
            enableVerticalDraft = com.horizon.nikonikodronesimulator.model.AppConfig.EnvironmentDefaults.ENABLE_VERTICAL_DRAFT
            useHardcorePhysics = com.horizon.nikonikodronesimulator.model.AppConfig.EnvironmentDefaults.HARDCORE_PHYSICS

            // 視覺還原
            mainFOV = com.horizon.nikonikodronesimulator.model.AppConfig.VisualDefaults.MAIN_FOV
            // 視覺還原 (依照 AppConfig 預設初始模式進行對接)
            cameraMode = com.horizon.nikonikodronesimulator.model.AppConfig.VisualDefaults.CAMERA_MODE
            mainFOV = com.horizon.nikonikodronesimulator.model.AppConfig.VisualDefaults.MAIN_FOV
            
            if (cameraMode == AppConfig.CAM_MODE_STATION_FIXED) {
                observerHeight = com.horizon.nikonikodronesimulator.model.AppConfig.VisualDefaults.OBSERVER_HEIGHT
                observerTilt = com.horizon.nikonikodronesimulator.model.AppConfig.VisualDefaults.OBSERVER_TILT
                zoomFactor = com.horizon.nikonikodronesimulator.model.AppConfig.VisualDefaults.ZOOM_FACTOR_FIXED
            } else {
                observerHeight = com.horizon.nikonikodronesimulator.model.AppConfig.VisualDefaults.OBSERVER_HEIGHT_TRACKING
                observerTilt = com.horizon.nikonikodronesimulator.model.AppConfig.VisualDefaults.OBSERVER_TILT_TRACKING
                zoomFactor = com.horizon.nikonikodronesimulator.model.AppConfig.VisualDefaults.ZOOM_FACTOR_TRACKING
            }

            showGroundAnchor = com.horizon.nikonikodronesimulator.model.AppConfig.VisualDefaults.SHOW_GROUND_ANCHOR
            autoPiPRelocate = com.horizon.nikonikodronesimulator.model.AppConfig.VisualDefaults.AUTO_PIP_RELOCATE
            enableZoomAssistant = com.horizon.nikonikodronesimulator.model.AppConfig.VisualDefaults.ENABLE_ZOOM_ASSISTANT
            showFlightPath = com.horizon.nikonikodronesimulator.model.AppConfig.VisualDefaults.SHOW_FLIGHT_PATH

            // 安全還原
            useFlightLimit = com.horizon.nikonikodronesimulator.model.AppConfig.SystemDefaults.USE_FLIGHT_LIMIT
            useStrictLanding = com.horizon.nikonikodronesimulator.model.AppConfig.SystemDefaults.USE_STRICT_LANDING
            isHidPriorityEnabled = com.horizon.nikonikodronesimulator.model.AppConfig.SystemDefaults.USE_HID_PRIORITY
            applyPhysicalSpecs = com.horizon.nikonikodronesimulator.model.AppConfig.SystemDefaults.APPLY_PHYSICAL_SPECS
            isThrottleHoldActive = com.horizon.nikonikodronesimulator.model.AppConfig.SystemDefaults.IS_THROTTLE_HOLD_ACTIVE
            
            // 系統與介面還原
            isMuted = com.horizon.nikonikodronesimulator.model.AppConfig.SystemDefaults.IS_MUTED
            showShadow = com.horizon.nikonikodronesimulator.model.AppConfig.SystemDefaults.SHOW_SHADOW
            showObstacles = com.horizon.nikonikodronesimulator.model.AppConfig.SystemDefaults.SHOW_OBSTACLES
            hideStatusBar = com.horizon.nikonikodronesimulator.model.AppConfig.SystemDefaults.HIDE_STATUS_BAR
            pauseInSettings = com.horizon.nikonikodronesimulator.model.AppConfig.SystemDefaults.PAUSE_IN_SETTINGS
            isAutoConnectEnabled = com.horizon.nikonikodronesimulator.model.AppConfig.SystemDefaults.AUTO_CONNECT_ENABLED
            isExpertModeLocked = com.horizon.nikonikodronesimulator.model.AppConfig.SystemDefaults.IS_EXPERT_MODE_LOCKED
            hudMode = com.horizon.nikonikodronesimulator.model.AppConfig.VisualDefaults.HUD_MODE
            radarZoomMode = com.horizon.nikonikodronesimulator.model.AppConfig.VisualDefaults.RADAR_ZOOM_MODE
            
            // 任務評測還原
            isSpotTimerEnabled = com.horizon.nikonikodronesimulator.model.AppConfig.SystemDefaults.IS_SPOT_TIMER_ENABLED
            spotTimerSeconds = com.horizon.nikonikodronesimulator.model.AppConfig.SystemDefaults.SPOT_TIMER_SECONDS

            // 介面細節還原
            showSpecialTitle = com.horizon.nikonikodronesimulator.model.AppConfig.VisualDefaults.SHOW_SPECIAL_TITLE
            currentTitleText = com.horizon.nikonikodronesimulator.model.AppConfig.getDefaultSpecialTitle(state.appLanguage)
            showSideSliders = com.horizon.nikonikodronesimulator.model.AppConfig.VisualDefaults.SHOW_SPECIAL_TITLE // 使用同一基準
            showSideRulers = com.horizon.nikonikodronesimulator.model.AppConfig.VisualDefaults.SHOW_SIDE_RULERS
            reverseSliderSides = com.horizon.nikonikodronesimulator.model.AppConfig.VisualDefaults.REVERSE_SLIDERS

            systemMessage = "RESTORE_DONE"
        }
    }

    /** [v1.7.7] 視角模式切換自動歸位邏輯 - 強制同步 AppConfig */
    fun applyCameraModeDefaults(state: DroneState, mode: String) {
        state.apply {
            when (mode) {
                AppConfig.CAM_MODE_STATION_FIXED -> {
                    observerHeight = AppConfig.VisualDefaults.OBSERVER_HEIGHT
                    observerTilt = AppConfig.VisualDefaults.OBSERVER_TILT
                    zoomFactor = AppConfig.VisualDefaults.ZOOM_FACTOR_FIXED
                }
                AppConfig.CAM_MODE_STATION_TRACK -> {
                    observerHeight = AppConfig.VisualDefaults.OBSERVER_HEIGHT_TRACKING
                    observerTilt = AppConfig.VisualDefaults.OBSERVER_TILT_TRACKING
                    zoomFactor = AppConfig.VisualDefaults.ZOOM_FACTOR_TRACKING
                }
                AppConfig.CAM_MODE_STATION_SMART -> {
                    // [v1.7.12] 智慧視角：使用專屬預設值
                    observerHeight = AppConfig.VisualDefaults.OBSERVER_HEIGHT_SMART
                    observerTilt = AppConfig.VisualDefaults.OBSERVER_TILT_SMART
                    zoomFactor = AppConfig.VisualDefaults.ZOOM_FACTOR_SMART
                }
                AppConfig.CAM_MODE_FPV -> {
                    // FPV 模式下的階層式視野讀取
                    val spec = DroneRegistry.getSpec(droneType)
                    mainFOV = spec.fpvFov
                    zoomFactor = 1.0f
                    cameraTilt = 0f // [v1.7.7] 進入 FPV 時雲台水平歸零
                }
            }
            // 觸發最後操作時間，防止視角被平滑平滑掉
            lastManualTouchTime = System.currentTimeMillis()
        }
    }

    /** [v1.7.7] 領域專屬流 A：動力學與姿態同步 (Dynamics & Attitude) */
    fun syncDynamics(
        state: DroneState,
        x: Float, y: Float, z: Float,
        yaw: Float, pitch: Float, roll: Float,
        speed: Float, isImpact: Boolean,
        physicsResult: com.horizon.nikonikodronesimulator.logic.PhysicsEngine.PhysicsResult?
    ) {
        val now = System.currentTimeMillis()
        if (now - lastStabilityCheckTime < 50) return
        val dt = if (lastStabilityCheckTime == 0L) 0.05f else (now - lastStabilityCheckTime) / 1000f
        lastStabilityCheckTime = now

        val isProtecting = (now - lastResetTime < 500)
        if ((state.showSettings || state.isCollision) && !isProtecting) return

        state.apply {
            this.lastYaw = this.yaw
            this.altitude = y
            this.posX = x
            this.posZ = z
            this.yaw = yaw
            this.pitch = pitch
            this.roll = roll
            this.speed = if (isImpact && physicsResult != null) physicsResult.impactSpeed else speed
            this.horizontalDist = sqrt(x.pow(2) + z.pow(2))
            
            val distToOpsCenter = sqrt(x.pow(2) + (z - 6f).pow(2))
            this.lastInZoomZone = distToOpsCenter > AppConfig.VisualDefaults.ZOOM_ASSISTANT_DISTANCE &&
                                  cameraMode != AppConfig.CAM_MODE_FPV && 
                                  cameraMode != AppConfig.CAM_MODE_FOLLOW &&
                                  cameraMode != AppConfig.CAM_MODE_STATION_SMART
        }

        if (isImpact) {
            state.isCollision = true
            state.isMotorLocked = true
            if (physicsResult != null) {
                state.systemMessage = when(physicsResult.systemMessage) {
                    "CRASH_EXTREME" -> "CRASH_EXTREME|${physicsResult.impactSpeed}"
                    "CRASH_STRUCTURAL" -> "CRASH_STRUCTURAL|${physicsResult.impactSpeed}"
                    else -> physicsResult.systemMessage
                }
                
                // [v1.7.12] 分級設置 CrashReason，確保 CollisionOverlay 標題正確顯示
                state.flight.crashReason = when(physicsResult.systemMessage) {
                    "CRASH_OUT_OF_BOUNDS" -> com.horizon.nikonikodronesimulator.model.CrashReason.OUT_OF_BOUNDS
                    else -> com.horizon.nikonikodronesimulator.model.CrashReason.IMPACT
                }
            }
        } else {
            val spec = DroneRegistry.getSpec(state.droneType)
            MissionManager.update(state, dt, spec)
            if (state.showFlightPath && !state.isMotorLocked) {
                val path = state.flightPath; val pos = androidx.compose.ui.geometry.Offset(x, z); val last = path.lastOrNull()
                if (last == null || sqrt((pos.x - last.x).pow(2) + (pos.y - last.y).pow(2)) > 0.15f) {
                    state.flightPath = (path + pos).takeLast(5400)
                }
            }
        }
        updateRadarScale(state)
    }

    /** [v1.7.7] 領域專屬流 B：大氣與環境同步 (Atmosphere & Environment) */
    fun syncAtmosphere(state: DroneState, windAngle: Float, cloudU: Float, cloudV: Float) {
        state.env.currentWindAngle = windAngle
        state.env.cloudU = cloudU
        state.env.cloudV = cloudV
    }

    /** [v1.7.7] 領域專屬流 C：電能與系統同步 (Power & Telemetry) */
    fun syncPower(state: DroneState, volt: Float, perc: Int, flightTime: Float) {
        state.batteryVoltage = volt
        state.batteryPercent = perc
        if (flightTime > 0) state.sessionFlightTime = flightTime
        if (state.useFlightLimit && perc <= 0) {
            state.isCollision = true
            state.isMotorLocked = true
            state.crashReason = com.horizon.nikonikodronesimulator.model.CrashReason.BATTERY_LOW
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
                    state.systemMessage = "WIZARD_DONE"
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
