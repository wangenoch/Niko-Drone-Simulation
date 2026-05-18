package com.horizon.caadronesimulator.ui

import android.opengl.GLSurfaceView
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import com.horizon.caadronesimulator.logic.InternalCommManager
import com.horizon.caadronesimulator.logic.ConnectivityCoordinator
import com.horizon.caadronesimulator.logic.DroneViewModel
import com.horizon.caadronesimulator.model.ChannelMapping
import com.horizon.caadronesimulator.model.DroneState
import com.horizon.caadronesimulator.logic.storage.ConfigurationStore
import com.horizon.caadronesimulator.model.StickInputState
import com.horizon.caadronesimulator.render.DroneSimulationRenderer
import com.horizon.caadronesimulator.audio.DroneSoundManager
import com.horizon.caadronesimulator.logic.UsbSerialManager
import com.horizon.caadronesimulator.mission.MissionManager
import com.horizon.caadronesimulator.ui.hud.DroneHUD
import com.horizon.caadronesimulator.ui.hud.FlightInteractionLayer
import com.horizon.caadronesimulator.ui.hud.SideNavInstruments
import com.horizon.caadronesimulator.ui.hud.StickInteractionLogic
import com.horizon.caadronesimulator.ui.overlays.*
import com.horizon.caadronesimulator.ui.settings.NetworkSettingsOverlay
import com.horizon.caadronesimulator.ui.settings.UnifiedSettingsScreen
import com.horizon.caadronesimulator.ui.tutorial.WelcomeTutorial
import com.horizon.caadronesimulator.ui.tutorial.JoystickSettingsTutorial
import com.horizon.caadronesimulator.ui.tutorial.ClimateSettingsTutorial
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.*

/**
 * Niko Drone Simulator 主 UI 結構層
 * [v1.5.9] 全專案「最終淨化」三部曲完成版
 */
