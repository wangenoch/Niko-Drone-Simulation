package com.horizon.caadronesimulator

// Android 系統與硬體支援
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.hardware.input.InputManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.InputDevice
import android.view.MotionEvent
import android.Manifest
import android.content.pm.PackageManager
import java.io.File
import kotlin.math.*

// AndroidX 與 Lifecycle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope

// Compose 狀態管理
import androidx.compose.runtime.*

// 內部邏輯與數據模型
import com.horizon.caadronesimulator.audio.DroneSoundManager
import com.horizon.caadronesimulator.model.DroneState
import com.horizon.caadronesimulator.model.SettingsManager
import com.horizon.caadronesimulator.render.DroneSimulationRenderer
import com.horizon.caadronesimulator.mission.MissionManager
import com.horizon.caadronesimulator.logic.UsbSerialManager
import com.horizon.caadronesimulator.logic.PhysicsEngine

// UI 統籌層
import com.horizon.caadronesimulator.ui.MainAppScreen

// 協程
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * [v1.2.86] 模擬器主入口
 */
class MainActivity : ComponentActivity() {
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { isGranted: Boolean ->
        if (isGranted) saveDiagnosticLog()
        else droneState.systemMessage = "Permission required for export"
    }

    private val droneState = DroneState()
    private val viewModel: com.horizon.caadronesimulator.logic.DroneViewModel by viewModels()
    private var stickInputState = com.horizon.caadronesimulator.model.StickInputState() 
    private var showSplash by mutableStateOf(value = true)
    private lateinit var renderer: DroneSimulationRenderer
    private lateinit var soundManager: DroneSoundManager
    private lateinit var settingsManager: SettingsManager
    private lateinit var usbSerialManager: UsbSerialManager
    private lateinit var networkStreamManager: com.horizon.caadronesimulator.logic.NetworkStreamManager
    private val uiHandler = Handler(Looper.getMainLooper())
    private val axisSnapshots = mutableMapOf<Int, Float>()
    private var lastResetTime = 0L
    private var lastStabilityCheckTime = 0L
    private var lastSerialUpdateTime = 0L
    private var lastUiUpdateTime = 0L
    private var lastUsbDialogTime = 0L
    private var isProcessingExternal = false 

