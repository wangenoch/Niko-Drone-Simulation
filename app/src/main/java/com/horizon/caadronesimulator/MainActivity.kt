package com.horizon.caadronesimulator

import android.hardware.input.InputManager
import android.os.Build
import android.os.Bundle
import android.view.InputDevice
import android.view.MotionEvent
import android.Manifest
import android.content.pm.PackageManager
import com.horizon.caadronesimulator.util.SystemUiHelper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.compose.runtime.*
import com.horizon.caadronesimulator.audio.DroneSoundManager
import com.horizon.caadronesimulator.model.DroneState
import com.horizon.caadronesimulator.logic.storage.ConfigurationStore
import com.horizon.caadronesimulator.render.DroneSimulationRenderer
import com.horizon.caadronesimulator.logic.UsbSerialManager
import com.horizon.caadronesimulator.logic.InputCoordinator
import com.horizon.caadronesimulator.util.LogExporter
import com.horizon.caadronesimulator.ui.MainAppScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * [v1.5.9] 模擬器主入口 - 效能極致優化版
 * 職責：管理組件生命週期與高頻渲染回調。
 */
class MainActivity : ComponentActivity() {
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { isGranted: Boolean ->
        if (isGranted) saveDiagnosticLog()
        else droneState.systemMessage = "Permission required for export"
    }

    private val droneState = DroneState.getInstance()
    private val viewModel: com.horizon.caadronesimulator.logic.DroneViewModel by viewModels()
    private var stickInputState = com.horizon.caadronesimulator.model.StickInputState() 
    private var showSplash by mutableStateOf(value = true)
    private lateinit var renderer: DroneSimulationRenderer
    private lateinit var soundManager: DroneSoundManager
    private lateinit var configStore: ConfigurationStore
    private lateinit var usbSerialManager: UsbSerialManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        configStore = ConfigurationStore(this)
        configStore.loadSettings(droneState)
        updateSystemUI()

        usbSerialManager = UsbSerialManager(this, 
            onStatusUpdate = { connected, message -> 
                droneState.usbSerialConnected = connected
                if (message.isNotEmpty()) droneState.systemMessage = message
                if (connected) droneState.wasInternalSuccess = true 
            },
            onDataReceived = { lsv, lsh, rsv, rsh ->
                if (droneState.inputMode == 0) return@UsbSerialManager
                stickInputState.updateRaw(lsv, lsh, rsv, rsh)
                stickInputState.serialByteCount++
            },
            onRawChannelsReceived = { channels ->
                InputCoordinator.processSerialInput(channels, droneState, stickInputState, usbSerialManager)
            },
            onDiagnosticUpdate = { status, path, log, extra ->
                stickInputState.packetsPerSecond = (extra["pps"] as? Int) ?: stickInputState.packetsPerSecond
                stickInputState.isSignalActive = (extra["is_signal_active"] as? Boolean) ?: stickInputState.isSignalActive
                droneState.diagnosticLog = log; if (path != "%SAME%") droneState.activeSerialPath = path
                (extra["linkType"] as? String)?.let { droneState.linkType = it }
                (extra["baud"] as? Int)?.let { droneState.baudRate = it }
                (extra["protocol"] as? String)?.let { droneState.detectedProtocol = it }
                (extra["conflict"] as? Boolean)?.let { droneState.isSerialConflict = it }
                (extra["raw_bytes_count"] as? Int)?.let { droneState.rawBytesCount = it }
                (extra["buffer_usage"] as? String)?.let { droneState.bufferUsage = it }
                (extra["jitter"] as? String)?.let { droneState.jitter = it }
                (extra["stability"] as? String)?.let { droneState.stability = it }
            },
            onProtocolDetected = { p -> droneState.lockedProtocol = p },
            onHandshakeStatus = { active, msg -> 
                droneState.isHandshaking = active
                if (msg == "TIMEOUT_60S") { if (!droneState.showTroubleshootingHint && !droneState.usbSerialConnected) droneState.showTroubleshootingHint = true }
                else if (msg.isNotEmpty()) droneState.systemMessage = msg
            },
            onConnectionStatusUpdate = { status -> droneState.connectionStatus = status },
            onIdentityVerified = {
                if (!droneState.isHardwareVerified) {
                    droneState.isHardwareVerified = true; droneState.isProbing = false; droneState.probeAttempts = 0
                    droneState.systemMessage = "✅ 已認證 RadioMaster 硬體"; configStore.saveSettings(droneState)
                }
            }
        )
        usbSerialManager.setBaudRate(droneState.baudRate)
        usbSerialManager.register(this)
        soundManager = DroneSoundManager(); soundManager.start()

        // [優化] 零分配回調：減少每秒 60 次的對象建立
        renderer = DroneSimulationRenderer { alt, x, z, yaw, pitch, roll, speed, isImpact, volt, perc, titlePos, _, _, _, _ ->
            droneState.specialTitleScreenPos = titlePos 
            
            // 直接從物理引擎讀取當前幀結果，不再進行中轉對象封裝
            com.horizon.caadronesimulator.logic.PhysicsEngine.stepResult?.let { res ->
                droneState.motorRpmFactor = res.motorRpm
                renderer.motorRpmFactor = res.motorRpm
                
                // 同步至全域狀態與任務系統
                viewModel.syncFlightData(
                    droneState, alt, x, z, yaw, pitch, roll, speed, isImpact, volt, perc, res
                )
            }
        }

