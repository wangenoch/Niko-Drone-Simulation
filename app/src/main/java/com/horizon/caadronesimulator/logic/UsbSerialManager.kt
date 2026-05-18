package com.horizon.caadronesimulator.logic

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import com.horizon.caadronesimulator.model.DroneState
import com.horizon.caadronesimulator.model.ConnectionStatus

/**
 * [v1.6.3] 通用 USB 通訊管理器 (Store-Compliant Serial Manager)
 * 職責：處理合規的外接 USB-Serial OTG、網路 UDP 數據與標準 USB 權限。
 * 這是「合規版 (Store)」的通訊核心，不涉及任何內部 /dev/ 節點。
 */
class UsbSerialManager(
    private val context: Context,
    private val droneState: DroneState,
    private val onConnectionStatusUpdate: (ConnectionStatus) -> Unit
) {
    // 這裡目前保留外接 USB 的占位，待後續將 MainActivity 中的 HID 邏輯與外接邏輯遷移至此
    fun toggleConnection() {
        // 合規掃描邏輯...
    }

    fun listAvailableSerialPorts(): List<String> {
        return listOf("USB", "NETWORK")
    }

    fun setLockedPath(path: String) {
        // ...
    }

    fun stopAll() {
        // ...
    }
}