    private val usbEventReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val usbManager = context.getSystemService(USB_SERVICE) as UsbManager
            val device = IntentCompat.getParcelableExtra(intent, UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
            when (intent.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    val now = System.currentTimeMillis()
                    if (now - lastUsbDialogTime < 3000) return
                    if (!UsbSerialManager.isRelevantDevice(device)) return
                    lastUsbDialogTime = now
                    
                    device?.let { dev ->
                        if (!usbManager.hasPermission(dev)) {
                            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                android.app.PendingIntent.FLAG_MUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
                            } else {
                                android.app.PendingIntent.FLAG_UPDATE_CURRENT
                            }
                            val intentAction = "com.horizon.caadronesimulator.USB_PERMISSION"
                            val permissionIntent = android.app.PendingIntent.getBroadcast(context, 0, Intent(intentAction), flags)
                            usbManager.requestPermission(dev, permissionIntent)
                        }
                    }
                    settingsManager.loadSettings(droneState)
                    droneState.showUsbSelectionDialog = true
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    settingsManager.loadSettings(droneState)
                    if (droneState.inputMode == 0 && isHidJoystick(device)) handleControllerLoss()
                }
            }
        }
    }

    private fun isHidJoystick(device: UsbDevice?): Boolean {
        if (device == null) return false
        for (i in 0 until device.interfaceCount) {
            if (device.getInterface(i).interfaceClass == 3) return true
        }
        return false
    }

    private fun hasAnyHidJoystick(): Boolean {
        val inputManager = getSystemService(INPUT_SERVICE) as InputManager
        return inputManager.inputDeviceIds.any { id ->
            val dev = inputManager.getInputDevice(id)
            dev != null && (dev.sources and InputDevice.SOURCE_JOYSTICK) != 0
        }
    }

    private fun handleControllerLoss() {
        if (droneState.isHardwareController) {
            droneState.inputMode = 1; droneState.systemMessage = "外接手把已中斷，自動切換回內置系統"; usbSerialManager.scanAndConnect()
        } else {
            droneState.inputMode = -1; droneState.showVirtualJoysticks = true; droneState.systemMessage = "外接手把已中斷，已開啟虛擬搖桿"
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemUI()
        lifecycleScope.launch {
            delay(500) 
            val hidAvailable = hasAnyHidJoystick()
            
            if (droneState.isProbing && !droneState.isHardwareVerified && droneState.probeAttempts < 3) {
                droneState.probeAttempts++; droneState.systemMessage = "⏳ 正在驗證 RadioMaster 硬體 (${droneState.probeAttempts}/3)..."
                settingsManager.saveSettings(droneState); usbSerialManager.scanAndConnect()
                lifecycleScope.launch {
                    delay(10000)
                    if (!droneState.isHardwareVerified && droneState.isProbing) {
                        droneState.isProbing = false
                        if (droneState.probeAttempts >= 3) droneState.systemMessage = "⚠️ 已嘗試偵測 3 次失敗，切換至靜默模式"
                        else droneState.systemMessage = "驗證超時，本次將以手機模式執行"
                        usbSerialManager.stopAll()
                    }
                }
            } else if (droneState.inputMode == 1 && droneState.wasInternalSuccess && !usbSerialManager.isUserStoppedManually()) {
                // [v1.3.6] 簡化生命週期恢復：僅在斷線時發起一次基礎掃描，後續由 UsbSerialManager 的 RX 監聽接管自癒
                if (!droneState.usbSerialConnected) {
                    usbSerialManager.scanAndConnect()
                }
            } else if (hidAvailable) {
                droneState.inputMode = 0
            } else {
                if (droneState.inputMode == 0) { 
                    droneState.systemMessage = "未偵測到外接手把，已切換至虛擬搖桿"; droneState.inputMode = -1; droneState.showVirtualJoysticks = true 
                } else if (droneState.inputMode == -1) {
                    if (!droneState.showVirtualJoysticks) droneState.showVirtualJoysticks = true
                }
            }
        }
    }

    override fun onStop() { super.onStop(); try { unregisterReceiver(usbEventReceiver) } catch (_: Exception) {}; usbSerialManager.stopAll(); soundManager.stop() }

    override fun onWindowFocusChanged(hasFocus: Boolean) { super.onWindowFocusChanged(hasFocus); if (hasFocus) hideSystemUI() }

    private fun updateSystemUI() { if (droneState.showStatusBar) showSystemUI() else hideSystemUI() }

    private fun hideSystemUI() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    private fun showSystemUI() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.show(WindowInsetsCompat.Type.systemBars())
    }

    private fun saveDiagnosticLog() {
        if (!droneState.isLogcatEnabled) { droneState.systemMessage = "📋 請先開啟 [即時監測 Logcat] 以收集診斷數據"; return }
        droneState.localSettingsMessage = "正在匯出日誌..."
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val permission = Manifest.permission.WRITE_EXTERNAL_STORAGE
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) { requestPermissionLauncher.launch(permission); return }
        }
        try {
            val physicalLog = usbSerialManager.getFullLog()
            if (physicalLog.isEmpty()) { droneState.systemMessage = "⏳ 正在收集初始數據，請操作搖桿幾秒後再試"; return }
            val combinedLog = buildString {
                append("=== NIKO DRONE SIMULATOR DIAGNOSTIC REPORT ===\n")
                append("Device: ${Build.MANUFACTURER} ${Build.MODEL}\n")
                append("Profile: ${droneState.hardwareProfile?.id ?: "Generic"}\n\n")
                append("=== SYSTEM LOG (LOGCAT) ===\n")
                append(droneState.logcatContent.takeLast(5000))
                append("\n\n=== PHYSICAL STATE SNAPSHOTS ===\n")
                append(physicalLog)
            }
            val fileName = "Niko_FullDiag_${System.currentTimeMillis()}.txt"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                    put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
                }
                contentResolver.insert(android.provider.MediaStore.Files.getContentUri("external"), contentValues)?.let { uri ->
                    contentResolver.openOutputStream(uri)?.use { it.write(combinedLog.toByteArray()) }
                    droneState.localSettingsMessage = "✅ 日誌已儲存至 Download 資料夾"
                }
            } else {
                val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                if (!downloadsDir.exists()) downloadsDir.mkdirs()
                val file = File(downloadsDir, fileName)
                java.io.FileOutputStream(file).use { it.write(combinedLog.toByteArray()) }
                android.media.MediaScannerConnection.scanFile(this, arrayOf(file.absolutePath), null) { _, _ -> }
                droneState.localSettingsMessage = "✅ 日誌已儲存至 Download 資料夾"
            }
        } catch (e: Exception) { droneState.localSettingsMessage = "❌ 匯出失敗: ${e.message}" }
    }

    private fun resetFlight() {
        lastResetTime = System.currentTimeMillis()
        renderer.resetFlight()
        
        // 1. 基礎物理與狀態還原
        droneState.isCollision = false
        droneState.isMotorLocked = true
        droneState.flightPath = emptyList()
        droneState.observerHeight = 6.0f // 恢復預設站位高度
        
        // 2. [v1.4.2] 任務系統還原
        droneState.spotTimerSuccess = false
        droneState.spotTimerSeconds = 5.0f
        droneState.spotTimerInZone = false
        droneState.spotTimerStable = false
        droneState.spotTimerMessage = if (droneState.isSpotTimerEnabled) "請重新起飛" else null
        
        // 3. [v1.4.2] 視覺參數自動優化校準
        com.horizon.caadronesimulator.logic.ViewportOptimizer.applyOptimization(droneState)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        settingsManager = SettingsManager(this)
        
        // [v1.2.86] 關鍵修正：先載入設定，再執行硬體識別，確保 isHardwareVerified 標記生效
        val isFirstLaunch = settingsManager.isFirstLaunch()
        settingsManager.loadSettings(droneState)
        
        // [v1.2.95] 初次啟動設備自動識別：手機/平板自動開啟虛擬搖桿以確保可操作性
        if (isFirstLaunch) {
            val hasTouchScreen = packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
            if (hasTouchScreen) {
                droneState.showVirtualJoysticks = true 
                settingsManager.saveSettings(droneState)
            }
        }

        updateSystemUI()

        val hintProfile = com.horizon.caadronesimulator.logic.HardwareRegistry.detectHardwareHint()
        val finalProfile = when {
            droneState.isHardwareVerified -> hintProfile ?: com.horizon.caadronesimulator.logic.HardwareRegistry.detectHardware()
            hintProfile != null && droneState.probeAttempts < 3 -> { droneState.isProbing = true; hintProfile }
            else -> com.horizon.caadronesimulator.logic.HardwareRegistry.getGenericProfile()
        }
        droneState.hardwareProfile = finalProfile
        droneState.isHardwareController = finalProfile.isProfessionalRemote
        val filter = IntentFilter().apply { addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED); addAction(UsbManager.ACTION_USB_DEVICE_DETACHED) }
        registerReceiver(usbEventReceiver, filter)

        usbSerialManager = UsbSerialManager(this, 
            onStatusUpdate = { connected, message -> uiHandler.post { droneState.usbSerialConnected = connected; droneState.systemMessage = message; if (connected) { droneState.inputMode = 1; droneState.wasInternalSuccess = true } } },
            onDataReceived = { lsv, lsh, rsv, rsh ->
                if (isProcessingExternal || droneState.inputMode == 0) return@UsbSerialManager
                uiHandler.post { stickInputState.updateRaw(lsv, lsh, rsv, rsh) }
                stickInputState.serialByteCount++
                val now = System.currentTimeMillis()
                if (now - lastSerialUpdateTime > 16 || droneState.setupWizardStep > 0) {
                    lastSerialUpdateTime = now
                    uiHandler.post { if (!droneState.isCollision && !droneState.controllerConnected) droneState.controllerConnected = true }
                }
            },
            onRawChannelsReceived = { channels ->
                if (isProcessingExternal) return@UsbSerialManager
                // [v1.2.86] 總清理：移除 ProtocolStandardizer，直接使用驅動標定數據
                stickInputState.rawChannels = channels
                uiHandler.post {
                    if (droneState.isCalibrating) {
                        fun updateCalib(m: com.horizon.caadronesimulator.model.ChannelMapping, axisIdx: Int, isSerial: Boolean): com.horizon.caadronesimulator.model.ChannelMapping {
                            val targetAxis = if (isSerial) axisIdx + 101 else axisIdx
                            if (m.axis != targetAxis) return m
                            val v = channels.getOrNull(axisIdx) ?: 0f
                            return when(droneState.calibrationStep) { 1 -> m.copy(center = v, min = v, max = v); 2 -> m.copy(min = min(v, m.min), max = max(v, m.max)); else -> m }
                        }
                        droneState.mappingLY = updateCalib(droneState.mappingLY, droneState.mappingLY.axis - 101, true)
                        droneState.mappingLX = updateCalib(droneState.mappingLX, droneState.mappingLX.axis - 101, true)
                        droneState.mappingRY = updateCalib(droneState.mappingRY, droneState.mappingRY.axis - 101, true)
                        droneState.mappingRX = updateCalib(droneState.mappingRX, droneState.mappingRX.axis - 101, true)
                    }
                    if (droneState.inputMode == 1 && droneState.isAutoBinding != null) {
                        var trig = -1; var mv = 0f; channels.forEachIndexed { i, v -> if (abs(v) > 0.85f) { trig = i; mv = v } }
                        if (trig != -1) {
                            val key = droneState.isAutoBinding; val isY = (key == "ly" || key == "ry")
                            val m = com.horizon.caadronesimulator.model.ChannelMapping(axis = trig + 101, inverted = (if (isY) -mv else mv) < 0, label = "Serial CH${trig + 1}")
                            when(key) { "ly" -> droneState.mappingLY = m; "lx" -> droneState.mappingLX = m; "ry" -> droneState.mappingRY = m; "rx" -> droneState.mappingRX = m }
                            droneState.isAutoBinding = null
                        }
                    }
                }
            },
            onDiagnosticUpdate = { log, path, _, extra ->
                uiHandler.post {
                    stickInputState.packetsPerSecond = (extra["pps"] as? Int) ?: stickInputState.packetsPerSecond
                    stickInputState.isSignalActive = (extra["is_signal_active"] as? Boolean) ?: stickInputState.isSignalActive
                    droneState.diagnosticLog = log; if (path != "%SAME%") droneState.activeSerialPath = path
                    (extra["linkType"] as? String)?.let { droneState.linkType = it }; (extra["baud"] as? Int)?.let { droneState.baudRate = it }
                    (extra["protocol"] as? String)?.let { droneState.detectedProtocol = it }; (extra["conflict"] as? Boolean)?.let { droneState.isSerialConflict = it }
                    (extra["raw_bytes_count"] as? Int)?.let { droneState.rawBytesCount = it }; (extra["buffer_usage"] as? String)?.let { droneState.bufferUsage = it }
                }
            },
            onProtocolDetected = { p -> 
                uiHandler.post { 
                    droneState.lockedProtocol = p 
                    if (p == "AX12(UMBUS)") {
                        droneState.baudRate = 921600
                        usbSerialManager.setBaudRate(921600)
                        settingsManager.saveSettings(droneState)
                    }
                } 
            },
            onHandshakeStatus = { active, msg -> 
                uiHandler.post { 
                    droneState.isHandshaking = active
                    if (msg == "TIMEOUT_60S") { if (!droneState.showTroubleshootingHint && !droneState.usbSerialConnected) droneState.showTroubleshootingHint = true }
                    else if (msg.isNotEmpty()) droneState.systemMessage = msg
                } 
            },
            onConnectionStatusUpdate = { status -> uiHandler.post { droneState.connectionStatus = status } },
            onIdentityVerified = {
                if (!droneState.isHardwareVerified) {
                    uiHandler.post {
                        droneState.isHardwareVerified = true; droneState.isProbing = false; droneState.probeAttempts = 0; droneState.systemMessage = "✅ 已認證 RadioMaster 硬體"; settingsManager.saveSettings(droneState)
                    }
                }
            }
        )
        usbSerialManager.setBaudRate(droneState.baudRate)

        networkStreamManager = com.horizon.caadronesimulator.logic.NetworkStreamManager(
            onDataReceived = { data ->
                if (droneState.inputMode == 2) {
                    // [v1.3.9] 智慧網路解析
                    // 模式 A: 96 Bytes 原始 Float 池 (24通道)
                    if (data.size == 96) {
                        val buffer = java.nio.ByteBuffer.wrap(data).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                        val channels = List(24) { buffer.float }
                        uiHandler.post { 
                            stickInputState.rawChannels = channels
                            stickInputState.updateRaw(channels[0], channels[1], channels[2], channels[3])
                        }
                    } 
                    // 模式 B: MAVLink RC_CHANNELS_OVERRIDE (封包通常約 18-30 bytes)
                    // 這裡實施針對 MK15 的 1050~1950 特殊解析
                    else if (data.size >= 18) {
                        // 簡單掃描 1000~2000 範圍的 Short 數據 (MAVLink 格式常態)
                        val buffer = java.nio.ByteBuffer.wrap(data).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                        val channels = mutableListOf<Float>()
                        val mk15Driver = com.horizon.caadronesimulator.logic.drivers.MK15Driver()
                        
                        // 假設前 8 個 Short 是通道數據 (MAVLink 標準偏移)
                        try {
                            for (i in 0 until 8) {
                                if (buffer.remaining() >= 2) {
                                    val rawVal = buffer.getShort().toFloat()
                                    // 套用 MK15 標定規一化
                                    channels.add(mk15Driver.normalizeMK15Value(rawVal))
                                }
                            }
                            if (channels.size >= 4) {
                                uiHandler.post {
                                    stickInputState.rawChannels = channels + List(24-channels.size){0f}
                                    // MK15 預設順序: Roll, Pitch, Thr, Yaw -> 轉為內部標準
                                    stickInputState.updateRaw(channels[2], channels[3], channels[1], channels[0])
                                }
                            }
                        } catch(e: Exception) {}
                    }
                }
            },
            onStatusUpdate = { active, msg ->
                uiHandler.post {
                    droneState.isNetworkConnected = active
                    droneState.systemMessage = msg
                }
            }
        )

        soundManager = DroneSoundManager(); soundManager.start()

        renderer = DroneSimulationRenderer { alt, x, z, yaw, pitch, roll, speed, isImpact, volt, perc, titlePos ->
            // [v1.4.2] 特殊標題位置必須零延遲更新，不參與 UI Throttling
            uiHandler.post { droneState.specialTitleScreenPos = titlePos }

            val now = System.currentTimeMillis()
            if (now - lastUiUpdateTime < 32 && !isImpact) return@DroneSimulationRenderer
            lastUiUpdateTime = now
            uiHandler.post {
                val cur = droneState; val isProtecting = (System.currentTimeMillis() - lastResetTime < 500)
                if ((cur.showSettings || cur.isCollision) && !isProtecting) return@post
                
                // 更新電池狀態
                cur.batteryVoltage = volt
                cur.batteryPercent = perc
                
                if (cur.useFlightLimit && perc <= 30 && perc > 0 && cur.systemMessage == null) {
                    cur.systemMessage = "⚠️ 低電壓請降落"
                }

                if (isImpact || PhysicsEngine.checkCollision(cur.droneType, alt, x, z, pitch, roll) || (cur.useFlightLimit && perc <= 0)) {
                    cur.isCollision = true
                    cur.isMotorLocked = true
                    cur.systemMessage = if (cur.useFlightLimit && perc <= 0) "⚠️ 電量耗盡，無人機已墜落" else "已撞毀"
                }
                else {
                    val spec = com.horizon.caadronesimulator.model.DroneRegistry.getSpec(cur.droneType)
                    if ((alt - spec.groundOffset) >= 29.99f && cur.systemMessage == null) cur.systemMessage = "已達限高 30m"
                    cur.altitude = alt; cur.posX = x; cur.posZ = z; cur.lastYaw = cur.yaw; cur.yaw = yaw; cur.pitch = pitch; cur.roll = roll; cur.speed = speed
                    if (cur.isLogcatEnabled && now % 1000 < 33) {
                        val sticks = "T:%.2f Y:%.2f P:%.2f R:%.2f".format(stickInputState.stickThrottle(cur), stickInputState.stickYaw(cur), stickInputState.stickPitch(cur), stickInputState.stickRoll(cur))
                        val raws = stickInputState.rawChannels.asSequence().take(4).joinToString(",") { "%.2f".format(it) }
                        val mapInfo = "M1:%d,M2:%d,M3:%d,M4:%d".format(cur.mappingLY.axis, cur.mappingLX.axis, cur.mappingRY.axis, cur.mappingRX.axis)
                        usbSerialManager.injectLog("DEBUG | MODEL:${cur.droneType} | MODE:${cur.joystickMode} | RAW:[$raws] | MAP:($mapInfo) | FINAL:[$sticks]")
                    }
                    val stabilityNow = System.currentTimeMillis(); val dt = if (lastStabilityCheckTime == 0L) 0f else (stabilityNow - lastStabilityCheckTime) / 1000f
                    lastStabilityCheckTime = stabilityNow; MissionManager.update(cur, dt, spec)
                    if (cur.showFlightPath && !cur.isMotorLocked) {
                        val path = cur.flightPath; val pos = androidx.compose.ui.geometry.Offset(x, z); val last = path.lastOrNull()
                        if (last == null || sqrt((pos.x - last.x).pow(2) + (pos.y - last.y).pow(2)) > 0.15f) cur.flightPath = (path + pos).takeLast(5400)
                    } else if (!cur.showFlightPath && cur.flightPath.isNotEmpty()) cur.flightPath = emptyList()
                }
            }
        }

        setContent {
            // [v1.2.86] 終極割接：徹底解決 ComposableFunction0 類型推導衝突
            MainAppScreen(
                droneState = droneState, 
                stickInputState = stickInputState, 
                renderer = renderer, 
                soundManager = soundManager, 
                usbSerialManager = usbSerialManager, 
                settingsManager = settingsManager,
                viewModel = viewModel, 
                showSplash = showSplash, 
                onCloseSplash = { showSplash = false }, 
                onResetFlight = { resetFlight() }, 
                onExportLog = { saveDiagnosticLog() }, 
                onUpdateBaudRate = { b -> 
                    droneState.baudRate = b
                    usbSerialManager.setBaudRate(b)
                    settingsManager.saveSettings(droneState)
                },
                onUpdateInputMode = { m ->
                    if (droneState.isInteractionLocked) return@MainAppScreen
                    if (droneState.inputMode != m) {
                        droneState.isInteractionLocked = true
                        settingsManager.saveSettings(droneState)
                        droneState.inputMode = m
                        settingsManager.loadSettings(droneState)
                        
                        // 停止所有現有連線
                        usbSerialManager.stopAll()
                        networkStreamManager.stopAll()

                        when (m) {
                            1 -> uiHandler.postDelayed({ usbSerialManager.scanAndConnect() }, 300)
                            2 -> {
                                // 開啟網路監聽
                                if (droneState.networkProtocol == "UDP") {
                                    networkStreamManager.startUdpListener(droneState.networkHost, droneState.networkPort)
                                }
                            }
                        }
                        lifecycleScope.launch { delay(1500); droneState.isInteractionLocked = false }
                    }
                },
                onUpdateSystemUI = { updateSystemUI() }
            )
        }
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean { if ((event.source and InputDevice.SOURCE_CLASS_JOYSTICK) != 0) return onGenericMotionEvent(event); return super.dispatchGenericMotionEvent(event) }
    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if ((event.source and InputDevice.SOURCE_CLASS_JOYSTICK) == 0 || droneState.inputMode == 1) return false
        if (event.action == MotionEvent.ACTION_MOVE) {
            isProcessingExternal = true
            if (droneState.isAutoBinding != null) {
                var trig = -1; var mv = 0f; for (i in 0..28) { val v = event.getAxisValue(i); if (abs(v) > 0.85f) { trig = i; mv = v; break } }
                if (trig != -1) {
                    val key = droneState.isAutoBinding; val isY = (key == "ly" || key == "ry")
                    val m = com.horizon.caadronesimulator.model.ChannelMapping(axis = trig, inverted = (if (isY) -mv else mv) < 0, label = "Axis $trig")
                    when(key) { "ly" -> droneState.mappingLY = m; "lx" -> droneState.mappingLX = m; "ry" -> droneState.mappingRY = m; "rx" -> droneState.mappingRX = m }
                    droneState.isAutoBinding = null; isProcessingExternal = false; return true
                }
            }
            if (droneState.setupWizardStep > 0 && !droneState.wizardWaitingForNeutral) {
                if (axisSnapshots.isEmpty()) { for (i in 0..28) axisSnapshots[i] = event.getAxisValue(i); isProcessingExternal = false; return true }
                var trig = -1; var mv = 0f; for (i in 0..28) { val d = event.getAxisValue(i) - (axisSnapshots[i] ?: 0f); if (abs(d) > 0.5f) { trig = i; mv = d; break } }
                if (trig != -1) {
                    val isY = (droneState.setupWizardStep == 1 || droneState.setupWizardStep == 3)
                    val m = com.horizon.caadronesimulator.model.ChannelMapping(axis = trig, inverted = (if (isY) -mv else mv) < 0, label = "Axis $trig")
                    when(droneState.setupWizardStep) { 1 -> { droneState.mappingLY = m; droneState.wizardWaitingForNeutral = true }; 2 -> { droneState.mappingLX = m; droneState.wizardWaitingForNeutral = true }; 3 -> { droneState.mappingRY = m; droneState.wizardWaitingForNeutral = true }; 4 -> { droneState.mappingRX = m; droneState.wizardWaitingForNeutral = true } }
                    axisSnapshots.clear()
                }
            } else {
                fun gV(m: com.horizon.caadronesimulator.model.ChannelMapping, d: Int, y: Boolean = false): Float { val r = event.getAxisValue(if (m.axis != -1) m.axis else d); return (if (y) -r else r).coerceIn(-1f, 1f) }
                stickInputState.updateRaw(gV(droneState.mappingLY, MotionEvent.AXIS_Y, true), gV(droneState.mappingLX, MotionEvent.AXIS_X), gV(droneState.mappingRY, MotionEvent.AXIS_RZ, true), gV(droneState.mappingRX, MotionEvent.AXIS_Z))
                if (droneState.isCalibrating) {
                    fun uC(m: com.horizon.caadronesimulator.model.ChannelMapping, d: Int): com.horizon.caadronesimulator.model.ChannelMapping { val v = event.getAxisValue(if (m.axis != -1) m.axis else d); return when(droneState.calibrationStep) { 1 -> m.copy(center = v, min = v, max = v); 2 -> m.copy(min = min(v, m.min), max = max(v, m.max)); else -> m } }
                    droneState.mappingLY = uC(droneState.mappingLY, MotionEvent.AXIS_Y); droneState.mappingLX = uC(droneState.mappingLX, MotionEvent.AXIS_X); droneState.mappingRY = uC(droneState.mappingRY, MotionEvent.AXIS_RZ); droneState.mappingRX = uC(droneState.mappingRX, MotionEvent.AXIS_Z)
                }
                if (!droneState.controllerConnected) droneState.controllerConnected = true
            }
            isProcessingExternal = false; return true
        }
        return false
    }
    override fun onDestroy() { super.onDestroy(); if (::soundManager.isInitialized) soundManager.stop(); if (::usbSerialManager.isInitialized) usbSerialManager.unregister() }
}
