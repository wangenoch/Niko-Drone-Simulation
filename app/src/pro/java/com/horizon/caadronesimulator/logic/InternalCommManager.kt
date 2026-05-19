package com.horizon.caadronesimulator.logic

import android.content.Context
import android.os.Build
import android.os.Handler
import com.horizon.caadronesimulator.model.DroneState
import com.horizon.caadronesimulator.model.ConnectionStatus
import com.horizon.caadronesimulator.model.CommDecisionState
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import kotlin.math.abs

/**
 * [v1.7.6] 內置專業通訊管理器 (Internal Pro Comm Manager)
 * 職責：專注於「內置 (Pro)」鏈路，包括 /dev/ttyS0 讀取、UMBUS 協議解析與系統診斷。
 */
class InternalCommManager(
    private val context: Context,
    private val onStatusUpdate: (Boolean, String) -> Unit,
    private val onDataReceived: (Float, Float, Float, Float) -> Unit,
    private val onRawChannelsReceived: (List<Float>) -> Unit,
    private val onDiagnosticUpdate: (String, String, String, Map<String, Any>) -> Unit,
    private val onProtocolDetected: (String) -> Unit,
    private val onConnectionStatusUpdate: (ConnectionStatus) -> Unit
) {
    private val droneState = DroneState.getInstance()
    private val uiHandler = Handler(android.os.Looper.getMainLooper())
    private val isRunning = AtomicBoolean(false)
    private var internalSerialThread: Thread? = null
    private var currentFis: FileInputStream? = null
    
    private var currentBaudRate = 115200 
    private val packetCount = AtomicInteger(0)
    private var pps = 0
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

    private val matrixPaths = listOf("/dev/ttyS0", "/dev/ttyHS0", "/dev/ttyS1")
    private var matrixPathIndex = 0
    private var matrixBaudIndex = 0
    private val matrixBauds = listOf(921600, 115200, 460800)
    
    private var consecutiveValidFrames = 0
    private var lastProbeMatchProtocol = ""
    private var activeDriver: com.horizon.caadronesimulator.logic.drivers.InternalDeviceDriver? = null
    private val assemblyBuffer = ByteArray(4096) 
    private var assemblyPos = 0
    private var detectionStartTime = 0L
    private var isUserStopped = true

    private var logcatJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun toggleLogcat(enabled: Boolean) {
        logcatJob?.cancel()
        if (!enabled) return
        logcatJob = scope.launch {
            try {
                val process = Runtime.getRuntime().exec("logcat -v time")
                val reader = java.io.BufferedReader(java.io.InputStreamReader(process.inputStream))
                while (enabled && isActive) {
                    if (reader.ready()) {
                        val line = reader.readLine() ?: break
                        if (line.contains("Niko") || line.contains("Serial") || line.contains("Drone")) {
                            withContext(Dispatchers.Main) {
                                val lines = droneState.logcatContent.lines().takeLast(100)
                                droneState.logcatContent = (lines + line).joinToString("\n")
                            }
                        }
                    } else delay(100)
                }
                process.destroy()
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { droneState.logcatContent = "Logcat 啟動失敗: ${e.message}" }
            }
        }
    }

    fun tryExportReport(activity: android.app.Activity, onPermissionNeeded: (String) -> Unit) {
        if (!droneState.isLogcatEnabled) { droneState.systemMessage = "📋 請先開啟 [即時監測 Logcat] 以收集診斷數據"; return }
        if (Build.VERSION.SDK_INT <= 28) {
            val perm = android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            if (androidx.core.content.ContextCompat.checkSelfPermission(activity, perm) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                droneState.systemMessage = "🔐 請允許儲存權限以匯出日誌"
                onPermissionNeeded(perm)
                return
            }
        }
        exportDiagnosticReport(activity)
    }

    fun exportDiagnosticReport(context: Context) {
        val physicalLog = getFullLog()
        if (physicalLog.isEmpty()) { droneState.systemMessage = "⏳ 正在收集初始數據，請操作搖桿幾秒後再試"; return }
        com.horizon.caadronesimulator.util.LogExporter.exportDiagnosticLog(context, droneState, physicalLog, { droneState.systemMessage = it }, { droneState.systemMessage = it })
    }

    fun register(ctx: Context) { /* Pro 鏈路目前不需監聽 USB 廣播，由 Coordinator 觸發 */ }
    fun unregister(ctx: Context) { stopAll() }

    private val diagnosticRunnable = object : Runnable {
        override fun run() { performDiagnosticCheck(); uiHandler.postDelayed(this, 500) }
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
        else {
            updateDecisionState(CommDecisionState.LOCKED); detectedProtocolName = protocol; addLogEntry("手動鎖定協議: $protocol")
            if (protocol.contains("UMBUS", ignoreCase = true)) {
                this.currentBaudRate = 921600
                droneState.lockedSerialPath = "/dev/ttyS0"
                matrixPathIndex = matrixPaths.indexOf("/dev/ttyS0").coerceAtLeast(0)
                addLogEntry("UMBUS 智慧連動：已預填 /dev/ttyS0 與 921600 波特率")
            }
        }
        if (!isUserStopped) scanAndConnect()
    }

    fun toggleConnection() {
        if (droneState.connectionStatus != ConnectionStatus.IDLE) stopAll() 
        else { isUserStopped = false; addLogEntry("使用者手動啟動掃描..."); matrixPathIndex = 0; matrixBaudIndex = 0; updateDecisionState(CommDecisionState.SCANNING); scanAndConnect() }
    }

    fun scanAndConnect() {
        if (isUserStopped) return
        synchronized(this) {
            stopInternalOnly(); detectionStartTime = System.currentTimeMillis()
            val forcedPath = droneState.lockedSerialPath
            if (forcedPath.isNotEmpty() || droneState.commDecisionState == CommDecisionState.LOCKED) {
                val path = if (forcedPath.isNotEmpty()) forcedPath else matrixPaths[matrixPathIndex]
                activeLinkPath = path; startInternalReading(path); return
            }
            val path = matrixPaths.getOrNull(matrixPathIndex) ?: run { stopAll(); return }
            val baud = matrixBauds.getOrNull(matrixBaudIndex) ?: 115200
            currentBaudRate = baud; activeLinkPath = path; addLogEntry("🔍 探測內置路徑: $path @ $baud"); startInternalReading(path)
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

    private fun startInternalReading(path: String) {
        val file = File(path); if (!file.exists()) { nextInMatrix(); return }
        isRunning.set(true)
        internalSerialThread = Thread {
            var myFis: FileInputStream? = null
            try {
                myFis = FileInputStream(file)
                if (myFis.fd == null || !myFis.fd.valid()) throw java.io.IOException("Invalid FD")
                currentFis = myFis; activeLinkPath = path; updateConnectionStatus(ConnectionStatus.LINKED)
                addLogEntry("✅ 物理層開啟成功: $path")
                val buf = ByteArray(2048)
                while (isRunning.get() && !Thread.currentThread().isInterrupted) {
                    val read = try { myFis.read(buf) } catch (e: Exception) { -1 }; if (read <= 0) break
                    handleRawIncoming(buf.copyOf(read), path)
                }
            } catch (e: Exception) { addLogEntry("❌ 開啟失敗: ${e.message}"); if (!isUserStopped) nextInMatrix() } 
            finally { try { myFis?.close() } catch (_: Exception) {}; isRunning.set(false) }
        }.apply { name = "InternalProEngine"; priority = Thread.MAX_PRIORITY; start() }
    }

    fun handleRawIncoming(data: ByteArray, path: String) {
        if (activeLinkPath != path && isRunning.get()) return
        synchronized(assemblyBuffer) {
            if (assemblyPos + data.size > assemblyBuffer.size) assemblyPos = 0
            System.arraycopy(data, 0, assemblyBuffer, assemblyPos, data.size); assemblyPos += data.size
            var i = 0
            while (i < assemblyPos) {
                val u = assemblyBuffer[i].toInt() and 0xFF; var consumed = 0
                if (u == 0xA6) { 
                    if (i + 5 <= assemblyPos) {
                        val isV2 = assemblyBuffer[i+2].toInt() == 0x10 && assemblyBuffer[i+3].toInt() == 0x02
                        val len = 87
                        if (i + len <= assemblyPos) { 
                            val packet = assemblyBuffer.copyOfRange(i, i + len)
                            if (activeDriver == null) activeDriver = if (isV2) com.horizon.caadronesimulator.logic.drivers.AX12V2Driver() else com.horizon.caadronesimulator.logic.drivers.AX12Driver()
                            activeDriver?.parseRaw(java.nio.ByteBuffer.wrap(packet).order(java.nio.ByteOrder.LITTLE_ENDIAN))?.let {
                                onRawChannelsReceived(it); if (it.size >= 4) onDataReceived(it[0], it[1], it[2], it[3])
                            }
                            markSuccessfulPacket(if (isV2) "RadioMaster AX12 (UMBUS-V2)" else "RadioMaster AX12 (UMBUS-V1)", path); consumed = len 
                        }
                    }
                } else if (u == 0xC8 || u == 0x81) {
                    if (i + 1 < assemblyPos) { val pLen = (assemblyBuffer[i+1].toInt() and 0xFF) + 2; if (i + pLen <= assemblyPos) { markSuccessfulPacket("CRSF", path); consumed = pLen } }
                }
                if (consumed > 0) i += consumed else i++
            }
            if (i > 0) { val rem = assemblyPos - i; if (rem > 0) System.arraycopy(assemblyBuffer, i, assemblyBuffer, 0, rem); assemblyPos = rem }
        }
    }

    private fun markSuccessfulPacket(proto: String, path: String) {
        val now = System.currentTimeMillis(); if (lastPacketTimestamp != 0L) { jitterSum += abs((now - lastPacketTimestamp) - 20); jitterSamples++ }
        lastPacketTimestamp = now; lastValidPacketTime = now; packetCount.incrementAndGet()
        if (proto == lastProbeMatchProtocol) consecutiveValidFrames++ else { lastProbeMatchProtocol = proto; consecutiveValidFrames = 1 }
        if (consecutiveValidFrames >= 3 && droneState.commDecisionState == CommDecisionState.SCANNING) {
            detectedProtocolName = "✅ $proto"; onProtocolDetected(proto); updateDecisionState(CommDecisionState.LOCKED)
        }
    }

    private fun performDiagnosticCheck() {
        val now = System.currentTimeMillis()
        if (now - lastPpsCalcTime >= 1000) { pps = packetCount.getAndSet(0); lastPpsCalcTime = now; if (jitterSamples > 0) { currentJitterMs = jitterSum.toFloat() / jitterSamples; jitterSum = 0; jitterSamples = 0 } }
        if (now - lastDiagnosticReportTime >= 500) {
            lastDiagnosticReportTime = now; val isSignalActive = (now - lastValidPacketTime < 1500) && (pps > 0)
            if (isSignalActive) onConnectionStatusUpdate(ConnectionStatus.ACTIVE) 
            else if (!isUserStopped) { onConnectionStatusUpdate(ConnectionStatus.SEARCHING); if (now - detectionStartTime > 3500 && droneState.commDecisionState == CommDecisionState.SCANNING) nextInMatrix() } 
            else onConnectionStatusUpdate(ConnectionStatus.IDLE)
            val diagMap = mapOf("pps" to pps, "protocol" to detectedProtocolName, "baud" to currentBaudRate, "is_signal_active" to isSignalActive, "jitter" to "%.1f ms".format(currentJitterMs), "stability" to "100%", "buffer_usage" to "$assemblyPos/4096", "state" to droneState.commDecisionState.name)
            onDiagnosticUpdate(if (isSignalActive) "SIGNAL OK" else "NO SIGNAL", activeLinkPath, getFullLog(), diagMap)
        }
    }

    private fun updateConnectionStatus(status: ConnectionStatus) { onConnectionStatusUpdate(status) }
    private fun stopInternalOnly() { isRunning.set(false); internalSerialThread?.interrupt(); try { currentFis?.close() } catch (_: Exception) {}; currentFis = null; activeDriver = null }
    fun stopAll() { isUserStopped = true; stopInternalOnly(); updateDecisionState(CommDecisionState.IDLE); packetCount.set(0); pps = 0; activeLinkPath = "None"; detectedProtocolName = "待機"; onConnectionStatusUpdate(ConnectionStatus.IDLE) }
    fun injectLog(msg: String) { addLogEntry(msg) }
    private fun addLogEntry(msg: String) { diagLogBuffer.add("[${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())}] $msg"); if (diagLogBuffer.size > 50) diagLogBuffer.removeAt(0) }
    fun getFullLog() = diagLogBuffer.joinToString("\n")
}
