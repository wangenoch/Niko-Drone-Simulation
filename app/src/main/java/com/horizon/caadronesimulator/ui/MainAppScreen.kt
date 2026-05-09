package com.horizon.caadronesimulator.ui

// Android 系統與圖形支援
import android.opengl.GLSurfaceView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.zIndex

// 內部邏輯與數據模型
import com.horizon.caadronesimulator.model.DroneState
import com.horizon.caadronesimulator.model.StickInputState
import com.horizon.caadronesimulator.model.DroneRegistry
import com.horizon.caadronesimulator.render.DroneSimulationRenderer
import com.horizon.caadronesimulator.audio.DroneSoundManager
import com.horizon.caadronesimulator.logic.UsbSerialManager
import com.horizon.caadronesimulator.model.SettingsManager
import com.horizon.caadronesimulator.mission.MissionManager

// UI 組件
import com.horizon.caadronesimulator.ui.components.*
import com.horizon.caadronesimulator.ui.tutorial.WelcomeTutorial
import com.horizon.caadronesimulator.ui.tutorial.JoystickSettingsTutorial
import com.horizon.caadronesimulator.ui.tutorial.ClimateSettingsTutorial
import com.horizon.caadronesimulator.ui.joystick.StickInteractionLogic
import com.horizon.caadronesimulator.ui.DroneHUD

// 協程與非同步
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.isActive

// 數學工具
import kotlin.math.*

/**
 * Niko Drone Simulator 主 UI 統籌層
 * [v1.2.81 階段三] 實施 V17 全系統分層與訊息分流架構
 */
