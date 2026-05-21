package com.horizon.caadronesimulator

import android.os.Bundle
import android.view.MotionEvent
import com.horizon.caadronesimulator.util.SystemUiHelper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.compose.runtime.*
import com.horizon.caadronesimulator.model.DroneState
import com.horizon.caadronesimulator.logic.storage.ConfigurationStore
import com.horizon.caadronesimulator.render.DroneSimulationRenderer
import com.horizon.caadronesimulator.logic.UsbSerialManager
import com.horizon.caadronesimulator.ui.MainAppScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import com.horizon.caadronesimulator.ui.theme.NikoTheme

/**
 * [v1.7.6] 模擬器主入口 - 效能極致優化版
 * 職責：管理組件生命週期與高頻渲染回調，作為 Pro (專業) 與 Store (合規) 鏈路的總掛載點。
 */
import java.util.Locale
import android.content.res.Configuration

class MainActivity : androidx.activity.ComponentActivity() {
    
    // --- 系統權限組件 ---
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { isGranted: Boolean ->
        if (isGranted) saveDiagnosticLog()
        else droneState.systemMessage = "PERM_REQUIRED"
    }

    // --- 核心狀態與邏輯組件 ---
    private val droneState = DroneState.getInstance()
    private val viewModel: com.horizon.caadronesimulator.logic.DroneViewModel by viewModels()
    private var stickInputState = com.horizon.caadronesimulator.model.StickInputState() 
    private var showSplash by mutableStateOf(value = true)
    
    // --- 渲染與通訊引擎 ---
    private lateinit var renderer: DroneSimulationRenderer
    private lateinit var soundManager: com.horizon.caadronesimulator.audio.DroneSoundManager
    private lateinit var configStore: ConfigurationStore
    private lateinit var usbSerialManager: com.horizon.caadronesimulator.logic.UsbSerialManager

