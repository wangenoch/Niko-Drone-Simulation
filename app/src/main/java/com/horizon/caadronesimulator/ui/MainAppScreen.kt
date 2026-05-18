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
    usbSerialManager: UsbSerialManager,
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

    // 2. 環境與氣象同步 (大氣物理歸口)
    LaunchedEffect(droneState.weatherMode, droneState.timeOfDay, droneState.showClouds, droneState.showMountains) {
        renderer.weatherMode = droneState.weatherMode
        renderer.timeOfDay = droneState.timeOfDay
        renderer.showClouds = droneState.showClouds
        renderer.showMountains = droneState.showMountains
    }

    LaunchedEffect(droneState.windLevel, droneState.windDirection) {
        while(isActive) {
            com.horizon.caadronesimulator.logic.WindManager.updateCloudDrift(droneState, 0.016f)
            delay(16)
        }
    }

    // 3. UI 狀態監控
    LaunchedEffect(droneState.hideStatusBar) { onUpdateSystemUI() }
    LaunchedEffect(droneState.lockedProtocol) { usbSerialManager.setLockedProtocol(droneState.lockedProtocol) }
    LaunchedEffect(droneState.isLogcatEnabled) { viewModel.toggleLogcat(droneState) }

    LaunchedEffect(droneState.radarZoomMode, droneState.posX, droneState.posZ) {
        while(isActive) {
            viewModel.updateRadarScale(droneState)
            delay(100)
        }
    }

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

                // [優化] 智慧變更偵測：僅在狀態改變時執行賦值，將寫入負載降低 80%
                if (renderer.isPaused != isPausedLocal) renderer.isPaused = isPausedLocal
                if (renderer.droneType != droneState.droneType) renderer.droneType = droneState.droneType
                if (renderer.cameraMode != droneState.cameraMode) renderer.cameraMode = droneState.cameraMode
                if (renderer.zoomFactor != droneState.zoomFactor) renderer.zoomFactor = droneState.zoomFactor
                if (renderer.cameraTilt != droneState.cameraTilt) renderer.cameraTilt = droneState.cameraTilt
                if (renderer.observerHeight != droneState.observerHeight) renderer.observerHeight = droneState.observerHeight
                if (renderer.isMotorLocked != droneState.isMotorLocked) renderer.isMotorLocked = droneState.isMotorLocked
                if (renderer.applyPhysicalSpecs != droneState.applyPhysicalSpecs) renderer.applyPhysicalSpecs = droneState.applyPhysicalSpecs
                
                // 環境物理參數更新
                renderer.windLevel = droneState.windLevel
                renderer.windDirection = droneState.windDirection
                renderer.windVariation = droneState.windVariation.toFloat()
                renderer.windDirVariation = droneState.windDirVariation.toFloat()
                
                // [v1.6.1] 隨機模式冷啟動保險：若尚未擲骰子則強制刷新一次
                if (droneState.windDirection == "隨機" && droneState.env.randomWindAngle == 0f) {
                    renderer.rerollWindDirection()
                }

                renderer.timeOfDay = droneState.timeOfDay
                renderer.showShadow = droneState.showShadow
                renderer.shadowIntensity = droneState.shadowIntensity
                renderer.showObstacles = droneState.showObstacles
                renderer.useHardcorePhysics = droneState.useHardcorePhysics
                renderer.isSunSimEnabled = droneState.isSunSimEnabled
                renderer.sunPosition = droneState.sunPosition
                renderer.observerTilt = droneState.observerTilt
                renderer.showClouds = droneState.showClouds
                renderer.cloudDensity = droneState.cloudDensity
                renderer.weatherMode = droneState.weatherMode
                renderer.showMountains = droneState.showMountains
                renderer.useSimplifiedMarkers = droneState.useSimplifiedMarkers
                renderer.showSpecialTitle = droneState.showSpecialTitle
                renderer.currentTitleText = if (droneState.customTitle.isNotBlank()) droneState.customTitle else com.horizon.caadronesimulator.model.AppConfig.SPECIAL_TITLE
                renderer.useFlightLimit = droneState.useFlightLimit
                renderer.mainFOV = droneState.mainFOV
                renderer.showGroundAnchor = droneState.showGroundAnchor
                renderer.isThrottleHoldActive = droneState.isThrottleHoldActive 
                renderer.lastManualTouchTime = droneState.lastManualTouchTime 
                
                // 通過數據層同步位移影本 (邏輯歸口於 WindManager)
                renderer.cloudU = droneState.env.cloudU
                renderer.cloudV = droneState.env.cloudV
                
                if (isPausedLocal) {
                    renderer.updateControls(0f, -1f, 0f, 0f)
                } else {
                    val sT = stickInputState.stickThrottle(droneState)
                    val sY = stickInputState.stickYaw(droneState)
                    val sP = stickInputState.stickPitch(droneState)
                    val sR = stickInputState.stickRoll(droneState)
                    renderer.updateControls(sY, sT, sR, sP)
                }
                soundManager.updateSelfDriven(droneState, stickInputState)
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

        // UI 覆蓋層 (判定邏輯已優化)
        if (droneState.showTutorial && !droneState.showSettings) {
            WelcomeTutorial(viewModel = viewModel, modifier = Modifier.zIndex(20f)) {
                droneState.showTutorial = false
                configStore.saveSettings(droneState)
            }
        }

        if (droneState.showSettings) {
            UnifiedSettingsScreen(
                state = droneState, stickState = stickInputState,
                onUpdateState = { action -> droneState.action() },
                onClose = { droneState.showSettings = false },
                modifier = Modifier.zIndex(30f),
                onReset = onResetFlight,
                onScanUsb = { usbSerialManager.toggleConnection() },
                onUpdateBaudRate = onUpdateBaudRate,
                onExportLog = onExportLog,
                onUpdateInputMode = onUpdateInputMode,
                onToggleNetworkConnection = onToggleNetworkConnection,
                onSaveSettings = { configStore.saveSettings(droneState) },
                onRerollWind = onRerollWind,
                onSaveModelSettings = { id -> configStore.saveModelSettings(id, droneState) },
                onLoadModelSettings = { id -> configStore.loadModelSettings(id, droneState) },
                onUpdateLockedPath = { path -> usbSerialManager.setLockedPath(path) },
                onOpenNetworkSettings = { droneState.showNetworkSettingsDialog = true },
                onRestoreDefaults = onRestoreDefaults,
                availablePorts = usbSerialManager.listAvailableSerialPorts(),
                onTargetPositioned = { name, rect -> tutorialTargets = tutorialTargets + (name to rect) }
            )
        }

        if (droneState.showNetworkSettingsDialog) {
            NetworkSettingsOverlay(
                host = droneState.networkHost,
                port = droneState.networkPort,
                protocol = droneState.networkProtocol,
                onDismiss = { droneState.showNetworkSettingsDialog = false },
                onSave = { host, port, proto ->
                    droneState.networkHost = host
                    droneState.networkPort = port
                    droneState.networkProtocol = proto
                    configStore.saveSettings(droneState)
                    if (droneState.inputMode == 2) {
                        usbSerialManager.setLockedPath("NETWORK")
                        usbSerialManager.scanAndConnect()
                    }
                    onUpdateInputMode(2)
                }
            )
        }
        
        if (droneState.showJoystickTutorial) {
            JoystickSettingsTutorial(onDismiss = { droneState.showJoystickTutorial = false }, targets = tutorialTargets, viewModel = viewModel, modifier = Modifier.zIndex(40f))
        }
        if (droneState.showClimateTutorial) {
            ClimateSettingsTutorial(onDismiss = { droneState.showClimateTutorial = false }, targets = tutorialTargets, viewModel = viewModel, modifier = Modifier.zIndex(41f))
        }

        if (droneState.isCollision) CollisionOverlay(onReset = onResetFlight, modifier = Modifier.zIndex(50f))
        if (showSplash) SplashScreen(modifier = Modifier.zIndex(60f), onTimeout = { onCloseSplash() })
        if (droneState.showUpdateNotice) UpdateNoticeOverlay(onClose = { droneState.showUpdateNotice = false; configStore.saveSettings(droneState) }, modifier = Modifier.zIndex(70f))

        // 遙控器設定引導與校準層
        if (droneState.setupWizardStep > 0) {
            JoystickWizardOverlay(
                setupWizardStep = droneState.setupWizardStep,
                isWizardWaiting = droneState.wizardWaitingForNeutral,
                wizardCountdown = droneState.wizardCountdown,
                stickLX = stickInputState.stickLX(droneState),
                stickLY = stickInputState.stickLY(droneState),
                stickRX = stickInputState.stickRX(droneState),
                stickRY = stickInputState.stickRY(droneState),
                onCancelWizard = { droneState.setupWizardStep = 0 }
            )
        }
        if (droneState.isCalibrating) {
            JoystickCalibrationOverlay(
                isCalibrating = droneState.isCalibrating,
                calibrationStep = droneState.calibrationStep,
                joystickMode = droneState.joystickMode,
                stickLX = stickInputState.rawLX,
                stickLY = stickInputState.rawLY,
                stickRX = stickInputState.rawRX,
                stickRY = stickInputState.rawRY,
                onNextStep = { 
                    if (droneState.calibrationStep < 3) droneState.calibrationStep++ 
                    else { droneState.isCalibrating = false; configStore.saveSettings(droneState) }
                },
                onFinish = { droneState.isCalibrating = false; configStore.saveSettings(droneState) }
            )
        }

        if (droneState.showTroubleshootingHint) {
            val context = LocalContext.current
            TroubleshootingOverlay(
                onOpenFactoryApp = {
                    val pkg = droneState.hardwareProfile?.factoryAppPackage
                    if (pkg != null) {
                        val intent = context.packageManager.getLaunchIntentForPackage(pkg)
                        if (intent != null) context.startActivity(intent)
                        else droneState.systemMessage = "尚未安裝該硬體的原裝 App"
                    } else droneState.systemMessage = "此設備不支援原裝 App快速啟動"
                },
                onDismiss = { droneState.showTroubleshootingHint = false }
            )
        }

        SystemStatusOverlay(droneState, stickInputState)

        // [v1.5.9] 協議優化引導對話框 (移至全域層級)
        ProtocolOptimizationOverlay(
            state = droneState,
            onApply = { proto, baud ->
                usbSerialManager.setLockedProtocol(proto)
                onUpdateBaudRate(baud)
                droneState.commDecisionState = com.horizon.caadronesimulator.model.CommDecisionState.LOCKED
            },
            onIgnore = { permanent ->
                if (permanent) {
                    droneState.optimizationPromptIgnored = true
                    configStore.saveSettings(droneState)
                }
                droneState.commDecisionState = com.horizon.caadronesimulator.model.CommDecisionState.SCANNING
            }
        )

        // [v1.5.9] 任務評測層：掛載計時器與任務進度提示 UI
        MissionManager.RenderOverlay(state = droneState, onUpdateState = { action -> droneState.action() })

        // [v1.5.9] 攝影機切換鎖定環：當切換模式或 Zoom 時顯示進度條
        if (droneState.isSwitchingMode) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(0.1f)).clickable { /* 攔截點擊 */ }, contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        progress = { droneState.switchProgress },
                        color = Color.Cyan,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(54.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(droneState.switchMessage, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}
