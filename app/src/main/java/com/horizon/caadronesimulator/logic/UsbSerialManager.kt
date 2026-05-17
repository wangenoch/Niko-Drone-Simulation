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
import androidx.core.content.IntentCompat
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.util.SerialInputOutputManager
import com.horizon.caadronesimulator.model.DroneState
import com.horizon.caadronesimulator.model.ConnectionStatus
import com.horizon.caadronesimulator.model.CommDecisionState
import java.io.File
import java.io.FileInputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs

/**
 * [v1.5.3] 專業通訊管理器 (Communication Manager)
 * 職責：整合串口、USB 與 UDP 網路通訊，管理硬體插拔生命週期與主權自動鎖定。
 */
class UsbSerialManager(
    private val context: Context,
    private val onStatusUpdate: (Boolean, String) -> Unit,
    private val onDataReceived: (Float, Float, Float, Float) -> Unit,
    private val onRawChannelsReceived: (List<Float>) -> Unit,
    private val onDiagnosticUpdate: (String, String, String, Map<String, Any>) -> Unit,
    private val onProtocolDetected: (String) -> Unit,
    private val onHandshakeStatus: (Boolean, String) -> Unit,
    private val onConnectionStatusUpdate: (ConnectionStatus) -> Unit,
    private val onIdentityVerified: (String) -> Unit = {}
) {
    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private val actionUsbPermission = "com.horizon.caadronesimulator.USB_PERMISSION"
    private val droneState = DroneState.getInstance()
    
    private var serialPort: UsbSerialPort? = null
    private var ioManager: SerialInputOutputManager? = null
    private var internalSerialThread: Thread? = null
    private var currentFis: FileInputStream? = null
    private var udpSocket: DatagramSocket? = null
    private var networkThread: Thread? = null
    private val isRunning = AtomicBoolean(false)
    private var currentBaudRate = 115200 
    private val uiHandler = Handler(android.os.Looper.getMainLooper())

    private val packetCount = AtomicInteger(0)
    private var pps = 0
    private var totalBytesRead = 0
    private var lastValidPacketTime = 0L
    private var lastDiagnosticReportTime = 0L
    private var lastPpsCalcTime = 0L
    private var detectedProtocolName = "待機"
    private var activeLinkPath = "None"
    private val diagLogBuffer = mutableListOf<String>()

    private var lastPacketTimestamp = 0L
    private var jitterSum = 0L
    private var jitterSamples = 0
    private var currentJitterMs = 0f

    private val matrixPaths = listOf("/dev/ttyS0", "NETWORK", "/dev/ttyHS0", "USB", "/dev/ttyUSB0", "/dev/ttyS1")
    private var matrixPathIndex = 0
    private var matrixBaudIndex = 0
    private val matrixBauds = listOf(921600, 115200, 460800)
    
    private var consecutiveValidFrames = 0
    private var lastProbeMatchProtocol = ""
    private val assemblyBuffer = ByteArray(4096) 
    private var assemblyPos = 0
    private var detectionStartTime = 0L
    private var isUserStopped = true

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val device = IntentCompat.getParcelableExtra(intent, UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
            when (intent.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    if (isRelevantDevice(device)) {
                        // [v1.8.36] 智慧通訊主權：偵測到專業硬體或 HID 手把，自動切換至「外接」模式並鎖定主權
                        if (device?.vendorId == 0x2E3C || isHidJoystick(device)) {
                            droneState.inputMode = 0
                            droneState.isUsbStickyActive = true
                            stopInternalOnly() // 關閉所有後台掃描與網路監聽
                            droneState.systemMessage = "🎮 已偵測到 ${if(device?.vendorId == 0x2E3C) "RadioMaster/AX12" else "外接手把"}，已自動跳轉外接模式"
                        }
                        requestUsbPermission(device!!)
                    }
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    if (activeLinkPath == "USB") stopAll()
                    if (droneState.inputMode == 0 && isHidJoystick(device)) {
                        droneState.isUsbStickyActive = false // 拔除後解除主權鎖定
                        handleControllerLoss()
                    }
                }
                actionUsbPermission -> {
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        device?.apply { startUsbReading(this) }
                    } else {
                        updateDecisionState(CommDecisionState.SCANNING)
                        if (!isUserStopped) nextInMatrix()
                    }
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

    private fun handleControllerLoss() {
        if (droneState.isHardwareController) {
            droneState.inputMode = 1; droneState.systemMessage = "外接手把已中斷，自動切換回內置系統"; scanAndConnect()
        } else {
            droneState.inputMode = -1; droneState.showVirtualJoysticks = true; droneState.systemMessage = "外接手把已中斷，已開啟虛擬搖桿"
        }
    }

    fun register(ctx: Context) {
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            addAction(actionUsbPermission)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ctx.registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            ctx.registerReceiver(usbReceiver, filter)
        }
    }

    fun unregister(ctx: Context) {
        try { ctx.unregisterReceiver(usbReceiver) } catch (_: Exception) {}
        stopAll()
    }

    private val diagnosticRunnable = object : Runnable {
        override fun run() {
            performDiagnosticCheck()
            uiHandler.postDelayed(this, 500)
        }
    }

    init { uiHandler.post(diagnosticRunnable) }

    private fun updateDecisionState(newState: CommDecisionState) {
        if (droneState.commDecisionState != newState) {
            droneState.commDecisionState = newState
            addLogEntry("系統狀態變更 ➔ $newState")
            synchronized(assemblyBuffer) { assemblyPos = 0; packetCount.set(0); consecutiveValidFrames = 0 }
        }
    }

    fun setBaudRate(baud: Int) {
        currentBaudRate = baud; updateDecisionState(CommDecisionState.LOCKED)
        addLogEntry("手動鎖定波特率: $baud"); if (!isUserStopped) scanAndConnect()
    }

    fun setLockedPath(path: String) {
        droneState.lockedSerialPath = path; updateDecisionState(CommDecisionState.LOCKED)
        addLogEntry("手動鎖定路徑: $path"); matrixPathIndex = matrixPaths.indexOf(path).coerceAtLeast(0)
        if (!isUserStopped) scanAndConnect()
    }

    fun setLockedProtocol(protocol: String) {
        droneState.lockedProtocol = protocol
        if (protocol.isEmpty()) { updateDecisionState(CommDecisionState.SCANNING); addLogEntry("重置為自動偵測模式") } 
        else { updateDecisionState(CommDecisionState.LOCKED); detectedProtocolName = protocol; addLogEntry("手動鎖定協議: $protocol") }
        if (!isUserStopped) scanAndConnect()
    }

    fun toggleConnection() {
        if (droneState.connectionStatus != ConnectionStatus.IDLE) stopAll() 
        else { isUserStopped = false; addLogEntry("使用者手動啟動掃描..."); matrixPathIndex = 0; matrixBaudIndex = 0; updateDecisionState(CommDecisionState.SCANNING); scanAndConnect() }
    }

    fun scanAndConnect() {
        if (isUserStopped) return
        synchronized(this) {
            // [v1.8.36] USB 主權保護：若 USB 外接鎖定中，則不啟動背景矩陣掃描
            if (droneState.isUsbStickyActive && droneState.inputMode == 0) {
                addLogEntry("USB 主權鎖定中，暫停背景自動掃描。")
                return
            }

            stopInternalOnly(); detectionStartTime = System.currentTimeMillis()
            if (droneState.inputMode == 2) { updateDecisionState(CommDecisionState.IDLE); return }
            val forcedPath = droneState.lockedSerialPath
            if (forcedPath.isNotEmpty() || droneState.commDecisionState == CommDecisionState.LOCKED) {
                val path = if (forcedPath.isNotEmpty()) forcedPath else matrixPaths[matrixPathIndex]
                activeLinkPath = path; startReadingByPath(path); return
            }
            val path = matrixPaths.getOrNull(matrixPathIndex) ?: run { stopAll(); return }
            val baud = matrixBauds.getOrNull(matrixBaudIndex) ?: 115200
            currentBaudRate = baud; activeLinkPath = path; addLogEntry("🔍 探測路徑: $path @ $baud"); startReadingByPath(path)
        }
    }

    private fun startReadingByPath(path: String) {
        when(path) {
            "USB" -> {
                val drivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
                if (drivers.isNotEmpty()) {
                    val driver = drivers[0]
                    if (usbManager.hasPermission(driver.device)) startUsbReading(driver.device)
                    else { updateDecisionState(CommDecisionState.AWAITING_PERMISSION); requestUsbPermission(driver.device) }
                } else nextInMatrix()
            }
            "NETWORK" -> { activeLinkPath = "NETWORK"; startUdpReading(droneState.networkHost, droneState.networkPort) }
            else -> startInternalReading(path)
        }
    }

    private fun nextInMatrix() {
        if (isUserStopped || droneState.commDecisionState == CommDecisionState.LOCKED) return
        uiHandler.postDelayed({
            if (isUserStopped) return@postDelayed
            matrixBaudIndex++; if (matrixBaudIndex >= matrixBauds.size) { matrixBaudIndex = 0; matrixPathIndex++; if (matrixPathIndex >= matrixPaths.size) matrixPathIndex = 0 }
            scanAndConnect()
        }, 300)
    }

    private fun requestUsbPermission(device: UsbDevice) {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) android.app.PendingIntent.FLAG_MUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT else android.app.PendingIntent.FLAG_UPDATE_CURRENT
        val pi = android.app.PendingIntent.getBroadcast(context, 0, Intent(actionUsbPermission), flags); usbManager.requestPermission(device, pi)
    }

    private fun startUsbReading(device: UsbDevice) {
        val connection = usbManager.openDevice(device) ?: run { nextInMatrix(); return }
        val drivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
        if (drivers.isEmpty()) return
        serialPort = drivers[0].ports[0]
        try {
            serialPort?.open(connection); serialPort?.setParameters(currentBaudRate, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            flushHardwareBuffer(serialPort); ioManager = SerialInputOutputManager(serialPort, object : SerialInputOutputManager.Listener {
                override fun onNewData(data: ByteArray) { handleRawIncoming(data, "USB") }
                override fun onRunError(e: Exception) { if (!isUserStopped) nextInMatrix() }
            }); ioManager?.start(); activeLinkPath = "USB"; isRunning.set(true); updateConnectionStatus(ConnectionStatus.LINKED)
        } catch (e: Exception) { nextInMatrix() }
    }

    private fun flushHardwareBuffer(port: UsbSerialPort?) {
        try { val dummy = ByteArray(1024); var r: Int; do { r = port?.read(dummy, 10) ?: 0 } while (r > 0) } catch (_: Exception) {}
    }

    private fun startInternalReading(path: String) {
        val file = File(path); if (!file.exists()) { nextInMatrix(); return }
        isRunning.set(true)
        internalSerialThread = Thread {
            var myFis: FileInputStream? = null
            try {
                if (!file.canRead()) { Runtime.getRuntime().exec("chmod 666 $path").waitFor() }
                if (!file.canRead()) { updateDecisionState(CommDecisionState.ERROR_PERMISSION); return@Thread }
                Runtime.getRuntime().exec("stty -F $path $currentBaudRate raw -echo").waitFor(); Thread.sleep(100) 
                myFis = FileInputStream(file); currentFis = myFis; activeLinkPath = path; updateConnectionStatus(ConnectionStatus.LINKED)
                val buf = ByteArray(2048)
                while (isRunning.get() && !Thread.currentThread().isInterrupted) {
                    val read = try { myFis.read(buf) } catch (e: Exception) { -1 }; if (read <= 0) break
                    handleRawIncoming(buf.copyOf(read), path)
                }
            } catch (e: Exception) { if (!isUserStopped) nextInMatrix() } finally { try { myFis?.close() } catch (_: Exception) {}; isRunning.set(false) }
        }.apply { name = "SerialEngine-V3"; priority = Thread.MAX_PRIORITY; start() }
    }

    fun handleRawIncoming(data: ByteArray, path: String) {
        totalBytesRead += data.size; if (activeLinkPath != path && isRunning.get()) return
        synchronized(assemblyBuffer) {
            if (assemblyPos + data.size > assemblyBuffer.size) assemblyPos = 0
            System.arraycopy(data, 0, assemblyBuffer, assemblyPos, data.size); assemblyPos += data.size
            if (totalBytesRead % 50 < data.size) { droneState.rawHexData = data.take(12).joinToString(" ") { "%02X".format(it) } }
            var i = 0
            while (i < assemblyPos) {
                val u = assemblyBuffer[i].toInt() and 0xFF; var consumed = 0
                val canDetectCRSF = droneState.lockedProtocol.isEmpty() || droneState.lockedProtocol == "CRSF"
                val canDetectUMBUS = droneState.lockedProtocol.isEmpty() || droneState.lockedProtocol.contains("UMBUS")
                if (u == 0xA6 && canDetectUMBUS) { 
                    if (i + 1 < assemblyPos) {
                        val type = assemblyBuffer[i+1].toInt() and 0xFF; val label = if (type == 0x55 || type == 0xAA) "AX-Enhanced" else "AX12(UMBUS)"
                        val len = if (label == "AX-Enhanced") 24 else 12; if (i + len <= assemblyPos) { markSuccessfulPacket(label, path); consumed = len }
                    }
                } else if ((u == 0xC8 || u == 0x81) && canDetectCRSF) {
                    if (i + 1 < assemblyPos) {
                        val pLen = (assemblyBuffer[i+1].toInt() and 0xFF) + 2; if (i + pLen <= assemblyPos) { markSuccessfulPacket("CRSF", path); consumed = pLen }
                    }
                }
                if (consumed > 0) i += consumed else i++
            }
            if (i > 0) { val rem = assemblyPos - i; if (rem > 0) System.arraycopy(assemblyBuffer, i, assemblyBuffer, 0, rem); assemblyPos = rem }
        }
    }

    private fun markSuccessfulPacket(proto: String, path: String) {
        val now = System.currentTimeMillis(); if (lastPacketTimestamp != 0L) { val interval = now - lastPacketTimestamp; jitterSum += abs(interval - 20); jitterSamples++ }
        lastPacketTimestamp = now; lastValidPacketTime = now; packetCount.incrementAndGet()
        if (proto == lastProbeMatchProtocol) consecutiveValidFrames++ else { lastProbeMatchProtocol = proto; consecutiveValidFrames = 1 }
        if (consecutiveValidFrames >= 3) {
            if (droneState.commDecisionState == CommDecisionState.SCANNING) {
                val profile = HardwareRegistry.detectHardware()
                if (profile.id != "GENERIC_MOBILE" && profile.driver?.isAutoPromptEnabled == true) updateDecisionState(CommDecisionState.ENGAGED) 
                else { detectedProtocolName = "✅ $proto"; onProtocolDetected(proto); updateDecisionState(CommDecisionState.LOCKED) }
            }
        }
    }

    private fun performDiagnosticCheck() {
        val now = System.currentTimeMillis()
        if (now - lastPpsCalcTime >= 1000) { pps = packetCount.getAndSet(0); lastPpsCalcTime = now; if (jitterSamples > 0) { currentJitterMs = jitterSum.toFloat() / jitterSamples; jitterSum = 0; jitterSamples = 0 } }
        if (now - lastDiagnosticReportTime >= 500) {
            lastDiagnosticReportTime = now; val isSignalActive = (now - lastValidPacketTime < 1500) && (pps > 0)
            if (isSignalActive) updateConnectionStatus(ConnectionStatus.ACTIVE) 
            else if (!isUserStopped) { updateConnectionStatus(ConnectionStatus.SEARCHING); if (now - detectionStartTime > 3500 && droneState.commDecisionState == CommDecisionState.SCANNING) nextInMatrix() } 
            else updateConnectionStatus(ConnectionStatus.IDLE)
            val stability = (100 - (currentJitterMs * 2)).coerceIn(0f, 100f)
            val diagMap = mapOf("pps" to pps, "protocol" to detectedProtocolName, "baud" to currentBaudRate, "is_signal_active" to isSignalActive, "jitter" to "%.1f ms".format(currentJitterMs), "stability" to "${stability.toInt()}%", "buffer_usage" to "$assemblyPos/4096", "state" to droneState.commDecisionState.name)
            onDiagnosticUpdate(if (isSignalActive) "SIGNAL OK" else "NO SIGNAL", activeLinkPath, getFullLog(), diagMap); totalBytesRead = 0
        }
    }

    private fun updateConnectionStatus(status: ConnectionStatus) { onConnectionStatusUpdate(status) }

    private fun stopInternalOnly() {
        isRunning.set(false); internalSerialThread?.interrupt(); try { currentFis?.close() } catch (_: Exception) {}; currentFis = null
        udpSocket?.close(); udpSocket = null; networkThread?.interrupt(); networkThread = null
        ioManager?.stop(); ioManager = null; serialPort?.close(); serialPort = null
    }

    fun stopAll() {
        isUserStopped = true; stopInternalOnly(); updateDecisionState(CommDecisionState.IDLE); packetCount.set(0); pps = 0; activeLinkPath = "None"; detectedProtocolName = "待機"
        onDiagnosticUpdate("已停止", "None", getFullLog(), mapOf("pps" to 0, "protocol" to "待機", "is_signal_active" to false)); updateConnectionStatus(ConnectionStatus.IDLE)
    }

    private fun startUdpReading(host: String, port: Int) {
        isRunning.set(true)
        networkThread = Thread {
            try {
                val address = InetAddress.getByName(host); udpSocket = DatagramSocket(port, address); udpSocket?.soTimeout = 1000 
                addLogEntry("🌐 UDP 監聽中 $host:$port"); updateConnectionStatus(ConnectionStatus.LINKED)
                val buffer = ByteArray(2048); val packet = DatagramPacket(buffer, buffer.size)
                while (isRunning.get() && !Thread.currentThread().isInterrupted) {
                    try { udpSocket?.receive(packet); if (packet.length > 0) handleRawIncoming(packet.data.copyOfRange(0, packet.length), "NETWORK") } 
                    catch (_: java.net.SocketTimeoutException) {} catch (e: Exception) { if (isRunning.get()) Log.e("CommManager", "UDP Receive Error: ${e.message}") }
                }
            } catch (e: Exception) { addLogEntry("❌ UDP 啟動失敗: ${e.message}"); if (!isUserStopped) nextInMatrix() } 
            finally { udpSocket?.close(); udpSocket = null }
        }.apply { name = "UdpEngine-V3"; start() }
    }

    fun acceptEngagedProtocol() { updateDecisionState(CommDecisionState.LOCKED); addLogEntry("使用者確認套用協議，系統已鎖定。") }

    fun listAvailableSerialPorts(): List<String> {
        val ports = mutableListOf<String>("USB", "NETWORK")
        try { File("/dev/").listFiles { _, name -> name.startsWith("ttyS") || name.startsWith("ttyUSB") || name.startsWith("ttyHS") }?.forEach { ports.add(it.absolutePath) } } catch (_: Exception) {}
        return ports.sorted()
    }

    fun injectLog(msg: String) { addLogEntry(msg) }
    private fun addLogEntry(msg: String) {
        diagLogBuffer.add("[${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())}] $msg")
        if (diagLogBuffer.size > 50) diagLogBuffer.removeAt(0)
    }
    fun getFullLog() = diagLogBuffer.joinToString("\n")
    fun isUserStoppedManually(): Boolean = isUserStopped

    companion object {
        fun isRelevantDevice(device: UsbDevice?): Boolean {
            if (device == null) return false
            for (i in 0 until device.interfaceCount) { if (device.getInterface(i).interfaceClass == 3) return true }
            return listOf(0x10C4, 0x1A86, 0x0403, 0x067B, 0x2341, 0x2E3C, 0x1204).contains(device.vendorId)
        }
    }
}