    private fun updateLocale(lang: String) {
        val locale = Locale(lang)
        Locale.setDefault(locale)
        val config = Configuration(resources.configuration)
        config.setLocale(locale)
        resources.updateConfiguration(config, resources.displayMetrics)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 1. 系統 UI 與存儲初始化
        WindowCompat.setDecorFitsSystemWindows(window, false)
        configStore = ConfigurationStore(this)
        configStore.loadSettings(droneState)
        
        // [v1.7.6] 套用手動選擇的語言
        updateLocale(droneState.appLanguage)

        updateSystemUI()

        // 2. [v1.7.6] Store (上架合規) 鏈路初始化：負責 USB OTG 與 網路通訊
        usbSerialManager = com.horizon.caadronesimulator.logic.UsbSerialManager(this, droneState, 
            onRawChannelsReceived = { channels ->
                com.horizon.caadronesimulator.logic.InputCoordinator.processSerialInput(channels, droneState, stickInputState, com.horizon.caadronesimulator.logic.ProHardwareBridge.internalCommManager)
            },
            onConnectionStatusUpdate = { status -> droneState.connectionStatus = status }
        )

        // 3. [v1.7.6] Pro (專業自用) 鏈路初始化：交由專屬 Bridge 管理內置串口與自動感知
        com.horizon.caadronesimulator.logic.ProHardwareBridge.initialize(
            this, droneState, stickInputState, configStore
        )

        // 4. 音效系統啟動
        soundManager = com.horizon.caadronesimulator.audio.DroneSoundManager(); soundManager.start()

        // 5. 3D 渲染引擎配置
        renderer = DroneSimulationRenderer { alt, x, z, yaw, pitch, roll, speed, isImpact, volt, perc, _, _, _, _, _ ->
            // 每幀物理結果對接：由 Renderer 驅動降頻數據同步
            com.horizon.caadronesimulator.logic.PhysicsEngine.stepResult?.let { res ->
                droneState.motorRpmFactor = res.motorRpm
                viewModel.syncFlightData(
                    droneState, alt, x, z, yaw, pitch, roll, speed, isImpact, volt, perc, res
                )
            }
        }
        // 視覺投影位置更新 (分離物理數據以防止無限碰撞)
        renderer.onTitlePosUpdate = { pos -> droneState.specialTitleScreenPos = pos }

        // 6. UI 視圖層載入
        setContent {
            NikoTheme(themeId = droneState.appTheme) {
                MainAppScreen(
                    droneState = droneState, 
                    stickInputState = stickInputState, 
                    renderer = renderer, 
                    soundManager = soundManager, 
                    usbSerialManager = usbSerialManager, // 傳遞 Store 鏈路管理器
                    configStore = configStore,
                    viewModel = viewModel, 
                    showSplash = showSplash, 
                    onCloseSplash = { showSplash = false }, 
                    onResetFlight = { viewModel.resetFlight(droneState, renderer) }, 
                    onRerollWind = { renderer.rerollWindDirection() },
                    onRestoreDefaults = { 
                        viewModel.restoreFactorySettings(droneState, renderer)
                        configStore.wipeAllSettings()
                        configStore.saveSettings(droneState) 
                        updateSystemUI()
                        com.horizon.caadronesimulator.logic.ProHardwareBridge.onStop()
                        droneState.inputMode = -1 
                    },
                    onExportLog = { saveDiagnosticLog() }, 
                    onUpdateBaudRate = { b -> 
                        droneState.baudRate = b
                        com.horizon.caadronesimulator.logic.ProHardwareBridge.internalCommManager.setBaudRate(b)
                        configStore.saveSettings(droneState) 
                    },
                    onUpdateInputMode = { m ->
                        // 手動輸入模式切換邏輯
                        if (droneState.isInteractionLocked) return@MainAppScreen
                        if (droneState.inputMode != m) {
                            droneState.isInteractionLocked = true; droneState.inputMode = m; configStore.saveSettings(droneState)
                            droneState.isUsbStickyActive = false
                            
                            com.horizon.caadronesimulator.logic.ProHardwareBridge.onStop()
                            if (m == -1 || m == 2) { droneState.isArmSafetyPassed = true; droneState.isHoldSafetyPassed = true; droneState.isThrottleHoldActive = false } 
                            else { droneState.isArmSafetyPassed = false; droneState.isHoldSafetyPassed = false }
                            if (m == 1) lifecycleScope.launch { delay(300); com.horizon.caadronesimulator.logic.ProHardwareBridge.internalCommManager.scanAndConnect() }
                            lifecycleScope.launch { delay(1000); droneState.isInteractionLocked = false }
                        }
                    },
                    onToggleNetworkConnection = { active ->
                        // 網路通訊鏈路開關
                        if (active) { 
                            com.horizon.caadronesimulator.logic.ProHardwareBridge.internalCommManager.setLockedPath("NETWORK")
                            com.horizon.caadronesimulator.logic.ProHardwareBridge.internalCommManager.scanAndConnect() 
                        } else { 
                            com.horizon.caadronesimulator.logic.ProHardwareBridge.onStop()
                        }
                    },
                    onUpdateSystemUI = { updateSystemUI() },
                    onLanguageChange = { lang ->
                        // 1. 識別是否正處於預設標題 (以便自動轉換)
                        val oldDefault = com.horizon.caadronesimulator.model.AppConfig.getDefaultSpecialTitle(droneState.appLanguage)
                        val isUsingDefault = droneState.currentTitleText == oldDefault || droneState.currentTitleText.isEmpty()
                        
                        // 2. 切換語言
                        droneState.appLanguage = lang
                        
                        // 3. 如果原本是預設標題，自動切換至新語言的預設值
                        if (isUsingDefault) {
                            droneState.currentTitleText = com.horizon.caadronesimulator.model.AppConfig.getDefaultSpecialTitle(lang)
                        }

                        // 4. [同步寫入磁碟] 確保重啟後 loadSettings 讀到的是最新語系
                        configStore.saveSettings(droneState)
                        
                        // 5. [狀態清理] 清除歷史日誌，防止中文日誌殘留在 Singleton 中
                        droneState.diagnosticLog = ""

                        recreate()
                    },
                    onThemeChange = { theme ->
                        droneState.appTheme = theme
                        configStore.saveSettings(droneState)
                        // 主題切換不一定需要 recreate，因為 Compose 會自動觀察 state
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateSystemUI()
        // 恢復硬體自動感知決策
        com.horizon.caadronesimulator.logic.ProHardwareBridge.onResume()
    }

    override fun onStop() { 
        super.onStop()
        soundManager.stop()
        // 停止所有背景通訊以節電
        com.horizon.caadronesimulator.logic.ProHardwareBridge.onStop() 
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) { 
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) updateSystemUI() 
    }

    private fun updateSystemUI() { 
        SystemUiHelper.toggleImmersiveMode(window, droneState.hideStatusBar) 
    }

    /** 執行 Pro 版診斷日誌匯出 (含權限請求) */
    private fun saveDiagnosticLog() {
        com.horizon.caadronesimulator.logic.ProHardwareBridge.internalCommManager.tryExportReport(this) { permission ->
            requestPermissionLauncher.launch(permission)
        }
    }

    /** 處理來自標準 HID 手把的搖桿事件 */
    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        return com.horizon.caadronesimulator.logic.InputCoordinator.handleJoystickEvent(
            event, droneState, stickInputState, com.horizon.caadronesimulator.logic.ProHardwareBridge.internalCommManager
        )
    }

    override fun onDestroy() { 
        super.onDestroy()
        // 徹底銷毀 Pro 橋接引用，防止記憶體洩漏
        com.horizon.caadronesimulator.logic.ProHardwareBridge.onDestroy(this)
    }
}
