package com.horizon.caadronesimulator.logic

import android.content.Context
import android.hardware.input.InputManager
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.view.InputDevice
import com.horizon.caadronesimulator.model.DroneState
import kotlinx.coroutines.*

/**
 * [v1.7.6] 通訊主權協調員 (Connectivity Coordinator)
 * 職責：負責硬體自動感知、身分分類 (Pro vs Store) 與連線決策。
 * 接管原 MainActivity 中的硬體掃描邏輯，解決「名實不符」的問題。
 */
class ConnectivityCoordinator(
    private val context: Context,
    private val droneState: DroneState,
    private val internalComm: InternalCommManager,
    private val onAutoConnect: () -> Unit
) {
    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private val inputManager = context.getSystemService(Context.INPUT_SERVICE) as InputManager
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    /**
     * 執行硬體環境掃描與自動連線決策
     */
    fun performAutoSensing() {
        scope.launch {
            delay(300)
            
            // 1. 偵測內置專業硬體 (Pro Link)
            val hasProHardware = usbManager.deviceList.values.any { dev ->
                if (isProHardware(dev)) {
                    if (!droneState.isUsbStickyActive) {
                        droneState.inputMode = 0
                        droneState.isUsbStickyActive = true
                        internalComm.stopAll()
                        droneState.systemMessage = "🔗 偵測到內置專業硬體，已自動對接"
                    }
                    true
                } else false
            }

            delay(200)

            // 2. 偵測通用 HID 手把 (Store Link)
            val hasHidJoystick = inputManager.inputDeviceIds.any { id ->
                val dev = inputManager.getInputDevice(id)
                dev != null && (dev.sources and InputDevice.SOURCE_JOYSTICK) != 0
            }

            // 3. 自動連線邏輯
            if (droneState.inputMode == 1 && !droneState.usbSerialConnected) {
                onAutoConnect()
            } else if (droneState.isAutoConnectEnabled && !droneState.usbSerialConnected) {
                onAutoConnect()
            }

            // 4. 手把狀態回退 (Fallback)
            if (hasHidJoystick && droneState.inputMode == -1) {
                droneState.inputMode = 0
            } else if (!hasHidJoystick && !hasProHardware && droneState.inputMode == 0) {
                droneState.systemMessage = "未偵測到外接手把，已切換至虛擬搖桿"
                droneState.inputMode = -1
                droneState.showVirtualJoysticks = true
            }

            // 5. 安全鎖定同步
            if (droneState.inputMode == -1 || droneState.inputMode == 2) {
                droneState.isArmSafetyPassed = true
                droneState.isHoldSafetyPassed = true
            }
        }
    }

    /**
     * 判斷是否為專業內置硬體 (RadioMaster/AX12 系列)
     */
    private fun isProHardware(device: UsbDevice): Boolean {
        // 匹配 VID 0x2E3C 或特定介面類別
        if (device.vendorId == 0x2E3C) return true
        for (i in 0 until device.interfaceCount) {
            if (device.getInterface(i).interfaceClass == 3) return true
        }
        return false
    }

    fun shutdown() {
        scope.cancel()
    }
}
