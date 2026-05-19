package com.horizon.caadronesimulator.logic

import android.content.Context
import com.horizon.caadronesimulator.model.DroneState
import com.horizon.caadronesimulator.model.ConnectionStatus

/**
 * [v1.7.6] 專業版中的 USB 通訊轉接器
 * 職責：在 Pro 版本中，將通用 USB 請求導向 InternalCommManager 或保持相容性。
 * 目的：解決 Pro 版編譯時找不到 UsbSerialManager 的斷鏈問題。
 */
class UsbSerialManager(
    private val context: Context,
    private val droneState: DroneState,
    private val onRawChannelsReceived: (List<Float>) -> Unit,
    private val onConnectionStatusUpdate: (ConnectionStatus) -> Unit
) {
    // 在 Pro 版中，這些功能通常由 InternalCommManager 處理
    fun toggleConnection() {
        ProHardwareBridge.internalCommManager.toggleConnection()
    }

    fun listAvailableSerialPorts(): List<String> {
        val ports = mutableListOf<String>("USB", "NETWORK")
        try {
            java.io.File("/dev/").listFiles { _, name -> name.startsWith("ttyS") || name.startsWith("ttyUSB") || name.startsWith("ttyHS") }?.forEach { ports.add(it.absolutePath) }
        } catch (_: Exception) {}
        return ports.sorted()
    }

    fun setLockedPath(path: String) {
        ProHardwareBridge.internalCommManager.setLockedPath(path)
    }

    fun setLockedProtocol(protocol: String) {
        ProHardwareBridge.internalCommManager.setLockedProtocol(protocol)
    }

    fun startReadingByPath(path: String) {
        // 直接橋接至 Pro 鏈路
        ProHardwareBridge.internalCommManager.scanAndConnect()
    }

    fun stopAll() {
        ProHardwareBridge.internalCommManager.stopAll()
    }
}
