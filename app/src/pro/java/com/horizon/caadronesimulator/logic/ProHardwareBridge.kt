package com.horizon.caadronesimulator.logic

import androidx.activity.ComponentActivity
import com.horizon.caadronesimulator.model.DroneState
import com.horizon.caadronesimulator.model.StickInputState
import com.horizon.caadronesimulator.logic.storage.ConfigurationStore

/**
 * [v1.7.6] 專業版硬體橋接器 (Pro Hardware Bridge)
 * 職責：整合 InternalCommManager 與 ConnectivityCoordinator 的初始化與生命週期管理。
 * 目的：徹底淨化 MainActivity，將所有專業硬體通訊邏輯從主入口抽離。
 */
object ProHardwareBridge {
    private var _internalCommManager: InternalCommManager? = null
    val internalCommManager: InternalCommManager get() = _internalCommManager!!
    
    private var _connectivityCoordinator: ConnectivityCoordinator? = null

    fun initialize(
        activity: ComponentActivity,
        droneState: DroneState,
        stickInputState: StickInputState,
        configStore: ConfigurationStore
    ) {
        // [v1.7.6] 注入驅動實體，解決 src/main 找不到 src/pro 驅動程式的問題
        HardwareRegistry.driverProvider = { id ->
            when(id) {
                "RM_AX12_V1" -> com.horizon.caadronesimulator.logic.drivers.AX12Driver()
                "RM_AX12_V2" -> com.horizon.caadronesimulator.logic.drivers.AX12V2Driver()
                "QUALCOMM MK15" -> com.horizon.caadronesimulator.logic.drivers.MK15Driver()
                else -> null
            }
        }

        _internalCommManager = InternalCommManager(activity, 
            onStatusUpdate = { connected, message -> 
                droneState.usbSerialConnected = connected
                if (message.isNotEmpty()) droneState.systemMessage = message
                if (connected) droneState.wasInternalSuccess = true 
            },
            onDataReceived = { lsv, lsh, rsv, rsh ->
                if (droneState.inputMode == 0) return@InternalCommManager
                stickInputState.updateRaw(lsv, lsh, rsv, rsh)
                stickInputState.serialByteCount++
            },
            onRawChannelsReceived = { channels ->
                InputCoordinator.processSerialInput(channels, droneState, stickInputState, internalCommManager)
            },
            onDiagnosticUpdate = { _, path, log, extra ->
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
            onConnectionStatusUpdate = { status -> droneState.connectionStatus = status }
        )

        _connectivityCoordinator = ConnectivityCoordinator(activity, droneState, internalCommManager) {
            internalCommManager.scanAndConnect()
        }

        internalCommManager.setBaudRate(droneState.baudRate)
        internalCommManager.register(activity)
    }

    fun onResume() {
        _connectivityCoordinator?.performAutoSensing()
    }

    fun onStop() {
        _internalCommManager?.stopAll()
    }

    fun onDestroy(activity: ComponentActivity) {
        _internalCommManager?.unregister(activity)
        _connectivityCoordinator?.shutdown()
        // [v1.7.6] 徹底釋放引用，防止記憶體洩漏
        _internalCommManager = null
        _connectivityCoordinator = null
    }
}
