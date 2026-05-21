package com.horizon.caadronesimulator.ui.overlays

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.horizon.caadronesimulator.logic.DroneViewModel
import com.horizon.caadronesimulator.logic.UsbSerialManager
import com.horizon.caadronesimulator.logic.storage.ConfigurationStore
import com.horizon.caadronesimulator.mission.MissionManager
import com.horizon.caadronesimulator.model.DroneState
import com.horizon.caadronesimulator.model.StickInputState
import com.horizon.caadronesimulator.ui.settings.NetworkSettingsOverlay
import com.horizon.caadronesimulator.ui.settings.UnifiedSettingsScreen
import com.horizon.caadronesimulator.ui.theme.NikoTheme
import com.horizon.caadronesimulator.ui.tutorial.ClimateSettingsTutorial
import com.horizon.caadronesimulator.ui.tutorial.JoystickSettingsTutorial
import com.horizon.caadronesimulator.ui.tutorial.WelcomeTutorial

/**
 * [v1.7.6] 全域 UI 覆蓋層調度器 (Overlay Dispatcher)
 * 職責：將 MainAppScreen 中的所有彈窗、教學與提示層解耦，降低主畫面編譯壓力。
 */
@Composable
fun OverlayDispatcher(
    droneState: DroneState,
    stickInputState: StickInputState,
    usbSerialManager: com.horizon.caadronesimulator.logic.UsbSerialManager,
    configStore: ConfigurationStore,
    viewModel: DroneViewModel,
    showSplash: Boolean,
    tutorialTargets: Map<String, Rect>,
    onUpdateTutorialTargets: (String, Rect) -> Unit,
    onCloseSplash: () -> Unit,
    onResetFlight: () -> Unit,
    onRerollWind: () -> Unit,
    onRestoreDefaults: () -> Unit,
    onExportLog: () -> Unit,
    onUpdateBaudRate: (Int) -> Unit,
    onUpdateInputMode: (Int) -> Unit,
    onToggleNetworkConnection: (Boolean) -> Unit,
    onLanguageChange: (String) -> Unit = {},
    onThemeChange: (String) -> Unit = {}
) {
    val context = LocalContext.current

    // 1. 歡迎教學
    if (droneState.showTutorial && !droneState.showSettings) {
        WelcomeTutorial(viewModel = viewModel, modifier = Modifier.zIndex(20f)) {
            droneState.showTutorial = false
            configStore.saveSettings(droneState)
        }
    }

    // 2. 統一設定選單
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
            onLanguageChange = onLanguageChange,
            onThemeChange = onThemeChange,
            availablePorts = usbSerialManager.listAvailableSerialPorts(),
            onTargetPositioned = { name, rect -> onUpdateTutorialTargets(name, rect) }
        )
    }

    // 3. 網路設定彈窗
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
                    usbSerialManager.startReadingByPath("NETWORK")
                }
                onUpdateInputMode(2)
            }
        )
    }

    // 4. 專項教學
    if (droneState.showJoystickTutorial) {
        JoystickSettingsTutorial(onDismiss = { droneState.showJoystickTutorial = false }, targets = tutorialTargets, viewModel = viewModel, modifier = Modifier.zIndex(40f))
    }
    if (droneState.showClimateTutorial) {
        ClimateSettingsTutorial(onDismiss = { droneState.showClimateTutorial = false }, targets = tutorialTargets, viewModel = viewModel, modifier = Modifier.zIndex(41f))
    }

    // 5. 碰撞提示
    if (droneState.isCollision) CollisionOverlay(onReset = onResetFlight, modifier = Modifier.zIndex(50f))

    // 6. 啟動畫面與更新通知
    if (showSplash) SplashScreen(modifier = Modifier.zIndex(60f), onTimeout = { onCloseSplash() })
    if (droneState.showUpdateNotice) UpdateNoticeOverlay(onClose = { droneState.showUpdateNotice = false; configStore.saveSettings(droneState) }, modifier = Modifier.zIndex(70f))

    // 7. 遙控器設定引導與校準
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

    // 8. 故障排除提示
    if (droneState.showTroubleshootingHint) {
        TroubleshootingOverlay(
            onOpenFactoryApp = {
                val pkg = droneState.hardwareProfile?.factoryAppPackage
                if (pkg != null) {
                    val intent = context.packageManager.getLaunchIntentForPackage(pkg)
                    if (intent != null) context.startActivity(intent)
                    else droneState.systemMessage = "尚未安裝該硬體的原裝 App"
                } else droneState.systemMessage = "此設備不支援原裝 App 快速啟動"
            },
            onDismiss = { droneState.showTroubleshootingHint = false }
        )
    }

    // 9. 系統狀態監控條
    SystemStatusOverlay(droneState, stickInputState)

    // 10. 協議優化引導
    ProtocolOptimizationOverlay(
        state = droneState,
        onApply = { proto, baud ->
            com.horizon.caadronesimulator.logic.ProHardwareBridge.internalCommManager.setLockedProtocol(proto)
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

    // 11. 任務評測層
    MissionManager.RenderOverlay(state = droneState, onUpdateState = { action -> droneState.action() })

    // 12. 攝影機切換進度條
    if (droneState.isSwitchingMode) {
        val themeColors = NikoTheme.colors
        Box(modifier = Modifier.fillMaxSize().background(themeColors.background.copy(alpha = 0.5f)).clickable { /* 攔截點擊 */ }, contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(
                    progress = { droneState.switchProgress },
                    color = themeColors.primary,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(54.dp)
                )
                Spacer(Modifier.height(12.dp))
                Text(droneState.switchMessage, color = themeColors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}