@Composable
fun MainAppScreen(
    droneState: DroneState,
    stickInputState: StickInputState,
    renderer: DroneSimulationRenderer,
    soundManager: DroneSoundManager,
    usbSerialManager: UsbSerialManager,
    settingsManager: SettingsManager,
    viewModel: com.horizon.caadronesimulator.logic.DroneViewModel, // [v1.2.85] 引入 ViewModel
    showSplash: Boolean,
    onCloseSplash: () -> Unit,
    onResetFlight: () -> Unit,
    onExportLog: () -> Unit,
    onUpdateBaudRate: (Int) -> Unit,
    onUpdateInputMode: (Int) -> Unit,
    onUpdateSystemUI: () -> Unit
) {
    var tutorialTargets by remember { mutableStateOf(mapOf<String, Rect>()) }
    var isStatusVisible by remember { mutableStateOf(true) }

    // 1. [v1.2.85] 委派 ViewModel 處理倒數計時
    LaunchedEffect(droneState.setupWizardStep, droneState.wizardWaitingForNeutral) {
        if (droneState.setupWizardStep > 0 && droneState.wizardWaitingForNeutral) {
            viewModel.startWizardCountdown(droneState, settingsManager)
        }
    }

    // 2. [v1.2.82] 智慧映射引擎 (已解耦：UI僅負責初次硬體握手)
    LaunchedEffect(droneState.inputMode) {
        if (droneState.inputMode == 1 && droneState.mappingLY.axis == -1) {
            // 物理真相：101:S1, 102:S2, 103:S3, 104:S4
            // 初次進入系統，根據預設 Mode 2 初始化物理線路
            droneState.mappingLY = com.horizon.caadronesimulator.model.ChannelMapping(104, true, "油門 Throttle")
            droneState.mappingLX = com.horizon.caadronesimulator.model.ChannelMapping(101, false, "航向 Yaw")
            droneState.mappingRY = com.horizon.caadronesimulator.model.ChannelMapping(102, false, "俯仰 Pitch")
            droneState.mappingRX = com.horizon.caadronesimulator.model.ChannelMapping(103, false, "橫滾 Roll")
        }
        settingsManager.saveSettings(droneState)
    }

    // 2b. 全局儲存監聽 (v1.3.5 擴充：含環境、陰影、手感與通訊參數)
    LaunchedEffect(
        droneState.droneType, droneState.showTutorial, droneState.hasShownJoystickTutorial, 
        droneState.hasShownClimateTutorial, droneState.isMappingUnlocked, droneState.joystickMode,
        droneState.shadowIntensity, droneState.windLevel, droneState.windDirection, 
        droneState.enableVerticalDraft, droneState.joystickDeadzone, droneState.halfThrottle,
        droneState.useGlobalRates, droneState.showIndividualRates, droneState.globalRate, droneState.globalExpo,
        droneState.baudRate, droneState.lockedProtocol, droneState.useHardcorePhysics,
        droneState.isSunSimEnabled, droneState.sunPosition, droneState.useSimplifiedMarkers,
        droneState.useFlightLimit, droneState.mainFOV, droneState.useSmartObserver,
        droneState.showSideRulers, droneState.showGroundAnchor, droneState.autoPiPRelocate
    ) {
        settingsManager.saveSettings(droneState) 
    }

    // 2c. [v1.4.0] 智慧觀察員運行迴圈 (60Hz)
    LaunchedEffect(droneState.useSmartObserver, droneState.cameraMode) {
        if (droneState.useSmartObserver) {
            while(isActive) {
                viewModel.runSmartObserver(droneState)
                delay(16) // 約 60fps
            }
        }
    }

    // 3. 系統 UI 更新
    LaunchedEffect(droneState.showStatusBar) { onUpdateSystemUI() }

    // 4. 協議鎖定更新 (v1.3.5 增加 AX12 強制波特率邏輯)
    LaunchedEffect(droneState.lockedProtocol) { 
        usbSerialManager.setLockedProtocol(droneState.lockedProtocol)
        if (droneState.lockedProtocol == "AX12(UMBUS)") {
            droneState.baudRate = 921600
            usbSerialManager.setBaudRate(921600)
        }
    }
    
    // 5. [v1.2.85] 委派 ViewModel 處理 Logcat
    LaunchedEffect(droneState.isLogcatEnabled) {
        viewModel.toggleLogcat(droneState)
    }

    // 7. [v1.2.86] RadarHUD 智慧縮放判定 (10Hz)
    LaunchedEffect(droneState.radarZoomMode, droneState.posX, droneState.posZ) {
        while(true) {
            viewModel.updateRadarScale(droneState)
            delay(100)
        }
    }

    // 8. [v1.3.5] 歡迎導覽與選單聯動：在介紹工具列時自動展開選單
    LaunchedEffect(viewModel.welcomeStep, droneState.showTutorial) {
        if (droneState.showTutorial) {
            when (viewModel.welcomeStep) {
                1 -> droneState.isMenuExpanded = true  // 步驟 1 (工具列)：自動展開
                2 -> droneState.isMenuExpanded = false // 步驟 2 (雷達)：自動收起
            }
        } else {
            // 教學關閉時確保選單縮回
            if (droneState.isMenuExpanded) droneState.isMenuExpanded = false
        }
    }

    // 6. [v1.2.82] 智慧姿態輔助：進入區域自動縮回選單
    val horizontalDist = sqrt(droneState.posX.pow(2) + (droneState.posZ + 6f).pow(2))
    val currentInZoomZone = horizontalDist > 10.0f && droneState.cameraMode != "FPV 視角" && droneState.cameraMode != "跟隨視角"
    
    LaunchedEffect(currentInZoomZone) {
        if (currentInZoomZone && !droneState.lastInZoomZone) {
            droneState.isMenuExpanded = false
        }
        droneState.lastInZoomZone = currentInZoomZone
    }

    // 8. [v1.3.5] 歡迎導覽與選單聯動：在介紹工具列時自動展開選單
    LaunchedEffect(viewModel.welcomeStep, droneState.showTutorial) {
        if (droneState.showTutorial) {
            when (viewModel.welcomeStep) {
                1 -> droneState.isMenuExpanded = true  // 步驟 1 (工具列)：自動展開
                2 -> droneState.isMenuExpanded = false // 步驟 2 (雷達)：自動收起
            }
        } else {
            // 教學關閉時確保選單縮回
            if (droneState.isMenuExpanded) droneState.isMenuExpanded = false
        }
    }

    MaterialTheme(colorScheme = darkColorScheme()) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 層級 0: 渲染主體
            AndroidView(
                modifier = Modifier.fillMaxSize().zIndex(0f),
                factory = { ctx -> GLSurfaceView(ctx).apply { setEGLContextClientVersion(2); setRenderer(renderer); isFocusable = true; isFocusableInTouchMode = true; requestFocus() } },
                update = {
                    // [v1.2.85] 教學期間自動暫停保護
                    val isTutorialActive = droneState.showTutorial || droneState.showJoystickTutorial || droneState.showClimateTutorial
                    val isPaused = (droneState.showSettings && droneState.pauseInSettings) || droneState.isCollision || droneState.showUsbSelectionDialog || isTutorialActive

                    renderer.isPaused = isPaused
                    renderer.droneType = droneState.droneType
                    renderer.cameraMode = droneState.cameraMode
                    renderer.cameraTilt = droneState.cameraTilt
                    renderer.observerHeight = droneState.observerHeight
                    renderer.zoomFactor = droneState.zoomFactor
                    renderer.isMotorLocked = droneState.isMotorLocked
                    renderer.windLevel = droneState.windLevel
                    renderer.windDirection = droneState.windDirection
                    renderer.windVariation = droneState.windVariation.toFloat()
                    renderer.windDirVariation = droneState.windDirVariation.toFloat()
                    renderer.timeOfDay = droneState.timeOfDay
                    renderer.enableVerticalDraft = droneState.enableVerticalDraft
                    renderer.applyPhysicalSpecs = droneState.applyPhysicalSpecs
                    renderer.showShadow = droneState.showShadow
                    renderer.shadowIntensity = droneState.shadowIntensity
                    renderer.showObstacles = droneState.showObstacles
                    renderer.useHardcorePhysics = droneState.useHardcorePhysics
                    renderer.isSunSimEnabled = droneState.isSunSimEnabled
                    renderer.sunPosition = droneState.sunPosition
                    renderer.observerTilt = droneState.observerTilt
                    renderer.useSimplifiedMarkers = droneState.useSimplifiedMarkers
                    renderer.showSpecialTitle = droneState.showSpecialTitle
                    renderer.useFlightLimit = droneState.useFlightLimit
                    renderer.mainFOV = droneState.mainFOV
                    renderer.showGroundAnchor = droneState.showGroundAnchor
                    renderer.isThrottleHoldActive = droneState.isThrottleHoldActive 
                    renderer.useSmartObserver = droneState.useSmartObserver 
                    renderer.lastManualTouchTime = droneState.lastManualTouchTime // [v1.6.0] 傳遞手動觸發時間

                    usbSerialManager.updateVirtualJoystickState(
                        t = stickInputState.stickThrottle(droneState), y = stickInputState.stickYaw(droneState),
                        p = stickInputState.stickPitch(droneState), r = stickInputState.stickRoll(droneState)
                    )

                    if (isPaused) {
                        renderer.updateControls(0f, -1f, 0f, 0f)
                        soundManager.update(true, -1f, 0f, 0, true, 0f)
                    } else {
                        val sT = stickInputState.stickThrottle(droneState); val sY = stickInputState.stickYaw(droneState); val sP = stickInputState.stickPitch(droneState); val sR = stickInputState.stickRoll(droneState)
                        renderer.updateControls(sY, sT, sR, sP)
                        val d = sqrt(droneState.posX.toDouble().pow(2) + (droneState.altitude - 1.6f).toDouble().pow(2) + (droneState.posZ - (-6.0f)).toDouble().pow(2)).toFloat()
                        soundManager.update(droneState.isMotorLocked, sT, droneState.speed, droneState.windLevel, droneState.isMuted, d)
                    }
                }
            )

            // 層級 10: 飛行儀表與搖桿
            DroneHUD(
                state = droneState, stickState = stickInputState,
                isStatusVisible = isStatusVisible, tutorialTargets = tutorialTargets,
                onUpdateState = { action -> droneState.action() },
                modifier = Modifier.zIndex(10f),
                onToggleStatus = { isStatusVisible = !isStatusVisible },
                onUpdatePipRect = { rect -> renderer.pipRect = rect },
                onUpdateZoomPipRect = { rect -> renderer.zoomPipRect = rect }
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

            // 層級 11: 側邊導航儀表 (v1.4.0)
            SideNavInstruments(
                state = droneState,
                onUpdateState = { action -> 
                    droneState.action()
                    // 記錄手動操作時間，觸發手動覆蓋屏蔽
                    droneState.lastManualTouchTime = System.currentTimeMillis()
                },
                modifier = Modifier.zIndex(11f)
            )

            // 層級 20: 任務與主教學
            MissionManager.RenderOverlay(droneState, { action -> droneState.action() })
            
            if (droneState.showTutorial && !droneState.showSettings) {
                WelcomeTutorial(viewModel = viewModel, modifier = Modifier.zIndex(20f)) {
                    droneState.showTutorial = false
                    settingsManager.saveSettings(droneState)
                }
            }

            // 層級 30: 系統設定面板
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
                    onOpenNetworkSettings = { droneState.showNetworkSettingsDialog = true },
                    onSaveSettings = { settingsManager.saveSettings(droneState) },
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
                        droneState.showNetworkSettingsDialog = false
                        settingsManager.saveSettings(droneState)
                        
                        // 如果目前處於網路模式，立即套用變更
                        if (droneState.inputMode == 2) {
                            onUpdateInputMode(2) 
                        }
                    }
                )
            }

            // 層級 40: 設定內教學 (高於設定視窗)
            if (droneState.showJoystickTutorial) {
                JoystickSettingsTutorial({ droneState.showJoystickTutorial = false }, tutorialTargets, viewModel = viewModel, modifier = Modifier.zIndex(40f))
            }
            if (droneState.showClimateTutorial) {
                ClimateSettingsTutorial({ droneState.showClimateTutorial = false }, tutorialTargets, viewModel = viewModel, modifier = Modifier.zIndex(41f))
            }

            // 層級 50+: 彈窗與啟動圖
            if (droneState.isCollision) CollisionOverlay(modifier = Modifier.zIndex(50f), onReset = onResetFlight)
            if (showSplash) SplashScreen(modifier = Modifier.zIndex(60f)) { onCloseSplash() }
            if (droneState.showUpdateNotice) UpdateNoticeOverlay(modifier = Modifier.zIndex(70f), onClose = { droneState.showUpdateNotice = false; settingsManager.saveSettings(droneState) })

            // 層級 80: 故障排除 (AX12 專屬)
            if (droneState.showTroubleshootingHint) {
                val context = LocalContext.current
                TroubleshootingOverlay(
                    onOpenFactoryApp = {
                        val pkg = droneState.hardwareProfile?.factoryAppPackage
                        if (pkg != null) {
                            val intent = context.packageManager.getLaunchIntentForPackage(pkg)
                            if (intent != null) context.startActivity(intent)
                            else droneState.systemMessage = "尚未安裝該設備的原廠 App"
                        } else droneState.systemMessage = "此設備不支援原廠 App 快捷啟動"
                    },
                    onDismiss = { droneState.showTroubleshootingHint = false }
                )
            }

            // 層級 99 (Absolute): 全域警告通道
            SystemStatusOverlay(droneState, stickInputState)
            
            if (droneState.showUsbSelectionDialog) {
                UsbConnectionSelectionOverlay(
                    modifier = Modifier.zIndex(90f),
                    onSelectExternal = { droneState.showUsbSelectionDialog = false; droneState.inputMode = 0; usbSerialManager.stopAll(); settingsManager.saveSettings(droneState) },
                    onSelectInternal = { droneState.showUsbSelectionDialog = false; droneState.inputMode = 1; usbSerialManager.clearUserStop(); usbSerialManager.scanAndConnect(); settingsManager.saveSettings(droneState) },
                    onDismiss = { droneState.showUsbSelectionDialog = false }
                )
            }
        }
    }
}
