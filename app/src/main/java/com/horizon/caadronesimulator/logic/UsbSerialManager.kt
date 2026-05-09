package com.horizon.caadronesimulator.logic

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Handler
import android.util.Log
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.util.SerialInputOutputManager
import com.horizon.caadronesimulator.model.StickInputState
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

/**
 * UMBUS協議統一管理器
 * 實作 UMBUS 專屬 Session 鎖定、異步緩衝處理與極速重組。
 */
class UsbSerialManager(
    private val context: Context,
    private val onStatusUpdate: (Boolean, String) -> Unit,
    private val onDataReceived: (Float, Float, Float, Float) -> Unit,
    private val onRawChannelsReceived: (List<Float>) -> Unit,
    private val onDiagnosticUpdate: (String, String, String, Map<String, Any>) -> Unit,
    private val onProtocolDetected: (String) -> Unit,
    private val onHandshakeStatus: (Boolean, String) -> Unit,
    private val onConnectionStatusUpdate: (com.horizon.caadronesimulator.model.ConnectionStatus) -> Unit,
    private val onIdentityVerified: (String) -> Unit = {} // [v1.2.82] 認證回調
) {
    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private val actionUsbPermission = "com.horizon.caadronesimulator.USB_PERMISSION"
    
    private var serialPort: UsbSerialPort? = null
    private var ioManager: SerialInputOutputManager? = null
    private var internalSerialThread: Thread? = null
    private val isRunning = AtomicBoolean(false)
    private var currentBaudRate = 115200 // 預設值，可由 UI 手動調整或協議自動鎖定
    private val uiHandler = Handler(android.os.Looper.getMainLooper())

    private var totalBytesRead = 0
    private var lastDiagnosticReportTime = 0L
    private var lastValidPacketTime = 0L
    private var lastSignalOnTime = 0L // [v1.2.81] 斷線防抖計時
    private var connectionStartTime = 0L // [v1.2.81] 5秒觀測期計時


    private val diagLogBuffer = mutableListOf<String>()

    private var packetCount: Int = 0
    private var lastPpsCalcTime: Long = 0L
    private var pps: Int = 0
    private var lastPacketTime: Long = 0L
    private var jitterSum: Long = 0L
    private var jitterCount: Int = 0
    private var avgInterval: Long = 20L
    private var detectedProtocolName = "掃描中..."
    private var currentPacketHex = "None"

    private var lastFilteredChannels = FloatArray(24)
    private var lastVirtualChannels = FloatArray(4)
    private var lockedProtocol = "" 
    private var isUserStopped = false
    private var lastReportedSignalState: Boolean? = null
    
    // [v1.3.6] 智慧自癒重連參數
    private var autoReconnectCount = 0
    private val maxAutoReconnects = 3
    private var lastAutoReconnectTime = 0L
    private val reconnectCooldown = 5000L // 5秒冷卻
    
    // [v1.2.68] UMBUS 專屬鎖定機制
    private var isSessionLocked = false
    private var lastSuccessfulLockedTime = 0L

    private val assemblyBuffer = ByteArray(4096) 
    private var assemblyPos = 0

    private var isAutoDetecting = false
    private var detectionStartTime = 0L
    private val ax12Handler = AX12ProtocolHandler()

    // [v1.4.2] RX 數據凍結自癒系統
    private var lastSnapshotChannels = FloatArray(4)
    private var frozenSecondsCount = 0
    private var lastFreezeCheckTime = 0L
    private var activeLinkPath = "" // 紀錄當前活躍路徑，用於快速重連

    private val primaryPath = "/dev/ttyS0"
    private val secondaryPath = "/dev/ttyHS0"
    private val fallbackPaths = listOf("/dev/ttyMTK0", "/dev/ttyS1", "/dev/ttyS4")

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (actionUsbPermission == intent.action) {
                val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                }
                if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                    device?.apply { startUsbReading(this) }
                } else {
                    onStatusUpdate(false, "USB 權限被拒絕")
                }
            }
        }
    }

    init {
        val filter = IntentFilter(actionUsbPermission)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(usbReceiver, filter)
        }
    }

    fun setBaudRate(baud: Int) {
        currentBaudRate = baud
        isSessionLocked = false // 切換波特率時強制解鎖
        if (isRunning.get()) scanAndConnect()
    }

    private fun updateConnectionStatus(status: com.horizon.caadronesimulator.model.ConnectionStatus) {
        onConnectionStatusUpdate(status)
    }

    fun setLockedProtocol(protocol: String) {
        lockedProtocol = protocol
        isSessionLocked = false
        if (protocol.isNotEmpty()) {
            detectedProtocolName = protocol
        }
    }

    fun toggleConnection() {
        if (isRunning.get()) {
            isUserStopped = true
            stopAll()
            onStatusUpdate(false, "已手動中斷連線")
        } else {
            isUserStopped = false
            scanAndConnect()
        }
    }

    // [v1.2.68] 修正使用者手動停止優先權
    fun clearUserStop() {
        isUserStopped = false
    }

    fun isUserStoppedManually(): Boolean = isUserStopped

    fun updateVirtualJoystickState(t: Float, y: Float, p: Float, r: Float) {
        lastVirtualChannels[0] = t
        lastVirtualChannels[1] = y
        lastVirtualChannels[2] = p
        lastVirtualChannels[3] = r
    }

    /**
     * 掃描並連線 (增加 UMBUS 鎖定防護)
     */
    fun scanAndConnect() {
        // [v1.2.68] 如果使用者明確停止過，且不是由掃描按鈕觸發的，拒絕自動連線
        if (isUserStopped && internalSerialThread == null) return

        // [關鍵避險] 如果 UMBUS Session 鎖定且信號正常，拒絕掃描請求
        if (isSessionLocked && System.currentTimeMillis() - lastSuccessfulLockedTime < 1500) {
            Log.d("NikoSerial", "Session locked. Skipping redundant scan.")
            return
        }

        if (isUserStopped && internalSerialThread == null) return
        
        stopAll()
        try { Thread.sleep(300) } catch (e: Exception) {}

        synchronized(assemblyBuffer) {
            assemblyPos = 0
            assemblyBuffer.fill(0)
        }
        
        connectionStartTime = System.currentTimeMillis() // [v1.2.81] 開始 5 秒觀測期
        onDiagnosticUpdate("Scanning devices...", "Scanning", "", emptyMap())
        updateConnectionStatus(com.horizon.caadronesimulator.model.ConnectionStatus.SEARCHING)
        
        val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
        if (availableDrivers.isNotEmpty()) {
            val driver = availableDrivers[0]
            if (usbManager.hasPermission(driver.device)) {
                startUsbReading(driver.device)
            } else {
                val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    android.app.PendingIntent.FLAG_MUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
                } else {
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT
                }
                val permissionIntent = android.app.PendingIntent.getBroadcast(context, 0, Intent(actionUsbPermission), flags)
                usbManager.requestPermission(driver.device, permissionIntent)
            }
            return
        }

        var foundPath: String? = null
        val testPaths = listOf(primaryPath, secondaryPath) + fallbackPaths
        for (path in testPaths) {
            val f = File(path)
            if (f.exists() && f.canRead()) {
                foundPath = path
                break
            }
        }

        if (foundPath != null) startInternalReading(foundPath)
        else {
            addLogEntry("[ERROR] No RC controller detected.")
            onStatusUpdate(false, "No RC controller detected")
        }
    }

    /**
     * [v1.4.2] 快速重設：僅重新撥號，不重掃、不換協議
     */
    private fun fastResetCurrentLink() {
        if (activeLinkPath.isEmpty()) {
            scanAndConnect()
            return
        }
        
        stopAll()
        try { Thread.sleep(200) } catch (e: Exception) {}
        
        synchronized(assemblyBuffer) {
            assemblyPos = 0
            assemblyBuffer.fill(0)
        }
        
        if (activeLinkPath.startsWith("/dev/")) {
            startInternalReading(activeLinkPath)
        } else {
            // USB 路徑，仍需重新尋找設備但維持參數
            scanAndConnect()
        }
    }

    private fun startUsbReading(device: UsbDevice) {
        val connection = usbManager.openDevice(device) ?: return
        serialPort = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)[0].ports[0]
        try {
            serialPort?.open(connection)
            serialPort?.setParameters(currentBaudRate, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            ioManager = SerialInputOutputManager(serialPort, object : SerialInputOutputManager.Listener {
                override fun onNewData(data: ByteArray) { handleRawIncoming(data, "USB") }
                override fun onRunError(e: Exception) { Log.e("NikoSerial", "USB Error: ${e.message}"); stopAll() }
            })
            ioManager?.start()
            isRunning.set(true)
            onStatusUpdate(true, "USB 已連線")
            updateConnectionStatus(com.horizon.caadronesimulator.model.ConnectionStatus.LINKED)
        } catch (e: Exception) { onStatusUpdate(false, "USB 連接失敗: ${e.message}") }
    }

    private fun startInternalReading(path: String) {
        synchronized(this) {
            if (internalSerialThread?.isAlive == true) return
            
            isRunning.set(true)
            internalSerialThread = Thread {
                var fis: FileInputStream? = null
                try {
                    val file = File(path)
                    if (!file.canRead()) {
                        try { Runtime.getRuntime().exec("chmod 666 $path").waitFor() } catch (e: Exception) {}
                    }
                    if (!file.canRead()) { onStatusUpdate(false, "權限不足"); return@Thread }

                    try { 
                        // [v1.2.82] 始終使用當前已設定的波特率，確保自動重連時維持 921600
                        Runtime.getRuntime().exec("stty -F $path $currentBaudRate raw -echo").waitFor()
                    } catch (e: Exception) {}

                    fis = FileInputStream(file)
                    addLogEntry("[CONNECT] Opening $path. canRead: ${file.canRead()}")
                    updateConnectionStatus(com.horizon.caadronesimulator.model.ConnectionStatus.LINKED)

                    if (lockedProtocol.isEmpty()) {
                        isAutoDetecting = true
                        detectionStartTime = System.currentTimeMillis()
                        onHandshakeStatus(true, "自動識別識別中...")
                    }

                    val readBuffer = ByteArray(2048) // 擴大讀取緩衝

                    while (isRunning.get()) {
                        val bytesRead = try { fis.read(readBuffer) } catch (e: Exception) { -1 }
                        if (bytesRead <= 0) break
                        handleRawIncoming(readBuffer.copyOf(bytesRead), path)
                    }
                } catch (e: Exception) {
                    addLogEntry("[CRITICAL] Reader Error: ${e.message}")
                } finally {
                    try { fis?.close() } catch (e: Exception) {}
                    isRunning.set(false)
                    isSessionLocked = false
                    internalSerialThread = null
                }
            }.apply { 
                name = "SerialReader"
                priority = Thread.NORM_PRIORITY + 2 // 提升至 URGENT 級別
                start() 
            }
        }
    }

    /**
     * [v1.2.68] 核心數據分發器：UMBUS 專屬邏輯分流
     * [v1.3.9] 設為公開以支援網路透傳解析
     */
    fun handleRawIncoming(data: ByteArray, path: String) {
        totalBytesRead += data.size
        activeLinkPath = path // 隨時更新活躍路徑以便重連
        
        synchronized(assemblyBuffer) {
            // 防止緩衝區溢出
            if (assemblyPos + data.size > assemblyBuffer.size) {
                // 如果是 AX12 且鎖定，我們執行快速對齊
                if (isSessionLocked) {
                    assemblyPos = 0
                } else {
                    assemblyPos = 0 
                }
            }
            System.arraycopy(data, 0, assemblyBuffer, assemblyPos, data.size)
            assemblyPos += data.size

            var i = 0
            
            // [v1.3.5] 直通模式處理：手動鎖定 UART 或 Serial USB 時，跳過所有協議解析
            if (lockedProtocol == "UART" || lockedProtocol == "Serial USB") {
                lastValidPacketTime = System.currentTimeMillis()
                packetCount++ // 增加計數以顯示 PPS 活動
                detectedProtocolName = lockedProtocol
                currentPacketHex = data.take(16).joinToString(" ") { String.format("%02X", it) }
                assemblyPos = 0 // 直接清空緩衝，不進行 Frame 組裝
                return
            }

            while (i < assemblyPos) {
                val u = assemblyBuffer[i].toInt() and 0xFF
                var consumed = 0

                // [UMBUS 專屬優化] 極速對齊與鎖定
                if ((lockedProtocol == "AX12(UMBUS)" || isSessionLocked) && u != 0xA6) {
                    i++; continue
                }

                when (u.toByte()) {
                    AX12ProtocolHandler.SYNC_BYTE -> {
                        if (i + 1 < assemblyPos) {
                            val type = assemblyBuffer[i + 1]
                            if (type == AX12ProtocolHandler.FRAME_TYPE_HEARTBEAT) {
                                if (i + AX12ProtocolHandler.HEARTBEAT_LEN <= assemblyPos) {
                                    val packet = assemblyBuffer.copyOfRange(i, i + AX12ProtocolHandler.HEARTBEAT_LEN)
                                    if (ax12Handler.verifyChecksum(packet)) {
                                        markSuccessfulPacket()
                                        consumed = AX12ProtocolHandler.HEARTBEAT_LEN
                                    }
                                } else break 
                            } else if (type == AX12ProtocolHandler.FRAME_TYPE_CHANNEL_DATA || type == AX12ProtocolHandler.FRAME_TYPE_IDLE) {
                                if (i + AX12ProtocolHandler.CHANNEL_DATA_LEN <= assemblyPos) {
                                    val packet = assemblyBuffer.copyOfRange(i, i + AX12ProtocolHandler.CHANNEL_DATA_LEN)
                                        if (ax12Handler.verifyChecksum(packet)) {
                                            detectedProtocolName = "AX12(UMBUS)"
                                            
                                            // [v1.2.82] 命中 UMBUS 協議特徵，執行身份鎖定
                                            onIdentityVerified("RM_AX12")

                                            if (isAutoDetecting) {
                                                isAutoDetecting = false
                                                lockedProtocol = "AX12(UMBUS)"
                                                onHandshakeStatus(false, "偵測到 AX12 數據流")
                                                onProtocolDetected("AX12(UMBUS)")
                                            }
                                        currentPacketHex = packet.take(16).joinToString(" ") { String.format("%02X", it) }
                                        
                                        // [v1.2.81 驅動化調用]
                                        val pool = ax12Handler.parseChannelData(packet)
                                        if (pool != null) {
                                            val profile = HardwareRegistry.detectHardware()
                                            val driver = profile.driver
                                            if (driver != null) {
                                                // 使用驅動解析 24 通道池 (LSV, LSH, RSV, RSH)
                                                val stickBuffer = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN)
                                                val fullPool = driver.parseRaw(stickBuffer)
                                                onDataReceived(fullPool[0], fullPool[1], fullPool[2], fullPool[3])
                                                onRawChannelsReceived(fullPool)
                                            } else {
                                                onRawChannelsReceived(pool)
                                            }
                                        }

                                        markSuccessfulPacket()
                                        consumed = AX12ProtocolHandler.CHANNEL_DATA_LEN
                                    }
                                } else break 
                            }
                        }
                    }
                    0xC8.toByte(), 0x81.toByte() -> { // CRSF
                        if (lockedProtocol == "CRSF" || lockedProtocol.isEmpty()) {
                            if (i + 1 < assemblyPos) {
                                val payloadLen = assemblyBuffer[i + 1].toInt() and 0xFF
                                val totalLen = payloadLen + 2
                                if (i + totalLen <= assemblyPos) {
                                    detectedProtocolName = "CRSF"
                                    parseCRSF(assemblyBuffer.copyOfRange(i, i + totalLen))
                                    consumed = totalLen
                                } else break
                            }
                        }
                    }
                }
                if (consumed > 0) i += consumed else i++
            }
            
            if (i > 0) {
                val remaining = assemblyPos - i
                if (remaining > 0) System.arraycopy(assemblyBuffer, i, assemblyBuffer, 0, remaining)
                assemblyPos = remaining
            }
        }

        // 定期檢查診斷與狀態
        performDiagnosticCheck(path)
    }

    private fun markSuccessfulPacket() {
        lastValidPacketTime = System.currentTimeMillis()
        lastSuccessfulLockedTime = lastValidPacketTime
        packetCount++
        
        // [v1.3.6] 只要有有效數據包，立即重置自癒次數
        if (autoReconnectCount > 0) autoReconnectCount = 0

        if (lockedProtocol == "AX12(UMBUS)") {
            isSessionLocked = true // 只有 AX12 觸發專屬鎖定
        }
    }

    private fun performDiagnosticCheck(path: String) {
        val now = System.currentTimeMillis()
        if (now - lastPpsCalcTime >= 1000) {
            pps = packetCount; packetCount = 0; lastPpsCalcTime = now
            // [v1.6.2] 放寬自動解鎖容忍度 (1.5s -> 3.0s) 避免網路或系統抖動導致閃爍
            if (now - lastValidPacketTime > 3000) {
                isSessionLocked = false
            }
        }

        if (now - lastDiagnosticReportTime >= 500) {
            lastDiagnosticReportTime = now
            val actualSignal = (now - lastValidPacketTime < 2000) && (pps > 10 || isSessionLocked)
            
            // [v1.6.2] 強化防抖邏輯 (800ms -> 1500ms)
            if (actualSignal) lastSignalOnTime = now
            val isSignalActive = (now - lastSignalOnTime < 1500) && (now - lastValidPacketTime < 5000)

            val statusText = if (isSignalActive) "SIGNAL OK" else "NO SIGNAL"
            
            if (lastReportedSignalState != isSignalActive) {
                lastReportedSignalState = isSignalActive
                onStatusUpdate(isSignalActive, if (isSignalActive) "連線狀態：正常運作中" else "連線狀態：等待訊號...")
            }

            // [v1.3.6] 智慧連線狀態回報
            if (isSignalActive) {
                updateConnectionStatus(com.horizon.caadronesimulator.model.ConnectionStatus.ACTIVE)
            } else if (isRunning.get()) {
                if (isAutoDetecting) {
                    updateConnectionStatus(com.horizon.caadronesimulator.model.ConnectionStatus.SEARCHING)
                } else {
                    updateConnectionStatus(com.horizon.caadronesimulator.model.ConnectionStatus.LINKED)
                }
            }
            
            // [v1.2.81 階段三修正] 60秒觀測期判定：若連線已開啟超過 60 秒且 PPS 持續為 0
            val isObserving = now - connectionStartTime < 60000
            if (!isSignalActive && !isObserving && !isUserStopped && isRunning.get()) {
                // 觸發故障引導訊號，由 UI 接收
                onHandshakeStatus(false, "TIMEOUT_60S")
                // [關鍵] 發送後重置計時器，防止每 500ms 重複觸發 UI 造成無限迴圈
                connectionStartTime = now
            }

            // [v1.3.6] 智慧自癒與波特率自適應重連判定
            val isZombie = isRunning.get() && !isSignalActive && !isUserStopped
            if (isZombie) {
                // 1. 如果是在自動識別中且超時，嘗試切換波特率 (針對 AX12)
                if (isAutoDetecting && now - detectionStartTime > 2000 && currentBaudRate != 921600) {
                    addLogEntry("[AUTO-DETECT] $currentBaudRate 無效，嘗試切換至 921600 (UMBUS)")
                    uiHandler.post { setBaudRate(921600) }
                    detectionStartTime = now + 5000 // 避免重連期間重複觸發
                    return@performDiagnosticCheck
                }

                // 2. 一般重連邏輯
                if (autoReconnectCount < maxAutoReconnects && now - lastAutoReconnectTime > reconnectCooldown) {
                    autoReconnectCount++
                    lastAutoReconnectTime = now
                    addLogEntry("[SELF-HEAL] Zombie connection detected. Attempt $autoReconnectCount/$maxAutoReconnects")
                    uiHandler.post { scanAndConnect() }
                } else if (autoReconnectCount >= maxAutoReconnects) {
                    isUserStopped = true 
                    onStatusUpdate(false, "通訊僵死且自動修復失敗，請手動檢查硬體")
                }
            }

            // [v1.4.2] RX 數據凍結自癒系統 (每秒檢查一次)
            if (isRunning.get() && isSignalActive && !isUserStopped && (now - lastFreezeCheckTime > 1000)) {
                lastFreezeCheckTime = now
                val profile = HardwareRegistry.detectHardware()
                val isTargetHardware = profile.id == "RM_AX12" || profile.id == "QUALCOMM MK15"
                
                if (isTargetHardware) {
                    // 比對當前 4 軸數據與上次快照
                    var isFrozen = true
                    for (idx in 0..3) {
                        if (lastVirtualChannels[idx] != lastSnapshotChannels[idx]) {
                            isFrozen = false
                            break
                        }
                    }

                    if (isFrozen) {
                        frozenSecondsCount++
                        if (frozenSecondsCount >= 3) { // 連續 3 次檢查位元級相等 (3秒)
                            if (autoReconnectCount < maxAutoReconnects) {
                                addLogEntry("[FREEZE-RECOVERY] 偵測到數據凍結，執行快速重設 ($autoReconnectCount/$maxAutoReconnects)")
                                autoReconnectCount++
                                frozenSecondsCount = 0
                                uiHandler.post { fastResetCurrentLink() }
                            } else {
                                isUserStopped = true
                                onStatusUpdate(false, "數據源持續凍結且重連無效，請檢查硬體")
                            }
                        }
                    } else {
                        frozenSecondsCount = 0
                    }
                    // 更新快照
                    System.arraycopy(lastVirtualChannels, 0, lastSnapshotChannels, 0, 4)
                }
            }

            val avgJitterValue = if (jitterCount > 0) jitterSum / jitterCount else 0L
            if (isSignalActive) {
                addLogEntry("[$detectedProtocolName] PPS: $pps, Buf: $totalBytesRead bytes")
            }

            val connType = if (path == primaryPath) "SoC Direct" else "External"
            val diagMap = mutableMapOf<String, Any>(
                "pps" to pps, "protocol" to detectedProtocolName, "baud" to currentBaudRate,
                "is_signal_active" to isSignalActive, "raw_hex" to currentPacketHex,
                "jitter" to avgJitterValue, "buffer_usage" to "$assemblyPos/4096", 
                "raw_bytes_count" to totalBytesRead, // [v1.4.2 修復] 接回數據密度回報
                "linkType" to if (path == primaryPath) "SoC 內置直連" else "外部連線",
                "connectionType" to connType
            )
            onDiagnosticUpdate(statusText, path, "", diagMap)
            totalBytesRead = 0
        }
    }

    private fun parseCRSF(f: ByteArray) {
        val ch = IntArray(16)
        ch[0] = ((f[3].toInt() and 0xFF) or ((f[4].toInt() and 0xFF) shl 8)) and 0x07FF
        ch[1] = (((f[4].toInt() and 0xFF) shr 3) or ((f[5].toInt() and 0xFF) shl 5)) and 0x07FF
        ch[2] = (((f[5].toInt() and 0xFF) shr 6) or ((f[6].toInt() and 0xFF) shl 2) or ((f[7].toInt() and 0xFF) shl 10)) and 0x07FF
        ch[3] = (((f[7].toInt() and 0xFF) shr 1) or ((f[8].toInt() and 0xFF) shl 7)) and 0x07FF
        ch[4] = (((f[8].toInt() and 0xFF) shr 4) or ((f[9].toInt() and 0xFF) shl 4)) and 0x07FF
        ch[5] = (((f[9].toInt() and 0xFF) shr 7) or ((f[10].toInt() and 0xFF) shl 1) or ((f[11].toInt() and 0xFF) shl 9)) and 0x07FF
        ch[6] = (((f[11].toInt() and 0xFF) shr 2) or ((f[12].toInt() and 0xFF) shl 6)) and 0x07FF
        ch[7] = (((f[12].toInt() and 0xFF) shr 5) or ((f[13].toInt() and 0xFF) shl 3)) and 0x07FF
        ch[8] = ((f[14].toInt() and 0xFF) or ((f[15].toInt() and 0xFF) shl 8)) and 0x07FF
        ch[9] = (((f[15].toInt() and 0xFF) shr 3) or ((f[16].toInt() and 0xFF) shl 5)) and 0x07FF
        ch[10] = (((f[16].toInt() and 0xFF) shr 6) or ((f[17].toInt() and 0xFF) shl 2) or ((f[18].toInt() and 0xFF) shl 10)) and 0x07FF
        ch[11] = (((f[18].toInt() and 0xFF) shr 1) or ((f[19].toInt() and 0xFF) shl 7)) and 0x07FF
        ch[12] = (((f[19].toInt() and 0xFF) shr 4) or ((f[20].toInt() and 0xFF) shl 4)) and 0x07FF
        ch[13] = (((f[20].toInt() and 0xFF) shr 7) or ((f[21].toInt() and 0xFF) shl 1) or ((f[22].toInt() and 0xFF) shl 9)) and 0x07FF
        ch[14] = (((f[22].toInt() and 0xFF) shr 2) or ((f[23].toInt() and 0xFF) shl 6)) and 0x07FF
        ch[15] = (((f[23].toInt() and 0xFF) shr 5) or ((f[24].toInt() and 0xFF) shl 3)) and 0x07FF
        dispatchCommon(ch, true)
    }

    private fun parseSBus(f: ByteArray) {
        val ch = IntArray(16)
        ch[0] = ((f[1].toInt() and 0xFF) or ((f[2].toInt() and 0xFF) shl 8)) and 0x07FF
        ch[1] = (((f[2].toInt() and 0xFF) shr 3) or ((f[3].toInt() and 0xFF) shl 5)) and 0x07FF
        ch[2] = (((f[3].toInt() and 0xFF) shr 6) or ((f[4].toInt() and 0xFF) shl 2) or ((f[5].toInt() and 0xFF) shl 10)) and 0x07FF
        ch[3] = (((f[5].toInt() and 0xFF) shr 1) or ((f[6].toInt() and 0xFF) shl 7)) and 0x07FF
        ch[4] = (((f[6].toInt() and 0xFF) shr 4) or ((f[7].toInt() and 0xFF) shl 4)) and 0x07FF
        ch[5] = (((f[7].toInt() and 0xFF) shr 7) or ((f[8].toInt() and 0xFF) shl 1) or ((f[9].toInt() and 0xFF) shl 9)) and 0x07FF
        ch[6] = (((f[9].toInt() and 0xFF) shr 2) or ((f[10].toInt() and 0xFF) shl 6)) and 0x07FF
        ch[7] = (((f[10].toInt() and 0xFF) shr 5) or ((f[11].toInt() and 0xFF) shl 3)) and 0x07FF
        ch[8] = ((f[12].toInt() and 0xFF) or ((f[13].toInt() and 0xFF) shl 8)) and 0x07FF
        ch[9] = (((f[13].toInt() and 0xFF) shr 3) or ((f[14].toInt() and 0xFF) shl 5)) and 0x07FF
        ch[10] = (((f[14].toInt() and 0xFF) shr 6) or ((f[15].toInt() and 0xFF) shl 2) or ((f[16].toInt() and 0xFF) shl 10)) and 0x07FF
        ch[11] = (((f[16].toInt() and 0xFF) shr 1) or ((f[17].toInt() and 0xFF) shl 7)) and 0x07FF
        ch[12] = (((f[17].toInt() and 0xFF) shr 4) or ((f[18].toInt() and 0xFF) shl 4)) and 0x07FF
        ch[13] = (((f[18].toInt() and 0xFF) shr 7) or ((f[19].toInt() and 0xFF) shl 1) or ((f[20].toInt() and 0xFF) shl 9)) and 0x07FF
        ch[14] = (((f[20].toInt() and 0xFF) shr 2) or ((f[21].toInt() and 0xFF) shl 6)) and 0x07FF
        ch[15] = (((f[21].toInt() and 0xFF) shr 5) or ((f[22].toInt() and 0xFF) shl 3)) and 0x07FF
        dispatchCommon(ch, true)
    }

    private fun parseMavlink(f: ByteArray, v2: Boolean) {
        val offset = if (v2) 10 else 6
        val availableBytes = f.size - offset - 4 
        val channelCount = kotlin.math.min(18, availableBytes / 2)
        if (channelCount < 4) return
        val ch = IntArray(channelCount)
        for (i in 0 until channelCount) {
            ch[i] = ((f[offset + 5 + i * 2].toInt() and 0xFF) shl 8) or (f[offset + 4 + i * 2].toInt() and 0xFF)
        }
        dispatchCommon(ch, false)
    }

    private fun dispatchCommon(channels: IntArray, isSBus: Boolean) {
        updateDiagnostics()
        val n = channels.map { if(isSBus) (it - 1024) / 672f else (it - 1500) / 500f }
        val count = kotlin.math.min(n.size, 24)
        for (i in 0 until count) {
            lastFilteredChannels[i] = (n[i] * 0.2f) + (lastFilteredChannels[i] * 0.8f)
        }
        onDataReceived(lastFilteredChannels[0], lastFilteredChannels[1], lastFilteredChannels[2], lastFilteredChannels[3])
        onRawChannelsReceived(lastFilteredChannels.toList())
    }

    private fun updateDiagnostics() {
        val now = System.currentTimeMillis()
        lastValidPacketTime = now
        packetCount++
    }

    // [v1.2.71] 允許外部注入診斷日誌
    fun injectLog(msg: String) {
        addLogEntry(msg)
    }

    fun getFullLog(): String = synchronized(diagLogBuffer) { diagLogBuffer.joinToString("\n") }

    private fun addLogEntry(msg: String) {
        synchronized(diagLogBuffer) {
            val ts = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US).format(java.util.Date())
            diagLogBuffer.add("[$ts] $msg")
            if (diagLogBuffer.size > 500) diagLogBuffer.removeAt(0)
        }
    }

    fun stopAll() {
        isRunning.set(false)
        updateConnectionStatus(com.horizon.caadronesimulator.model.ConnectionStatus.IDLE)
        isSessionLocked = false // [v1.2.82] 停止時重置鎖定，確保下次掃描能正常執行
        ioManager?.stop(); ioManager = null
        serialPort?.close(); serialPort = null
        internalSerialThread?.interrupt()
        try { internalSerialThread?.join(500) } catch (e: Exception) {}
        internalSerialThread = null
    }

    fun unregister() {
        stopAll()
        try { context.unregisterReceiver(usbReceiver) } catch (e: Exception) {}
    }

    companion object {
        /**
         * [v1.2.68] 判斷裝置是否為相關硬體（手把或串口）
         */
        fun isRelevantDevice(device: UsbDevice?): Boolean {
            if (device == null) return false
            
            // 1. 檢查 HID 類別 (通常為 3)
            for (i in 0 until device.interfaceCount) {
                if (device.getInterface(i).interfaceClass == 3) return true
            }

            // 2. 檢查知名串口晶片廠商 ID
            val vid = device.vendorId
            val knownVids = listOf(
                0x10C4, // Silicon Labs (CP210x)
                0x1A86, // QinHeng (CH34x)
                0x0403, // FTDI
                0x067B, // Prolific
                0x2341, // Arduino
                0x2E3C, // SIYI (MK15/AX12 可能使用的廠編)
                0x1204  // RadioMaster / EdgeTX
            )
            return knownVids.contains(vid)
        }
    }
}