        setContent {
            MainAppScreen(
                droneState = droneState, stickInputState = stickInputState, renderer = renderer, 
                soundManager = soundManager, usbSerialManager = usbSerialManager, configStore = configStore,
                viewModel = viewModel, showSplash = showSplash, 
                onCloseSplash = { showSplash = false }, 
                onResetFlight = { viewModel.resetFlight(droneState, renderer) }, 
                onRerollWind = { renderer.rerollWindDirection() },
                onRestoreDefaults = { 
                    // [v1.6.1] 恢復原廠設定：中央協調層
                    viewModel.restoreFactorySettings(droneState, renderer)
                    configStore.wipeAllSettings()
                    configStore.saveSettings(droneState) // 抹除後立刻寫入當前預設值
                    updateSystemUI()
                    usbSerialManager.stopAll()
                    droneState.inputMode = -1 // 回歸手選模式
                },
                onExportLog = { saveDiagnosticLog() }, 
                onUpdateBaudRate = { b -> droneState.baudRate = b; usbSerialManager.setBaudRate(b); configStore.saveSettings(droneState) },
                onUpdateInputMode = { m ->
                    if (droneState.isInteractionLocked) return@MainAppScreen
                    if (droneState.inputMode != m) {
                        droneState.isInteractionLocked = true; droneState.inputMode = m; configStore.saveSettings(droneState)
                        
                        // [v1.5.9] 手動切換邏輯：使用者手動點擊模式後，解除 USB 主權鎖定
                        droneState.isUsbStickyActive = false
                        
                        usbSerialManager.stopAll()
                        if (m == -1 || m == 2) { droneState.isArmSafetyPassed = true; droneState.isHoldSafetyPassed = true; droneState.isThrottleHoldActive = false } 
                        else { droneState.isArmSafetyPassed = false; droneState.isHoldSafetyPassed = false }
                        if (m == 1) lifecycleScope.launch { delay(300); usbSerialManager.scanAndConnect() }
                        lifecycleScope.launch { delay(1000); droneState.isInteractionLocked = false }
                    }
                },
                onToggleNetworkConnection = { active ->
                    if (active) { usbSerialManager.setLockedPath("NETWORK"); usbSerialManager.scanAndConnect() } 
                    else { usbSerialManager.stopAll() }
                },
                onUpdateSystemUI = { updateSystemUI() }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        updateSystemUI()
        
        // [v1.5.9] 冷啟動自動感知：檢查當前是否已插入 AX12 或 HID 設備
        lifecycleScope.launch {
            delay(300)
            val usbManager = getSystemService(USB_SERVICE) as android.hardware.usb.UsbManager
            usbManager.deviceList.values.any { dev ->
                if (deviceMatchesExpertOrHid(dev)) {
                    if (!droneState.isUsbStickyActive) {
                        droneState.inputMode = 0
                        droneState.isUsbStickyActive = true
                        usbSerialManager.stopAll()
                        droneState.systemMessage = "🔗 偵測到已連接的硬體，已自動設為外接模式"
                    }
                    true
                } else false
            }
            
            delay(200) 
            val inputManager = getSystemService(INPUT_SERVICE) as InputManager
            val hidAvailable = inputManager.inputDeviceIds.any { id ->
                val dev = inputManager.getInputDevice(id)
                dev != null && (dev.sources and InputDevice.SOURCE_JOYSTICK) != 0
            }
            if (droneState.inputMode == 1 && !droneState.usbSerialConnected) usbSerialManager.scanAndConnect()
            else if (droneState.isAutoConnectEnabled && !droneState.usbSerialConnected) usbSerialManager.scanAndConnect()
            if (hidAvailable && droneState.inputMode == -1) droneState.inputMode = 0
            else if (!hidAvailable && droneState.inputMode == 0) {
                droneState.systemMessage = "未偵測到外接手把，已切換至虛擬搖桿"
                droneState.inputMode = -1; droneState.showVirtualJoysticks = true 
            }
            if (droneState.inputMode == -1 || droneState.inputMode == 2) { droneState.isArmSafetyPassed = true; droneState.isHoldSafetyPassed = true }
        }
    }

    override fun onStop() { super.onStop(); soundManager.stop(); usbSerialManager.stopAll() }
    override fun onWindowFocusChanged(hasFocus: Boolean) { super.onWindowFocusChanged(hasFocus); if (hasFocus) updateSystemUI() }
    private fun updateSystemUI() { SystemUiHelper.toggleImmersiveMode(window, droneState.hideStatusBar) }

    private fun saveDiagnosticLog() {
        if (!droneState.isLogcatEnabled) { droneState.systemMessage = "📋 請先開啟 [即時監測 Logcat] 以收集診斷數據"; return }
        val physicalLog = usbSerialManager.getFullLog()
        if (physicalLog.isEmpty()) { droneState.systemMessage = "⏳ 正在收集初始數據，請操作搖桿幾秒後再試"; return }
        LogExporter.exportDiagnosticLog(this, droneState, physicalLog, onSuccess = { droneState.systemMessage = it }, onError = { droneState.systemMessage = it })
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        return InputCoordinator.handleJoystickEvent(event, droneState, stickInputState, usbSerialManager)
    }

    private fun deviceMatchesExpertOrHid(device: android.hardware.usb.UsbDevice): Boolean {
        if (device.vendorId == 0x2E3C) return true
        for (i in 0 until device.interfaceCount) {
            if (device.getInterface(i).interfaceClass == 3) return true
        }
        return false
    }

    override fun onDestroy() { 
        super.onDestroy()
        if (::soundManager.isInitialized) soundManager.stop()
        if (::usbSerialManager.isInitialized) usbSerialManager.unregister(this) 
    }
}