@Composable
fun MainAppScreen(
    droneState: DroneState,
    stickInputState: StickInputState,
    renderer: DroneSimulationRenderer,
    soundManager: DroneSoundManager,
    usbSerialManager: InternalCommManager,
    configStore: ConfigurationStore,
    viewModel: DroneViewModel,
    showSplash: Boolean,
    onCloseSplash: () -> Unit,
    onResetFlight: () -> Unit,
    onRerollWind: () -> Unit,
    onRestoreDefaults: () -> Unit,
    onExportLog: () -> Unit,
    onUpdateBaudRate: (Int) -> Unit,
    onUpdateInputMode: (Int) -> Unit,
    onToggleNetworkConnection: (Boolean) -> Unit,
    onUpdateSystemUI: () -> Unit
) {
    var isStatusVisible by remember { mutableStateOf(true) }
    var tutorialTargets by remember { mutableStateOf<Map<String, Rect>>(emptyMap()) }

    // 1. 設置與初始化 Effect (邏輯已移至 ViewModel)
    LaunchedEffect(droneState.setupWizardStep, droneState.wizardWaitingForNeutral) {
        if (droneState.setupWizardStep > 0 && droneState.wizardWaitingForNeutral) {
            viewModel.startWizardCountdown(droneState, configStore)
        }
    }

    LaunchedEffect(droneState.inputMode) {
        if (droneState.inputMode == 1 && droneState.mappingLY.axis == -1) {
            droneState.mappingLY = ChannelMapping(104, true, "油門 Throttle")
            droneState.mappingLX = ChannelMapping(101, false, "航向 Yaw")
            droneState.mappingRY = ChannelMapping(102, false, "俯仰 Pitch")
            droneState.mappingRX = ChannelMapping(103, false, "橫滾 Roll")
        }
        configStore.saveSettings(droneState)
    }

    LaunchedEffect(
        droneState.droneType, droneState.showTutorial, droneState.hasShownJoystickTutorial,
        droneState.hasShownClimateTutorial, droneState.isMappingUnlocked, droneState.joystickMode,
        droneState.shadowIntensity, droneState.windLevel, droneState.windDirection,
        droneState.enableVerticalDraft, droneState.joystickDeadzone, droneState.halfThrottle,
        droneState.useGlobalRates, droneState.showIndividualRates, droneState.globalRate, droneState.globalExpo,
        droneState.baudRate, droneState.lockedProtocol, droneState.useHardcorePhysics,
        droneState.isSunSimEnabled, droneState.sunPosition, droneState.useSimplifiedMarkers,
        droneState.useFlightLimit, droneState.mainFOV,
        droneState.showSideRulers, droneState.showGroundAnchor, droneState.autoPiPRelocate,
        droneState.showClouds, droneState.cloudDensity,
        droneState.mappingLY, droneState.mappingLX, droneState.mappingRY, droneState.mappingRX,
        droneState.mappingHold, droneState.mappingArm, droneState.mappingObsHeight, 
        droneState.mappingObsTilt, droneState.mappingFpvTilt, droneState.mappingFlightMode
    ) {
        configStore.saveSettings(droneState) 
    }

    // 2. 環境與氣象同步 [v1.7.3 效能修復]：嚴格限制同步參數，移除任何導致遞迴重組的高頻監聽
    LaunchedEffect(droneState.weatherMode, droneState.timeOfDay, droneState.showClouds, droneState.showMountains,
                   droneState.windLevel, droneState.windDirection, droneState.windVariation, droneState.windDirVariation,
                   droneState.showShadow, droneState.shadowIntensity, droneState.showObstacles, droneState.useHardcorePhysics,
                   droneState.isSunSimEnabled, droneState.sunPosition, droneState.observerTilt, droneState.cloudDensity,
                   droneState.useSimplifiedMarkers, droneState.showSpecialTitle, droneState.customTitle,
                   droneState.useFlightLimit, droneState.mainFOV, droneState.showGroundAnchor, droneState.isThrottleHoldActive,
                   droneState.isMotorLocked) { // [v1.7.5] 補回馬達鎖定監聽
        // 僅同步「設定型」參數
        renderer.weatherMode = droneState.weatherMode
        renderer.timeOfDay = droneState.timeOfDay
        renderer.showClouds = droneState.showClouds
        renderer.showMountains = droneState.showMountains
        renderer.windLevel = droneState.windLevel
        renderer.windDirection = droneState.windDirection
        renderer.windVariation = droneState.windVariation.toFloat()
        renderer.windDirVariation = droneState.windDirVariation.toFloat()
        renderer.showShadow = droneState.showShadow
        renderer.shadowIntensity = droneState.shadowIntensity
        renderer.showObstacles = droneState.showObstacles
        renderer.useHardcorePhysics = droneState.useHardcorePhysics
        renderer.isSunSimEnabled = droneState.isSunSimEnabled
        renderer.sunPosition = droneState.sunPosition
        renderer.observerTilt = droneState.observerTilt
        renderer.cloudDensity = droneState.cloudDensity
        renderer.useSimplifiedMarkers = droneState.useSimplifiedMarkers
        renderer.showSpecialTitle = droneState.showSpecialTitle
        renderer.currentTitleText = if (droneState.customTitle.isNotBlank()) droneState.customTitle else com.horizon.caadronesimulator.model.AppConfig.SPECIAL_TITLE
        renderer.useFlightLimit = droneState.useFlightLimit
        renderer.mainFOV = droneState.mainFOV
        renderer.showGroundAnchor = droneState.showGroundAnchor
        renderer.isThrottleHoldActive = droneState.isThrottleHoldActive 
        renderer.isMotorLocked = droneState.isMotorLocked // [v1.7.5] 同步解鎖狀態至 Renderer，恢復起槳功能

        if (droneState.windDirection == "隨機" && droneState.env.randomWindAngle == 0f) {
            renderer.rerollWindDirection()
        }
    }

    LaunchedEffect(droneState.droneType, droneState.cameraMode, droneState.zoomFactor, droneState.cameraTilt, droneState.observerHeight) {
        // 同步攝影機與機型配置 (低頻)
        renderer.droneType = droneState.droneType
        renderer.cameraMode = droneState.cameraMode
        renderer.zoomFactor = droneState.zoomFactor
        renderer.cameraTilt = droneState.cameraTilt
        renderer.observerHeight = droneState.observerHeight
    }

    // 移除 LaunchedEffect(droneState.env.cloudU...) 高頻監聽，改由 WindManager 在背景線程處理

    // 3. UI 狀態監控
    LaunchedEffect(droneState.hideStatusBar) { onUpdateSystemUI() }
    LaunchedEffect(droneState.lockedProtocol) { usbSerialManager.setLockedProtocol(droneState.lockedProtocol) }
    LaunchedEffect(droneState.isLogcatEnabled) { viewModel.toggleLogcat(droneState) }

    LaunchedEffect(droneState.radarZoomMode) {
        // [v1.7.3] 降頻雷達運算，僅在模式改變時同步
        viewModel.updateRadarScale(droneState)
    }

    // 將位置更新移除 LaunchedEffect，改由 ViewModel 物理循環直接驅動
    
    // ... 其他教學邏輯保持不變 ...


    LaunchedEffect(viewModel.welcomeStep, droneState.showTutorial) {
        if (droneState.showTutorial) {
            when (viewModel.welcomeStep) {
                1 -> droneState.isMenuExpanded = true
                2 -> droneState.isMenuExpanded = false
            }
        } else {
            if (droneState.isMenuExpanded) droneState.isMenuExpanded = false
        }
    }

    LaunchedEffect(droneState.lastInZoomZone) {
        if (droneState.lastInZoomZone) droneState.isMenuExpanded = false
    }

    // [v1.6.1] 攝影機切換自動歸位與緩衝監聽
    LaunchedEffect(droneState.cameraMode) {
        if (droneState.isSettingsLoaded) {
            viewModel.applyCameraModeDefaults(droneState, droneState.cameraMode)
            viewModel.startSwitchBuffer(droneState, "視訊數據同步中...")
        }
    }

    // 4. [v1.6.3] 物理引擎主驅動 (大掃除：邏輯由 ViewModel 分離驅動)
    LaunchedEffect(Unit) {
        viewModel.startPhysicsLoop(droneState, stickInputState, renderer, soundManager)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize().zIndex(0f),
            factory = { ctx -> 
                com.horizon.caadronesimulator.ui.common.SimulationSurfaceView(ctx, renderer).apply {
                    setZOrderMediaOverlay(false)
                }
            },
            update = {
                val isTutorialActive = droneState.showTutorial || droneState.showJoystickTutorial || droneState.showClimateTutorial
                val isPausedLocal = (droneState.showSettings && droneState.pauseInSettings) || droneState.isCollision || (droneState.newHardwareDetected != null) || isTutorialActive
                if (renderer.isPaused != isPausedLocal) renderer.isPaused = isPausedLocal
                
                // [v1.7.2] 極簡化更新區：原本幾十行的比對邏輯已移至專屬的 LaunchedEffect。
                // 這裡現在僅負責處理「暫停」與「手把時間」等極高頻且無法由 Effect 涵蓋的狀態。
                renderer.lastManualTouchTime = droneState.lastManualTouchTime 
            }
        )

        DroneHUD(
            state = droneState, stickState = stickInputState,
            isStatusVisible = isStatusVisible, tutorialTargets = tutorialTargets,
            onUpdateState = { action -> droneState.action() },
            modifier = Modifier.zIndex(10f),
            onToggleStatus = { isStatusVisible = !isStatusVisible },
            onUpdatePipRect = { rect -> renderer.pipRect = rect?.let { android.graphics.Rect(it.left.toInt(), it.top.toInt(), it.right.toInt(), it.bottom.toInt()) } },
            onUpdateZoomPipRect = { rect -> renderer.zoomPipRect = rect?.let { android.graphics.Rect(it.left.toInt(), it.top.toInt(), it.right.toInt(), it.bottom.toInt()) } }
        )
        FlightInteractionLayer(
            state = droneState,
            onUpdateState = { action -> droneState.action() },
            onReset = onResetFlight,
            modifier = Modifier.zIndex(11f)
        )
        StickInteractionLogic(
            state = droneState, stickState = stickInputState,
            onUpdateState = { action -> droneState.action() }
        )
        SideNavInstruments(
            state = droneState,
            onUpdateState = { action -> droneState.action() },
            modifier = Modifier.zIndex(12f)
        )

        // [v1.6.3] 第一階段大掃除：使用 OverlayDispatcher 統一管理所有覆蓋層
        OverlayDispatcher(
            droneState = droneState,
            stickInputState = stickInputState,
            usbSerialManager = usbSerialManager,
            configStore = configStore,
            viewModel = viewModel,
            showSplash = showSplash,
            tutorialTargets = tutorialTargets,
            onUpdateTutorialTargets = { name, rect -> tutorialTargets = tutorialTargets + (name to rect) },
            onCloseSplash = onCloseSplash,
            onResetFlight = onResetFlight,
            onRerollWind = onRerollWind,
            onRestoreDefaults = onRestoreDefaults,
            onExportLog = onExportLog,
            onUpdateBaudRate = onUpdateBaudRate,
            onUpdateInputMode = onUpdateInputMode,
            onToggleNetworkConnection = onToggleNetworkConnection
        )
    }
}
